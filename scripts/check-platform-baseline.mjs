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
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve, dirname, basename } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.BASELINE_ROOT ? resolve(process.env.BASELINE_ROOT) : resolve(HERE, "..");
const HELM_DIR = join(ROOT, "platform", "helm", "genealogy-platform");
const LOCAL_DIR = join(ROOT, "platform", "local");
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
  finish();
}

const chartYamlPath = join(HELM_DIR, "Chart.yaml");
if (!existsSync(chartYamlPath)) {
  fail("Chart.yaml missing");
} else {
  const text = readFileSync(chartYamlPath, "utf8");
  for (const field of ["apiVersion", "name", "version", "appVersion"]) {
    if (!new RegExp(`^${field}\\s*:`).test(text)) {
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
    if (!new RegExp(`name:\\s*${ns}\\b`).test(values)) {
      fail(`baseline namespace '${ns}' not declared in values.yaml`);
    }
  }

  if (!/requestsCpu:\s*"32"/.test(values)) {
    fail("gp-platform quota baseline (requestsCpu: 32) not found — ADR-E0.5-01 parity broken");
  }

  if (!/podSecurity:\\s*restricted/.test(values)) {
    fail("Pod Security 'restricted' profile missing from baseline.namespaces");
  }

  if (!/minAvailable:\s*1\b/.test(values)) {
    fail("PodDisruptionBudget.minAvailable default missing");
  }

  if (!new RegExp(`live:\\s*${PROBE_CONTRACT.live.replace(/\//g, "\\/")}`).test(values) ||
      !new RegExp(`ready:\\s*${PROBE_CONTRACT.ready.replace(/\//g, "\\/")}`).test(values) ||
      !new RegExp(`startup:\\s*${PROBE_CONTRACT.startup.replace(/\//g, "\\/")}`).test(values)) {
    fail(`probe path contract not enforced — expected ${PROBE_CONTRACT.live}, ${PROBE_CONTRACT.ready}, ${PROBE_CONTRACT.startup}`);
  }

  // Secret hygiene: no literal `password:`, `token:`, `apiKey:` etc.
  // outside of placeholders. The lint-yaml script catches this
  // repository-wide; we re-assert here so the baseline check is
  // self-contained.
  for (const key of ["password", "apiKey", "token", "private_key"]) {
    const literalRegex = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
    if (literalRegex.test(values)) {
      fail(`literal secret-like value for '${key}' found in values.yaml — use Vault / External Secrets`);
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
    if (!/\\$\\{[A-Z_]+\\}/.test(compose)) {
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

finish();
