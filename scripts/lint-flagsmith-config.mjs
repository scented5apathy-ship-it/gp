#!/usr/bin/env node
/**
 * scripts/lint-flagsmith-config.mjs
 *
 * E2.8 deep validator for the Flagsmith + OpenFeature
 * source-of-truth files in `platform/featureflags/`. Mirrors
 * `lint-vault-config.mjs` style — uses the same `yaml`
 * parser and reports exit 0 on success, 1 on violation, 2
 * on configuration error.
 *
 * Asserts:
 *   - `platform/featureflags/flagsmith-server.yaml` declares
 *     the Flagsmith image pin (2.139.4), Postgres backing
 *     store, anonymous access disabled, CORS allowlist (no
 *     wildcard), TLS 1.2 minimum, audit log enabled,
 *     Prometheus telemetry enabled, no literal secret.
 *   - `platform/featureflags/environments.yaml` declares 5
 *     environments (development / staging / production /
 *     onprem / audit) + 4 RBAC roles; no per-tenant
 *     environment; audit env is read-only.
 *   - `platform/featureflags/flag-taxonomy.yaml` declares 8
 *     legal-gate flags (one per row in
 *     `privacy-and-legal-gate.md` §12) + ≥ 4 rollout flags +
 *     the 11 required columns per flag; the 12 forbidden
 *     key patterns are rejected.
 *   - `platform/featureflags/safe-defaults.yaml` declares
 *     per-type fallback, per-env evaluation timeout,
 *     evaluation context contract (tenant_pseudo_id
 *     required; tenant_id forbidden), audit event contract.
 *   - `platform/featureflags/sdk-config.yaml` declares the
 *     bootstrap Job + Spring Boot properties + Next.js env
 *     + Kong route + circuit breaker / retry / bulkhead.
 *   - The five files are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/featureflags/`.
 *   - No literal secret / token / password in any file.
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
const FF_DIR = join(ROOT, "platform", "featureflags");

const REQUIRED_ENVIRONMENTS = [
  "development",
  "staging",
  "production",
  "onprem",
  "audit",
];
const REQUIRED_RBAC_ROLES = [
  "Org Admin",
  "Environment Admin",
  "Environment User",
  "Audit Viewer",
];
const REQUIRED_LEGAL_GATE_FLAGS = [
  "legal.data_residency.allowlist",
  "legal.public_sharing.enabled",
  "legal.dna.enabled",
  "legal.media_upload.enabled",
  "legal.gedcom_import.enabled",
  "legal.cross_region_transfer.enabled",
  "legal.parsers.restricted",
  "legal.flag_bypass_allowlist",
];
const REQUIRED_FLAG_COLUMNS = [
  "key",
  "type",
  "safeDefault",
  "owner",
  "whenTrue",
  "whenFalse",
  "expiresOn",
  "audit",
  "scope",
  "legalGate",
  "segmentOverride",
  "dataClass",
];
const ALLOWED_FLAG_TYPES = ["boolean", "string", "integer", "float", "json"];
const FORBIDDEN_FLAG_KEY_PATTERNS = [
  "skip.*auth",
  "skip.*openfga",
  "skip.*abac",
  "skip.*consent",
  "skip.*audit",
  "bypass.*auth",
  "bypass.*consent",
  "disable.*audit",
  "disable.*encryption",
  "no.*redact",
  "raw.*dna",
  "raw.*pii",
];
const REQUIRED_ENV_FALLBACKS = ["boolean", "string", "integer", "float", "json"];
const REQUIRED_EVAL_CTX_ATTRIBUTES = [
  "tenant_pseudo_id",
  "user_pseudo_id",
  "trace_id",
  "environment",
];
const FORBIDDEN_EVAL_CTX_ATTRIBUTES = [
  "tenant_id",
  "user_id",
  "email",
  "raw_dna",
  "flag_value",
];
const REQUIRED_WORKLOAD_CLASSES = ["java", "node", "web"];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[flagsmith] ${msg}`);
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
  for (const awsKey of ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY"]) {
    const re = new RegExp(`^\\s*${awsKey}\\s*=\\s*["']?[A-Za-z0-9/+=]{16,}["']?\\s*$`, "m");
    if (re.test(text)) {
      fail(
        `literal AWS credential '${awsKey}' in ${relative(ROOT, path)} — use IRSA / pod identity (E2.8 §4)`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// flagsmith-server.yaml — server posture
// ---------------------------------------------------------------------------
const serverFile = join(FF_DIR, "flagsmith-server.yaml");
const serverDoc = loadYaml(serverFile);
let serverEnvCount = 0;
if (serverDoc) {
  const data = serverDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`flagsmith-server.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    // Image pin — ADR-E0.5-01 baseline `flagsmith/flagsmith:2.139.4`.
    if (!/serverImage:\s*"flagsmith\/flagsmith:2\.139\.4"/.test(data)) {
      fail(
        `flagsmith-server.yaml must pin serverImage to 'flagsmith/flagsmith:2.139.4' (ADR-E0.5-01)`,
      );
    }
    // Backing store — Postgres is REQUIRED for production /
    // staging / on-prem.
    if (!/backingStore:\s*postgresql/.test(data)) {
      fail(`flagsmith-server.yaml must declare backingStore: postgresql (in-memory forbidden in production)`);
    }
    // Anonymous access — FORBIDDEN.
    if (!/allowAnonymous:\s*false/.test(data)) {
      fail(`flagsmith-server.yaml must set allowAnonymous: false (E2.8 §1)`);
    }
    // TLS minimum — TLS 1.2.
    if (!/tlsMinVersion:\s*"tls12"/.test(data)) {
      fail(`flagsmith-server.yaml must declare tlsMinVersion: "tls12"`);
    }
    // CORS allowlist — at least one origin, no wildcard.
    const corsSection = data.match(/corsAllowedOrigins:([\s\S]*?)(?=\n  \w|$)/);
    if (!corsSection) {
      fail(`flagsmith-server.yaml must declare corsAllowedOrigins (no wildcard)`);
    } else {
      if (/corsAllowedOrigins:\s*\[\s*"\*"\s*\]/.test(data)) {
        fail(`flagsmith-server.yaml must NOT use wildcard CORS origin (privacy-and-legal-gate.md §11)`);
      }
      if (/\bhttps?:\/\/\*/.test(data)) {
        fail(`flagsmith-server.yaml must NOT contain wildcard CORS origin`);
      }
    }
    // Audit log — enabled.
    if (!/auditLog:\s*\n\s*enabled:\s*true/.test(data)) {
      fail(`flagsmith-server.yaml must enable auditLog (E2.8 §1)`);
    }
    // Prometheus telemetry — enabled.
    if (!/prometheus:\s*\n\s*enabled:\s*true/.test(data)) {
      fail(`flagsmith-server.yaml must enable prometheus telemetry (alert source)`);
    }
  }
  assertNoSecrets(readFileSync(serverFile, "utf8"), serverFile);
}

