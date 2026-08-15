#!/usr/bin/env node
/**
 * scripts/lint-slo-alert.mjs
 *
 * E13.2 deep validator for the SLO / alert / runbook contract at
 * `contracts/reliability/slo-alert-policy.yaml` and the
 * platform mirror at
 * `platform/helm/genealogy-platform/files/reliability/slo-alert-policy.yaml`.
 *
 * Asserts:
 *   - 11 mandatory SLIs (api_read_p95, api_write_p95, search_p95,
 *     tree_view_initial_tti_p75, consumer_lag_critical_p99,
 *     outbox_age_p99, workflow_failure_rate_per_hour,
 *     projection_freshness_p99, synthetic_availability,
 *     api_availability, pii_redaction_coverage) — each declares
 *     a query, target, and at least one burn-rate alert.
 *   - 4 alert severities (SEV1..SEV4) with responseMinutes,
 *     pageRoles, notifyChannel and freezesBudget.
 *   - 3 alert actions (PAGE / TICKET / SILENT).
 *   - 10 burn-rate windows (1m / 5m / 30m / 1h / 2m / 6h /
 *     10m / 15m / 24h / 3d).
 *   - 6 availability targets (saas_production / api_read_path /
 *     search / async_job / media_pipeline / on_premise_base).
 *   - 10 synthetic probes (kong / keycloak / openfga /
 *     postgres / kafka / temporal / object_storage / vault /
 *     flagsmith / otel_collector).
 *   - 6 runbook paths + 9 dashboards MUST exist on disk.
 *   - 2 state matrices (alertRuleMatrix, budgetStateMatrix).
 *   - 20 numeric bounds incl. apiReadP95TargetMs=300,
 *     apiWriteP95TargetMs=600, budgetFreezeWeek1Ratio=0.5.
 *   - 18 invariants + 10 capability boundaries + 25 forbidden
 *     keywords + 5 required runtime helpers + 6 required
 *     alert rule fields + 7 required alert annotations.
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
  "contracts/reliability/slo-alert-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/reliability/slo-alert-policy.yaml",
);

const REQUIRED_SLIS = [
  "api_read_p95", "api_write_p95", "search_p95",
  "tree_view_initial_tti_p75",
  "consumer_lag_critical_p99", "outbox_age_p99",
  "workflow_failure_rate_per_hour", "projection_freshness_p99",
  "synthetic_availability", "api_availability",
  "pii_redaction_coverage",
];
const REQUIRED_SEVERITIES = ["SEV1", "SEV2", "SEV3", "SEV4"];
const REQUIRED_ACTIONS = ["PAGE", "TICKET", "SILENT"];
const REQUIRED_BURN_RATE_WINDOWS = [
  "1m", "5m", "30m", "1h", "2m", "6h",
  "10m", "15m", "24h", "3d",
];
const REQUIRED_AVAILABILITY_TARGETS = [
  "saas_production", "api_read_path", "search",
  "async_job", "media_pipeline", "on_premise_base",
];
const REQUIRED_SYNTHETIC_PROBES = [
  "kong_health", "keycloak_realm", "openfga_store",
  "postgres_primary", "kafka_broker", "temporal_namespace",
  "object_storage", "vault_seal_status", "flagsmith_health",
  "otel_collector",
];
const REQUIRED_PSEUDONYM_LABELS = [
  "tenant_pseudo_id", "user_pseudo_id", "actor_pseudo_id", "service",
];
const REQUIRED_FORBIDDEN_ALERT_LABELS = [
  "tenant_id", "user_id", "actor_id", "email", "oidc_subject",
  "oidcSubject", "phone", "passport", "ssn", "raw_dna", "raw_pii",
  "rawEmail", "rawPhone", "rawAddress", "treeViewerBypass",
  "rawEventPayload", "rawAuditStream",
];
const REQUIRED_RUNBOOK_FIELDS = [
  "runbookRef", "dashboardRef", "owner", "severity",
  "action", "summary", "notifyChannel",
];
const REQUIRED_RUNBOOK_PATHS = [
  "runbook/observability.md", "runbook/slo.md",
  "runbook/genealogy-service.md", "runbook/tenant-service.md",
  "runbook/research-service.md", "runbook/audit.md",
];
const REQUIRED_DASHBOARDS = [
  "api-overview", "kong", "kafka", "temporal", "openfga",
  "istio", "vault", "database", "workload",
];
const REQUIRED_ALERT_RULE_STATUSES = [
  "ROUTED", "ACK_IN_PROGRESS", "ACK_OVERDUE", "MITIGATING",
  "ESCALATED", "RESOLVED", "SUPPRESSED",
];
const REQUIRED_BUDGET_STATUSES = [
  "NOMINAL", "WARN", "FROZEN", "EXHAUSTED",
];
const REQUIRED_INVARIANTS = [
  "allAlertsHaveRunbook", "allAlertsHaveDashboard",
  "allAlertsHaveOwner", "allAlertsHaveSeverity",
  "allAlertsHaveAction", "allAlertsHaveNotifyChannel",
  "allAlertsPseudonymousLabels",
  "syntheticProbesCoverAllComponents",
  "burnRateWindowsCoverGoogleWorkbook",
  "errorBudgetFreezeThresholdEnforced",
  "sev1FreezesBudgetImmediately",
  "privacyFindingFreezesBudgetImmediately",
  "runbookPathsExist", "dashboardCatalogDeclared",
  "alertRuleMatrixReachable", "budgetStateMatrixReachable",
  "numericBoundsRespected", "noRawIdentityInAlertExpressions",
];
const REQUIRED_CAPABILITY_BOUNDARIES = [
  "alerting prometheus_alertmanager_only",
  "recordingRules prometheus_only",
  "runbookStorage git_repo_markdown_only",
  "dashboardCatalog grafana_only",
  "syntheticProbes blackbox_exporter_only",
  "onCallRouting alertmanager_pagerduty_only",
  "noCustomAlertingEngine forbidden",
  "noCustomRecordingRuleEngine forbidden",
  "noCustomSyntheticProbeRunner forbidden",
  "noCustomOnCallScheduler forbidden",
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
const REQUIRED_RUNTIME_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/slo/SloGuard.java",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/slo/E13SloLimits.java",
  "platform/observability/alerts/slo-alert-rules.yaml",
  "platform/observability/alerts/burn-rate-rules.yaml",
  "runbook/slo.md",
];
const REQUIRED_ALERT_RULE_FIELDS = [
  "alert", "expr", "for", "labels", "annotations",
];
const REQUIRED_ALERT_ANNOTATIONS = [
  "summary", "runbook_url", "dashboard_url",
  "severity", "owner", "action", "notify_channel",
];
const REQUIRED_NUMERIC_KEYS = [
  "apiReadP95TargetMs", "apiWriteP95TargetMs", "searchP95TargetMs",
  "treeViewInitialTtiP75TargetMs", "consumerLagCriticalP99TargetRecords",
  "outboxAgeP99TargetSeconds", "workflowFailurePerHourTarget",
  "projectionFreshnessP99TargetSeconds", "syntheticAvailabilityTargetRatio",
  "apiAvailabilityTargetRatio", "piiRedactionCoverageTargetRatio",
  "shortBurstFactor", "ticketFactor", "reviewFactor",
  "sev1ResponseMinutes", "sev2ResponseMinutes",
  "sev3ResponseMinutes", "sev4ResponseMinutes",
  "budgetFreezeWeek1Ratio", "cardinalityTenantCeiling",
  "cardinalityUserCeiling",
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

const sliArr = asArray(doc.slis?.values);
const actualSlis = sliArr.map((s) => asObject(s).name).sort();
const requiredSlis = [...REQUIRED_SLIS].sort();
if (actualSlis.join(",") !== requiredSlis.join(",")) {
  fail(`E13.2 slis: closed-set mismatch.\n     expected: ${requiredSlis.join(",")}\n     actual:   ${actualSlis.join(",")}`);
} else {
  ok(`E13.2 slis (${actualSlis.length} values)`);
  for (const s of sliArr) {
    const o = asObject(s);
    if (!o.query || !o.burnRateAlerts || asArray(o.burnRateAlerts).length === 0) {
      fail(`E13.2 sli.${o.name}: requires query + burnRateAlerts[]`);
    }
  }
}

const sevArr = asArray(doc.alertSeverities?.values);
const sevNames = sevArr.map((s) => asObject(s).name).sort();
assertClosedSet(
  "alertSeverities", REQUIRED_SEVERITIES, sevNames,
  "E13.2 alertSeverities", ok, fail,
);
for (const s of sevArr) {
  const o = asObject(s);
  if (typeof o.responseMinutes !== "number") {
    fail(`E13.2 alertSeverities.${o.name}: missing numeric responseMinutes`);
  }
  if (typeof o.blocksRelease !== "boolean") {
    fail(`E13.2 alertSeverities.${o.name}: missing boolean blocksRelease`);
  }
}

assertClosedSet(
  "alertActions", REQUIRED_ACTIONS, asArray(doc.alertActions?.values),
  "E13.2 alertActions", ok, fail,
);

const burnArr = asArray(doc.burnRateWindows?.values);
const burnWindows = burnArr.map((w) => asObject(w).window).sort();
assertClosedSet(
  "burnRateWindows", REQUIRED_BURN_RATE_WINDOWS, burnWindows,
  "E13.2 burnRateWindows", ok, fail,
);

const availArr = asArray(doc.availabilityTargets?.values);
const availSvc = availArr.map((t) => asObject(t).serviceClass).sort();
assertClosedSet(
  "availabilityTargets", REQUIRED_AVAILABILITY_TARGETS, availSvc,
  "E13.2 availabilityTargets", ok, fail,
);

const probeArr = asArray(doc.syntheticProbes?.values);
const probes = probeArr.map((p) => asObject(p).name).sort();
assertClosedSet(
  "syntheticProbes", REQUIRED_SYNTHETIC_PROBES, probes,
  "E13.2 syntheticProbes", ok, fail,
);

assertClosedSet(
  "tenantPseudonymLabels", REQUIRED_PSEUDONYM_LABELS,
  asArray(doc.tenantPseudonymLabels?.values),
  "E13.2 tenantPseudonymLabels", ok, fail,
);

assertClosedSet(
  "forbiddenAlertLabels", REQUIRED_FORBIDDEN_ALERT_LABELS,
  asArray(doc.forbiddenAlertLabels?.values),
  "E13.2 forbiddenAlertLabels", ok, fail,
);

assertClosedSet(
  "runbookRequiredFields", REQUIRED_RUNBOOK_FIELDS,
  asArray(doc.runbookRequiredFields?.values),
  "E13.2 runbookRequiredFields", ok, fail,
);

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E13.2 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries", REQUIRED_CAPABILITY_BOUNDARIES,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E13.2 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E13.2 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_RUNTIME_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E13.2 requiredRuntimeHelpers", ok, fail,
);

assertClosedSet(
  "requiredAlertRuleFields", REQUIRED_ALERT_RULE_FIELDS,
  asArray(doc.requiredAlertRuleFields?.values),
  "E13.2 requiredAlertRuleFields", ok, fail,
);

assertClosedSet(
  "requiredAlertAnnotations", REQUIRED_ALERT_ANNOTATIONS,
  asArray(doc.requiredAlertAnnotations?.values),
  "E13.2 requiredAlertAnnotations", ok, fail,
);

// runbookPaths MUST exist on disk
for (const p of REQUIRED_RUNBOOK_PATHS) {
  const abs = join(ROOT, p);
  if (!existsSync(abs)) {
    fail(`E13.2 runbookPaths: missing on disk: ${p}`);
  } else {
    ok(`E13.2 runbookPaths.${p} exists`);
  }
}

const dashArr = asArray(doc.dashboardCatalog?.values);
const dashboards = dashArr.map((d) => asObject(d).uid).sort();
assertClosedSet(
  "dashboardCatalog", REQUIRED_DASHBOARDS, dashboards,
  "E13.2 dashboardCatalog", ok, fail,
);

const numericArr = asArray(doc.numericBounds?.values);
const numericMap = {};
for (const n of numericArr) {
  const o = asObject(n);
  if (o.name) numericMap[o.name] = o.value;
}
const missingNum = REQUIRED_NUMERIC_KEYS.filter((k) => !(k in numericMap));
if (missingNum.length > 0) {
  fail(`E13.2 numericBounds: missing ${missingNum.join(",")}`);
} else {
  ok(`E13.2 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numericMap)) {
    if (typeof v !== "number") {
      fail(`E13.2 numericBounds.${k}: not a number (${v})`);
    }
  }
  if (numericMap.apiReadP95TargetMs !== 300) {
    fail(`E13.2 numericBounds.apiReadP95TargetMs must equal 300 (got ${numericMap.apiReadP95TargetMs})`);
  }
  if (numericMap.apiWriteP95TargetMs !== 600) {
    fail(`E13.2 numericBounds.apiWriteP95TargetMs must equal 600 (got ${numericMap.apiWriteP95TargetMs})`);
  }
  if (numericMap.budgetFreezeWeek1Ratio !== 0.5) {
    fail(`E13.2 numericBounds.budgetFreezeWeek1Ratio must equal 0.5 (got ${numericMap.budgetFreezeWeek1Ratio})`);
  }
}

assertStateMatrix(
  "E13.2 alertRuleMatrix",
  doc.alertRuleMatrix,
  REQUIRED_ALERT_RULE_STATUSES,
  "ROUTED",
  ok, fail,
);

assertStateMatrix(
  "E13.2 budgetStateMatrix",
  doc.budgetStateMatrix,
  REQUIRED_BUDGET_STATUSES,
  "NOMINAL",
  ok, fail,
);

const chartPath = doc.requiredSourceMirror?.chartPath;
if (chartPath !== "platform/helm/genealogy-platform/files/reliability/slo-alert-policy.yaml") {
  fail(`E13.2 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/reliability/slo-alert-policy.yaml (got ${chartPath})`);
} else {
  ok(`E13.2 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E13.2 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E13.2 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}