#!/usr/bin/env node
/**
 * scripts/check-platform-baseline.mjs
 *
 * E2.1 cluster baseline preflight — validates the Kubernetes / Helm
 * baseline and the local-development profile **statically** (no live
 * cluster required) so the check runs in CI on every PR.
 *
 * Per `tasks.md` E2.1 the baseline must:
 *   - declare namespaces with PodSecurity labels and ResourceQuotas
 *   - apply a default-deny NetworkPolicy per namespace
 *   - declare PodDisruptionBudgets (including the single-replica rule)
 *   - pin StorageClass encryption-at-rest
 *   - ship a per-environment `values-<env>.yaml` (saas / onprem / dev)
 *   - validate probe paths against the contract enforced by
 *     `libs/platform-spring-boot-starter`
 *   - keep secrets out of values files (delegated to
 *     `scripts/lint-yaml.mjs`, asserted here as well)
 *
 * The script returns exit code 0 on success, 1 on violation, 2 on
 * configuration error.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname, basename } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.BASELINE_ROOT ? resolve(process.env.BASELINE_ROOT) : resolve(HERE, "..");
const HELM_DIR = join(ROOT, "platform", "helm", "genealogy-platform");
const LOCAL_DIR = join(ROOT, "platform", "local");
const KONG_DIR = join(ROOT, "platform", "kong");
const PROBE_CONTRACT = {
  live: "/healthz/live",
  ready: "/healthz/ready",
  startup: "/healthz/startup",
};

const REQUIRED_NAMESPACES = [
  "gp-platform",
  "gp-edge",
  "gp-bff",
  "gp-services",
  "gp-workers",
  "gp-data",
  "gp-observability",
  "gp-argocd",
];

const REQUIRED_ENV_VALUES = ["values-saas.yaml", "values-onprem.yaml", "values-dev.yaml"];

const REQUIRED_LOCAL_FILES = [
  "profile.yaml",
  "docker-compose.yml",
  "db",
  "keycloak",
  "openfga",
  "kafka",
  "storage",
  "cache",
  "flags",
  "observability",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[baseline] ${msg}`);
};

if (!existsSync(HELM_DIR)) {
  fail(`umbrella chart missing — expected ${relative(ROOT, HELM_DIR)}`);
// ---------------------------------------------------------------------------
// E2.6 — Vault + cloud KMS abstraction runtime invariants (static
// check; `scripts/lint-vault-config.mjs` runs the deep YAML
// validation).
// ---------------------------------------------------------------------------
const VAULT_DIR = join(ROOT, "platform", "vault");
const VAULT_FILES = [
  join(VAULT_DIR, "server-config.yaml"),
  join(VAULT_DIR, "auth-methods.yaml"),
  join(VAULT_DIR, "policies.yaml"),
  join(VAULT_DIR, "kms-abstraction.yaml"),
  join(VAULT_DIR, "injector-templates.yaml"),
];
const vaultTemplates = join(HELM_DIR, "templates", "components", "vault");
const REQUIRED_VAULT_TEMPLATES = [
  "statefulset.yaml",
  "services.yaml",
  "serviceaccounts.yaml",
  "init-scripts-configmap.yaml",
  "policies-configmap.yaml",
  "kms-abstraction-configmap.yaml",
  "auth-methods-configmap.yaml",
  "bootstrap-job.yaml",
  "network-policies.yaml",
];

for (const f of VAULT_FILES) {
  if (!existsSync(f)) {
    fail(`E2.6 source-of-truth file missing — ${relative(ROOT, f)}`);
  }
}

for (const tpl of REQUIRED_VAULT_TEMPLATES) {
  const p = join(vaultTemplates, tpl);
  if (!existsSync(p)) {
    fail(`E2.6 helm template missing — ${relative(ROOT, p)}`);
  }
}

if (existsSync(valuesPath)) {
  const v = readFileSync(valuesPath, "utf8");
  // ADR-E0.5-01 baseline pin (Vault 1.17.x) plus the
  // Vault Agent Injector image the chart's `vault-k8s`
  // subchart consumes.
  if (!/tag:\s*"1\.17\.1"/.test(v)) {
    fail("values.yaml must pin Vault image tag to 1.17.1 (ADR-E0.5-01)");
  }
  if (!/repository:\s*hashicorp\/vault-k8s/.test(v)) {
    fail("values.yaml must declare the Vault Agent Injector image (E2.6 §5)");
  }
  // KMS seal — the active seal type is rendered into the
  // StatefulSet env block; the chart never inlines a literal
  // AWS key.
  if (!/seal:\s*\n\s*type:\s*awskms/.test(v)) {
    fail("values.yaml must declare components.vault.seal.type: awskms (E2.6 §4)");
  }
  if (!/kmsKeyId:\s*arn:aws:kms/.test(v)) {
    fail("values.yaml must declare components.vault.seal.kmsKeyId (E2.6 §4)");
  }
  // Source-of-truth ConfigMap paths.
  for (const path of [
    "serverConfig:",
    "authMethods:",
    "policies:",
    "kmsAbstraction:",
    "injectorTemplates:",
  ]) {
    if (!new RegExp(`${path}\\s*files/vault/`).test(v)) {
      fail(`values.yaml must declare components.vault.configPaths.${path} (E2.6 §1)`);
    }
  }
  // Policy + auth method allow-lists.
  if (!/enabledPolicies:\s*\n\s*-\s*default/.test(v)) {
    fail("values.yaml must declare components.vault.policies.enabledPolicies (E2.6 §3)");
  }
  if (!/forbiddenCapabilities:\s*\n\s*-\s*root/.test(v)) {
    fail("values.yaml must declare components.vault.policies.forbiddenCapabilities (E2.6 §3)");
  }
  if (!/authMethods:\s*\n\s*enabled:\s*\n\s*-\s*kubernetes/.test(v)) {
    fail("values.yaml must declare components.vault.authMethods.enabled (E2.6 §2)");
  }
  if (!/forbidden:\s*\n\s*-\s*userpass/.test(v)) {
    fail("values.yaml must declare components.vault.authMethods.forbidden (E2.6 §2)");
  }
  // KMS abstraction contract.
  if (!/contractInterface:\s*com\.genealogy\.platform\.kms\.KmsProvider/.test(v)) {
    fail("values.yaml must declare components.vault.kmsAbstraction.contractInterface (E2.6 §4)");
  }
  // Per-data-class key assignments.
  for (const cls of [
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
  ]) {
    if (!new RegExp(`-\\s*${cls.replace(/\./g, "\\.")}\\s*$`, "m").test(v)) {
      fail(`values.yaml must declare data class '${cls}' in components.vault.kmsAbstraction.dataClasses (E2.6 §4)`);
    }
  }
}

// Alert rules must cover the 5 E2.6 signal classes.
if (!existsSync(ALERTS_DIR) || !existsSync(join(ALERTS_DIR, "vault-rules.yaml"))) {
  fail("platform/observability/alerts/vault-rules.yaml missing (E2.6 alert contract)");
} else {
  const r = readFileSync(join(ALERTS_DIR, "vault-rules.yaml"), "utf8");
  for (const alert of [
    "VaultServerDown",
    "VaultSealed",
    "VaultSecretRetrievalLatencyHigh",
    "VaultTokenCountHigh",
    "VaultTokenCountCritical",
    "VaultTokenCreationFailureRateHigh",
    "VaultKMSProviderUnhealthy",
    "VaultKMSProviderUnhealthyCritical",
    "VaultRaftStorageLowDisk",
    "VaultRaftNoLeader",
    "VaultBootstrapJobFailed",
  ]) {
    if (!new RegExp(`alert:\\s*${alert}\\b`).test(r)) {
      fail(`platform/observability/alerts/vault-rules.yaml missing alert '${alert}' (E2.6)`);
    }
  }
}

// Per-env overrides must declare the seal type. Dev uses
// shamir; on-prem uses transit; saas uses awskms. The
// chart-level default is awskms so dev + on-prem values
// files MUST override.
for (const envFile of REQUIRED_ENV_VALUES) {
  const p = join(HELM_DIR, envFile);
  if (!existsSync(p)) continue;
  const text = readFileSync(p, "utf8");
  const envName = basename(envFile, ".yaml").replace(/^values-/, "");
  if (envName === "dev") {
    if (!/seal:\s*\n\s*type:\s*shamir/.test(text)) {
      fail(`${envFile} must declare components.vault.seal.type: shamir (E2.6 §4)`);
    }
  } else if (envName === "onprem") {
    if (!/seal:\s*\n\s*type:\s*transit/.test(text)) {
      fail(`${envFile} must declare components.vault.seal.type: transit (E2.6 §4)`);
    }
  } else if (envName === "saas") {
    if (!/seal:\s*\n\s*type:\s*awskms/.test(text)) {
      fail(`${envFile} must declare components.vault.seal.type: awskms (E2.6 §4)`);
    }
  }
}

// Profile.yaml must declare the Vault dev pin.
if (existsSync(join(LOCAL_DIR, "profile.yaml"))) {
  const p = readFileSync(join(LOCAL_DIR, "profile.yaml"), "utf8");
  if (!/image:\s*hashicorp\/vault:1\.17\.1/.test(p)) {
    fail("platform/local/profile.yaml must pin Vault image to hashicorp/vault:1.17.1 (ADR-E0.5-01)");
  }
}

finish();
}

const chartYamlPath = join(HELM_DIR, "Chart.yaml");
if (!existsSync(chartYamlPath)) {
  fail("Chart.yaml missing");
} else {
  const text = readFileSync(chartYamlPath, "utf8");
  for (const field of ["apiVersion", "name", "version", "appVersion"]) {
    if (!new RegExp(`^${field}\\s*:`, "m").test(text)) {
      fail(`Chart.yaml missing required field '${field}'`);
    }
  }
}

const valuesPath = join(HELM_DIR, "values.yaml");
if (!existsSync(valuesPath)) {
  fail("values.yaml missing");
} else {
  const values = readFileSync(valuesPath, "utf8");
  for (const envFile of REQUIRED_ENV_VALUES) {
    if (!existsSync(join(HELM_DIR, envFile))) {
      fail(`per-environment overrides missing — expected ${envFile}`);
    }
  }

  for (const ns of REQUIRED_NAMESPACES) {
    // Match the namespace declaration either as a list entry
    // (`- name: gp-XXX`) or as a map key (`gp-XXX:` followed by
    // a `name: gp-XXX` line). Use a non-word boundary so
    // `gp-data` does NOT match `gp-data-ssd` (the storage class).
    const mapKey = new RegExp(`\\n\\s*${ns}:\\s*\\n`).test(values);
    const listEntry = new RegExp(`-\\s*name:\\s*${ns}(?:\\s|$|,|"|\\})`).test(values);
    if (!mapKey && !listEntry) {
      fail(`baseline namespace '${ns}' not declared in values.yaml`);
    }
  }

  if (!/requestsCpu:\s*"32"/.test(values)) {
    fail("gp-platform quota baseline (requestsCpu: 32) not found — ADR-E0.5-01 parity broken");
  }

  if (!/podSecurity:\s*restricted/.test(values)) {
    fail("Pod Security 'restricted' profile missing from baseline.namespaces");
  }

  if (!/minAvailable:\s*1\b/.test(values)) {
    fail("PodDisruptionBudget.minAvailable default missing");
  }

  if (
    !new RegExp(`live:\\s*${PROBE_CONTRACT.live.replace(/\//g, "\\/")}`).test(values) ||
    !new RegExp(`ready:\\s*${PROBE_CONTRACT.ready.replace(/\//g, "\\/")}`).test(values) ||
    !new RegExp(`startup:\\s*${PROBE_CONTRACT.startup.replace(/\//g, "\\/")}`).test(values)
  ) {
    fail(
      `probe path contract not enforced — expected ${PROBE_CONTRACT.live}, ${PROBE_CONTRACT.ready}, ${PROBE_CONTRACT.startup}`,
    );
  }

  // Secret hygiene: no literal `password:`, `token:`, `apiKey:` etc.
  // outside of placeholders. The lint-yaml script catches this
  // repository-wide; we re-assert here so the baseline check is
  // self-contained.
  for (const key of ["password", "apiKey", "token", "private_key"]) {
    const literalRegex = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
    if (literalRegex.test(values)) {
      fail(
        `literal secret-like value for '${key}' found in values.yaml — use Vault / External Secrets`,
      );
    }
  }

  // Tenant-shared namespace is required on at least one entry so
  // NetworkPolicy / PodSecurity defaults land in a namespace that
  // also carries the `gp-tenant-shared: "true"` label consumed by
  // OpenFGA and ABAC.
  if (!/tenantShared:\s*true/.test(values)) {
    fail("values.yaml must declare at least one namespace with 'tenantShared: true'");
  }
}

const templatesDir = join(HELM_DIR, "templates");
if (!existsSync(templatesDir)) {
  fail("templates/ directory missing");
} else {
  const requiredTemplates = [
    "baseline/namespaces.yaml",
    "baseline/network-policies.yaml",
    "baseline/pod-disruption-budgets.yaml",
    "baseline/storage-classes.yaml",
    "_helpers.tpl",
    "_probes.tpl",
  ];
  for (const tpl of requiredTemplates) {
    if (!existsSync(join(templatesDir, tpl))) {
      fail(`template missing — templates/${tpl}`);
    }
  }

  // Verify NetworkPolicy default-deny contains a `policyTypes` entry
  // for both Ingress and Egress in every rendered namespace.
  const netPol = readFileSync(join(templatesDir, "baseline", "network-policies.yaml"), "utf8");
  if (!/policyTypes:\s*\n\s*-\s*Ingress\s*\n\s*-\s*Egress/.test(netPol)) {
    fail("NetworkPolicy default-deny must declare both Ingress and Egress policyTypes");
  }

  // Verify PDB template references single-replica workloads (the
  // template iterates `singleReplicaWorkloads` and emits a
  // `app.kubernetes.io/component: <name>` label).
  const pdb = readFileSync(join(templatesDir, "baseline", "pod-disruption-budgets.yaml"), "utf8");
  const pdbSingle = new RegExp(`\\{\\{\\s*\\$single\\s*\\}\\}`).test(pdb)
    ? new RegExp(`maxUnavailable:\\s*0`).test(pdb)
    : false;
  if (!pdbSingle) {
    fail("PodDisruptionBudget must iterate singleReplicaWorkloads and force maxUnavailable: 0");
  }
  if (!new RegExp(`maxUnavailable:\\s*0`).test(pdb)) {
    fail("PodDisruptionBudget missing maxUnavailable: 0 for single-replica workloads");
  }

  // Verify StorageClass encryption flag is wired through.
  const storage = readFileSync(join(templatesDir, "baseline", "storage-classes.yaml"), "utf8");
  if (!/encrypted:\s*"true"/.test(storage)) {
    fail("StorageClass must declare encrypted: 'true'");
  }
}

for (const envFile of REQUIRED_ENV_VALUES) {
  const p = join(HELM_DIR, envFile);
  if (!existsSync(p)) continue;
  const text = readFileSync(p, "utf8");
  const envName = basename(envFile, ".yaml").replace(/^values-/, "");
  if (!new RegExp(`environment:\\s*${envName}\\b`).test(text)) {
    fail(`${envFile} must set 'global.environment: ${envName}'`);
  }
  // Region pin — every env file declares the region it ships in.
  if (!/region:\s*\S+/.test(text)) {
    fail(`${envFile} must declare 'global.region'`);
  }
  // Tenant-shared namespace declaration is only required in values.yaml
  // (the per-env files inherit it). We assert it in the loop above.
}

if (!existsSync(LOCAL_DIR)) {
  fail(`local profile directory missing — expected ${relative(ROOT, LOCAL_DIR)}`);
} else {
  for (const f of REQUIRED_LOCAL_FILES) {
    if (!existsSync(join(LOCAL_DIR, f))) {
      fail(`platform/local missing required entry '${f}'`);
    }
  }

  const profilePath = join(LOCAL_DIR, "profile.yaml");
  if (existsSync(profilePath)) {
    const profile = readFileSync(profilePath, "utf8");
    if (!/postgres:/.test(profile) || !/version:\s*"16"/.test(profile)) {
      fail("platform/local/profile.yaml must pin Postgres 16 (ADR-E0.5-01)");
    }
    if (!/image:\s*apicurio\/apicurio-registry:2\.6/.test(profile)) {
      fail("platform/local/profile.yaml must pin Apicurio 2.6 (ADR-E0.5-01)");
    }
    if (!/image:\s*temporalio\/auto-setup:1\.26\.2/.test(profile)) {
      fail("platform/local/profile.yaml must pin Temporal 1.26 (ADR-E0.5-01)");
    }
    if (!/image:\s*openfga\/openfga:1\.10/.test(profile)) {
      fail("platform/local/profile.yaml must pin OpenFGA 1.x (ADR-E0.5-01)");
    }
    if (!/image:\s*valkey\/valkey:7\.2-alpine/.test(profile)) {
      fail("platform/local/profile.yaml must pin Valkey 7.2 (ADR-E0.5-01)");
    }
  }

  const composePath = join(LOCAL_DIR, "docker-compose.yml");
  if (existsSync(composePath)) {
    const compose = readFileSync(composePath, "utf8");
    for (const required of [
      "postgres:",
      "keycloak:",
      "openfga:",
      "kafka:",
      "apicurio:",
      "temporal:",
      "minio:",
      "valkey:",
      "flagsmith:",
      "otel-collector:",
    ]) {
      if (!new RegExp(`^  ${required.replace(/[:]/g, ":")}`, "m").test(compose)) {
        fail(`docker-compose.yml missing service '${required.replace(/[:]/g, "")}'`);
      }
    }
    if (!/\$\{[A-Z_]+\}/.test(compose)) {
      fail("docker-compose.yml must reference env vars for secrets (no literal credentials)");
    }
  }
}

// Preflight capacity / version checks (static, ADR-E0.5-01 parity).
const versionMatrix = [
  { component: "java", expected: "21 LTS" },
  { component: "node", expected: "22 LTS" },
  { component: "next", expected: "15.x" },
  { component: "spring-boot", expected: "3.3.x" },
  { component: "postgres", expected: "16.x" },
  { component: "kafka", expected: "3.8.x" },
  { component: "apicurio", expected: "2.6.x" },
  { component: "keycloak", expected: "26.x" },
  { component: "openfga", expected: "1.x" },
  { component: "temporal", expected: "1.26.x" },
  { component: "istio", expected: "1.23.x" },
  { component: "kong", expected: "3.8.x" },
  { component: "vault", expected: "1.17.x" },
];
const chartYaml = readFileSync(chartYamlPath, "utf8");
if (!/kubeVersion:\s*">=1\.28\.0-0"/.test(chartYaml)) {
  fail("Chart.yaml must pin kubeVersion >= 1.28.0-0 (Istio 1.23 requirement)");
}

function finish() {
  if (violations > 0) {
    console.error(`\n[baseline] ${violations} violation(s)`);
    process.exit(1);
  }
  console.log(
    `[baseline] clean — namespaces=${REQUIRED_NAMESPACES.length}, envs=${REQUIRED_ENV_VALUES.length}, versions=${versionMatrix.length}`,
  );
}

// ---------------------------------------------------------------------------
// E2.2 — Kong runtime invariants (static check; `scripts/lint-kong-config.mjs`
// runs the deep YAML validation).
// ---------------------------------------------------------------------------
const kongConfig = join(KONG_DIR, "kong.yml");
const kongValues = join(KONG_DIR, "values.yaml");
const kongTemplates = join(HELM_DIR, "templates", "components", "kong");
const kongDeclarativeCm = join(kongTemplates, "declarative-configmap.yaml");
const kongDeployment = join(kongTemplates, "deployment.yaml");
const kongService = join(kongTemplates, "service.yaml");
const kongNetPol = join(kongTemplates, "network-policy.yaml");

if (!existsSync(kongConfig)) {
  fail("kong declarative config missing — expected platform/kong/kong.yml (E2.2)");
} else {
  const kong = readFileSync(kongConfig, "utf8");
  if (!/_format_version:\s*"3\.0"/.test(kong)) {
    fail("kong.yml must declare _format_version: '3.0' (Kong 3.x DB-less)");
  }
  // DB-less mode is enforced via the `KONG_DATABASE=off` env var (in
  // docker-compose + the Helm Deployment env block). Declaring a
  // top-level `database: "off"` key in the config file is a Kong
  // parse error — the linter rejects it.
  if (/^database:\s*"off"/.test(kong)) {
    fail(
      "kong.yml must not declare a top-level 'database' key — use KONG_DATABASE=off in the runtime env",
    );
  }
  // `allowed_payload_size` must be an integer (Kong 3.x rejects
  // string values even when `size_unit` is set).
  if (/"allowed_payload_size":\s*"[0-9]/.test(kong)) {
    fail(
      "kong.yml request-size-limiting.allowed_payload_size must be an integer (not a quoted string)",
    );
  }
  for (const route of ["public-web-root", "web-bff-api", "public-api-v1", "admin-api-v1"]) {
    if (!new RegExp(`name:\\s*${route}\\b`).test(kong)) {
      fail(`kong.yml missing required route '${route}' (E2.2)`);
    }
  }
  if (
    !/route-class:\s*public\b/.test(kong) ||
    !/route-class:\s*authenticated\b/.test(kong) ||
    !/route-class:\s*partner\b/.test(kong) ||
    !/route-class:\s*admin\b/.test(kong)
  ) {
    fail("kong.yml must tag every route with 'route-class:<public|authenticated|partner|admin>'");
  }
  if (!/correlation-id/.test(kong) || !/rate-limiting/.test(kong)) {
    fail("kong.yml must enable correlation-id and rate-limiting plugins");
  }
  if (!/cors/.test(kong)) {
    fail("kong.yml must enable the cors plugin on the browser routes");
  }
  if (!/request-size-limiting/.test(kong)) {
    fail("kong.yml must enable the request-size-limiting plugin on body-bearing routes");
  }
  if (!/ip-restriction/.test(kong)) {
    fail("kong.yml must enable the ip-restriction plugin on the admin route");
  }
  // Domain authorization must never appear in Kong.
  for (const forbidden of [
    "oauth2-introspection",
    "mtls-auth",
    "key-auth",
    "acl",
    "basic-auth",
    "ldap-auth",
  ]) {
    if (new RegExp(`-\\s*name:\\s*${forbidden}\\b`).test(kong)) {
      fail(
        `kong.yml must not enable plugin '${forbidden}' — domain authorization belongs to the destination service`,
      );
    }
  }
}

if (!existsSync(kongValues)) {
  fail("platform/kong/values.yaml missing — E2.2 contract values file");
} else {
  const kv = readFileSync(kongValues, "utf8");
  if (!/repository:\s*kong\b/.test(kv) || !/tag:\s*"3\.8/.test(kv)) {
    fail("platform/kong/values.yaml must pin Kong 3.8.x (ADR-E0.5-01)");
  }
  if (!/database:\s*"off"/.test(kv)) {
    fail("platform/kong/values.yaml must declare database: 'off' (DB-less)");
  }
}

for (const tpl of [kongDeclarativeCm, kongDeployment, kongService, kongNetPol]) {
  if (!existsSync(tpl)) {
    fail(`kong helm template missing — ${relative(ROOT, tpl)}`);
  }
}

// `domainAuthorizationInKong` must remain false in every per-env
// values file — domain authorization is a service-side concern per
// design.md §4.1.
for (const envFile of REQUIRED_ENV_VALUES) {
  const p = join(HELM_DIR, envFile);
  if (!existsSync(p)) continue;
  const text = readFileSync(p, "utf8");
  if (/domainAuthorizationInKong:\s*true/.test(text)) {
    fail(`${envFile} must keep 'domainAuthorizationInKong: false' (E2.2 contract)`);
  }
}

// ---------------------------------------------------------------------------
// E2.3 — Strimzi Kafka + Apicurio runtime invariants (static check;
// `scripts/lint-kafka-config.mjs` runs the deep YAML validation).
// ---------------------------------------------------------------------------
const KAFKA_DIR = join(ROOT, "platform", "kafka");
const APICURIO_DIR = join(ROOT, "platform", "apicurio");
const KAFKA_CR = join(KAFKA_DIR, "kafka.yaml");
const KAFKA_TOPICS = join(KAFKA_DIR, "topics.yaml");
const KAFKA_USERS = join(KAFKA_DIR, "users.yaml");
const APICURIO_CFG = join(APICURIO_DIR, "registry-config.yaml");
const ALERTS_DIR = join(ROOT, "platform", "observability", "alerts");

const kafkaTemplates = join(HELM_DIR, "templates", "components", "kafka");
const apicurioTemplates = join(HELM_DIR, "templates", "components", "apicurio");

for (const f of [KAFKA_CR, KAFKA_TOPICS, KAFKA_USERS, APICURIO_CFG]) {
  if (!existsSync(f)) {
    fail(`E2.3 source-of-truth file missing — ${relative(ROOT, f)}`);
  }
}

// Kafka CR must pin Kafka 3.8.x (ADR-E0.5-01) and disable auto topic
// creation. The chart copies the same file into
// `files/kafka/...`; the linter enforces the deep invariants.
if (existsSync(KAFKA_CR)) {
  const k = readFileSync(KAFKA_CR, "utf8");
  if (!/version:\s*3\.8\.0/.test(k)) {
    fail("kafka.yaml must pin Kafka 3.8.0 (ADR-E0.5-01)");
  }
  if (!/auto\.create\.topics\.enable:\s*false/.test(k)) {
    fail("kafka.yaml must disable auto topic creation (every topic is declared in topics.yaml)");
  }
  if (!/CN=genea-kafka-admin/.test(k)) {
    fail("kafka.yaml must declare genea-kafka-admin as a super-user");
  }
  // StaticQuotaCallback REMOVED — Strimzi 0.45.x forbidden-list still
  // strips `client.quota.callback.static.kafka.admin.bootstrap.servers`,
  // `client.quota.callback.static.produce`, and
  // `client.quota.callback.static.excluded.principal.name.list`. Broker
  // crashes with "Missing required configuration" if
  // `client.quota.callback.class` is set without those keys. Re-enable
  // only when Strimzi 0.46.x is adopted platform-wide (ADR-E0.5-08
  // supersession). Quota enforcement moves to KafkaUser.spec.quotas +
  // Kong edge rate-limit (E2.2). See evidence/E2.3.md §5 follow-up #2.
  // Match only uncommented occurrences (lines that do NOT start with
  // optional whitespace + `#`). The kafka.yaml rationale comment
  // explicitly references the string to document why it is removed.
  const strippedK = k.replace(/^\s*#.*$/gm, "");
  if (/StaticQuotaCallback/.test(strippedK)) {
    fail(
      "kafka.yaml must NOT enable Strimzi StaticQuotaCallback — forbidden-list still active in 0.45.x; tracked in ADR-E0.5-08",
    );
  }
  if (!/kind:\s*Kafka\b/.test(k)) {
    fail("kafka.yaml must declare a Strimzi 'Kafka' resource");
  }
}

// Topics file must declare the 4 ADR-E0.5-08 classes.
if (existsSync(KAFKA_TOPICS)) {
  const t = readFileSync(KAFKA_TOPICS, "utf8");
  for (const cls of ["domain-event", "projection-rebuild", "audit", "dlq"]) {
    if (!new RegExp(`topicClass:\\s*${cls}\\b`).test(t)) {
      fail(`kafka topics.yaml must declare at least one '${cls}' topic (ADR-E0.5-08)`);
    }
  }
  // KRaft metadataVersion (3.8) must be quoted as a string per
  // K8s schema. Bare `3.8` parses as a float and Strimzi rejects
  // it.
  const kcr = readFileSync(KAFKA_CR, "utf8");
  if (!/metadataVersion:\s*"3\.8"/.test(kcr) && !/metadataVersion:\s*'3\.8'/.test(kcr)) {
    fail(`kafka.yaml must pin metadataVersion as a quoted string "3.8" (KRaft)`);
  }
  // Strimzi 0.43 still requires `spec.zookeeper` block even when
  // KRaft metadataVersion is set. Enforce presence.
  if (!/zookeeper:[\s\S]*?replicas:\s*\d+[\s\S]*?storage:/.test(kcr)) {
    fail(
      `kafka.yaml must declare a zookeeper block with replicas and storage (Strimzi 0.43 schema)`,
    );
  }
}

// Users file must cover admin / producer / consumer.
if (existsSync(KAFKA_USERS)) {
  const u = readFileSync(KAFKA_USERS, "utf8");
  for (const role of ["admin", "producer", "consumer"]) {
    if (!new RegExp(`role:\\s*${role}\\b`).test(u)) {
      fail(`kafka users.yaml must declare at least one '${role}' user`);
    }
  }
  if (/authType:\s*scram-sha-512/i.test(u)) {
    fail(
      "kafka users.yaml must not declare scram-sha-512 (no literal credentials per ADR-E0.5-01)",
    );
  }
  if (!/genea-kafka-admin/.test(u)) {
    fail("kafka users.yaml must declare the 'genea-kafka-admin' super-user");
  }
}

// Apicurio config must keep the SQL store and disable the Confluent
// license shim.
if (existsSync(APICURIO_CFG)) {
  const a = readFileSync(APICURIO_CFG, "utf8");
  if (!/registry\.storage\.kind=sql/.test(a)) {
    fail(
      "apicurio registry-config.yaml must keep registry.storage.kind=sql (in-memory forbidden in production)",
    );
  }
  if (!/registry\.apis\.confluent\.enabled=false/.test(a)) {
    fail("apicurio must keep 'registry.apis.confluent.enabled=false' (license compliance)");
  }
  if (!/BACKWARD/.test(a)) {
    fail("apicurio must declare 'BACKWARD' as the global default compatibility (ADR-E0.5-08)");
  }
  if (!/AVRO/.test(a)) {
    fail("apicurio must declare at least one AVRO artifact (Avro is the canonical serializer)");
  }
}

// Alert rules must cover the 4 E2.3 signals.
if (!existsSync(ALERTS_DIR) || !existsSync(join(ALERTS_DIR, "kafka-rules.yaml"))) {
  fail("platform/observability/alerts/kafka-rules.yaml missing (E2.3 alert contract)");
} else {
  const r = readFileSync(join(ALERTS_DIR, "kafka-rules.yaml"), "utf8");
  for (const alert of [
    "KafkaUnderReplicatedPartitions",
    "KafkaOfflinePartitions",
    "KafkaBrokerLogDiskPressure",
    "KafkaBrokerOutOfDisk",
    "KafkaConsumerLag",
    "KafkaConsumerLagCritical",
    "ApicurioRegistryDown",
    "ApicurioRegistryArtifactFailures",
  ]) {
    if (!new RegExp(`alert:\\s*${alert}\\b`).test(r)) {
      fail(`platform/observability/alerts/kafka-rules.yaml missing alert '${alert}' (E2.3)`);
    }
  }
}

// Helm templates for kafka + apicurio must exist.
for (const tpl of [
  join(kafkaTemplates, "kafka.yaml"),
  join(kafkaTemplates, "topics.yaml"),
  join(kafkaTemplates, "users.yaml"),
  join(kafkaTemplates, "metrics-configmap.yaml"),
  join(kafkaTemplates, "network-policy.yaml"),
  join(apicurioTemplates, "registry.yaml"),
]) {
  if (!existsSync(tpl)) {
    fail(`E2.3 helm template missing — ${relative(ROOT, tpl)}`);
  }
}

// ---------------------------------------------------------------------------
// E2.5 — Istio service mesh runtime invariants (static check;
// `scripts/lint-istio-config.mjs` runs the deep YAML validation).
// ---------------------------------------------------------------------------
const ISTIO_DIR = join(ROOT, "platform", "istio");
const ISTIO_FILES = [
  join(ISTIO_DIR, "mesh-config.yaml"),
  join(ISTIO_DIR, "peer-auth.yaml"),
  join(ISTIO_DIR, "authz-policies.yaml"),
  join(ISTIO_DIR, "telemetry.yaml"),
];
const istioTemplates = join(HELM_DIR, "templates", "components", "istio");
const REQUIRED_ISTIO_TEMPLATES = [
  "mesh-config-configmap.yaml",
  "peer-auth-configmap.yaml",
  "authz-policies-configmap.yaml",
  "telemetry-configmap.yaml",
  "serviceaccount.yaml",
  "init-scripts-configmap.yaml",
  "bootstrap-job.yaml",
  "network-policies.yaml",
];

for (const f of ISTIO_FILES) {
  if (!existsSync(f)) {
    fail(`E2.5 source-of-truth file missing — ${relative(ROOT, f)}`);
  }
}

for (const tpl of REQUIRED_ISTIO_TEMPLATES) {
  const p = join(istioTemplates, tpl);
  if (!existsSync(p)) {
    fail(`E2.5 helm template missing — ${relative(ROOT, p)}`);
  }
}

if (existsSync(valuesPath)) {
  const v = readFileSync(valuesPath, "utf8");
  // ADR-E0.5-01 baseline pin (Istio 1.23.x).
  if (!/version:\s*"1\.23"/.test(v)) {
    fail("values.yaml must pin Istio version to 1.23 (ADR-E0.5-01)");
  }
  if (!/outboundPolicy:\s*REGISTRY_ONLY/.test(v)) {
    fail("values.yaml must declare components.istio.mesh.outboundPolicy: REGISTRY_ONLY (E2.5 §1)");
  }
  if (!/inboundPolicy:\s*MUTUAL_TLS/.test(v)) {
    fail("values.yaml must declare components.istio.mesh.inboundPolicy: MUTUAL_TLS (E2.5 §1)");
  }
  if (!/mtls:\s*STRICT/.test(v)) {
    fail("values.yaml must declare components.istio.mesh.mtls: STRICT (E2.5 §2)");
  }
  if (!/retryBudget:\s*null/.test(v)) {
    fail("values.yaml must declare components.istio.mesh.retryBudget: null (E2.5 §4)");
  }
  if (!/kubectlImage:/.test(v)) {
    fail("values.yaml must declare components.istio.kubectlImage (E2.5 bootstrap Job)");
  }
  if (!/trustDomain:\s*cluster\.local/.test(v)) {
    fail("values.yaml must declare components.istio.trustDomain: cluster.local (E2.5 §1)");
  }
}

// Alert rules must cover the 5 E2.5 signal classes.
if (!existsSync(ALERTS_DIR) || !existsSync(join(ALERTS_DIR, "istio-rules.yaml"))) {
  fail("platform/observability/alerts/istio-rules.yaml missing (E2.5 alert contract)");
} else {
  const r = readFileSync(join(ALERTS_DIR, "istio-rules.yaml"), "utf8");
  for (const alert of [
    "IstioControlPlaneDown",
    "IstioPilotPushErrors",
    "IstioMtlsHandshakeFailures",
    "IstioMtlsHandshakeFailuresHigh",
    "IstioAuthzDenialSpike",
    "IstioAuthzDenialSpikeCritical",
    "IstioUpstreamFailureSpike",
    "IstioUpstreamFailureSpikeCritical",
    "IstioBootstrapJobFailed",
  ]) {
    if (!new RegExp(`alert:\\s*${alert}\\b`).test(r)) {
      fail(`platform/observability/alerts/istio-rules.yaml missing alert '${alert}' (E2.5)`);
    }
  }
}

// Profile.yaml must declare the Istio dev pin.
if (existsSync(join(LOCAL_DIR, "profile.yaml"))) {
  const p = readFileSync(join(LOCAL_DIR, "profile.yaml"), "utf8");
  if (!/version:\s*"1\.23"/.test(p)) {
    fail("platform/local/profile.yaml must pin Istio version to 1.23 (ADR-E0.5-01)");
  }
  if (!/kubectlImage:\s*bitnami\/kubectl:1\.31\.1/.test(p)) {
    fail("platform/local/profile.yaml must pin the Istio bootstrap Job kubectl image (E2.5 §4)");
  }
}


// ---------------------------------------------------------------------------
// E2.4 — Temporal runtime invariants (static check;
// `scripts/lint-temporal-config.mjs` runs the deep YAML validation).
// ---------------------------------------------------------------------------
const TEMPORAL_DIR = join(ROOT, "platform", "temporal");
const TEMPORAL_FILES = [
  join(TEMPORAL_DIR, "namespace-config.yaml"),
  join(TEMPORAL_DIR, "search-attrs.yaml"),
  join(TEMPORAL_DIR, "dynamic-config.yaml"),
  join(TEMPORAL_DIR, "task-queues.yaml"),
];
const temporalTemplates = join(HELM_DIR, "templates", "components", "temporal");
const REQUIRED_TEMPORAL_TEMPLATES = [
  "statefulset.yaml",
  "ui-deployment.yaml",
  "services.yaml",
  "serviceaccounts.yaml",
  "namespace-init-job.yaml",
  "task-queue-init-job.yaml",
  "init-scripts-configmap.yaml",
  "namespace-configmap.yaml",
  "search-attrs-configmap.yaml",
  "dynamic-config-configmap.yaml",
  "task-queue-configmap.yaml",
  "network-policies.yaml",
];
const ALERTS_DIR_E24 = join(ROOT, "platform", "observability", "alerts");

for (const f of TEMPORAL_FILES) {
  if (!existsSync(f)) {
    fail(`E2.4 source-of-truth file missing — ${relative(ROOT, f)}`);
  }
}

for (const tpl of REQUIRED_TEMPORAL_TEMPLATES) {
  const p = join(temporalTemplates, tpl);
  if (!existsSync(p)) {
    fail(`E2.4 helm template missing — ${relative(ROOT, p)}`);
  }
}

if (existsSync(valuesPath)) {
  const v = readFileSync(valuesPath, "utf8");
  // ADR-E0.5-01 baseline pin (Temporal 1.26.x) plus the
  // admin-tools image the Helm-hook Jobs use.
  if (!/tag:\s*"1\.26\.2"/.test(v)) {
    fail("values.yaml must pin Temporal image tag to 1.26.2 (ADR-E0.5-01)");
  }
  if (!/adminToolsImage:/.test(v)) {
    fail("values.yaml must declare components.temporal.adminToolsImage (E2.4 Helm-hook Jobs)");
  }
  if (!/ui:\s*\n\s*enabled:\s*true/.test(v)) {
    fail("values.yaml must enable the Temporal UI block (E2.4 §6); dev/onprem values may override");
  }
  // The dynamic-config.yaml ConfigMap must mount into the StatefulSet.
  if (!/dynamic-config/.test(readFileSync(join(temporalTemplates, "statefulset.yaml"), "utf8"))) {
    fail("E2.4 statefulset.yaml must mount the dynamic-config ConfigMap (E2.4 §2)");
  }
  // The dynamic-config source-of-truth file must carry the
  // 9 visibility attributes. The chart mounts the file via
  // ConfigMap; the linter enforces the source.
  const dynPath = join(TEMPORAL_DIR, "dynamic-config.yaml");
  if (existsSync(dynPath)) {
    const dyn = readFileSync(dynPath, "utf8");
    const requiredAttrs = [
      "TenantId",
      "WorkflowType",
      "TaskQueue",
      "Attempt",
      "AggregateType",
      "AggregateId",
      "MediaAssetId",
      "TransferJobId",
      "ConsentId",
    ];
    for (const attr of requiredAttrs) {
      if (!new RegExp(`-\\s*name:\\s*${attr}\\b`).test(dyn)) {
        fail(`dynamic-config.yaml missing visibility attribute '${attr}' (E2.4 §3)`);
      }
    }
  }
  // The source-of-truth namespace-config.yaml must declare the
  // `genea-dna` namespace with 365-day retention (ADR-E0.5-07 +
  // `privacy-and-legal-gate.md` §14).
  const nsPath = join(TEMPORAL_DIR, "namespace-config.yaml");
  if (existsSync(nsPath)) {
    const ns = readFileSync(nsPath, "utf8");
    if (!/- name:\s*genea-dna\b/.test(ns)) {
      fail("namespace-config.yaml must declare the 'genea-dna' namespace (E2.4 §1)");
    }
    // Best-effort retention assertion — the linter already
    // enforces this structurally.
    if (!/genea-dna[\s\S]*?retentionDays:\s*365/.test(ns)) {
      fail("namespace-config.yaml must declare 'genea-dna' retentionDays: 365");
    }
  }
}

// Alert rules must cover the 4 E2.4 signal classes.
if (!existsSync(ALERTS_DIR_E24) || !existsSync(join(ALERTS_DIR_E24, "temporal-rules.yaml"))) {
  fail("platform/observability/alerts/temporal-rules.yaml missing (E2.4 alert contract)");
} else {
  const r = readFileSync(join(ALERTS_DIR_E24, "temporal-rules.yaml"), "utf8");
  for (const alert of [
    "TemporalServerDown",
    "TemporalWorkflowStartLatencyHigh",
    "TemporalWorkflowFailureRateHigh",
    "TemporalActivityFailureRateHigh",
    "TemporalTaskQueueDepthHigh",
    "TemporalReconciliationFailed",
  ]) {
    if (!new RegExp(`alert:\\s*${alert}\\b`).test(r)) {
      fail(`platform/observability/alerts/temporal-rules.yaml missing alert '${alert}' (E2.4)`);
    }
  }
}

// Profile.yaml + docker-compose must declare the Temporal dev
// service with the ADR-E0.5-01 pinned image.
if (existsSync(join(LOCAL_DIR, "profile.yaml"))) {
  const p = readFileSync(join(LOCAL_DIR, "profile.yaml"), "utf8");
  if (!/image:\s*temporalio\/auto-setup:1\.26\.2/.test(p)) {
    fail(
      "platform/local/profile.yaml must pin Temporal image to temporalio/auto-setup:1.26.2 (ADR-E0.5-01)",
    );
  }
  if (!/adminToolsImage:\s*temporalio\/admin-tools:1\.26\.2/.test(p)) {
    fail("platform/local/profile.yaml must pin Temporal admin-tools image (E2.4 Helm-hook Jobs)");
  }
}

// values.yaml must pin Kafka 3.8.x + Apicurio 3.x (ADR-E0.5-01
// supersession — bumped Strimzi 0.43.0 → 0.45.2 to fix entity-operator
// Admin API bug + KRaft stability; bumped Apicurio 2.6.x → 3.3.x after
// Docker Hub dropped 2.6.x tags).
if (existsSync(valuesPath)) {
  const v = readFileSync(valuesPath, "utf8");
  if (!/tag:\s*0\.45\.2-kafka-3\.8\.0/.test(v)) {
    fail("values.yaml must pin Kafka image tag to 0.45.2-kafka-3.8.0 (ADR-E0.5-01 supersession)");
  }
  if (!/tag:\s*"?3\.3/.test(v)) {
    fail(
      "values.yaml must pin Apicurio image tag to 3.x (ADR-E0.5-01 supersession; Docker Hub dropped 2.6.x)",
    );
  }
  if (!/defaultCompatibility:\s*BACKWARD/.test(v)) {
    fail("values.yaml must set apicurio.defaultCompatibility to BACKWARD (ADR-E0.5-08)");
  }
}

finish();