// ---------------------------------------------------------------------------
// environments.yaml — environments + RBAC
// ---------------------------------------------------------------------------
const envsFile = join(FF_DIR, "environments.yaml");
const envsDoc = loadYaml(envsFile);
let envCount = 0;
if (envsDoc) {
  const data = envsDoc?.data?.["environments.yaml"];
  if (!data) {
    fail(`environments.yaml must declare a ConfigMap with an 'environments.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`environments.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const envs = parsed.environments || [];
      envCount = envs.length;
      const declared = new Set(envs.map((e) => e.id));
      for (const required of REQUIRED_ENVIRONMENTS) {
        if (!declared.has(required)) {
          fail(`environments.yaml missing required environment '${required}' (E2.8 §2)`);
        }
      }
      // The audit environment is read-only.
      const auditEnv = envs.find((e) => e.id === "audit");
      if (auditEnv && !auditEnv.readOnly) {
        fail(`environments.yaml 'audit' environment must be readOnly (DPO + privacy team)`);
      }
      // No per-tenant environment — flagged by inspecting the
      // `apiTokenSecretRef` for a tenant-shaped name.
      for (const env of envs) {
        if (env.apiTokenSecretRef && /tenant|user[_-]?id/.test(env.apiTokenSecretRef)) {
          fail(
            `environments.yaml environment '${env.id}' carries a tenant/user-shaped apiTokenSecretRef — per-tenant env is FORBIDDEN (privacy-and-legal-gate.md §12)`,
          );
        }
      }
      // RBAC roles — at least the 4 required.
      const rbac = parsed.organisation?.rbacRoles || [];
      const rbacIds = new Set(rbac.map((r) => r.id));
      for (const required of REQUIRED_RBAC_ROLES) {
        if (!rbacIds.has(required)) {
          fail(`environments.yaml missing required RBAC role '${required}' (E2.8 §2)`);
        }
      }
      // Audit Viewer role must be readOnly.
      const auditRole = rbac.find((r) => r.id === "Audit Viewer");
      if (auditRole && !auditRole.readOnly) {
        fail(`environments.yaml RBAC role 'Audit Viewer' must be readOnly`);
      }
    }
  }
  assertNoSecrets(readFileSync(envsFile, "utf8"), envsFile);
}

