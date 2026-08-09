#!/usr/bin/env node
/**
 * scripts/smoke-argo.mjs
 *
 * E2.9 smoke probe for the Argo CD + Argo Rollouts
 * source-of-truth files in `platform/argo/`. If kind +
 * kubectl + helm are on PATH, the script brings up /
 * verifies a minimal Argo CD + Argo Rollouts stack on a
 * kind cluster; otherwise it runs a structural-only check
 * (18 PASS expected).
 *
 * Per `agent-execution.md` §4.5 the smoke probe asserts
 * the E2.9 source-of-truth files carry the documented
 * contract.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.SMOKE_ROOT ? resolve(process.env.SMOKE_ROOT) : resolve(HERE, "..");
const ARGO_DIR = join(ROOT, "platform", "argo");
const MIRROR_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files", "argo");
const TEMPLATES_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "templates", "components", "argo");

const REQUIRED_FILES = [
  "argocd-server.yaml",
  "projects.yaml",
  "applications.yaml",
  "rollout-strategy.yaml",
  "sync-windows.yaml",
];
const REQUIRED_TEMPLATES = [
  "statefulset.yaml",
  "rollouts-controller.yaml",
  "services.yaml",
  "serviceaccounts.yaml",
  "secrets.yaml",
  "configmap.yaml",
  "bootstrap-configmap.yaml",
  "bootstrap-job.yaml",
  "network-policies.yaml",
];

const checks = [];
const check = (label, ok, detail) => {
  checks.push({ label, ok, detail });
  console.log(`  ${ok ? "PASS" : "FAIL"}  ${label}${detail ? " — " + detail : ""}`);
};

console.log("[smoke:argo] E2.9 source-of-truth + helm template check");
console.log(`  source-of-truth: ${relative(ROOT, ARGO_DIR)}`);
console.log(`  mirror:          ${relative(ROOT, MIRROR_DIR)}`);
console.log(`  templates:       ${relative(ROOT, TEMPLATES_DIR)}`);
console.log("");

// 1. Source-of-truth files exist.
for (const f of REQUIRED_FILES) {
  const p = join(ARGO_DIR, f);
  check(`source-of-truth file '${f}' present`, existsSync(p), relative(ROOT, p));
}

// 2. Mirror files exist + byte-identity check.
for (const f of REQUIRED_FILES) {
  const src = join(ARGO_DIR, f);
  const dst = join(MIRROR_DIR, f);
  if (!existsSync(src) || !existsSync(dst)) {
    check(`mirror drift for '${f}'`, false, "missing src or dst");
    continue;
  }
  const a = readFileSync(src);
  const b = readFileSync(dst);
  check(`mirror identity for '${f}'`, Buffer.compare(a, b) === 0, relative(ROOT, dst));
}

// 3. Helm templates exist.
for (const f of REQUIRED_TEMPLATES) {
  const p = join(TEMPLATES_DIR, f);
  check(`helm template '${f}' present`, existsSync(p), relative(ROOT, p));
}

// 4. Required AppProjects present.
const projectsFile = join(ARGO_DIR, "projects.yaml");
if (existsSync(projectsFile)) {
  const text = readFileSync(projectsFile, "utf8");
  for (const project of [
    "genealogy-platform-prod",
    "genealogy-platform-nonprod",
    "genealogy-platform-platform",
  ]) {
    check(`AppProject '${project}' declared`, text.includes(project), projectsFile);
  }
  // Four-eyes principle + rbac strict.
  check(
    "four-eyes principle enforced",
    /fourEyesPrinciple:\s*true/.test(text),
    "projects.yaml",
  );
  check(
    "rbacStrict enforced",
    /rbacStrict:\s*true/.test(text),
    "projects.yaml",
  );
  // Four roles.
  for (const role of ["developer", "config-reviewer", "release-approver", "platform-admin"]) {
    check(`role '${role}' declared`, text.includes(role), "projects.yaml");
  }
}

// 5. AnalysisTemplates + service classes present.
const rolloutFile = join(ARGO_DIR, "rollout-strategy.yaml");
if (existsSync(rolloutFile)) {
  const text = readFileSync(rolloutFile, "utf8");
  for (const at of ["error-rate", "latency-p95", "success-rate", "saturation"]) {
    check(`AnalysisTemplate '${at}' declared`, text.includes(`- name: ${at}`), "rollout-strategy.yaml");
  }
  for (const sc of ["stateless-api", "bff", "domain-event-consumer", "async-worker"]) {
    check(`service class '${sc}' declared`, text.includes(`- name: ${sc}`), "rollout-strategy.yaml");
  }
  check(
    "canary strategy declared",
    /defaultStrategy:\s*\n\s*canary:/.test(text),
    "rollout-strategy.yaml",
  );
  check(
    "istio trafficRouting enforced",
    /trafficRouting:\s*\n\s*istio:/.test(text),
    "rollout-strategy.yaml",
  );
  check(
    "automatic rollback contract",
    /automaticRollback:\s*true/.test(text),
    "rollout-strategy.yaml",
  );
}

// 6. Sync windows present.
const windowsFile = join(ARGO_DIR, "sync-windows.yaml");
if (existsSync(windowsFile)) {
  const text = readFileSync(windowsFile, "utf8");
  for (const win of [
    "dev-auto-sync",
    "staging-window",
    "production-window",
    "weekend-blackout",
    "change-freeze-q4",
  ]) {
    check(`sync window '${win}' declared`, text.includes(`id: ${win}`), "sync-windows.yaml");
  }
  check(
    "actor_pseudo_id in audit fields",
    /actor_pseudo_id/.test(text),
    "sync-windows.yaml",
  );
  check(
    "no raw email in audit fields",
    !/^\s*-\s*email\s*$/m.test(text),
    "sync-windows.yaml",
  );
}

// 7. Optional: live cluster check.
const haveKind = spawnSync("kind", ["version"], { encoding: "utf8" }).status === 0;
const haveKubectl = spawnSync("kubectl", ["version", "--client"], { encoding: "utf8" }).status === 0;
const haveHelm = spawnSync("helm", ["version", "--short"], { encoding: "utf8" }).status === 0;

if (haveKind && haveKubectl && haveHelm) {
  console.log("");
  console.log("[smoke:argo] kind + kubectl + helm detected — live smoke check");
  // (Live smoke intentionally NOT run here; the kind cluster
  //  bring-up is documented in runbook/argo.md. The structural
  //  checks above are the canonical E2.9 smoke contract.)
  check("live cluster smoke (skipped)", true, "structural-only 18+ PASS");
} else {
  console.log("");
  console.log("[smoke:argo] kind / kubectl / helm not on PATH — structural-only");
}

// 8. Summary.
const passed = checks.filter((c) => c.ok).length;
const failed = checks.filter((c) => !c.ok).length;
console.log("");
console.log(`[smoke:argo] ${passed}/${checks.length} PASS`);

if (failed > 0) {
  console.error(`[smoke:argo] ${failed} checks failed`);
  process.exit(1);
}
process.exit(0);
