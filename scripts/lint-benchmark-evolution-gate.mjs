#!/usr/bin/env node
/**
 * scripts/lint-benchmark-evolution-gate.mjs
 *
 * E8.4 deep validator for the benchmark + evolution gate policy
 * contract under
 * `contracts/search/benchmark-evolution-gate-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/benchmark-evolution-gate-policy.yaml`.
 *
 * Mirrors the structure of `lint-search-projection.mjs` (E8.1).
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/search/benchmark-evolution-gate-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/benchmark-evolution-gate-policy.yaml");

const REQUIRED_BENCHMARK_WORKLOADS = [
  "EXACT_PERSON",
  "EXACT_ALBUM",
  "FUZZY_PERSON_TRIGRAM",
  "FUZZY_PLACE_TRIGRAM",
  "FACET_TREE_FAMILY",
  "FACET_DECADE_LIVING_STATUS",
  "CURSOR_PAGINATION",
  "SAVED_SEARCH_ALERT",
];
const REQUIRED_BENCHMARK_DATASET_SHAPES = [
  "SMALL_HOT_TREE",
  "MEDIUM_MULTI_TREE",
  "LARGE_PUBLIC_CORPUS",
  "LARGE_PRIVATE_CORPUS",
  "LARGE_MULTILINGUAL_CORPUS",
  "WORST_CASE_DIACRITICS",
  "WORST_CASE_MIXED_SCRIPT",
  "WORST_CASE_MAX_CURSOR_DEPTH",
];
const REQUIRED_BENCHMARK_VERDICTS = [
  "PASS",
  "PASS_WITH_NOTES",
  "FAIL_P95",
  "FAIL_P99",
  "FAIL_LAG",
  "FAIL_FRESHNESS",
  "FAIL_FUZZY_RECALL",
  "FAIL_FUZZY_PRECISION",
  "FAIL_FACET_CARDINALITY",
  "FAIL_INDEX",
  "FAIL_SAFETY",
  "BLOCKED_ADR_REQUIRED",
];
const REQUIRED_BENCHMARK_EVOLUTION_PATHS = [
  "POSTGRES_HOLD",
  "POSTGRES_REINDEX",
  "POSTGRES_PARTITION",
  "POSTGRES_GIN_REWRITE",
  "POSTGRES_GIST_REWRITE",
  "POSTGRES_BRIN_PARTITION",
  "ADAPTIVE_INDEX_REQUIRED",
  "OPENSEARCH_REQUIRED",
];
const REQUIRED_BENCHMARK_SLO_METRICS = [
  "P95_QUERY_LATENCY_MS",
  "P99_QUERY_LATENCY_MS",
  "P95_FACET_LATENCY_MS",
  "P95_CURSOR_PAGINATION_LATENCY_MS",
  "P95_FRESHNESS_SECONDS",
  "FUZZY_RECALL_AT_10",
  "FUZZY_PRECISION_AT_10",
  "FACET_CARDINALITY",
  "SAFETY_BUDGET_VIOLATIONS",
  "SAFETY_DNA_BUCKET_LEAKS",
];
const REQUIRED_BENCHMARK_QUERY_LANGUAGES = ["en", "vi", "ja", "fr", "es"];
const REQUIRED_BENCHMARK_ROLLOUT_STAGES = [
  "NIGHTLY",
  "PRE_MERGE",
  "RELEASE_CANDIDATE",
  "POST_RELEASE",
  "AD_HOC",
];
const REQUIRED_BENCHMARK_FAILURE_REASONS = [
  "BENCHMARK_WORKLOAD_UNKNOWN",
  "BENCHMARK_DATASET_SHAPE_UNKNOWN",
  "BENCHMARK_SLO_METRIC_UNKNOWN",
  "BENCHMARK_QUERY_LANGUAGE_UNKNOWN",
  "BENCHMARK_EVOLUTION_PATH_UNKNOWN",
  "BENCHMARK_VERDICT_UNKNOWN",
  "BENCHMARK_ROLLOUT_STAGE_UNKNOWN",
  "BENCHMARK_DATASET_MISSING",
  "BENCHMARK_DATASET_TOO_SMALL",
  "BENCHMARK_DATASET_TOO_LARGE",
  "BENCHMARK_SLO_BUDGET_EXCEEDED",
  "BENCHMARK_FUZZY_RECALL_BELOW_FLOOR",
  "BENCHMARK_FUZZY_PRECISION_BELOW_FLOOR",
  "BENCHMARK_FACET_CARDINALITY_BELOW_FLOOR",
  "BENCHMARK_FRESHNESS_BUDGET_EXCEEDED",
  "BENCHMARK_INDEX_INVALID",
  "BENCHMARK_SAFETY_BUDGET_EXCEEDED",
  "BENCHMARK_DNA_BUCKET_LEAK",
  "BENCHMARK_CONTRACT_HASH_DRIFT",
  "BENCHMARK_RUNTIME_TIMEOUT",
  "BENCHMARK_DETERMINISTIC_FAIL",
  "BENCHMARK_ADR_REQUIRED",
];
const REQUIRED_BENCHMARK_AUDIT_EVENTS = [
  "BENCHMARK_RUN_QUEUED",
  "BENCHMARK_RUN_STARTED",
  "BENCHMARK_WORKLOAD_STARTED",
  "BENCHMARK_WORKLOAD_FINISHED",
  "BENCHMARK_SLO_BREACHED",
  "BENCHMARK_SLO_RECOVERED",
  "BENCHMARK_VERDICT_EMITTED",
  "BENCHMARK_EVOLUTION_PATH_RECOMMENDED",
  "BENCHMARK_ADR_REQUIRED_RECORDED",
  "BENCHMARK_DATASET_INVALID",
  "BENCHMARK_DNA_BUCKET_LEAK_REFUSED",
  "BENCHMARK_RUN_ABORTED",
  "BENCHMARK_RUN_FINISHED",
  "BENCHMARK_RUN_PROMOTED",
];
const REQUIRED_SANDBOX_EGRESS = [
  "postgres",
  "apicurio",
  "vault-agent",
  "openfga",
  "audit-service",
  "kafka-broker",
];
const REQUIRED_DNA_BUCKET_PREFIXES = ["dna/raw", "dna/match", "dna/consent"];
const REQUIRED_OUTBOX_FIELDS = [
  "eventId",
  "eventType",
  "occurredAt",
  "tenantId",
  "aggregateId",
  "aggregateVersion",
  "traceId",
  "payload",
];
const REQUIRED_OUTBOX_TYPES = [
  "gp.search.v1.BenchmarkVerdictEmitted",
  "gp.search.v1.BenchmarkEvolutionPathRecommended",
];
const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = [
  "rawDnaSequence",
  "rawFastq",
  "rawBam",
  "rawVcf",
  "exifGps",
  "cameraSerial",
  "passportNumber",
  "socialSecurityNumber",
  "nameOnBirth",
  "rawEmail",
  "rawPhone",
  "rawAddress",
  "biometricTemplate",
  "rawFacialEmbedding",
  "rawLivingStatus",
  "rawMinorStatus",
  "rawConsentDocument",
  "productionPii",
];
const REQUIRED_ROLLOUT_TO_EVOLUTION = {
  NIGHTLY: [
    "POSTGRES_HOLD",
    "POSTGRES_REINDEX",
    "POSTGRES_PARTITION",
    "POSTGRES_GIN_REWRITE",
    "POSTGRES_GIST_REWRITE",
    "POSTGRES_BRIN_PARTITION",
    "ADAPTIVE_INDEX_REQUIRED",
  ],
  PRE_MERGE: ["POSTGRES_HOLD", "POSTGRES_REINDEX", "POSTGRES_PARTITION", "POSTGRES_GIN_REWRITE"],
  RELEASE_CANDIDATE: ["POSTGRES_HOLD"],
  POST_RELEASE: ["POSTGRES_HOLD", "POSTGRES_REINDEX"],
  AD_HOC: [
    "POSTGRES_HOLD",
    "POSTGRES_REINDEX",
    "POSTGRES_PARTITION",
    "POSTGRES_GIN_REWRITE",
    "POSTGRES_GIST_REWRITE",
    "POSTGRES_BRIN_PARTITION",
    "ADAPTIVE_INDEX_REQUIRED",
    "OPENSEARCH_REQUIRED",
  ],
};
const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom OpenSearch indexing (the worker consumes Avro events only)",
  "Custom PII / DNA detector (use the platform-wide redactor)",
  "Cross-service aggregation (use Kafka events + publisher resolution)",
  "Custom benchmark framework (re-use k6/Gatling + platform tooling)",
  "OpenSearch without ADR supersession (gate enforces ADR_REQUIRED)",
];

const violations = [];
const ok = (msg) => {
  // eslint-disable-next-line no-console
  console.log(`OK  ${msg}`);
};
const fail = (msg) => {
  violations.push(msg);
  // eslint-disable-next-line no-console
  console.error(`FAIL ${msg}`);
};

function assertClosedSet(name, expected, actual, label) {
  const expectedSorted = [...expected].sort().join(",");
  const actualSorted = [...actual].sort().join(",");
  if (expectedSorted !== actualSorted) {
    fail(
      `${label || name}: closed-set mismatch.\n     expected: ${expectedSorted}\n     actual:   ${actualSorted}`,
    );
    return;
  }
  if (expected.length === 0) {
    fail(`${label || name}: empty closed-set is forbidden`);
    return;
  }
  ok(`${label || name} (${actual.length} values)`);
}

function assertStateMatrix(label, matrix, expectedStatuses, initialStatus) {
  if (!matrix || typeof matrix !== "object") {
    fail(`${label}: state matrix missing`);
    return;
  }
  const statuses = asArray(matrix.statuses);
  if (statuses.length === 0) {
    fail(`${label}: state matrix is empty`);
    return;
  }
  const seen = new Set();
  const reachable = new Set([initialStatus]);
  for (const entry of statuses) {
    if (!entry || typeof entry !== "object") {
      fail(`${label}: invalid status entry ${JSON.stringify(entry)}`);
      continue;
    }
    const status = entry.status;
    if (!status) {
      fail(`${label}: status entry missing 'status' field`);
      continue;
    }
    seen.add(status);
    const transitions = asArray(entry.transitions);
    if (!Array.isArray(transitions)) {
      fail(`${label}: ${status}.transitions must be an array`);
      continue;
    }
    if (entry.terminal === true) {
      if (transitions.length !== 0) {
        fail(`${label}: terminal status ${status} MUST have empty transitions (got ${JSON.stringify(transitions)})`);
      } else {
        ok(`${label}: terminal status ${status} has empty transitions`);
      }
    } else {
      if (transitions.length === 0) {
        fail(`${label}: non-terminal status ${status} MUST declare at least one transition`);
      }
    }
    for (const t of transitions) {
      if (typeof t !== "string") {
        fail(`${label}: ${status} transition ${JSON.stringify(t)} is not a string`);
      }
    }
    if (entry.terminal !== true) {
      for (const t of transitions) reachable.add(t);
    }
  }
  if (matrix.initialStatus !== initialStatus) {
    fail(`${label}: initialStatus MUST equal ${initialStatus} (got ${matrix.initialStatus})`);
  }
  for (const s of expectedStatuses) {
    if (!seen.has(s)) {
      fail(`${label}: expected status ${s} missing`);
    }
  }
  for (const s of seen) {
    if (!expectedStatuses.includes(s)) {
      fail(`${label}: unexpected status ${s} in matrix`);
    }
  }
  for (const s of seen) {
    if (!reachable.has(s) && s !== initialStatus) {
      fail(`${label}: status ${s} is unreachable from ${initialStatus}`);
    }
  }
  ok(`${label}: ${seen.size} statuses, ${expectedStatuses.length - seen.size} missing`);
}

function readContract(path) {
  return loadYaml(readFileSync(path, "utf8"));
}

function main() {
  let contract;
  try {
    contract = readContract(CONTRACT);
  } catch (err) {
    fail(`could not read contract ${CONTRACT}: ${err.message}`);
    process.exit(2);
  }
  if (!contract || typeof contract !== "object") {
    fail(`contract ${CONTRACT} is empty or malformed`);
    process.exit(2);
  }

  assertClosedSet("benchmarkWorkloads", REQUIRED_BENCHMARK_WORKLOADS, asArray(contract.benchmarkWorkloads?.values));
  assertClosedSet(
    "benchmarkDatasetShapes",
    REQUIRED_BENCHMARK_DATASET_SHAPES,
    asArray(contract.benchmarkDatasetShapes?.values),
  );
  assertClosedSet("benchmarkVerdicts", REQUIRED_BENCHMARK_VERDICTS, asArray(contract.benchmarkVerdicts?.values));
  assertClosedSet(
    "benchmarkEvolutionPaths",
    REQUIRED_BENCHMARK_EVOLUTION_PATHS,
    asArray(contract.benchmarkEvolutionPaths?.values),
  );
  assertClosedSet("benchmarkSloMetrics", REQUIRED_BENCHMARK_SLO_METRICS, asArray(contract.benchmarkSloMetrics?.values));
  assertClosedSet(
    "benchmarkQueryLanguages",
    REQUIRED_BENCHMARK_QUERY_LANGUAGES,
    asArray(contract.benchmarkQueryLanguages?.values),
  );
  assertClosedSet(
    "benchmarkRolloutStages",
    REQUIRED_BENCHMARK_ROLLOUT_STAGES,
    asArray(contract.benchmarkRolloutStages?.values),
  );
  assertClosedSet(
    "benchmarkFailureReasons",
    REQUIRED_BENCHMARK_FAILURE_REASONS,
    asArray(contract.benchmarkFailureReasons?.values),
  );
  assertClosedSet(
    "benchmarkAuditEvents",
    REQUIRED_BENCHMARK_AUDIT_EVENTS,
    asArray(contract.benchmarkAuditEvents?.values),
  );
  assertClosedSet(
    "sandboxEgressAllowlist",
    REQUIRED_SANDBOX_EGRESS,
    asArray(contract.sandboxEgressAllowlist?.values),
    "sandbox egress allowlist",
  );

  assertStateMatrix(
    "benchmarkGateStateMatrix",
    contract.benchmarkGateStateMatrix,
    ["QUEUED", "DATASETS_LOADING", "WORKLOADS_RUNNING", "SLO_AGGREGATING", "VERDICT_EMITTED", "RECOMMENDED", "PROMOTED", "BLOCKED", "FAILED", "DECIDED"],
    "QUEUED",
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["postgresqlOnlyByDefault", true],
    ["opensearchRequiresAdr", true],
    ["benchmarkSuiteRequiredForRelease", true],
    ["fuzzyRecallFloorEnforced", true],
    ["fuzzyPrecisionFloorEnforced", true],
    ["freshnessBudgetEnforced", true],
    ["facetCardinalityFloorEnforced", true],
    ["p95BudgetEnforced", true],
    ["p99BudgetEnforced", true],
    ["safetyBudgetEnforced", true],
    ["deterministicRunnerRequired", true],
    ["contractHashBoundToRun", true],
    ["datasetShapeSealed", true],
    ["benchmarkOutOfBandForbidden", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
  ];
  for (const [key, expected] of booleanGuards) {
    if (gr[key] !== expected) {
      fail(`guardRails.${key} MUST be ${expected} (got ${gr[key]})`);
    } else {
      ok(`guardRails.${key} = ${expected}`);
    }
  }
  if (gr.dnaBucketAccess !== "FORBIDDEN") {
    fail(`guardRails.dnaBucketAccess MUST equal FORBIDDEN (got ${gr.dnaBucketAccess})`);
  } else {
    ok("guardRails.dnaBucketAccess = FORBIDDEN");
  }
  assertClosedSet(
    "guardRails.dnaBucketPrefixes",
    REQUIRED_DNA_BUCKET_PREFIXES,
    asArray(gr.dnaBucketPrefixes),
    "DNA bucket prefixes",
  );

  const nb = contract.numericBounds || {};
  const numericGuards = {
    benchmarkSuiteMaxDatasets: 16,
    benchmarkSuiteMaxQueriesPerDataset: 1024,
    benchmarkSuiteMaxWorkloads: 8,
    benchmarkSuiteMaxRolloutStages: 5,
    benchmarkSuiteWarmupIterations: 50,
    benchmarkSuiteMeasurementIterations: 200,
    benchmarkSuiteCooldownSeconds: 60,
    benchmarkSuiteP95BudgetMilliseconds: 1000,
    benchmarkSuiteP99BudgetMilliseconds: 2000,
    benchmarkSuiteFacetP95BudgetMilliseconds: 250,
    benchmarkSuiteCursorP95BudgetMilliseconds: 500,
    benchmarkSuiteFreshnessBudgetSeconds: 60,
    benchmarkSuiteFuzzyRecallFloor: 0.85,
    benchmarkSuiteFuzzyPrecisionFloor: 0.90,
    benchmarkSuiteFacetCardinalityFloor: 16,
    benchmarkSuiteSafetyBudgetViolations: 0,
    benchmarkSuiteDnaBucketLeaks: 0,
    benchmarkSuiteRuntimeTimeoutSeconds: 120,
    benchmarkSuiteHeartbeatSeconds: 30,
    benchmarkSuiteContractHashLength: 64,
    benchmarkSuiteDatasetMaxRows: 10000000,
    benchmarkSuiteDatasetMinRows: 1000,
    benchmarkSuiteRecommendationTtlSeconds: 2592000,
  };
  for (const [key, expected] of Object.entries(numericGuards)) {
    if (nb[key] !== expected) {
      fail(`numericBounds.${key} MUST equal ${expected} (got ${nb[key]})`);
    } else {
      ok(`numericBounds.${key} = ${expected}`);
    }
  }

  const ri = contract.reconciliationInvariants || {};
  const invariants = {
    p95P99BudgetRatio: 2,
    heartbeatMultiplier: 6,
    workloadMultiplier: 1,
    rolloutStagesMultiplier: 1,
    fuzzyFloorPrecisionFloorRatio: 1,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (nb.benchmarkSuiteP99BudgetMilliseconds !== ri.p95P99BudgetRatio * nb.benchmarkSuiteP95BudgetMilliseconds) {
    fail(
      `p95/p99 invariant violated: p99=${nb.benchmarkSuiteP99BudgetMilliseconds}ms MUST equal ${ri.p95P99BudgetRatio} × p95=${nb.benchmarkSuiteP95BudgetMilliseconds}ms`,
    );
  } else {
    ok(`p95/p99 invariant: ${nb.benchmarkSuiteP99BudgetMilliseconds} = ${ri.p95P99BudgetRatio} × ${nb.benchmarkSuiteP95BudgetMilliseconds}`);
  }
  if (nb.benchmarkSuiteRuntimeTimeoutSeconds >= ri.heartbeatMultiplier * nb.benchmarkSuiteHeartbeatSeconds) {
    fail(
      `runtime invariant violated: timeout=${nb.benchmarkSuiteRuntimeTimeoutSeconds}s MUST be < ${ri.heartbeatMultiplier} × heartbeat=${nb.benchmarkSuiteHeartbeatSeconds}s`,
    );
  } else {
    ok(`runtime invariant: timeout=${nb.benchmarkSuiteRuntimeTimeoutSeconds} < ${ri.heartbeatMultiplier} × ${nb.benchmarkSuiteHeartbeatSeconds}`);
  }
  if (nb.benchmarkSuiteMaxDatasets !== ri.workloadMultiplier * nb.benchmarkSuiteMaxWorkloads * 2) {
    fail(
      `datasets invariant violated: maxDatasets=${nb.benchmarkSuiteMaxDatasets} MUST equal ${ri.workloadMultiplier} × maxWorkloads=${nb.benchmarkSuiteMaxWorkloads} × 2`,
    );
  } else {
    ok(`datasets invariant: ${nb.benchmarkSuiteMaxDatasets} = ${ri.workloadMultiplier} × ${nb.benchmarkSuiteMaxWorkloads} × 2`);
  }
  if (nb.benchmarkSuiteMaxRolloutStages !== ri.rolloutStagesMultiplier * nb.benchmarkSuiteMaxRolloutStages) {
    // trivial self-check kept for completeness; the real guard is the
    // closed-set rollout stages check above.
  }

  const rolloutMap = contract.rolloutToEvolutionPath || {};
  for (const [stage, paths] of Object.entries(REQUIRED_ROLLOUT_TO_EVOLUTION)) {
    const declared = asArray(rolloutMap[stage]);
    if (declared.length === 0) {
      fail(`rolloutToEvolutionPath.${stage} MUST declare at least one evolution path`);
      continue;
    }
    const expectedSorted = [...paths].sort().join(",");
    const declaredSorted = [...declared].sort().join(",");
    if (expectedSorted !== declaredSorted) {
      fail(
        `rolloutToEvolutionPath.${stage} closed-set mismatch.\n     expected: ${expectedSorted}\n     actual:   ${declaredSorted}`,
      );
    } else {
      ok(`rolloutToEvolutionPath.${stage} = ${declaredSorted}`);
    }
  }

  const outbox = asArray(contract.outboxEvents?.items);
  if (outbox.length === 0) {
    fail("outboxEvents.items MUST declare at least one event");
  } else {
    const declaredTypes = new Set();
    for (const evt of outbox) {
      if (!evt || typeof evt !== "object" || typeof evt.type !== "string") {
        fail(`outboxEvents.items: invalid entry ${JSON.stringify(evt)}`);
        continue;
      }
      declaredTypes.add(evt.type);
      const fields = asArray(evt.envelopeFields);
      for (const required of REQUIRED_OUTBOX_FIELDS) {
        if (!fields.includes(required)) {
          fail(`outboxEvents.items[${evt.type}] MUST declare envelope field '${required}'`);
        }
      }
      ok(`outboxEvents.items[${evt.type}] envelope fields ok`);
    }
    for (const required of REQUIRED_OUTBOX_TYPES) {
      if (!declaredTypes.has(required)) {
        fail(`outboxEvents.items missing required event type '${required}'`);
      } else {
        ok(`outboxEvents.items has ${required}`);
      }
    }
  }

  assertClosedSet(
    "auditHooks.auditRequired",
    REQUIRED_BENCHMARK_AUDIT_EVENTS,
    asArray(contract.auditHooks?.auditRequired),
    "auditHooks.auditRequired",
  );
  assertClosedSet(
    "forbiddenPayloadPatterns",
    REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS,
    asArray(contract.forbiddenPayloadPatterns),
    "forbidden payload patterns",
  );
  assertClosedSet(
    "capabilityBoundaries.forbiddenSelfBuilt",
    REQUIRED_CAPABILITY_FORBIDDEN,
    asArray(contract.capabilityBoundaries?.forbiddenSelfBuilt),
    "capability boundaries",
  );

  try {
    const a = readFileSync(CONTRACT, "utf8");
    const b = readFileSync(CHART_FILE, "utf8");
    if (a !== b) {
      fail(`chart mirror drift: ${CONTRACT} !== ${CHART_FILE}`);
    } else {
      ok(`chart mirror byte-equal (${a.length} bytes)`);
    }
  } catch (err) {
    fail(`chart mirror check failed: ${err.message}`);
  }

  if (violations.length > 0) {
    // eslint-disable-next-line no-console
    console.error(`\n${violations.length} violation(s).`);
    process.exit(1);
  }
  // eslint-disable-next-line no-console
  console.log("\nbenchmark evolution gate policy contract OK.");
}

main();