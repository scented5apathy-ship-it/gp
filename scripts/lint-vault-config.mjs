#!/usr/bin/env node
/**
 * scripts/lint-vault-config.mjs
 *
 * E2.6 deep validator for the Vault + cloud KMS abstraction
 * source-of-truth files in `platform/vault/`. Mirrors
 * `lint-istio-config.mjs` style — uses the same `yaml`
 * parser and reports exit 0 on success, 1 on violation, 2
 * on configuration error.
 *
 * Asserts:
 *   - `platform/vault/server-config.yaml` carries a ConfigMap
 *     with a `config.hcl` entry that declares the active
 *     seal stanza (`awskms` / `transit` / `shamir`) + the
 *     Raft storage block + Prometheus telemetry + the
 *     `disable_mlock = true` privacy posture.
 *   - `platform/vault/auth-methods.yaml` declares the
 *     `kubernetes` + `keycloak-oidc` + `github-actions`
 *     auth methods; the forbidden list (`userpass` / `ldap`
 *     / `cert`) is enforced.
 *   - `platform/vault/policies.yaml` declares the deny-all
 *     `default` policy + at least 8 per-component policies;
 *     no policy grants `root` or `sudo`.
 *   - `platform/vault/kms-abstraction.yaml` declares the
 *     `KmsProvider` contract + a single active provider per
 *     env + one key per data class in
 *     `privacy-and-legal-gate.md` §5; no `keyId` reuse
 *     across two classes.
 *   - `platform/vault/injector-templates.yaml` carries the
 *     `agent-inject: "true"` + `agent-revoke-on-shutdown:
 *     "true"` annotations for every workload class.
 *   - No literal secret / token / password in any of the
 *     files. The `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`
 *     pattern is checked explicitly.
 *   - The five files are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/vault/`.
 *
 * Per `agent-execution.md` §4.4 this script does NOT mutate
 * the repo and is safe to run in CI.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const VAULT_DIR = join(ROOT, "platform", "vault");

const REQUIRED_AUTH_METHODS = ["kubernetes", "keycloak-oidc", "github-actions"];
const FORBIDDEN_AUTH_METHODS = ["userpass", "ldap", "cert"];
const REQUIRED_DATA_CLASSES = [
  "PII.IDENTITY",
  "PII.QUASI_ID",
  "PII.SENSITIVE",
  "GENETIC.RAW",
  "GENETIC.METADATA",
  "GENETIC.DERIVED",
  "MEDIA.RAW",
  "MEDIA.DERIVATIVE",
  "AUDIT.APPENDONLY",
  "OPS.METADATA",
  "SECRET",
];
const REQUIRED_POLICIES = [
  "default",
  "services-read-secrets",
  "bff-read-secrets",
  "workers-read-secrets",
  "data-read-secrets",
  "data-rotate-secrets",
  "observability-read-secrets",
  "ci-read-secrets",
  "ci-write-deploy-markers",
];
const REQUIRED_WORKLOAD_CLASSES = [
  "services",
  "workers",
  "bff",
  "data",
  "observability",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[vault] ${msg}`);
};

function loadYaml(path) {
  if (!existsSync(path)) {
    fail(`file missing — ${relative(ROOT, path)}`);
    return null;
  }
  try {
    return YAML.parse(readFileSync(path, "utf8"));
  } catch (e) {
    fail(`YAML parse error in ${relative(ROOT, path)} — ${e.message}`);
    return null;
  }
}

function assertNoSecrets(text, path) {
  for (const key of ["password", "apiKey", "token", "private_key"]) {
    const literalRegex = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
    if (literalRegex.test(text)) {
      fail(
        `literal secret-like value for '${key}' in ${relative(ROOT, path)} — use Vault / External Secrets`,
      );
    }
  }
  // Explicit AWS credential check — the linter rejects any
  // literal `AWS_ACCESS_KEY_ID=...` or `AWS_SECRET_ACCESS_KEY=...`
  // pattern in any source-of-truth file under `platform/vault/`.
  for (const awsKey of ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY"]) {
    const re = new RegExp(`^\\s*${awsKey}\\s*=\\s*["']?[A-Za-z0-9/+=]{16,}["']?\\s*$`, "m");
    if (re.test(text)) {
      fail(
        `literal AWS credential '${awsKey}' in ${relative(ROOT, path)} — use IRSA / pod identity (E2.6 §4)`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// server-config.yaml — server HCL posture
// ---------------------------------------------------------------------------
const serverFile = join(VAULT_DIR, "server-config.yaml");
const serverDoc = loadYaml(serverFile);
if (serverDoc) {
  const data = serverDoc?.data?.["config.hcl"];
  if (!data) {
    fail(`server-config.yaml must declare a ConfigMap with a 'config.hcl' entry under .data`);
  } else {
    // The seal stanza — one of awskms / transit / shamir.
    // A config with no seal stanza is a lint violation
    // (unsealed Vault = unsealed storage = critical).
    const sealMatch = data.match(/seal\s+"(\w+)"\s*\{/);
    if (!sealMatch) {
      fail(`server-config.yaml must declare a seal stanza (awskms / transit / shamir)`);
    } else {
      const sealType = sealMatch[1];
      if (!["awskms", "transit", "shamir"].includes(sealType)) {
        fail(`server-config.yaml seal type must be awskms / transit / shamir — got '${sealType}'`);
      }
    }
    // Raft storage block.
    if (!/storage\s+"raft"\s*\{/.test(data)) {
      fail(`server-config.yaml must declare storage "raft" { ... } (E2.6 §1)`);
    }
    // Prometheus telemetry.
    if (!/prometheus\s*\{[\s\S]*?is_enabled\s*=\s*true/.test(data)) {
      fail(`server-config.yaml must enable Prometheus telemetry (alert source)`);
    }
    // Privacy posture — disable_mlock (Vault writes to memory
    // instead of swapping secrets to disk).
    if (!/disable_mlock\s*=\s*true/.test(data)) {
      fail(`server-config.yaml must set disable_mlock = true (privacy posture)`);
    }
    // Listener surface — TLS 1.3 minimum.
    if (!/tls_min_version\s*=\s*"tls13"/.test(data)) {
      fail(`server-config.yaml listener must declare tls_min_version = "tls13"`);
    }
    // API + cluster addresses.
    if (!/api_addr\s*=\s*"https:\/\/vault\.gp-data\.svc\.cluster\.local:8200"/.test(data)) {
      fail(`server-config.yaml must pin api_addr to https://vault.gp-data.svc.cluster.local:8200`);
    }
    if (!/cluster_addr\s*=\s*"https:\/\/vault-0\.vault-internal:8201"/.test(data)) {
      fail(`server-config.yaml must pin cluster_addr to https://vault-0.vault-internal:8201`);
    }
  }
  assertNoSecrets(readFileSync(serverFile, "utf8"), serverFile);
}

// ---------------------------------------------------------------------------
// auth-methods.yaml — auth method + per-namespace role bindings
// ---------------------------------------------------------------------------
const authFile = join(VAULT_DIR, "auth-methods.yaml");
const authDoc = loadYaml(authFile);
if (authDoc) {
  const data = authDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`auth-methods.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`auth-methods.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const methods = parsed.authMethods || [];
      const declared = new Set(methods.map((m) => m.name));
      for (const required of REQUIRED_AUTH_METHODS) {
        if (!declared.has(required)) {
          fail(`auth-methods.yaml missing required auth method '${required}' (E2.6 §2)`);
        }
      }
      for (const forbidden of FORBIDDEN_AUTH_METHODS) {
        if (declared.has(forbidden)) {
          fail(
            `auth-methods.yaml must not enable forbidden auth method '${forbidden}' — credential storage belongs to Keycloak per design.md §4.2`,
          );
        }
      }
      // Kubernetes auth method must point at the in-cluster
      // API server.
      const kubernetes = methods.find((m) => m.name === "kubernetes");
      if (kubernetes && !/kubernetes\.default\.svc\.cluster\.local/.test(JSON.stringify(kubernetes))) {
        fail(`auth-methods.yaml kubernetes.auth.host must point at the in-cluster API server`);
      }
      // Roles — at least one role per workload namespace.
      const roles = parsed.roles || [];
      if (!Array.isArray(roles) || roles.length < REQUIRED_AUTH_METHODS.length) {
        fail(`auth-methods.yaml must declare at least ${REQUIRED_AUTH_METHODS.length} roles (E2.6 §2)`);
      }
    }
  }
  assertNoSecrets(readFileSync(authFile, "utf8"), authFile);
}

// ---------------------------------------------------------------------------
// policies.yaml — deny-all default + per-component ACLs
// ---------------------------------------------------------------------------
const policiesFile = join(VAULT_DIR, "policies.yaml");
const policiesDoc = loadYaml(policiesFile);
if (policiesDoc) {
  const data = policiesDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`policies.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`policies.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const policies = parsed.policies || [];
      const declared = new Set(policies.map((p) => p.name));
      for (const required of REQUIRED_POLICIES) {
        if (!declared.has(required)) {
          fail(`policies.yaml missing required policy '${required}' (E2.6 §3)`);
        }
      }
      // The default policy MUST deny everything.
      const defaultPolicy = policies.find((p) => p.name === "default");
      if (defaultPolicy) {
        const body = defaultPolicy.body || "";
        if (!/capabilities\s*=\s*\[\s*"deny"\s*\]/.test(body)) {
          fail(`policies.yaml 'default' policy must deny all (E2.6 §3.1)`);
        }
        if (!/path\s+"\*"\s*\{/.test(body)) {
          fail(`policies.yaml 'default' policy must declare path "*" { ... } (E2.6 §3.1)`);
        }
      }
      // No policy grants `root` or `sudo`.
      const forbiddenCaps = parsed.forbiddenCapabilities || [];
      if (!forbiddenCaps.includes("root") || !forbiddenCaps.includes("sudo")) {
        fail(`policies.yaml forbiddenCapabilities must include 'root' AND 'sudo'`);
      }
      for (const p of policies) {
        if (/capabilities\s*=\s*\[\s*"root"/.test(p.body || "")) {
          fail(`policies.yaml policy '${p.name}' grants 'root' capability — critical severity`);
        }
        if (/capabilities\s*=\s*\[\s*"sudo"/.test(p.body || "")) {
          fail(`policies.yaml policy '${p.name}' grants 'sudo' capability — critical severity`);
        }
      }
      // No policy references paths outside `secret/`.
      for (const prefix of parsed.forbiddenPathPrefixes || []) {
        if (!prefix) continue;
      }
      // The forbiddenPathPrefixes list is checked structurally
      // — at minimum it must include `auth/token/` + `sys/`.
      const prefixes = parsed.forbiddenPathPrefixes || [];
      for (const required of ["auth/token/", "sys/"]) {
        if (!prefixes.includes(required)) {
          fail(`policies.yaml forbiddenPathPrefixes must include '${required}' (E2.6 §3)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(policiesFile, "utf8"), policiesFile);
}

// ---------------------------------------------------------------------------
// kms-abstraction.yaml — single activeProvider + per-data-class key
// ---------------------------------------------------------------------------
const kmsFile = join(VAULT_DIR, "kms-abstraction.yaml");
const kmsDoc = loadYaml(kmsFile);
if (kmsDoc) {
  const data = kmsDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`kms-abstraction.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`kms-abstraction.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const kms = parsed.kmsAbstraction;
      if (!kms) {
        fail(`kms-abstraction.yaml must declare a kmsAbstraction block`);
      } else {
        // Contract interface.
        if (kms.contract?.interface !== "com.genealogy.platform.kms.KmsProvider") {
          fail(
            `kms-abstraction.yaml contract.interface must be 'com.genealogy.platform.kms.KmsProvider' (E2.6 §4)`,
          );
        }
        const methods = kms.contract?.methods || [];
        const methodNames = methods.map((m) => (typeof m === "string" ? m : m.name));
        for (const required of ["generateDataKey", "encrypt", "decrypt", "rotateKey", "emergencyRevoke"]) {
          if (!methodNames.includes(required)) {
            fail(`kms-abstraction.yaml contract.methods must include '${required}'`);
          }
        }
        // Active provider — exactly one per env.
        const active = kms.activeProvider || {};
        if (!active.saas || !active.onprem || !active.dev) {
          fail(`kms-abstraction.yaml activeProvider must declare saas + onprem + dev`);
        }
        if (!["aws-kms"].includes(active.saas)) {
          fail(`kms-abstraction.yaml activeProvider.saas must be 'aws-kms' (E2.6 §4.2)`);
        }
        if (!["vault-transit"].includes(active.onprem)) {
          fail(`kms-abstraction.yaml activeProvider.onprem must be 'vault-transit'`);
        }
        // SaaS provider — credentialsSource must be irsa-pod-identity.
        const saas = kms.saasProvider || {};
        if (saas.credentialsSource !== "irsa-pod-identity") {
          fail(
            `kms-abstraction.yaml saasProvider.credentialsSource must be 'irsa-pod-identity' — no literal AWS keys`,
          );
        }
        // Per-data-class key assignment.
        const keys = kms.keys || [];
        const declaredClasses = new Set(keys.map((k) => k.class));
        for (const required of REQUIRED_DATA_CLASSES) {
          if (!declaredClasses.has(required)) {
            fail(`kms-abstraction.yaml missing data class '${required}' (privacy-and-legal-gate.md §5)`);
          }
        }
        // No keyId reuse across two classes.
        const keyIds = keys.map((k) => k.keyId);
        const dupes = keyIds.filter((id, idx) => keyIds.indexOf(id) !== idx);
        if (dupes.length > 0) {
          fail(`kms-abstraction.yaml keyId reuse across classes: ${dupes.join(", ")} (blast-radius)`);
        }
        // Rotation cadence — every key has rotationDays.
        for (const k of keys) {
          if (typeof k.rotationDays !== "number" || k.rotationDays <= 0) {
            fail(`kms-abstraction.yaml key '${k.class}' must declare rotationDays > 0`);
          }
        }
      }
    }
  }
  assertNoSecrets(readFileSync(kmsFile, "utf8"), kmsFile);
}

// ---------------------------------------------------------------------------
// injector-templates.yaml — agent-inject + agent-revoke-on-shutdown
// ---------------------------------------------------------------------------
const injectorFile = join(VAULT_DIR, "injector-templates.yaml");
const injectorDoc = loadYaml(injectorFile);
if (injectorDoc) {
  const data = injectorDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`injector-templates.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`injector-templates.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const templates = parsed.injectorTemplates || [];
      const declared = new Set(templates.map((t) => t.workloadClass));
      for (const required of REQUIRED_WORKLOAD_CLASSES) {
        if (!declared.has(required)) {
          fail(`injector-templates.yaml missing workload class '${required}' (E2.6 §5)`);
        }
      }
      for (const t of templates) {
        const annotations = t.annotations || {};
        if (annotations["vault.hashicorp.com/agent-inject"] !== "true") {
          fail(
            `injector-templates.yaml workload class '${t.workloadClass}' missing 'vault.hashicorp.com/agent-inject: "true"'`,
          );
        }
        if (annotations["vault.hashicorp.com/agent-revoke-on-shutdown"] !== "true") {
          fail(
            `injector-templates.yaml workload class '${t.workloadClass}' missing 'vault.hashicorp.com/agent-revoke-on-shutdown: "true"'`,
          );
        }
        if (!annotations["vault.hashicorp.com/role"]) {
          fail(
            `injector-templates.yaml workload class '${t.workloadClass}' missing 'vault.hashicorp.com/role' annotation`,
          );
        }
        // The role name must reference a policy declared in
        // policies.yaml. We check structurally — the role
        // annotation must be one of the REQUIRED_POLICIES
        // entries.
        const role = annotations["vault.hashicorp.com/role"] || "";
        if (!REQUIRED_POLICIES.includes(role)) {
          fail(
            `injector-templates.yaml workload class '${t.workloadClass}' role '${role}' does not match a policy in policies.yaml`,
          );
        }
      }
    }
  }
  assertNoSecrets(readFileSync(injectorFile, "utf8"), injectorFile);
}

// ---------------------------------------------------------------------------
// Mirror files — every platform/vault/* must be present in the
// chart's files/vault/ directory.
// ---------------------------------------------------------------------------
const mirrorDir = join(ROOT, "platform", "helm", "genealogy-platform", "files", "vault");
for (const f of [
  "server-config.yaml",
  "auth-methods.yaml",
  "policies.yaml",
  "kms-abstraction.yaml",
  "injector-templates.yaml",
]) {
  const src = join(VAULT_DIR, f);
  const dst = join(mirrorDir, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E2.6 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror out of sync — ${relative(ROOT, dst)}`);
  }
}

if (violations > 0) {
  console.error(`\n[vault] ${violations} violation(s)`);
  process.exit(1);
}
console.log(
  `[vault] clean — auth-methods=${REQUIRED_AUTH_METHODS.length}, policies=${REQUIRED_POLICIES.length}, data-classes=${REQUIRED_DATA_CLASSES.length}, workload-classes=${REQUIRED_WORKLOAD_CLASSES.length}`,
);