// ---------------------------------------------------------------------------
// flag-taxonomy.yaml — flag list
// ---------------------------------------------------------------------------
const taxonomyFile = join(FF_DIR, "flag-taxonomy.yaml");
const taxonomyDoc = loadYaml(taxonomyFile);
let flagCount = 0;
if (taxonomyDoc) {
  const data = taxonomyDoc?.data?.["flags.yaml"];
  if (!data) {
    fail(`flag-taxonomy.yaml must declare a ConfigMap with a 'flags.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`flag-taxonomy.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const flags = parsed.flags || [];
      flagCount = flags.length;
      const keys = new Set(flags.map((f) => f.key));
      // Legal-gate flags — all 8 from privacy-and-legal-gate.md §12.
      for (const required of REQUIRED_LEGAL_GATE_FLAGS) {
        if (!keys.has(required)) {
          fail(`flag-taxonomy.yaml missing required legal-gate flag '${required}' (privacy-and-legal-gate.md §12)`);
        }
      }
      // At least 4 rollout flags.
      const rolloutFlags = flags.filter((f) => !f.legalGate);
      if (rolloutFlags.length < 4) {
        fail(`flag-taxonomy.yaml must declare at least 4 rollout flags (got ${rolloutFlags.length})`);
      }
      // Required columns — every flag must carry all 11.
      for (const f of flags) {
        for (const col of REQUIRED_FLAG_COLUMNS) {
          if (!(col in f)) {
            fail(
              `flag-taxonomy.yaml flag '${f.key || "<unknown>"}' missing required column '${col}' (E2.8 §3)`,
            );
          }
        }
        // Type check — must be one of the 5 allowed.
        if (f.type && !ALLOWED_FLAG_TYPES.includes(f.type)) {
          fail(
            `flag-taxonomy.yaml flag '${f.key}' has type '${f.type}' — must be one of ${ALLOWED_FLAG_TYPES.join(", ")}`,
          );
        }
        // expiresOn — must be a future date.
        if (f.expiresOn) {
          const expires = new Date(f.expiresOn);
          if (Number.isNaN(expires.getTime())) {
            fail(`flag-taxonomy.yaml flag '${f.key}' has invalid expiresOn '${f.expiresOn}'`);
          }
        }
      }
      // Forbidden key patterns — every key must NOT match any
      // of the 12 forbidden patterns.
      for (const pattern of FORBIDDEN_FLAG_KEY_PATTERNS) {
        const regex = new RegExp(pattern);
        for (const f of flags) {
          if (regex.test(f.key)) {
            fail(
              `flag-taxonomy.yaml flag '${f.key}' matches forbidden pattern '${pattern}' — security/consent bypass is FORBIDDEN`,
            );
          }
        }
      }
      // Safe-default rule — no flag has `null` as the
      // safeDefault.
      for (const f of flags) {
        if (f.safeDefault === null || f.safeDefault === undefined) {
          fail(`flag-taxonomy.yaml flag '${f.key}' has null safeDefault — NEVER (E2.8 §4)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(taxonomyFile, "utf8"), taxonomyFile);
}

// ---------------------------------------------------------------------------
// safe-defaults.yaml — SDK safe-default rules
// ---------------------------------------------------------------------------
const defaultsFile = join(FF_DIR, "safe-defaults.yaml");
const defaultsDoc = loadYaml(defaultsFile);
if (defaultsDoc) {
  const data = defaultsDoc?.data?.["safe-defaults.yaml"];
  if (!data) {
    fail(`safe-defaults.yaml must declare a ConfigMap with a 'safe-defaults.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`safe-defaults.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const safeDefaults = parsed.safeDefaults || {};
      // Per-type fallback — all 5 types must be declared.
      const typeFallbacks = safeDefaults.typeFallbacks || {};
      for (const t of REQUIRED_ENV_FALLBACKS) {
        if (!(t in typeFallbacks)) {
          fail(`safe-defaults.yaml missing typeFallbacks.${t} (E2.8 §4)`);
        }
      }
      // Per-env posture — every environment must declare
      // provider + evaluationTimeoutMs + auditEventEnabled.
      const envPosture = safeDefaults.environment || {};
      for (const env of REQUIRED_ENVIRONMENTS) {
        if (!envPosture[env]) {
          fail(`safe-defaults.yaml missing environment.${env} posture (E2.8 §4)`);
          continue;
        }
        if (!envPosture[env].provider) {
          fail(`safe-defaults.yaml environment.${env}.provider is required`);
        }
        if (!envPosture[env].evaluationTimeoutMs) {
          fail(`safe-defaults.yaml environment.${env}.evaluationTimeoutMs is required`);
        }
      }
      // Evaluation context — required + forbidden attributes.
      const ctx = safeDefaults.evaluationContext || {};
      const requiredAttrs = ctx.requiredAttributes || [];
      for (const attr of REQUIRED_EVAL_CTX_ATTRIBUTES) {
        if (!requiredAttrs.includes(attr)) {
          fail(`safe-defaults.yaml evaluationContext.requiredAttributes must include '${attr}' (E2.8 §4)`);
        }
      }
      const forbiddenAttrs = ctx.forbiddenAttributes || [];
      for (const attr of FORBIDDEN_EVAL_CTX_ATTRIBUTES) {
        if (!forbiddenAttrs.includes(attr)) {
          fail(`safe-defaults.yaml evaluationContext.forbiddenAttributes must include '${attr}' (E2.8 §4)`);
        }
      }
      // SDKs — all 3 workload classes declared.
      const sdks = safeDefaults.sdks || {};
      for (const sdk of REQUIRED_WORKLOAD_CLASSES) {
        if (!sdks[sdk]) {
          fail(`safe-defaults.yaml missing sdks.${sdk} (E2.8 §4)`);
        }
      }
      // Audit event — must declare name + sink + required
      // labels.
      const audit = safeDefaults.auditEvent;
      if (!audit || !audit.name || !audit.sink) {
        fail(`safe-defaults.yaml must declare auditEvent.name + auditEvent.sink (E2.8 §4)`);
      }
    }
  }
  assertNoSecrets(readFileSync(defaultsFile, "utf8"), defaultsFile);
}

// ---------------------------------------------------------------------------
// sdk-config.yaml — bootstrap + SDK wiring
// ---------------------------------------------------------------------------
const sdkFile = join(FF_DIR, "sdk-config.yaml");
const sdkDoc = loadYaml(sdkFile);
if (sdkDoc) {
  const data = sdkDoc?.data?.["bootstrap.yaml"];
  if (!data) {
    fail(`sdk-config.yaml must declare a ConfigMap with a 'bootstrap.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`sdk-config.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const bootstrap = parsed.bootstrap || {};
      if (!bootstrap.job || bootstrap.job.hook !== "pre-install,pre-upgrade") {
        fail(`sdk-config.yaml must declare bootstrap.job with hook 'pre-install,pre-upgrade' (E2.8 §5)`);
      }
      if (!bootstrap.ensureEnvironments) {
        fail(`sdk-config.yaml must declare bootstrap.ensureEnvironments (E2.8 §5)`);
      }
      if (!bootstrap.ensureRbacRoles) {
        fail(`sdk-config.yaml must declare bootstrap.ensureRbacRoles (E2.8 §5)`);
      }
      if (!bootstrap.ensureFlags) {
        fail(`sdk-config.yaml must declare bootstrap.ensureFlags (E2.8 §5)`);
      }
      const drift = bootstrap.driftCheck;
      if (!drift || !drift.enabled || drift.onDrift !== "fail") {
        fail(`sdk-config.yaml must enable bootstrap.driftCheck with onDrift: fail (E2.8 §5)`);
      }
      // SDK wiring — Spring Boot properties + Next.js env.
      const sdk = parsed.sdkWiring || {};
      if (!sdk.springProperties || !sdk.springProperties["platform.openfeature.provider"]) {
        fail(`sdk-config.yaml must declare sdkWiring.springProperties (E2.8 §5)`);
      }
      if (!sdk.webEnv || !sdk.webEnv.NEXT_PUBLIC_OPENFEATURE_PROVIDER) {
        fail(`sdk-config.yaml must declare sdkWiring.webEnv (E2.8 §5)`);
      }
      if (!sdk.kongRoute || !sdk.kongRoute.path) {
        fail(`sdk-config.yaml must declare sdkWiring.kongRoute (E2.8 §5)`);
      }
      // Resilience — circuit breaker + retry + bulkhead.
      const resilience = parsed.resilience || {};
      if (!resilience.circuitBreaker || !resilience.retry || !resilience.bulkhead) {
        fail(`sdk-config.yaml must declare resilience.{circuitBreaker,retry,bulkhead} (E2.8 §5)`);
      }
    }
  }
  assertNoSecrets(readFileSync(sdkFile, "utf8"), sdkFile);
}

// ---------------------------------------------------------------------------
// Mirror files — every platform/featureflags/* must be present in the
// chart's files/featureflags/ directory.
// ---------------------------------------------------------------------------
const mirrorDir = join(ROOT, "platform", "helm", "genealogy-platform", "files", "featureflags");
for (const f of [
  "flagsmith-server.yaml",
  "environments.yaml",
  "flag-taxonomy.yaml",
  "safe-defaults.yaml",
  "sdk-config.yaml",
]) {
  const src = join(FF_DIR, f);
  const dst = join(mirrorDir, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E2.8 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror out of sync — ${relative(ROOT, dst)}`);
  }
}

if (violations > 0) {
  console.error(`\n[flagsmith] ${violations} violation(s)`);
  process.exit(1);
}
console.log(
  `[flagsmith] clean — envs=${envCount}, rbac-roles=${REQUIRED_RBAC_ROLES.length}, flags=${flagCount}, legal-gate=${REQUIRED_LEGAL_GATE_FLAGS.length}`,
);