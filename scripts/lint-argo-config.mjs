#!/usr/bin/env node
/**
 * scripts/lint-argo-config.mjs
 *
 * E2.9 deep validator for the Argo CD + Argo Rollouts
 * source-of-truth files in `platform/argo/`. Mirrors
 * `lint-flagsmith-config.mjs` style — uses the same `yaml`
 * parser and reports exit 0 on success, 1 on violation, 2
 * on configuration error.
 *
 * Asserts:
 *   - `platform/argo/argocd-server.yaml` declares the Argo CD
 *     image pin (v2.13.4) + Argo Rollouts image pin
 *     (v1.7.2), RBAC strict mode, anonymous access disabled,
 *     TLS 1.2 minimum, audit log enabled, Prometheus
 *     telemetry enabled, drift detection enabled (180s
 *     resync), no literal secret.
 *   - `platform/argo/projects.yaml` declares >= 3 AppProjects
 *     (production / non-prod / platform) with source-repo +
 *     destination-namespace allowlist; the four-eyes
 *     principle (developer / config-reviewer /
 *     release-approver / platform-admin) is enforced; no
 *     per-tenant AppProject.
 *   - `platform/argo/applications.yaml` declares >= 8
 *     canonical Applications, promotion pipeline
 *     (dev -> staging -> production) with release-approver
 *     + MFA gate on production.
 *   - `platform/argo/rollout-strategy.yaml` declares the
 *     canary strategy + the 4 AnalysisTemplates + the 4
 *     service-class templates + the forbidden-strategy
 *     contract (no setWeight: 100 without analysis, no
 *     tenant-scoped canary).
 *   - `platform/argo/sync-windows.yaml` declares >= 5 windows
 *     (dev + staging + production + weekend-blackout +
 *     change-freeze), no raw email / OIDC subject in audit
 *     fields.
 *   - The five files are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/argo/`.
 *   - No literal secret / token / password / API key /
 *     private key / ssh-key in any file.
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
const ARGO_DIR = join(ROOT, "platform", "argo");
const MIRROR_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files", "argo");

const REQUIRED_PROJECTS = [
  "genealogy-platform-prod",
  "genealogy-platform-nonprod",
  "genealogy-platform-platform",
];
const REQUIRED_ROLES = [
  "developer",
  "config-reviewer",
  "release-approver",
  "platform-admin",
];
const REQUIRED_ANALYSIS_TEMPLATES = [
  "error-rate",
  "latency-p95",
  "success-rate",
  "saturation",
];
const REQUIRED_SERVICE_CLASSES = [
  "stateless-api",
  "bff",
  "domain-event-consumer",
  "async-worker",
];
const REQUIRED_SYNC_WINDOWS = [
  "dev-auto-sync",
  "staging-window",
  "production-window",
  "weekend-blackout",
  "change-freeze-q4",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[argo] ${msg}`);
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
  for (const key of ["password", "apiKey", "token", "private_key", "sshKey"]) {
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
        `literal AWS credential '${awsKey}' in ${relative(ROOT, path)} — use IRSA / pod identity (E2.9 §1)`,
      );
    }
  }
  // Generic Argo CD secrets — placeholder patterns only.
  if (/ARGOCD_SERVER_ADMIN_PASSWORD:\s*"?[A-Za-z0-9]{8,}"?\s*$/m.test(text)) {
    fail(`literal ARGOCD_SERVER_ADMIN_PASSWORD in ${relative(ROOT, path)} — use ESO / Vault`);
  }
  if (/clientSecret:\s*"?[A-Za-z0-9]{16,}"?\s*$/m.test(text)) {
    fail(`literal OIDC clientSecret in ${relative(ROOT, path)} — use ESO / Vault`);
  }
}

// ---------------------------------------------------------------------------
// argocd-server.yaml — server posture
// ---------------------------------------------------------------------------
const serverFile = join(ARGO_DIR, "argocd-server.yaml");
const serverDoc = loadYaml(serverFile);
let controllerReplicaCount = 0;
let redisTag = "";
let argoCdTag = "";
let rolloutsTag = "";
if (serverDoc) {
  const raw = serverDoc?.data?.["argocd-server.yaml"];
  let data = null;
  if (!raw) {
    fail(`argocd-server.yaml must declare a ConfigMap with an 'argocd-server.yaml' entry under .data`);
  } else {
    try {
      data = YAML.parse(raw);
    } catch (e) {
      fail(`argocd-server.yaml content is not valid YAML — ${e.message}`);
    }
  }
  if (data) {
    // Image pin — ADR-E0.5-01 baseline `argoproj/argocd:v2.13.4`.
    const argoRepo = data?.argocd?.repo;
    if (!argoRepo || !/argoproj\/argocd/.test(String(argoRepo.name))) {
      fail(`argocd-server.yaml must declare argocd.repo.name with 'argoproj/argocd' (ADR-E0.5-01)`);
    }
    if (!argoRepo || !/v2\.13\.\d+/.test(String(argoRepo.tag))) {
      fail(`argocd-server.yaml must pin argocd.repo.tag to 'v2.13.x' (ADR-E0.5-01)`);
    } else {
      argoCdTag = String(argoRepo.tag);
    }
    // Rollouts controller image pin — ADR-E0.5-01 baseline
    // `argoproj/argocd-rollouts:v1.7.2`.
    const rollouts = data?.argocd?.rolloutsController;
    if (!rollouts || !/argoproj\/argocd-rollouts/.test(String(rollouts.name))) {
      fail(`argocd-server.yaml must declare argocd.rolloutsController.name with 'argoproj/argocd-rollouts' (ADR-E0.5-01)`);
    }
    if (!rollouts || !/v1\.7\.\d+/.test(String(rollouts.tag))) {
      fail(`argocd-server.yaml must pin argocd.rolloutsController.tag to 'v1.7.x' (ADR-E0.5-01)`);
    } else {
      rolloutsTag = String(rollouts.tag);
    }
    // Redis image pin.
    const redis = data?.argocd?.redis;
    if (!redis || !/7\.2-alpine/.test(String(redis.tag))) {
      fail(`argocd-server.yaml must pin argocd.redis.tag to '7.2-alpine'`);
    } else {
      redisTag = String(redis.tag);
    }
    // RBAC — strict mode (policyDefault role:readonly).
    const rbac = data?.rbac;
    if (!rbac || !/role:readonly/.test(String(rbac.policyDefault))) {
      fail(`argocd-server.yaml must set rbac.policyDefault to 'role:readonly' (E2.9 §1)`);
    }
    // Anonymous access — FORBIDDEN.
    const auth = data?.auth;
    if (!auth || auth.anonymousEnabled !== false) {
      fail(`argocd-server.yaml must set auth.anonymousEnabled: false (E2.9 §1)`);
    }
    // TLS minimum — TLS 1.2.
    if (!data?.tls || !/1\.2|2|3/.test(String(data.tls.minVersion))) {
      fail(`argocd-server.yaml must declare tls.minVersion '1.2' or higher`);
    }
    // Audit log — enabled.
    if (!data?.audit || data.audit.enabled !== true) {
      fail(`argocd-server.yaml must enable audit (E2.9 §1)`);
    }
    // Audit fields — must use actor_pseudo_id (no raw email).
    const auditFields = data?.audit?.fields;
    if (Array.isArray(auditFields)) {
      for (const field of auditFields) {
        if (String(field) === "email" || String(field) === "raw_email") {
          fail(`argocd-server.yaml audit.fields must NOT include 'email' / 'raw_email' — use actor_pseudo_id (E2.9 §1)`);
        }
      }
      if (!auditFields.map(String).includes("actor_pseudo_id")) {
        fail(`argocd-server.yaml audit.fields must include 'actor_pseudo_id' (E2.9 §1)`);
      }
    }
    // Prometheus telemetry — enabled.
    if (!data?.telemetry || data.telemetry.prometheusEnabled !== true) {
      fail(`argocd-server.yaml must enable telemetry.prometheusEnabled (alert source)`);
    }
    // Controller replica counts — at least 1, never 0.
    const controller = data?.argocd?.controller;
    if (controller) {
      const reps = [
        controller.replicas,
        controller.server?.replicas,
        controller.repoServer?.replicas,
        controller.applicationController?.replicas,
      ].filter((x) => typeof x === "number");
      controllerReplicaCount = reps.reduce((acc, x) => acc + x, 0);
      if (reps.some((x) => x < 1)) {
        fail(`argocd-server.yaml must declare controller replica counts >= 1 (E2.9 §1)`);
      }
    }
    // Drift detection — enabled, resyncSeconds 60..1800.
    const drift = data?.driftDetection;
    if (!drift || drift.enabled !== true) {
      fail(`argocd-server.yaml must enable driftDetection.enabled (E2.9 §1)`);
    }
    if (drift && (drift.resyncSeconds < 60 || drift.resyncSeconds > 1800)) {
      fail(`argocd-server.yaml driftDetection.resyncSeconds must be in [60, 1800]`);
    }
  }
  assertNoSecrets(readFileSync(serverFile, "utf8"), serverFile);
}

// ---------------------------------------------------------------------------
// projects.yaml — AppProject RBAC matrix
// ---------------------------------------------------------------------------
const projectsFile = join(ARGO_DIR, "projects.yaml");
const projectsDoc = loadYaml(projectsFile);
let projectsCount = 0;
let rolesSeen = new Set();
if (projectsDoc) {
  const raw = projectsDoc?.data?.["projects.yaml"];
  let data = null;
  if (!raw) {
    fail(`projects.yaml must declare a ConfigMap with a 'projects.yaml' entry under .data`);
  } else {
    try {
      data = YAML.parse(raw);
    } catch (e) {
      fail(`projects.yaml content is not valid YAML — ${e.message}`);
    }
  }
  if (data && (!data?.enforcement || data.enforcement.fourEyesPrinciple !== true)) {
    fail(`projects.yaml must set enforcement.fourEyesPrinciple: true (E2.9 §2)`);
  }
  if (data && (!data?.enforcement || data.enforcement.rbacStrict !== true)) {
    fail(`projects.yaml must set enforcement.rbacStrict: true (E2.9 §2)`);
  }
  // MFA required for production promotion + audit write.
  const mfaRequired = data?.enforcement?.mfaRequired;
  if (data && (!Array.isArray(mfaRequired) || !mfaRequired.includes("production-promotion"))) {
    fail(`projects.yaml must require MFA for 'production-promotion' (E2.9 §2)`);
  }
  const projects = data && Array.isArray(data?.projects) ? data.projects : [];
  projectsCount = projects.length;
  if (projectsCount < 3) {
    fail(`projects.yaml must declare >= 3 AppProjects (E2.9 §2) — found ${projectsCount}`);
  }
  const declaredProjects = new Set(projects.map((p) => p.name));
  for (const required of REQUIRED_PROJECTS) {
    if (!declaredProjects.has(required)) {
      fail(`projects.yaml missing required AppProject '${required}' (E2.9 §2)`);
    }
  }
  // Per-project: roles + destination namespace allowlist.
  if (data) for (const project of projects) {
    const roles = Array.isArray(project?.roles) ? project.roles : [];
    for (const role of roles) {
      if (role?.name) rolesSeen.add(String(role.name));
    }
    const destinations = Array.isArray(project?.destinations) ? project.destinations : [];
    if (destinations.length === 0) {
      fail(`projects.yaml AppProject '${project.name}' must declare destinations (E2.9 §2)`);
    }
    // No tenant-shaped namespace — privacy gate.
    for (const dest of destinations) {
      if (dest?.namespace && /tenant-/i.test(String(dest.namespace))) {
        fail(`projects.yaml AppProject '${project.name}' destination '${dest.namespace}' looks tenant-shaped — per-tenant AppProject is FORBIDDEN (privacy-and-legal-gate.md §12)`);
      }
    }
    // release-approver role must requireMfa.
    const releaseApprover = roles.find((r) => r?.name === "release-approver");
    if (releaseApprover && releaseApprover.requiresMfa !== true) {
      fail(`projects.yaml AppProject '${project.name}' role 'release-approver' must requireMfa: true (E2.9 §2 four-eyes)`);
    }
  }
  for (const required of REQUIRED_ROLES) {
    if (!rolesSeen.has(required)) {
      fail(`projects.yaml missing required role '${required}' across all AppProjects (E2.9 §2 four-eyes)`);
    }
  }
  assertNoSecrets(readFileSync(projectsFile, "utf8"), projectsFile);
}

// ---------------------------------------------------------------------------
// applications.yaml — canonical Application set
// ---------------------------------------------------------------------------
const appsFile = join(ARGO_DIR, "applications.yaml");
const appsDoc = loadYaml(appsFile);
let appCount = 0;
if (appsDoc) {
  const raw = appsDoc?.data?.["applications.yaml"];
  let data = null;
  if (!raw) {
    fail(`applications.yaml must declare a ConfigMap with an 'applications.yaml' entry under .data`);
  } else {
    try {
      data = YAML.parse(raw);
    } catch (e) {
      fail(`applications.yaml content is not valid YAML — ${e.message}`);
    }
  }
  const apps = data && Array.isArray(data?.applications) ? data.applications : [];
  appCount = apps.length;
  if (appCount < 8) {
    fail(`applications.yaml must declare >= 8 Applications (E2.9 §3) — found ${appCount}`);
  }
  // Every Application must declare project + destination + namespace.
  if (data) for (const app of apps) {
    if (!app?.project) {
      fail(`applications.yaml Application '${app?.name}' missing project (E2.9 §3)`);
    }
    if (!app?.destination?.namespace) {
      fail(`applications.yaml Application '${app?.name}' missing destination.namespace (E2.9 §3)`);
    }
    if (!app?.namespace) {
      fail(`applications.yaml Application '${app?.name}' missing namespace (E2.9 §3)`);
    }
  }
  // Promotion pipeline — production must require release-approver + MFA.
  const pipeline = data && Array.isArray(data?.promotionPipeline) ? data.promotionPipeline : [];
  const prod = pipeline.find((p) => p?.name === "production");
  if (data && !prod) {
    fail(`applications.yaml must declare a production promotion step (E2.9 §3)`);
  } else {
    if (prod.autoPromote !== false) {
      fail(`applications.yaml production promotion must set autoPromote: false (E2.9 §2 four-eyes)`);
    }
    if (prod.requiresApproval !== true) {
      fail(`applications.yaml production promotion must require approval (E2.9 §2 four-eyes)`);
    }
    if (prod.approverRole !== "release-approver") {
      fail(`applications.yaml production promotion must use approverRole 'release-approver' (E2.9 §2 four-eyes)`);
    }
    if (prod.requiresMfa !== true) {
      fail(`applications.yaml production promotion must requireMfa: true (E2.9 §2 four-eyes)`);
    }
  }
  // Promotion gates — health checks required.
  const gates = data?.promotionGate;
  if (!gates?.postSyncChecks || !Array.isArray(gates.postSyncChecks)) {
    fail(`applications.yaml must declare promotionGate.postSyncChecks (E2.9 §3)`);
  }
  assertNoSecrets(readFileSync(appsFile, "utf8"), appsFile);
}

// ---------------------------------------------------------------------------
// rollout-strategy.yaml — canary strategy + AnalysisTemplates
// ---------------------------------------------------------------------------
const rolloutFile = join(ARGO_DIR, "rollout-strategy.yaml");
const rolloutDoc = loadYaml(rolloutFile);
let analysisTemplateCount = 0;
let serviceClassCount = 0;
if (rolloutDoc) {
  const raw = rolloutDoc?.data?.["rollout-strategy.yaml"];
  let data = null;
  if (!raw) {
    fail(`rollout-strategy.yaml must declare a ConfigMap with a 'rollout-strategy.yaml' entry under .data`);
  } else {
    try {
      data = YAML.parse(raw);
    } catch (e) {
      fail(`rollout-strategy.yaml content is not valid YAML — ${e.message}`);
    }
  }
  if (data) {
    // Default canary strategy must declare setWeight steps.
    const canary = data?.defaultStrategy?.canary;
    if (!canary) {
      fail(`rollout-strategy.yaml must declare defaultStrategy.canary (E2.9 §4)`);
    } else if (!Array.isArray(canary.steps) || canary.steps.length === 0) {
      fail(`rollout-strategy.yaml defaultStrategy.canary must declare steps (E2.9 §4)`);
    }
    // Traffic routing — must be istio.
    const trafficRouting = canary?.trafficRouting;
    if (!trafficRouting || !trafficRouting.istio) {
      fail(`rollout-strategy.yaml defaultStrategy.canary.trafficRouting.istio is required (E2.9 §4)`);
    }
    // AnalysisTemplates — the 4 required.
    const analysisTemplates = Array.isArray(data?.analysisTemplates) ? data.analysisTemplates : [];
    analysisTemplateCount = analysisTemplates.length;
    const declaredAnalysis = new Set(analysisTemplates.map((a) => a.name));
    for (const required of REQUIRED_ANALYSIS_TEMPLATES) {
      if (!declaredAnalysis.has(required)) {
        fail(`rollout-strategy.yaml missing required AnalysisTemplate '${required}' (E2.9 §4)`);
      }
    }
    // Every AnalysisTemplate must wire Prometheus + success/failure condition.
    for (const at of analysisTemplates) {
      const metrics = Array.isArray(at?.metrics) ? at.metrics : [];
      if (metrics.length === 0) {
        fail(`rollout-strategy.yaml AnalysisTemplate '${at.name}' must declare metrics (E2.9 §4)`);
      }
      for (const metric of metrics) {
        if (!metric?.successCondition || !metric?.failureCondition) {
          fail(`rollout-strategy.yaml AnalysisTemplate '${at.name}' metric '${metric.name}' must declare success/failure conditions (E2.9 §4)`);
        }
        if (!metric?.provider?.prometheus?.address) {
          fail(`rollout-strategy.yaml AnalysisTemplate '${at.name}' metric '${metric.name}' must wire Prometheus (E2.9 §4)`);
        }
      }
    }
    // Service classes — the 4 required.
    const serviceClasses = Array.isArray(data?.serviceClasses) ? data.serviceClasses : [];
    serviceClassCount = serviceClasses.length;
    const declaredClasses = new Set(serviceClasses.map((s) => s.name));
    for (const required of REQUIRED_SERVICE_CLASSES) {
      if (!declaredClasses.has(required)) {
        fail(`rollout-strategy.yaml missing required service class '${required}' (E2.9 §4)`);
      }
    }
    // Forbidden strategies — at least the 4 documented rules.
    const forbidden = Array.isArray(data?.forbiddenStrategies) ? data.forbiddenStrategies : [];
    if (forbidden.length < 4) {
      fail(`rollout-strategy.yaml must declare >= 4 forbidden strategies (E2.9 §4)`);
    }
    if (!forbidden.some((f) => /setWeight/.test(String(f?.pattern)))) {
      fail(`rollout-strategy.yaml forbiddenStrategies must include a setWeight: 100 rule (E2.9 §4)`);
    }
    if (!forbidden.some((f) => /tenant/i.test(String(f?.pattern)))) {
      fail(`rollout-strategy.yaml forbiddenStrategies must include a per-tenant canary rule (E2.9 §4)`);
    }
    // Rollback contract — automatic + abort.
    const rollback = data?.rollbackContract;
    if (!rollback?.onFailure || !rollback.automaticRollback) {
      fail(`rollout-strategy.yaml must declare rollbackContract.automaticRollback (E2.9 §4)`);
    }
  }
  assertNoSecrets(readFileSync(rolloutFile, "utf8"), rolloutFile);
}

// ---------------------------------------------------------------------------
// sync-windows.yaml — allow / deny windows
// ---------------------------------------------------------------------------
const windowsFile = join(ARGO_DIR, "sync-windows.yaml");
const windowsDoc = loadYaml(windowsFile);
let windowCount = 0;
if (windowsDoc) {
  const raw = windowsDoc?.data?.["sync-windows.yaml"];
  let data = null;
  if (!raw) {
    fail(`sync-windows.yaml must declare a ConfigMap with a 'sync-windows.yaml' entry under .data`);
  } else {
    try {
      data = YAML.parse(raw);
    } catch (e) {
      fail(`sync-windows.yaml content is not valid YAML — ${e.message}`);
    }
  }
  const windows = data && Array.isArray(data?.windows) ? data.windows : [];
  windowCount = windows.length;
  if (data && windowCount < 5) {
    fail(`sync-windows.yaml must declare >= 5 windows (E2.9 §5) — found ${windowCount}`);
  }
  if (data) {
    const declaredIds = new Set(windows.map((w) => w.id));
    for (const required of REQUIRED_SYNC_WINDOWS) {
      if (!declaredIds.has(required)) {
        fail(`sync-windows.yaml missing required window '${required}' (E2.9 §5)`);
      }
    }
    // Production-window must require MFA.
    const productionWindow = windows.find((w) => w.id === "production-window");
    if (productionWindow && productionWindow.requiresMfa !== true) {
      fail(`sync-windows.yaml 'production-window' must requireMfa: true (E2.9 §5)`);
    }
    // No raw email / OIDC subject in audit fields.
    const auditFields = data?.defaults?.auditFields;
    if (Array.isArray(auditFields)) {
      for (const field of auditFields) {
        if (String(field) === "email" || String(field) === "oidcSubject") {
          fail(`sync-windows.yaml defaults.auditFields must NOT include 'email' / 'oidcSubject' — use actor_pseudo_id (E2.9 §5)`);
        }
      }
      if (!auditFields.map(String).includes("actor_pseudo_id")) {
        fail(`sync-windows.yaml defaults.auditFields must include 'actor_pseudo_id' (E2.9 §5)`);
      }
    }
    // Reconciliation defaults — resyncSeconds 60..1800.
    const reconciliation = data?.reconciliation;
    if (reconciliation && (reconciliation.resyncSeconds < 60 || reconciliation.resyncSeconds > 1800)) {
      fail(`sync-windows.yaml reconciliation.resyncSeconds must be in [60, 1800]`);
    }
  }
  assertNoSecrets(readFileSync(windowsFile, "utf8"), windowsFile);
}

// ---------------------------------------------------------------------------
// Mirror files — byte-identity check
// ---------------------------------------------------------------------------
const MIRROR_FILES = [
  "argocd-server.yaml",
  "projects.yaml",
  "applications.yaml",
  "rollout-strategy.yaml",
  "sync-windows.yaml",
];
for (const f of MIRROR_FILES) {
  const src = join(ARGO_DIR, f);
  const dst = join(MIRROR_DIR, f);
  if (!existsSync(src)) {
    fail(`source-of-truth file missing — ${relative(ROOT, src)}`);
    continue;
  }
  if (!existsSync(dst)) {
    fail(`mirror file missing — ${relative(ROOT, dst)}`);
    continue;
  }
  const a = readFileSync(src);
  const b = readFileSync(dst);
  if (Buffer.compare(a, b) !== 0) {
    fail(`mirror drift detected — ${relative(ROOT, dst)} differs from ${relative(ROOT, src)}`);
  }
}

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------
if (violations === 0) {
  console.log(
    `[argo] clean — controller-replicas=${controllerReplicaCount}, projects=${projectsCount}, apps=${appCount}, analysis-templates=${analysisTemplateCount}, service-classes=${serviceClassCount}, windows=${windowCount}, argo-cd=${argoCdTag}, rollouts=${rolloutsTag}, redis=${redisTag}`,
  );
  process.exit(0);
}
process.exit(1);
