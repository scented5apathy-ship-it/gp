#!/usr/bin/env node
/**
 * scripts/lint-istio-config.mjs
 *
 * E2.5 deep validator for the Istio service mesh source-of-truth
 * files in `platform/istio/`. Mirrors `lint-temporal-config.mjs`
 * style — uses the same `yaml` parser and reports exit 0 on
 * success, 1 on violation, 2 on configuration error.
 *
 * Asserts:
 *   - `platform/istio/mesh-config.yaml` carries a `MeshConfig`
 *     payload with:
 *       - `outboundTrafficPolicy.mode == REGISTRY_ONLY` (E2.5 §1)
 *       - `inboundTrafficPolicy.mode == MUTUAL_TLS`
 *       - `defaultConfig.proxyMetadata.ISTIO_META_ENABLE_HBONE == "true"`
 *       - `defaultConfig.retryBudget == null` (E2.5 §4 — no
 *         mesh-level retry)
 *       - `trustDomain == cluster.local`
 *   - `platform/istio/peer-auth.yaml` declares the
 *     `peerAuthentications` list with one STRICT entry per
 *     workload namespace (gp-platform, gp-edge, gp-bff,
 *     gp-services, gp-workers, gp-data, gp-observability,
 *     gp-argocd). PERMISSIVE / DISABLE are forbidden.
 *   - `platform/istio/authz-policies.yaml` declares the
 *     `authorizationPolicies` list with the mandatory DENY
 *     blocks for dna-service + media-worker + dna-worker.
 *     The deny-plaintext and kong-to-bff allow rules are also
 *     required.
 *   - `platform/istio/telemetry.yaml` declares the disjoint
 *     retry policy (`mesh.retryBudget == null` AND
 *     `app.retry.maxAttempts == 3`). The OTel trace driver and
 *     the JSON accesslog with the SPIFFE principal are required.
 *   - No literal secret / token / password in any of the files.
 *   - The four files are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/istio/`.
 *
 * Per `agent-execution.md` §4.4 this script does NOT mutate the
 * repo and is safe to run in CI.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const ISTIO_DIR = join(ROOT, "platform", "istio");

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

const REQUIRED_AUTHZ_RULES = [
  "deny-plaintext",
  "kong-to-bff",
  "dna-service-egress-deny",
  "dna-service-ingress-deny",
  "media-worker-egress-deny",
  "media-worker-ingress-allow",
  "dna-worker-egress-deny",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[istio] ${msg}`);
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
  for (const key of ["password", "apiKey", "token", "private_key", "secret"]) {
    const literalRegex = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
    if (literalRegex.test(text)) {
      fail(
        `literal secret-like value for '${key}' in ${relative(ROOT, path)} — use Vault / External Secrets`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// mesh-config.yaml — MeshConfig posture
// ---------------------------------------------------------------------------
const meshFile = join(ISTIO_DIR, "mesh-config.yaml");
const meshDoc = loadYaml(meshFile);
if (meshDoc) {
  const data = meshDoc?.data?.["mesh.yaml"];
  if (!data) {
    fail(`mesh-config.yaml must declare a ConfigMap with a 'mesh.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`mesh-config.yaml mesh is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      if (parsed.outboundTrafficPolicy?.mode !== "REGISTRY_ONLY") {
        fail(
          `mesh-config.yaml outboundTrafficPolicy.mode must be 'REGISTRY_ONLY' (E2.5 §1) — got ${parsed.outboundTrafficPolicy?.mode}`,
        );
      }
      if (parsed.inboundTrafficPolicy?.mode !== "MUTUAL_TLS") {
        fail(
          `mesh-config.yaml inboundTrafficPolicy.mode must be 'MUTUAL_TLS' — got ${parsed.inboundTrafficPolicy?.mode}`,
        );
      }
      if (parsed.defaultConfig?.proxyMetadata?.ISTIO_META_ENABLE_HBONE !== "true") {
        fail(
          `mesh-config.yaml defaultConfig.proxyMetadata.ISTIO_META_ENABLE_HBONE must be 'true' (Istio 1.23 ambient mode)`,
        );
      }
      if (
        parsed.defaultConfig?.retryBudget !== null &&
        parsed.defaultConfig?.retryBudget !== undefined
      ) {
        fail(
          `mesh-config.yaml defaultConfig.retryBudget must be null (E2.5 §4 — no mesh-level retry) — got ${JSON.stringify(parsed.defaultConfig?.retryBudget)}`,
        );
      }
      if (parsed.trustDomain !== "cluster.local") {
        fail(
          `mesh-config.yaml trustDomain must be 'cluster.local' — got ${parsed.trustDomain}`,
        );
      }
      if (!Array.isArray(parsed.extensionProviders) || parsed.extensionProviders.length === 0) {
        fail(`mesh-config.yaml must declare an extensionProviders list`);
      }
    }
  }
  assertNoSecrets(readFileSync(meshFile, "utf8"), meshFile);
}

// ---------------------------------------------------------------------------
// peer-auth.yaml — STRICT mTLS on every namespace
// ---------------------------------------------------------------------------
const peerFile = join(ISTIO_DIR, "peer-auth.yaml");
const peerDoc = loadYaml(peerFile);
if (peerDoc) {
  const data = peerDoc?.data?.["policy.yaml"];
  if (!data) {
    fail(`peer-auth.yaml must declare a ConfigMap with a 'policy.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`peer-auth.yaml policy is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const list = parsed.peerAuthentications;
      if (!Array.isArray(list) || list.length === 0) {
        fail(`peer-auth.yaml must declare a non-empty peerAuthentications array (E2.5 §2)`);
      } else {
        const declared = new Set(list.map((p) => p.namespace));
        for (const required of REQUIRED_NAMESPACES) {
          if (!declared.has(required)) {
            fail(`peer-auth.yaml missing PeerAuthentication for namespace '${required}' (E2.5 §2)`);
          }
        }
        for (const entry of list) {
          if (entry.mtls?.mode === undefined) {
            fail(`peer-auth.yaml entry for '${entry.namespace || "<unnamed>"}' missing mtls.mode`);
          } else if (entry.mtls.mode !== "STRICT") {
            fail(
              `peer-auth.yaml entry for '${entry.namespace}' must declare mtls.mode: STRICT — got ${entry.mtls.mode}`,
            );
          }
        }
      }
      const forbidden = parsed.forbiddenModes || [];
      for (const mode of ["PERMISSIVE", "DISABLE"]) {
        if (!forbidden.includes(mode)) {
          fail(`peer-auth.yaml forbiddenModes must include '${mode}' (E2.5 §2)`);
        }
      }
      if (parsed.trustDomain !== "cluster.local") {
        fail(`peer-auth.yaml trustDomain must be 'cluster.local' — got ${parsed.trustDomain}`);
      }
    }
  }
  assertNoSecrets(readFileSync(peerFile, "utf8"), peerFile);
}

// ---------------------------------------------------------------------------
// authz-policies.yaml — DENY rules for dna-service / media-worker
// ---------------------------------------------------------------------------
const authzFile = join(ISTIO_DIR, "authz-policies.yaml");
const authzDoc = loadYaml(authzFile);
if (authzDoc) {
  const data = authzDoc?.data?.["policy.yaml"];
  if (!data) {
    fail(`authz-policies.yaml must declare a ConfigMap with a 'policy.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`authz-policies.yaml policy is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const list = parsed.authorizationPolicies;
      if (!Array.isArray(list) || list.length === 0) {
        fail(`authz-policies.yaml must declare a non-empty authorizationPolicies array (E2.5 §3)`);
      } else {
        const declared = new Set(list.map((p) => p.name));
        for (const required of REQUIRED_AUTHZ_RULES) {
          if (!declared.has(required)) {
            fail(
              `authz-policies.yaml missing mandatory AuthorizationPolicy '${required}' (E2.5 §3)`,
            );
          }
        }
        for (const p of list) {
          if (p.action === "CUSTOM") {
            fail(
              `authz-policies.yaml entry '${p.name}' uses action: CUSTOM without an explicit envoyExtAuthzHttp provider — startup crash`,
            );
          }
          if (
            p.action === "ALLOW" &&
            (!Array.isArray(p.rules) ||
              p.rules.length === 0 ||
              !p.rules[0].from ||
              !p.rules[0].from[0]?.source?.principals)
          ) {
            fail(
              `authz-policies.yaml entry '${p.name}' is an ALLOW without source.principals — the mesh will fall back to deny-by-default anyway; the rule is misleading`,
            );
          }
        }
      }
    }
  }
  assertNoSecrets(readFileSync(authzFile, "utf8"), authzFile);
}

// ---------------------------------------------------------------------------
// telemetry.yaml — disjoint retry + OTel + JSON accesslog
// ---------------------------------------------------------------------------
const telFile = join(ISTIO_DIR, "telemetry.yaml");
const telDoc = loadYaml(telFile);
if (telDoc) {
  const data = telDoc?.data?.["policy.yaml"];
  if (!data) {
    fail(`telemetry.yaml must declare a ConfigMap with a 'policy.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`telemetry.yaml policy is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      if (parsed.mesh?.retryBudget !== null && parsed.mesh?.retryBudget !== undefined) {
        fail(
          `telemetry.yaml mesh.retryBudget must be null (E2.5 §4 — no mesh-level retry) — got ${JSON.stringify(parsed.mesh?.retryBudget)}`,
        );
      }
      if (parsed.app?.retry?.maxAttempts !== 3) {
        fail(
          `telemetry.yaml app.retry.maxAttempts must be 3 (E2.5 §4 disjoint retry policy) — got ${parsed.app?.retry?.maxAttempts}`,
        );
      }
      if (parsed.telemetry?.tracing?.driver !== "otel") {
        fail(
          `telemetry.yaml tracing.driver must be 'otel' (E2.5 §5) — got ${parsed.telemetry?.tracing?.driver}`,
        );
      }
      if (parsed.telemetry?.accesslog?.format !== "JSON") {
        fail(
          `telemetry.yaml accesslog.format must be 'JSON' (E2.5 §5) — got ${parsed.telemetry?.accesslog?.format}`,
        );
      }
      const fields = parsed.telemetry?.accesslog?.fields || [];
      if (!fields.includes("downstream_peer_identity") && !fields.includes("source_principal")) {
        fail(
          `telemetry.yaml accesslog.fields must include the SPIFFE principal (downstream_peer_identity or source_principal)`,
        );
      }
    }
  }
  assertNoSecrets(readFileSync(telFile, "utf8"), telFile);
}

// ---------------------------------------------------------------------------
// Mirror files — every platform/istio/* must be present in the
// chart's files/istio/ directory.
// ---------------------------------------------------------------------------
const mirrorDir = join(ROOT, "platform", "helm", "genealogy-platform", "files", "istio");
for (const f of ["mesh-config.yaml", "peer-auth.yaml", "authz-policies.yaml", "telemetry.yaml"]) {
  const src = join(ISTIO_DIR, f);
  const dst = join(mirrorDir, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E2.5 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror out of sync — ${relative(ROOT, dst)}`);
  }
}

if (violations > 0) {
  console.error(`\n[istio] ${violations} violation(s)`);
  process.exit(1);
}
console.log(
  `[istio] clean — namespaces=${REQUIRED_NAMESPACES.length}, authz-rules=${REQUIRED_AUTHZ_RULES.length}`,
);
