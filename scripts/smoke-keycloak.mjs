#!/usr/bin/env node
/**
 * scripts/smoke-keycloak.mjs
 *
 * E3.1 smoke probe for the Keycloak OIDC identity provider
 * source-of-truth files in `platform/keycloak/`. If helm is on
 * PATH, the script renders the Keycloak ConfigMaps from the
 * umbrella chart; otherwise it runs a structural-only check.
 *
 * Per `agent-execution.md` §4.5 the smoke probe asserts the
 * E3.1 source-of-truth files carry the documented contract.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.SMOKE_ROOT ? resolve(process.env.SMOKE_ROOT) : resolve(HERE, "..");
const KEYCLOAK_DIR = join(ROOT, "platform", "keycloak");
const MIRROR_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files", "keycloak");
const TEMPLATES_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "templates", "components", "keycloak");

const REQUIRED_FILES = [
  "realm-strategy.yaml",
  "realm-export.yaml",
  "client-configs.yaml",
  "federation.yaml",
  "key-rotation.yaml",
];
const REQUIRED_TEMPLATES = [
  "configmap.yaml",
  "secrets.yaml",
  "serviceaccounts.yaml",
  "services.yaml",
  "statefulset.yaml",
  "network-policies.yaml",
  "bootstrap-job.yaml",
];
const REQUIRED_CLIENTS = [
  "web-app",
  "web-bff",
  "public-api",
  "kong-oidc-broker",
  "grafana-sso",
];

const checks = [];
const check = (label, ok, detail) => {
  checks.push({ label, ok, detail });
  console.log(`  ${ok ? "PASS" : "FAIL"}  ${label}${detail ? " — " + detail : ""}`);
};

console.log("[smoke:keycloak] E3.1 source-of-truth + helm template check");
console.log(`  source-of-truth: ${relative(ROOT, KEYCLOAK_DIR)}`);
console.log(`  mirror:          ${relative(ROOT, MIRROR_DIR)}`);
console.log(`  templates:       ${relative(ROOT, TEMPLATES_DIR)}`);
console.log("");

// 1. Source-of-truth files exist.
for (const f of REQUIRED_FILES) {
  const p = join(KEYCLOAK_DIR, f);
  check(`source-of-truth file present: ${f}`, existsSync(p));
}

// 2. Mirror files exist + byte-identical.
for (const f of REQUIRED_FILES) {
  const src = join(KEYCLOAK_DIR, f);
  const dst = join(MIRROR_DIR, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    check(`mirror file present: ${f}`, false, `expected ${relative(ROOT, dst)}`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  check(`mirror byte-identical: ${f}`, a === b, a === b ? "OK" : "drift detected");
}

// 3. Helm templates exist.
for (const t of REQUIRED_TEMPLATES) {
  const p = join(TEMPLATES_DIR, t);
  check(`helm template present: ${t}`, existsSync(p));
}

// 4. Mandatory clients + realm contract.
const clientFile = join(KEYCLOAK_DIR, "client-configs.yaml");
if (existsSync(clientFile)) {
  const txt = readFileSync(clientFile, "utf8");
  for (const client of REQUIRED_CLIENTS) {
    check(`mandatory client: ${client}`, new RegExp(`clientId:\\s*${client}\\b`).test(txt));
  }
  check("PKCE S256", /pkce\.code\.challenge\.method:\s*S256/.test(txt));
  check("direct grants disabled", /directAccessGrantsEnabled:\s*false/.test(txt));
  check("implicit grants disabled", /implicitFlowEnabled:\s*false/.test(txt));
  check("tenant_pseudo_id mapper", /claim\.name:\s*"tenant_pseudo_id"/.test(txt));
  check("actor_pseudo_id mapper", /claim\.name:\s*"actor_pseudo_id"/.test(txt));
}
const realmFile = join(KEYCLOAK_DIR, "realm-export.yaml");
if (existsSync(realmFile)) {
  const txt = readFileSync(realmFile, "utf8");
  check("realm = genealogy-shared", /realm:\s*genealogy-shared/.test(txt));
  check("MFA flow", /name:\s*mfa\b/.test(txt));
  check("step-up flow", /name:\s*step-up\b/.test(txt));
  check("email verification", /verifyEmail:\s*true/.test(txt));
  check("brute-force protection", /bruteForceProtected:\s*true/.test(txt));
}

// 5. Federation + key rotation.
const federationFile = join(KEYCLOAK_DIR, "federation.yaml");
if (existsSync(federationFile)) {
  const txt = readFileSync(federationFile, "utf8");
  check("OIDC federation providers", (txt.match(/providerId:\s*(?:oidc|google)/g) || []).length >= 5);
  check("SAML providers marked deprecated", (txt.match(/deprecatedPath:\s*true/g) || []).length >= 2);
  check("federated attributes denylist", /forbiddenFederatedAttributes:[\s\S]*?raw_dna/.test(txt));
}
const rotationFile = join(KEYCLOAK_DIR, "key-rotation.yaml");
if (existsSync(rotationFile)) {
  const txt = readFileSync(rotationFile, "utf8");
  check("RS256 realm signing key", /algorithm:\s*RS256/.test(txt));
  check("4096-bit key size", /keySize:\s*4096/.test(txt));
  check("90-day signing key rotation", /rotationDays:\s*90/.test(txt));
  check("30-day client-secret rotation", /clientSecretRotationDays:\s*30/.test(txt));
  check("JWKS endpoint", /\/protocol\/openid-connect\/certs/.test(txt));
}

// 6. Helm template render check (if helm is on PATH).
if (spawnSync("helm", ["version", "--short"], { stdio: "ignore" }).status === 0) {
  const res = spawnSync(
    "helm",
    [
      "template",
      "smoke",
      join(ROOT, "platform", "helm", "genealogy-platform"),
      "--set",
      "components.keycloak.enabled=true",
      "--show-only",
      "templates/components/keycloak/configmap.yaml",
    ],
    { encoding: "utf8" },
  );
  check(
    "helm template render — keycloak configmaps",
    res.status === 0 && /genea-keycloak-realm-strategy/.test(res.stdout || ""),
    res.status !== 0 ? (res.stderr || "").split("\n").slice(0, 3).join(" | ") : "rendered",
  );
} else {
  check("helm template render (skipped — helm not on PATH)", true, "structural only");
}

// 7. No literal secrets in any source-of-truth file.
let literalSecretCount = 0;
for (const f of REQUIRED_FILES) {
  const p = join(KEYCLOAK_DIR, f);
  if (!existsSync(p)) continue;
  const txt = readFileSync(p, "utf8");
  const re = /^\s*(password|apiKey|api_key|token|pepper|jwt|private_key|client_secret)\s*:\s*"?[A-Za-z0-9._/+=-]{8,}"?\s*$/m;
  if (re.test(txt)) {
    literalSecretCount++;
    check(`no literal secret in ${f}`, false);
  }
}
if (literalSecretCount === 0) {
  check("no literal secrets across all source-of-truth files", true);
}

// Summary.
const passed = checks.filter((c) => c.ok).length;
const failed = checks.length - passed;
console.log("");
console.log(`[smoke:keycloak] ${passed} passed, ${failed} failed (${checks.length} total)`);
process.exit(failed === 0 ? 0 : 1);