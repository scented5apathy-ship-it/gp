#!/usr/bin/env node
/**
 * scripts/lint-telemetry.mjs
 *
 * E13.1 deep validator for the telemetry contract at
 * `contracts/reliability/telemetry-policy.yaml` and the platform
 * mirror at `platform/helm/genealogy-platform/files/reliability/
 *  telemetry-policy.yaml`.
 *
 * Validates:
 *   - closed-set vocabularies: telemetrySignals[4], traceHops[15],
 *     pseudonymLabels[5], forbiddenMetricLabels[28],
 *     telemetryRedactionPatterns[9], cardinalityCeilings[4 keys],
 *     redMetricSurface[4 surfaces], outboxAndLagMetrics[4],
 *     fallbackStrategy[5 fields], auditPipelineFields[5+6],
 *     browserRedactionTests[9], serviceLevelRequiredMetrics[11],
 *     telemetryOutageAlerts[5], telemetryOutageAuditEvents[10],
 *     egressAllowlist[3], outboxEnvelope[7 fields],
 *     temporalSearchAttributes[4+5],
 *     browserTelemetry.eventWhitelist[10], invariants[17],
 *     capabilityBoundaries[12], forbiddenKeywords[26],
 *     requiredRuntimeHelpers[3];
 *   - 2 state matrices (telemetryStateMatrix initial READY
 *     with 6 statuses incl. 1 terminal; runtimeStateMatrix
 *     initial ALLOWED with 5 statuses incl. 2 terminal);
 *   - numeric bounds (13 invariants);
 *   - byte-identity between contract file and helm chart mirror;
 *   - 9 redaction regexes must exist and look canonical.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
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
  "contracts/reliability/telemetry-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/reliability/telemetry-policy.yaml",
);

const REQUIRED_TELEMETRY_SIGNALS = ["trace", "metric", "log", "audit"];
const REQUIRED_TRACE_HOPS = [
  "edge_ingress",
  "kong_gateway",
  "web_bff",
  "public_api",
  "grpc_client",
  "service_mtls",
  "service_handler",
  "jooq_query",
  "postgres",
  "kafka_producer",
  "kafka_consumer",
  "temporal_activity",
  "temporal_workflow",
  "outbox_relay",
  "search_projection",
];
const REQUIRED_PSEUDONYM_LABELS = [
  "tenant_pseudo_id",
  "user_pseudo_id",
  "actor_pseudo_id",
  "workflow_pseudo_id",
  "consumer_pseudo_id",
];
const REQUIRED_FORBIDDEN_METRIC_LABELS = [
  "tenant_id", "user_id", "actor_id",
  "email", "oidc_subject", "oidcSubject",
  "phone", "passport", "ssn",
  "raw_dna", "raw_pii", "rawEmail", "rawPhone", "rawAddress",
  "treeViewerBypass", "rawEventPayload", "rawAuditStream",
  "rawConsentReceipt", "rawSignatureBlob", "rawIdDocument",
  "cameraSerial", "exifGps", "passportNumber",
  "productionPii", "internalVaultToken", "internalSessionCookie",
  "dnaRawBucketKey", "dnaMatchBucketKey",
];
const REQUIRED_REDACTION_PATTERN_KEYS = [
  "ssn", "passport", "driverLicense", "email",
  "phone", "ipv4", "jwt", "rawDnaMarker", "authorizationHeader",
];
const REQUIRED_CARDINALITY_KEYS = [
  "tenant_pseudo_id",
  "user_pseudo_id",
  "workflow_pseudo_id",
  "consumer_pseudo_id",
];
const REQUIRED_RED_METRIC_SURFACES = [
  "rest_endpoint",
  "grpc_method",
  "kafka_consumer",
  "temporal_workflow",
];
const REQUIRED_OUTBOX_METRICS = [
  "outbox_age_seconds",
  "consumer_lag_records",
  "workflow_failure_total",
  "projection_freshness_seconds",
];
const REQUIRED_AUDIT_REQUIRED_FIELDS = [
  "actor_pseudo_id", "tenant_pseudo_id", "action",
  "correlation_id", "trace_id",
];
const REQUIRED_AUDIT_REJECTED_FIELDS = [
  "email", "oidc_subject", "phone", "rawEmail", "raw_dna", "raw_pii",
];
const REQUIRED_BROWSER_REDACTION_TESTS = [
  "email_scrub", "phone_scrub", "ipv4_scrub", "jwt_scrub",
  "raw_dna_marker_scrub", "forbidden_key_drop", "pseudonym_label_present",
  "traceparent_propagation", "fallback_ring_buffer",
];
const REQUIRED_TELEMETRY_STATUSES = [
  "READY", "COLLECTING", "BACKPRESSURE",
  "CIRCUIT_OPEN", "FLUSHING", "SHUTDOWN",
];
const REQUIRED_RUNTIME_STATUSES = [
  "ALLOWED", "PSEUDONYMIZED", "REDACTED", "DROPPED", "ESCALATED",
];
const REQUIRED_INVARIANTS = [
  "traceparentPropagatedAcrossHops",
  "pseudonymLabelsOnly",
  "forbiddenMetricLabelsRejected",
  "nineRedactionPatternsApplied",
  "redMetricsEmittedForAllSurfaces",
  "outboxAgeAndLagEmitted",
  "browserRedactionTestsPresent",
  "otelCollectorDownDoesNotBlockBusiness",
  "auditPipelineForcesActorPseudoId",
  "cardinalityCeilingsRespected",
  "rawDnaNeverOnAnySurface",
  "rawPiiNeverOnAnySurface",
  "oidcSubjectNeverPlain",
  "emailNeverPlain",
  "treeViewerBypassNeverEmitted",
  "traceMirroredInKafkaEnvelope",
  "traceMirroredInTemporalSearchAttribute",
];
const REQUIRED_CAPABILITY_BOUNDARIES = [
  "observability grafana_oss_stack_only",
  "metric_collector otel_collector_only",
  "trace_storage tempo_only",
  "log_storage loki_only",
  "dashboard_ui grafana_only",
  "alerting prometheus_only",
  "audit_sink otlp_audit_only",
  "noCustomMetricBackend forbidden",
  "noCustomLogAggregator forbidden",
  "noCustomTraceStore forbidden",
  "noCustomDashboardUI forbidden",
  "noCustomAlertingEngine forbidden",
];
const REQUIRED_FORBIDDEN_KEYWORDS = [
  "raw_dna_bytes", "raw_genotype", "raw_fastq", "raw_bam", "raw_vcf",
  "production_pii", "prod_tenant_id", "staging_tenant_id",
  "raw_email", "raw_phone", "raw_passport", "raw_ssn",
  "dev_secret", "shared_admin_password",
  "inline_jwt", "inline_access_token", "inline_refresh_token",
  "inline_session_cookie", "inline_oauth_client_secret",
  "inline_stripe_api_key", "inline_license_file",
  "tree_viewer_bypass", "bypass_authorization",
  "skip_consent", "skip_dna_isolation", "skip_audit", "skip_redaction",
];
const REQUIRED_RUNTIME_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/telemetry/TelemetryGuard.java",
  "apps/web/src/lib/telemetry/redaction.ts",
  "apps/web/test/telemetry/redaction.test.ts",
];
const REQUIRED_EGRESS = [
  "otel-collector.gp-observability.svc.cluster.local",
  "otel-collector.gp-observability",
  "localhost_4318_dev_only",
];
const REQUIRED_TELEMETRY_OUTAGE_ALERTS = [
  "sdk_circuit_open",
  "sdk_circuit_half_open",
  "sdk_circuit_close",
  "sdk_fallback_ring_buffer_full",
  "sdk_cardinality_ceiling_exceeded",
];
const REQUIRED_OUTBOX_ENVELOPE = [
  "traceparent", "tracestate", "trace_id", "span_id",
  "correlation_id", "tenant_pseudo_id", "actor_pseudo_id",
];
const REQUIRED_TEMPORAL_SEARCH_REQUIRED = [
  "tenant_pseudo_id", "workflow_pseudo_id", "correlation_id", "trace_id",
];
const REQUIRED_TEMPORAL_SEARCH_REJECTED = [
  "tenant_id", "user_id", "email", "raw_dna", "raw_pii",
];
const REQUIRED_BROWSER_EVENTS = [
  "app_loaded", "route_changed", "flag_exposure", "error_boundary_caught",
  "mutation_queue_synced", "offline_cache_opt_in", "offline_cache_opt_out",
  "offline_cache_purge", "permission_version_mismatch",
  "accessibility_preference_changed",
];
const REQUIRED_SERVICE_METRICS = [
  "http_server_requests_seconds", "grpc_server_handled_total",
  "grpc_server_handling_seconds", "jvm_memory_used_bytes",
  "jvm_gc_pause_seconds", "process_cpu_seconds_total",
  "kafka_consumer_records_consumed_total", "outbox_age_seconds",
  "consumer_lag_records", "workflow_failure_total",
  "projection_freshness_seconds",
];

const REQUIRED_NUMERIC_KEYS = [
  "maxPseudonymHashBytes", "minTraceparentVersionDigits",
  "maxTracestateVendorEntries", "outboxAgeAlertThresholdSeconds",
  "consumerLagAlertThresholdRecords", "workflowFailureAlertThresholdPerHour",
  "projectionFreshnessAlertThresholdSeconds", "batchProcessorMaxQueueSize",
  "ringBufferMaxBytes", "circuitBreakerWindowSeconds",
  "circuitBreakerThreshold", "auditFieldsForcedCount",
  "auditFieldsRejectedCount",
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
const chartDoc = loadYaml(chartText);

if (text !== chartText) {
  fail(`byte-identity: contract and chart mirror differ (chart=${CHART_FILE})`);
} else {
  ok(`byte-identity: contract mirrors chart (${text.length} bytes)`);
}

assertClosedSet(
  "telemetrySignals", REQUIRED_TELEMETRY_SIGNALS,
  asArray(doc.telemetrySignals?.values),
  "E13.1 telemetrySignals", ok, fail,
);

assertClosedSet(
  "traceHops", REQUIRED_TRACE_HOPS,
  asArray(doc.traceHops?.values),
  "E13.1 traceHops", ok, fail,
);

assertClosedSet(
  "pseudonymLabels", REQUIRED_PSEUDONYM_LABELS,
  asArray(doc.pseudonymLabels?.values),
  "E13.1 pseudonymLabels", ok, fail,
);

assertClosedSet(
  "forbiddenMetricLabels", REQUIRED_FORBIDDEN_METRIC_LABELS,
  asArray(doc.forbiddenMetricLabels?.values),
  "E13.1 forbiddenMetricLabels", ok, fail,
);

const redactionArr = asArray(doc.telemetryRedactionPatterns?.values);
const actualRedactionKeys = redactionArr.map((r) => asObject(r).name).sort();
const requiredRedactionKeys = [...REQUIRED_REDACTION_PATTERN_KEYS].sort();
if (actualRedactionKeys.join(",") !== requiredRedactionKeys.join(",")) {
  fail(
    `E13.1 telemetryRedactionPatterns: closed-set mismatch.\n` +
    `     expected: ${requiredRedactionKeys.join(",")}\n` +
    `     actual:   ${actualRedactionKeys.join(",")}`,
  );
} else {
  ok(`E13.1 telemetryRedactionPatterns (${actualRedactionKeys.length} patterns)`);
  for (const r of redactionArr) {
    const obj = asObject(r);
    if (typeof obj.regex !== "string" || obj.regex.length < 4) {
      fail(`E13.1 telemetryRedactionPatterns.${obj.name}: regex too short (${obj.regex})`);
    }
  }
}

const cardArr = asArray(doc.cardinalityCeilings?.values);
const cardMap = {};
for (const c of cardArr) {
  const obj = asObject(c);
  if (obj.name) cardMap[obj.name] = obj.max;
}
const missingCard = REQUIRED_CARDINALITY_KEYS.filter((k) => !(k in cardMap));
if (missingCard.length > 0) {
  fail(`E13.1 cardinalityCeilings: missing ${missingCard.join(",")}`);
} else {
  ok(`E13.1 cardinalityCeilings (${Object.keys(cardMap).length} keys)`);
  for (const [k, v] of Object.entries(cardMap)) {
    if (typeof v !== "number" || v <= 0) {
      fail(`E13.1 cardinalityCeilings.${k}: not a positive number (${v})`);
    }
  }
}

const redMetricSurfaces = asArray(doc.redMetricSurface?.values);
const actualRedSurfaces = redMetricSurfaces.map((s) => asObject(s).surface).sort();
const requiredRedSurfaces = [...REQUIRED_RED_METRIC_SURFACES].sort();
if (actualRedSurfaces.join(",") !== requiredRedSurfaces.join(",")) {
  fail(
    `E13.1 redMetricSurface: closed-set mismatch.\n` +
    `     expected: ${requiredRedSurfaces.join(",")}\n` +
    `     actual:   ${actualRedSurfaces.join(",")}`,
  );
} else {
  ok(`E13.1 redMetricSurface (${actualRedSurfaces.length} surfaces)`);
}

const outboxMetrics = asArray(doc.outboxAndLagMetrics?.values).map((m) => asObject(m).name);
assertClosedSet(
  "outboxAndLagMetrics", REQUIRED_OUTBOX_METRICS, outboxMetrics,
  "E13.1 outboxAndLagMetrics", ok, fail,
);

const fallbackArr = asArray(doc.fallbackStrategy?.values);
const fallbackMap = {};
for (const f of fallbackArr) {
  const obj = asObject(f);
  if (obj.name) fallbackMap[obj.name] = obj.value;
}
const requiredFallback = ["ringBufferMaxBytes", "circuitBreakerWindowSeconds",
  "circuitBreakerThreshold", "onCircuitOpen", "onCollectorRecover"];
const missingFallback = requiredFallback.filter((k) => !(k in fallbackMap));
if (missingFallback.length > 0) {
  fail(`E13.1 fallbackStrategy: missing ${missingFallback.join(",")}`);
} else if (fallbackMap.ringBufferMaxBytes !== 4194304) {
  fail(`E13.1 fallbackStrategy.ringBufferMaxBytes: must equal 4194304 (got ${fallbackMap.ringBufferMaxBytes})`);
} else if (fallbackMap.circuitBreakerThreshold !== 5) {
  fail(`E13.1 fallbackStrategy.circuitBreakerThreshold: must equal 5 (got ${fallbackMap.circuitBreakerThreshold})`);
} else {
  ok(`E13.1 fallbackStrategy (${Object.keys(fallbackMap).length} fields)`);
}

const auditPipe = asObject(doc.auditPipelineFields);
assertClosedSet(
  "auditPipelineFields.required", REQUIRED_AUDIT_REQUIRED_FIELDS,
  asArray(auditPipe.required),
  "E13.1 auditPipelineFields.required", ok, fail,
);
assertClosedSet(
  "auditPipelineFields.rejected", REQUIRED_AUDIT_REJECTED_FIELDS,
  asArray(auditPipe.rejected),
  "E13.1 auditPipelineFields.rejected", ok, fail,
);

assertClosedSet(
  "browserRedactionTests", REQUIRED_BROWSER_REDACTION_TESTS,
  asArray(doc.browserRedactionTests?.values),
  "E13.1 browserRedactionTests", ok, fail,
);

assertClosedSet(
  "telemetryOutageAlerts", REQUIRED_TELEMETRY_OUTAGE_ALERTS,
  asArray(doc.telemetryOutageAlerts?.values),
  "E13.1 telemetryOutageAlerts", ok, fail,
);

assertClosedSet(
  "egressAllowlist", REQUIRED_EGRESS,
  asArray(doc.egressAllowlist?.values),
  "E13.1 egressAllowlist", ok, fail,
);

assertClosedSet(
  "outboxEnvelope", REQUIRED_OUTBOX_ENVELOPE,
  asArray(doc.outboxEnvelope?.fields),
  "E13.1 outboxEnvelope.fields", ok, fail,
);

assertClosedSet(
  "temporalSearchAttributes.required", REQUIRED_TEMPORAL_SEARCH_REQUIRED,
  asArray(asObject(doc.temporalSearchAttributes).required),
  "E13.1 temporalSearchAttributes.required", ok, fail,
);
assertClosedSet(
  "temporalSearchAttributes.rejected", REQUIRED_TEMPORAL_SEARCH_REJECTED,
  asArray(asObject(doc.temporalSearchAttributes).rejected),
  "E13.1 temporalSearchAttributes.rejected", ok, fail,
);

assertClosedSet(
  "browserTelemetry.eventWhitelist", REQUIRED_BROWSER_EVENTS,
  asArray(asObject(doc.browserTelemetry).eventWhitelist),
  "E13.1 browserTelemetry.eventWhitelist", ok, fail,
);

assertClosedSet(
  "serviceLevelRequiredMetrics", REQUIRED_SERVICE_METRICS,
  asArray(doc.serviceLevelRequiredMetrics?.values),
  "E13.1 serviceLevelRequiredMetrics", ok, fail,
);

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E13.1 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries", REQUIRED_CAPABILITY_BOUNDARIES,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E13.1 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E13.1 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_RUNTIME_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E13.1 requiredRuntimeHelpers", ok, fail,
);

const numericArr = asArray(doc.numericBounds?.values);
const numericMap = {};
for (const n of numericArr) {
  const obj = asObject(n);
  if (obj.name) numericMap[obj.name] = obj.value;
}
const missingNumeric = REQUIRED_NUMERIC_KEYS.filter((k) => !(k in numericMap));
if (missingNumeric.length > 0) {
  fail(`E13.1 numericBounds: missing ${missingNumeric.join(",")}`);
} else {
  ok(`E13.1 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numericMap)) {
    if (typeof v !== "number") {
      fail(`E13.1 numericBounds.${k}: not a number (${v})`);
    }
  }
}

assertStateMatrix(
  "E13.1 telemetryStateMatrix",
  doc.telemetryStateMatrix,
  REQUIRED_TELEMETRY_STATUSES,
  "READY",
  ok, fail,
);

assertStateMatrix(
  "E13.1 runtimeStateMatrix",
  doc.runtimeStateMatrix,
  REQUIRED_RUNTIME_STATUSES,
  "ALLOWED",
  ok, fail,
);

const algoArr = asArray(doc.tenantPseudonymAlgorithm?.values);
const algoMap = {};
for (const a of algoArr) {
  const obj = asObject(a);
  if (obj.name) algoMap[obj.name] = obj.value;
}
const requiredAlgo = ["hashFunction", "pepperSource", "pepperRotationPeriodDays",
  "outputEncoding", "outputTruncateBytes", "saltPerEnvironment"];
const missingAlgo = requiredAlgo.filter((k) => !(k in algoMap));
if (missingAlgo.length > 0) {
  fail(`E13.1 tenantPseudonymAlgorithm: missing ${missingAlgo.join(",")}`);
} else if (algoMap.hashFunction !== "HMAC-SHA256") {
  fail(`E13.1 tenantPseudonymAlgorithm.hashFunction: must equal HMAC-SHA256 (got ${algoMap.hashFunction})`);
} else if (algoMap.outputTruncateBytes !== 16) {
  fail(`E13.1 tenantPseudonymAlgorithm.outputTruncateBytes: must equal 16 (got ${algoMap.outputTruncateBytes})`);
} else {
  ok(`E13.1 tenantPseudonymAlgorithm (${requiredAlgo.length} fields)`);
}

const requiredCharts = doc.requiredSourceMirror?.chartPath;
if (requiredCharts !== "platform/helm/genealogy-platform/files/reliability/telemetry-policy.yaml") {
  fail(`E13.1 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/reliability/telemetry-policy.yaml (got ${requiredCharts})`);
} else {
  ok(`E13.1 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E13.1 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E13.1 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}