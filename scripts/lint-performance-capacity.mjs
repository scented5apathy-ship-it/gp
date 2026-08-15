#!/usr/bin/env node
/**
 * scripts/lint-performance-capacity.mjs
 *
 * E13.3 deep validator for the performance / capacity
 * contract at `contracts/reliability/performance-capacity-policy.yaml`
 * and the platform mirror at
 * `platform/helm/genealogy-platform/files/reliability/
 *  performance-capacity-policy.yaml`.
 *
 * Asserts:
 *   - 6 workload classes (browse_tree / search / detail_read /
 *     write_proposal / media_upload / async_job) each declare
 *     a benchmark profile (mix, maxRps, targetP95Ms, hpaMetric,
 *     hpaTarget).
 *   - 9 benchmark harness capabilities (k6, gatling,
 *     testcontainers_seed, 3 dataset sizes, deterministic_seed,
 *     core_web_vitals_budget, p50_p95_p99_histogram).
 *   - 7 synthetic dataset locales (vi-VN, en-US, fr-FR, ar-SA,
 *     he-IL, ja-JP, zh-CN).
 *   - 6 HPA metrics (cpu / memory / rps / lag / queue_depth /
 *     custom_metric) + 4 HPA anti-patterns.
 *   - Connection pool ceilings (postgres 75, kafka producer
 *     200, kafka consumer 400, grpc 500, redis 600, temporal
 *     100).
 *   - 4 postgres thresholds, kafka sizing, temporal sizing.
 *   - 6 Core Web Vitals budget entries (lcp, cls, inp, ttfb,
 *     tti, tbt).
 *   - 4 regression rules + capacity envelope per environment.
 *   - 2 state matrices (performanceRunStateMatrix,
 *     hpaStateMatrix).
 *   - 16 numeric bounds + 18 invariants + 8 capability
 *     boundaries + 25 forbidden keywords + 5 runtime helpers.
 *   - 5 mandatory benchmark scenarios with p95Ms + errorRate.
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
  "contracts/reliability/performance-capacity-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/reliability/performance-capacity-policy.yaml",
);

const REQUIRED_WORKLOAD_CLASSES = [
  "browse_tree", "search", "detail_read",
  "write_proposal", "media_upload", "async_job",
];
const REQUIRED_HARNESS = [
  "k6", "gatling", "testcontainers_seed",
  "synthetic_dataset_10k", "synthetic_dataset_100k",
  "synthetic_dataset_1m", "deterministic_seed",
  "core_web_vitals_budget", "p50_p95_p99_histogram",
];
const REQUIRED_LOCALES = [
  "vi-VN", "en-US", "fr-FR", "ar-SA",
  "he-IL", "ja-JP", "zh-CN",
];
const REQUIRED_HPA_METRICS = [
  "cpu", "memory", "rps", "lag", "queue_depth", "custom_metric",
];
const REQUIRED_HPA_ANTI = [
  "manualScalingProduction", "scaleOnRawIdentity",
  "scaleOnTenantId", "bypass_hpa",
];
const REQUIRED_CWV = ["lcp", "cls", "inp", "ttfb", "tti", "tbt"];
const REQUIRED_RUN_STATUSES = [
  "QUEUED", "RUNNING", "COLLECTING", "CANCELLED",
  "COMPLETED", "FAILED", "ABORTED",
];
const REQUIRED_HPA_STATUSES = [
  "STEADY", "SCALING_UP", "SCALING_DOWN",
  "SATURATED", "DISABLED",
];
const REQUIRED_INVARIANTS = [
  "allWorkloadClassesHaveBenchmark", "k6IsTheLoadGenerator",
  "syntheticDatasetsCarryLicenseMarker",
  "noRealPiiInBenchmarkDataset", "noRealDnaInBenchmarkDataset",
  "hpaUsesClosedSetMetrics", "manualScalingProductionForbidden",
  "connectionPoolsRespectCeilings", "postgresThresholdsTracked",
  "kafkaSizingRuleEnforced", "temporalSizingRuleEnforced",
  "coreWebVitalsBudgetEnforced", "regressionBlocksCanary",
  "capacityHeadroomDocumented", "deterministicSeedReproducible",
  "performanceRunMatrixReachable", "hpaStateMatrixReachable",
  "numericBoundsRespected",
];
const REQUIRED_CAPABILITY = [
  "loadGenerator k6_or_gatling_only",
  "hpa kubernetes_hpa_only",
  "syntheticData testcontainers_seed_only",
  "coreWebVitals web_vitals_api_only",
  "noCustomLoadGenerator forbidden",
  "noCustomHpa forbidden",
  "noCustomConnectionPoolManager forbidden",
  "noCustomCwvCollector forbidden",
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
  "tools/k6/bench-suite.mjs",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/capacity/CapacityGuard.java",
  "apps/web/bench/perf-budget.test.ts",
  "platform/helm/genealogy-platform/files/capacity/hpa-policy.yaml",
  "docs/capacity-report.md",
];
const REQUIRED_NUMERIC_KEYS = [
  "maxP95RegressionPercent", "maxErrorRateRegressionPercent",
  "maxThroughputRegressionPercent", "maxBundleSizeRegressionPercent",
  "minTenantHeadroomMultiplier", "saasBurstMultiplier",
  "onPremiseBurstMultiplier", "kafkaMaxPartitionsPerTopic",
  "kafkaPartitionPerRps", "kafkaMinInSyncReplicas",
  "temporalWorkerPerWorkflow", "temporalMaxConcurrentPerWorker",
  "postgresPoolCeilingRatio", "coreWebVitalLcpMs",
  "coreWebVitalCls", "coreWebVitalInpMs",
];
const REQUIRED_ENVELOPES = ["dev", "saas", "on_premise"];
const REQUIRED_POOL_FIELDS = [
  "postgresMax", "kafkaProducerMax", "kafkaConsumerMax",
  "grpcClientMax", "redisClientMax", "temporalWorkerMax",
];

let violations = 0;
const oks = [];
const ok = (msg) => oks.push(msg);
const fail = (msg) => { violations += 1; console.error(`FAIL: ${msg}`); };

function read(path) {
  return readFileSync(path, "utf8");
}
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

const wcArr = asArray(doc.workloadClasses?.values);
const wcNames = wcArr.map((w) => asObject(w).name).sort();
assertClosedSet(
  "workloadClasses", REQUIRED_WORKLOAD_CLASSES, wcNames,
  "E13.3 workloadClasses", ok, fail,
);
for (const w of wcArr) {
  const o = asObject(w);
  if (typeof o.maxRps !== "number" || o.maxRps <= 0) {
    fail(`E13.3 workloadClasses.${o.name}: missing maxRps`);
  }
  if (typeof o.targetP95Ms !== "number" || o.targetP95Ms <= 0) {
    fail(`E13.3 workloadClasses.${o.name}: missing targetP95Ms`);
  }
  if (typeof o.hpaMetric !== "string"
      || !REQUIRED_HPA_METRICS.includes(o.hpaMetric)) {
    fail(`E13.3 workloadClasses.${o.name}: hpaMetric must be in HPA closed-set (got ${o.hpaMetric})`);
  }
}

assertClosedSet(
  "benchmarkHarness", REQUIRED_HARNESS,
  asArray(doc.benchmarkHarness?.values),
  "E13.3 benchmarkHarness", ok, fail,
);

assertClosedSet(
  "syntheticDatasetLocales", REQUIRED_LOCALES,
  asArray(doc.syntheticDatasetLocales?.values),
  "E13.3 syntheticDatasetLocales", ok, fail,
);

assertClosedSet(
  "hpaMetrics", REQUIRED_HPA_METRICS,
  asArray(doc.hpaMetrics?.values),
  "E13.3 hpaMetrics", ok, fail,
);

assertClosedSet(
  "hpaAntiPatterns", REQUIRED_HPA_ANTI,
  asArray(doc.hpaAntiPatterns?.values),
  "E13.3 hpaAntiPatterns", ok, fail,
);

const pool = asObject(doc.connectionPool);
const missingPool = REQUIRED_POOL_FIELDS.filter((k) => !(k in pool));
if (missingPool.length > 0) {
  fail(`E13.3 connectionPool: missing ${missingPool.join(",")}`);
} else {
  ok(`E13.3 connectionPool (${Object.keys(pool).length} fields)`);
  if (pool.postgresMax !== 75) {
    fail(`E13.3 connectionPool.postgresMax must equal 75 (got ${pool.postgresMax})`);
  }
}

const db = asObject(doc.databaseThresholds);
const requiredDb = ["pgMaxConnectionsRatio", "pgReplicationLagSeconds",
  "pgLongestQuerySeconds", "pgDeadTuplesRatio"];
const missingDb = requiredDb.filter((k) => !(k in db));
if (missingDb.length > 0) {
  fail(`E13.3 databaseThresholds: missing ${missingDb.join(",")}`);
} else {
  ok(`E13.3 databaseThresholds (${Object.keys(db).length} fields)`);
}

const kafka = asObject(doc.kafkaSizing);
const requiredKafka = ["partitionPerRps", "maxPartitionsPerTopic",
  "minInSyncReplicas", "consumerGroupLagSecondsCritical",
  "consumerGroupLagSecondsAsync"];
const missingKafka = requiredKafka.filter((k) => !(k in kafka));
if (missingKafka.length > 0) {
  fail(`E13.3 kafkaSizing: missing ${missingKafka.join(",")}`);
} else {
  ok(`E13.3 kafkaSizing (${Object.keys(kafka).length} fields)`);
  if (kafka.maxPartitionsPerTopic !== 256) {
    fail(`E13.3 kafkaSizing.maxPartitionsPerTopic must equal 256 (got ${kafka.maxPartitionsPerTopic})`);
  }
}

const temporal = asObject(doc.temporalSizing);
const requiredTemporal = ["workersPerConcurrentWorkflow",
  "maxConcurrentWorkflowsPerWorker", "activityHeartbeatSeconds",
  "workflowTaskTimeoutSeconds"];
const missingTemporal = requiredTemporal.filter((k) => !(k in temporal));
if (missingTemporal.length > 0) {
  fail(`E13.3 temporalSizing: missing ${missingTemporal.join(",")}`);
} else {
  ok(`E13.3 temporalSizing (${Object.keys(temporal).length} fields)`);
}

const cwvArr = asArray(doc.coreWebVitalsBudget?.values);
const cwvNames = cwvArr.map((c) => asObject(c).name).sort();
assertClosedSet(
  "coreWebVitalsBudget", REQUIRED_CWV, cwvNames,
  "E13.3 coreWebVitalsBudget", ok, fail,
);

const envArr = asArray(doc.capacityEnvelope?.values);
const envs = envArr.map((e) => asObject(e).environment).sort();
assertClosedSet(
  "capacityEnvelope", REQUIRED_ENVELOPES, envs,
  "E13.3 capacityEnvelope", ok, fail,
);

const numArr = asArray(doc.numericBounds?.values);
const numMap = {};
for (const n of numArr) {
  const o = asObject(n);
  if (o.name) numMap[o.name] = o.value;
}
const missingNum = REQUIRED_NUMERIC_KEYS.filter((k) => !(k in numMap));
if (missingNum.length > 0) {
  fail(`E13.3 numericBounds: missing ${missingNum.join(",")}`);
} else {
  ok(`E13.3 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numMap)) {
    if (typeof v !== "number") {
      fail(`E13.3 numericBounds.${k}: not a number (${v})`);
    }
  }
  if (numMap.maxP95RegressionPercent !== 10) {
    fail(`E13.3 numericBounds.maxP95RegressionPercent must equal 10 (got ${numMap.maxP95RegressionPercent})`);
  }
  if (numMap.saasBurstMultiplier !== 3) {
    fail(`E13.3 numericBounds.saasBurstMultiplier must equal 3 (got ${numMap.saasBurstMultiplier})`);
  }
}

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E13.3 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries", REQUIRED_CAPABILITY,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E13.3 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E13.3 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E13.3 requiredRuntimeHelpers", ok, fail,
);

for (const helper of REQUIRED_HELPERS) {
  if (helper.startsWith("tools/") || helper.startsWith("apps/")
      || helper.startsWith("docs/")
      || helper.startsWith("platform/helm/")) {
    const abs = join(ROOT, helper);
    if (!existsSync(abs)) {
      fail(`E13.3 runtimeHelpers: missing on disk: ${helper}`);
    } else {
      ok(`E13.3 runtimeHelpers.${helper} exists`);
    }
  }
}

assertStateMatrix(
  "E13.3 performanceRunStateMatrix",
  doc.performanceRunStateMatrix,
  REQUIRED_RUN_STATUSES,
  "QUEUED",
  ok, fail,
);

assertStateMatrix(
  "E13.3 hpaStateMatrix",
  doc.hpaStateMatrix,
  REQUIRED_HPA_STATUSES,
  "STEADY",
  ok, fail,
);

const benchArr = asArray(doc.benchmarkScenarios?.values);
const benchNames = benchArr.map((b) => asObject(b).workloadClass).sort();
const requiredBenchNames = [...REQUIRED_WORKLOAD_CLASSES].sort();
if (benchNames.join(",") !== requiredBenchNames.join(",")) {
  fail(`E13.3 benchmarkScenarios: workloadClass closed-set mismatch.\n     expected: ${requiredBenchNames.join(",")}\n     actual:   ${benchNames.join(",")}`);
} else {
  ok(`E13.3 benchmarkScenarios (${benchNames.length} scenarios)`);
  for (const b of benchArr) {
    const o = asObject(b);
    const t = asObject(o.thresholds);
    if (typeof t.p95Ms !== "number") {
      fail(`E13.3 benchmarkScenarios.${o.workloadClass}: thresholds.p95Ms missing`);
    }
    if (typeof t.errorRate !== "number") {
      fail(`E13.3 benchmarkScenarios.${o.workloadClass}: thresholds.errorRate missing`);
    }
  }
}

const chartPath = doc.requiredSourceMirror?.chartPath;
if (chartPath !== "platform/helm/genealogy-platform/files/reliability/performance-capacity-policy.yaml") {
  fail(`E13.3 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/reliability/performance-capacity-policy.yaml (got ${chartPath})`);
} else {
  ok(`E13.3 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E13.3 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E13.3 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}