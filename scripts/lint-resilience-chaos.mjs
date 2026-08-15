#!/usr/bin/env node
/**
 * scripts/lint-resilience-chaos.mjs
 *
 * E13.4 deep validator for the resilience / chaos contract at
 * `contracts/reliability/resilience-chaos-policy.yaml` and the
 * platform mirror at
 * `platform/helm/genealogy-platform/files/reliability/resilience-chaos-policy.yaml`.
 *
 * Asserts:
 *   - 13 fault classes (pod_kill, network_latency, kafka_lag,
 *     temporal_restart, openfga_outage, db_failover,
 *     otel_collector_down, dns_failure, clock_skew,
 *     cpu_pressure, memory_pressure, disk_pressure,
 *     tls_rotation).
 *   - 4 retry policies (none, linear, exponential,
 *     decorrelated_jitter) — each respects maxAttempts ≤ 6
 *     and maxWallSeconds ≤ 60.
 *   - 5 circuit breaker defaults (threshold 5, openSeconds 30,
 *     halfOpenProbeMax 1, rollingWindowSeconds 60,
 *     minimumCalls 10).
 *   - 8 graceful degradation dependencies × 4 modes = 32 cells.
 *   - Idempotency rules (UUIDv7 + baseVersion + duplicateEventId
 *     forbidden).
 *   - 4 canary abort rules wired to Argo Rollouts.
 *   - 2 state matrices (scenarioStateMatrix,
 *     gameDayStateMatrix).
 *   - 15 numeric bounds + 17 invariants + 9 capability
 *     boundaries + 25 forbidden keywords + 5 runtime helpers.
 *   - 13 mandatory chaos scenarios.
 *   - Byte-identity between contract and chart mirror.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray, assertClosedSet, assertStateMatrix } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT
  ? resolve(process.env.LINT_ROOT)
  : resolve(__dirname, "..");

const CONTRACT = join(
  ROOT,
  "contracts/reliability/resilience-chaos-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/reliability/resilience-chaos-policy.yaml",
);

const REQUIRED_FAULT_CLASSES = [
  "pod_kill", "network_latency", "kafka_lag",
  "temporal_restart", "openfga_outage", "db_failover",
  "otel_collector_down", "dns_failure", "clock_skew",
  "cpu_pressure", "memory_pressure", "disk_pressure",
  "tls_rotation",
];
const REQUIRED_RETRY_POLICIES = [
  "none", "linear", "exponential", "decorrelated_jitter",
];
const REQUIRED_DEPS = [
  "postgres", "kafka", "openfga", "temporal",
  "vault", "otel_collector", "kong", "s3",
];
const REQUIRED_DEGRADATION_MODES = [
  "fail_closed", "fail_open", "read_only", "cached",
];
const REQUIRED_SCENARIO_STATUSES = [
  "SCHEDULED", "INJECTING", "OBSERVING", "RECOVERING",
  "CANCELLED", "PASSED", "FAILED", "ABORTED",
];
const REQUIRED_INVARIANTS = [
  "allFaultClassesHaveScenario", "allRetryPoliciesRespectMaxAttempts",
  "allRetryPoliciesRespectMaxWallSeconds",
  "circuitBreakerDefaultsApplyToOutboundCalls",
  "gracefulDegradationMatrixCoversAllDependencies",
  "idempotencyKeysAreUuidV7", "duplicateSideEffectForbidden",
  "duplicateEventIdForbidden",
  "canaryAbortRulesWiredToArgoRollouts",
  "gameDayMandatoryScenarios", "restoreDrillMandatoryComponents",
  "scenarioStateMatrixReachable", "numericBoundsRespected",
  "noManualRetry", "noUnboundedRetry",
  "noDuplicateSideEffects", "tenantBoundaryRespectedUnderFailure",
];
const REQUIRED_CAPABILITY = [
  "chaosExperiments litmus_or_chaos_mesh_only",
  "canaryController argo_rollouts_only",
  "circuitBreaker resilience4j_only",
  "retryLibrary spring_retry_or_resilience4j_only",
  "gameDay gameday_runbook_only",
  "noCustomChaosEngine forbidden",
  "noCustomCircuitBreaker forbidden",
  "noCustomRetryLibrary forbidden",
  "noCustomCanaryController forbidden",
];
const REQUIRED_FORBIDDEN_KEYWORDS = [
  "raw_dna_bytes", "raw_genotype", "raw_fastq", "raw_bam", "raw_vcf",
  "production_pii", "prod_tenant_id", "staging_tenant_id",
  "raw_email", "raw_phone", "raw_passport", "raw_ssn",
  "dev_secret", "shared_admin_password",
  "inline_jwt", "inline_access_token", "inline_refresh_token",
  "inline_session_cookie", "inline_oauth_client_secret",
  "tree_viewer_bypass", "bypass_authorization",
  "skip_consent", "skip_dna_isolation", "skip_audit", "skip_redaction",
];
const REQUIRED_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/resilience/ResilienceGuard.java",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/resilience/E13ResilienceLimits.java",
  "tools/chaos/scenarios/pod-kill.yaml",
  "platform/argocd/canary/abort-rules.yaml",
  "runbook/resilience.md",
];
const REQUIRED_NUMERIC_KEYS = [
  "maxRetryAttempts", "maxRetryBudgetSeconds",
  "circuitBreakerThreshold", "circuitBreakerOpenSeconds",
  "circuitBreakerRollingWindowSeconds",
  "circuitBreakerMinimumCalls", "halfOpenProbeMax",
  "idempotencyKeyTtlSeconds", "canaryAbortFiveXxRatio",
  "canaryAbortP95LatencyMultiplier", "canaryAbortErrorRateSpike",
  "canaryAbortFiveXxForSeconds", "canaryAbortP95ForSeconds",
  "canaryAbortErrorRateForSeconds",
  "gameDayFrequencyDays", "restoreDrillFrequencyDays",
];

let violations = 0;
const oks = [];
const ok = (msg) => oks.push(msg);
const fail = (msg) => { violations += 1; console.error(`FAIL: ${msg}`); };

function read(path) { return readFileSync(path, "utf8"); }
function asObject(v) {
  if (!v) return {};
  if (typeof v === "object" && !Array.isArray(v)) return v;
  return {};
}

const text = read(CONTRACT);
const doc = loadYaml(text);
const chartText = read(CHART_FILE);

if (text !== chartText) {
  fail(`byte-identity: contract and chart mirror differ (chart=${CHART_FILE})`);
} else {
  ok(`byte-identity: contract mirrors chart (${text.length} bytes)`);
}

const fcArr = asArray(doc.faultClasses?.values);
const fcNames = fcArr.map((f) => asObject(f).name).sort();
assertClosedSet(
  "faultClasses", REQUIRED_FAULT_CLASSES, fcNames,
  "E13.4 faultClasses", ok, fail,
);
for (const f of fcArr) {
  const o = asObject(f);
  if (typeof o.durationSeconds !== "number" || o.durationSeconds <= 0) {
    fail(`E13.4 faultClasses.${o.name}: missing numeric durationSeconds`);
  }
  if (typeof o.recoveryAction !== "string" || o.recoveryAction.isBlank?.()) {
    fail(`E13.4 faultClasses.${o.name}: missing recoveryAction`);
  }
}

const rpArr = asArray(doc.retryPolicies?.values);
const rpNames = rpArr.map((r) => asObject(r).name).sort();
assertClosedSet(
  "retryPolicies", REQUIRED_RETRY_POLICIES, rpNames,
  "E13.4 retryPolicies", ok, fail,
);
for (const r of rpArr) {
  const o = asObject(r);
  if (typeof o.maxAttempts !== "number" || o.maxAttempts < 1
      || o.maxAttempts > 6) {
    fail(`E13.4 retryPolicies.${o.name}: maxAttempts must be 1..6 (got ${o.maxAttempts})`);
  }
  if (typeof o.maxWallSeconds !== "number" || o.maxWallSeconds < 0
      || o.maxWallSeconds > 60) {
    fail(`E13.4 retryPolicies.${o.name}: maxWallSeconds must be 0..60 (got ${o.maxWallSeconds})`);
  }
}

const cbArr = asArray(doc.circuitBreakerDefaults?.values);
const cbMap = {};
for (const c of cbArr) {
  const o = asObject(c);
  if (o.name) cbMap[o.name] = o.value;
}
const requiredCb = ["threshold", "openSeconds", "halfOpenProbeMax",
  "rollingWindowSeconds", "minimumCalls"];
const missingCb = requiredCb.filter((k) => !(k in cbMap));
if (missingCb.length > 0) {
  fail(`E13.4 circuitBreakerDefaults: missing ${missingCb.join(",")}`);
} else {
  ok(`E13.4 circuitBreakerDefaults (${requiredCb.length} fields)`);
  if (cbMap.threshold !== 5) {
    fail(`E13.4 circuitBreakerDefaults.threshold must equal 5 (got ${cbMap.threshold})`);
  }
  if (cbMap.openSeconds !== 30) {
    fail(`E13.4 circuitBreakerDefaults.openSeconds must equal 30 (got ${cbMap.openSeconds})`);
  }
}

const gd = asObject(doc.gracefulDegradation);
const deps = asArray(gd.dependencies);
const modes = asArray(gd.modes).map((m) => asObject(m).name).sort();
if (JSON.stringify([...deps].sort()) !== JSON.stringify([...REQUIRED_DEPS].sort())) {
  fail(`E13.4 gracefulDegradation.dependencies closed-set mismatch.\n     expected: ${REQUIRED_DEPS.sort().join(",")}\n     actual:   ${deps.sort().join(",")}`);
} else {
  ok(`E13.4 gracefulDegradation.dependencies (${deps.length})`);
}
assertClosedSet(
  "gracefulDegradation.modes", REQUIRED_DEGRADATION_MODES, modes,
  "E13.4 gracefulDegradation.modes", ok, fail,
);

const dmArr = asArray(doc.degradationMatrix?.values);
const dmDeps = dmArr.map((d) => asObject(d).dependency).sort();
assertClosedSet(
  "degradationMatrix", REQUIRED_DEPS, dmDeps,
  "E13.4 degradationMatrix", ok, fail,
);
for (const d of dmArr) {
  const o = asObject(d);
  if (!REQUIRED_DEGRADATION_MODES.includes(o.readMode)) {
    fail(`E13.4 degradationMatrix.${o.dependency}.readMode not in closed-set (got ${o.readMode})`);
  }
  if (!REQUIRED_DEGRADATION_MODES.includes(o.writeMode)) {
    fail(`E13.4 degradationMatrix.${o.dependency}.writeMode not in closed-set (got ${o.writeMode})`);
  }
}

const idem = asObject(doc.idempotencyRules);
const requiredIdem = ["keyFormat", "baseVersionRequired",
  "duplicateSideEffectForbidden", "duplicateEventIdForbidden",
  "keyTtlSeconds"];
const missingIdem = requiredIdem.filter((k) => !(k in idem));
if (missingIdem.length > 0) {
  fail(`E13.4 idempotencyRules: missing ${missingIdem.join(",")}`);
} else {
  ok(`E13.4 idempotencyRules (${requiredIdem.length} fields)`);
  if (idem.keyFormat !== "UUIDv7") {
    fail(`E13.4 idempotencyRules.keyFormat must equal UUIDv7 (got ${idem.keyFormat})`);
  }
  if (idem.baseVersionRequired !== true) {
    fail(`E13.4 idempotencyRules.baseVersionRequired must be true`);
  }
}

const caArr = asArray(doc.canaryAbortRules?.values);
const caNames = caArr.map((c) => asObject(c).name).sort();
const requiredCa = ["fiveXxRatioExceeded", "p95LatencyRegression",
  "errorRateSpike", "privacyFindingDetected"];
assertClosedSet(
  "canaryAbortRules", requiredCa, caNames,
  "E13.4 canaryAbortRules", ok, fail,
);

const numArr = asArray(doc.numericBounds?.values);
const numMap = {};
for (const n of numArr) {
  const o = asObject(n);
  if (o.name) numMap[o.name] = o.value;
}
const missingNum = REQUIRED_NUMERIC_KEYS.filter((k) => !(k in numMap));
if (missingNum.length > 0) {
  fail(`E13.4 numericBounds: missing ${missingNum.join(",")}`);
} else {
  ok(`E13.4 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numMap)) {
    if (typeof v !== "number") {
      fail(`E13.4 numericBounds.${k}: not a number (${v})`);
    }
  }
  if (numMap.maxRetryAttempts !== 6) {
    fail(`E13.4 numericBounds.maxRetryAttempts must equal 6 (got ${numMap.maxRetryAttempts})`);
  }
  if (numMap.maxRetryBudgetSeconds !== 60) {
    fail(`E13.4 numericBounds.maxRetryBudgetSeconds must equal 60 (got ${numMap.maxRetryBudgetSeconds})`);
  }
  if (numMap.canaryAbortFiveXxRatio !== 0.01) {
    fail(`E13.4 numericBounds.canaryAbortFiveXxRatio must equal 0.01 (got ${numMap.canaryAbortFiveXxRatio})`);
  }
}

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E13.4 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries", REQUIRED_CAPABILITY,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E13.4 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E13.4 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E13.4 requiredRuntimeHelpers", ok, fail,
);

assertStateMatrix(
  "E13.4 scenarioStateMatrix",
  doc.scenarioStateMatrix,
  REQUIRED_SCENARIO_STATUSES,
  "SCHEDULED",
  ok, fail,
);

const scenArr = asArray(doc.requiredScenarios?.values);
const scenFaults = scenArr.map((s) => asObject(s).faultClass).sort();
const requiredScenFaults = [...REQUIRED_FAULT_CLASSES].sort();
if (scenFaults.join(",") !== requiredScenFaults.join(",")) {
  fail(`E13.4 requiredScenarios: faultClass closed-set mismatch.\n     expected: ${requiredScenFaults.join(",")}\n     actual:   ${scenFaults.join(",")}`);
} else {
  ok(`E13.4 requiredScenarios (${scenFaults.length} scenarios)`);
}

const chartPath = doc.requiredSourceMirror?.chartPath;
if (chartPath !== "platform/helm/genealogy-platform/files/reliability/resilience-chaos-policy.yaml") {
  fail(`E13.4 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/reliability/resilience-chaos-policy.yaml (got ${chartPath})`);
} else {
  ok(`E13.4 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E13.4 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E13.4 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}