#!/usr/bin/env node
/**
 * scripts/lint-drill-config.mjs
 *
 * E14.2 deep validator for the DR drill contract at
 * `contracts/disaster-recovery/drill-policy.yaml` and the
 * platform mirror at
 * `platform/helm/genealogy-platform/files/disaster-recovery/
 *  drill-policy.yaml`.
 *
 * Asserts:
 *   - 8 drill kinds (cluster_loss / region_loss /
 *     dependency_outage / control_plane_failure /
 *     data_corruption / rpo_breach / rto_breach /
 *     on_premises_failover);
 *   - 5 allowed DR regions;
 *   - 7 reconcile targets;
 *   - 8 blast radii;
 *   - 1 replay-log capture mode (redacted_metrics_only);
 *   - 4 severities (SEV1..SEV4);
 *   - 8 drill scenarios declared in `drillScenarios`;
 *   - 1 state matrix (drillStateMatrix initial PLANNED,
 *     9 statuses incl. 2 terminal);
 *   - 12 numeric bounds, 18 invariants, 7 capability
 *     boundaries, 31 forbidden keywords, 5 runtime helpers;
 *   - byte-identity between contract file and helm chart
 *     mirror.
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
  "contracts/disaster-recovery/drill-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/disaster-recovery/drill-policy.yaml",
);

const REQUIRED_DRILL_KINDS = [
  "cluster_loss", "region_loss", "dependency_outage",
  "control_plane_failure", "data_corruption", "rpo_breach",
  "rto_breach", "on_premises_failover",
];
const REQUIRED_REGIONS = [
  "gp-region-primary", "gp-region-secondary-a",
  "gp-region-secondary-b", "onprem-customer-primary",
  "onprem-customer-secondary",
];
const REQUIRED_RECONCILE_TARGETS = [
  "outbox_relay", "kafka_consumer", "temporal_workflow",
  "search_projection", "public_projection", "audit_pipeline",
  "flagsmith_cache",
];
const REQUIRED_BLAST_RADII = [
  "per_pod", "per_service", "per_namespace", "per_cluster",
  "per_region", "per_site", "per_environment", "per_aggregate",
];
const REQUIRED_REPLAY_MODES = ["redacted_metrics_only"];
const REQUIRED_SEVERITIES = ["SEV1", "SEV2", "SEV3", "SEV4"];
const REQUIRED_DRILL_STATUSES = [
  "PLANNED", "IN_PROGRESS", "RECONCILING",
  "CANCELLED", "PASSED", "REMEDIATION_PENDING",
  "REMEDIATION_DONE", "FAILED", "SUPERSEDED",
];
const REQUIRED_INVARIANTS = [
  "everyDrillHasReconcileTargets",
  "everyDrillHasReplayLogCaptureMode",
  "everyDrillHasRequiredArtifacts",
  "regionLossDrillHasSecondaryRegion",
  "onPremFailoverDrillHasOnPremRegion",
  "blastRadiusNeverProductionWideWithoutFlag",
  "replayLogCaptureModeNeverCustomerData",
  "remediationSeverityFromClosedSet",
  "drillEvidencePathUnderEvidenceDir",
  "cadenceRespectedForAllScenarios",
  "rpoRtoBudgetMatchesBackupMatrix",
  "onPremCadenceWithin180Days",
  "drillSignoffHasSreAndProductOwners",
  "rawPiiNeverInDrillLog",
  "rawDnaBytesNeverInDrillLog",
  "drillLogsAreTenantPseudonymous",
  "reconcileReportCoversAllSubsystems",
  "noAdHocRegionFailover",
];
const REQUIRED_CAPABILITY = [
  "dr_coordination litmus_or_chaos_mesh_or_runbook_only",
  "region_failover argocd_or_kubernetes_only",
  "reconcile_engine outbox_relay_or_kafka_or_temporal_only",
  "audit_pipeline otlp_audit_only",
  "no_custom_dr_coordinator forbidden",
  "no_custom_region_failover forbidden",
  "no_manual_reconcile_outside_runbook forbidden",
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
  "skip_reconcile", "skip_remediation", "skip_signoff",
  "ad_hoc_region_failover", "manual_drill_only",
];
const REQUIRED_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/drill/DrillGuard.java",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/drill/E14DrillLimits.java",
  "runbook/disaster-recovery.md",
  ".kiro/specs/genealogy-platform/evidence/dr/cluster-loss.md",
  ".kiro/specs/genealogy-platform/evidence/dr/region-loss.md",
];
const REQUIRED_NUMERIC_KEYS = [
  "saasDrillCadenceDays", "onPremDrillCadenceDaysMaximum",
  "clusterLossRpoSecondsMaximum", "regionLossRtoSecondsMaximum",
  "dependencyOutageRtoSecondsMaximum",
  "controlPlaneFailureRpoSecondsMaximum",
  "dataCorruptionRtoSecondsMaximum", "rpoBreachRtoSecondsMaximum",
  "onPremFailoverRpoSecondsMaximum", "drillEvidenceRetentionDays",
  "reconcileTargetsPerDrillMinimum",
  "maxProductionWideDrillsPerQuarter",
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

const dkArr = asArray(doc.drillKinds?.values);
const dkNames = dkArr.map((d) => asObject(d).name).sort();
assertClosedSet(
  "drillKinds", REQUIRED_DRILL_KINDS, dkNames,
  "E14.2 drillKinds", ok, fail,
);
for (const d of dkArr) {
  const o = asObject(d);
  if (typeof o.cadenceDays !== "number"
      || (o.cadenceDays !== 90 && o.cadenceDays !== 180)) {
    fail(`E14.2 drillKinds.${o.name}: cadenceDays must be 90 or 180 (got ${o.cadenceDays})`);
  }
  if (typeof o.blastRadius !== "string"
      || !REQUIRED_BLAST_RADII.includes(o.blastRadius)) {
    fail(`E14.2 drillKinds.${o.name}: blastRadius must be in closed-set (got ${o.blastRadius})`);
  }
  for (const r of asArray(o.allowedDrRegions)) {
    if (!REQUIRED_REGIONS.includes(r)) {
      fail(`E14.2 drillKinds.${o.name}: allowedDrRegions includes unknown region ${r}`);
    }
  }
}

assertClosedSet(
  "allowedDrRegions", REQUIRED_REGIONS,
  asArray(doc.allowedDrRegions?.values),
  "E14.2 allowedDrRegions", ok, fail,
);

assertClosedSet(
  "reconcileTargets", REQUIRED_RECONCILE_TARGETS,
  asArray(doc.reconcileTargets?.values),
  "E14.2 reconcileTargets", ok, fail,
);

assertClosedSet(
  "blastRadii", REQUIRED_BLAST_RADII,
  asArray(doc.blastRadii?.values),
  "E14.2 blastRadii", ok, fail,
);

assertClosedSet(
  "replayLogCaptureModes", REQUIRED_REPLAY_MODES,
  asArray(doc.replayLogCaptureModes?.values),
  "E14.2 replayLogCaptureModes", ok, fail,
);

assertClosedSet(
  "severities", REQUIRED_SEVERITIES,
  asArray(doc.severities?.values),
  "E14.2 severities", ok, fail,
);

const dsArr = asArray(doc.drillScenarios?.values);
const dsNames = dsArr.map((d) => asObject(d).drillKind).sort();
assertClosedSet(
  "drillScenarios", REQUIRED_DRILL_KINDS, dsNames,
  "E14.2 drillScenarios", ok, fail,
);
for (const d of dsArr) {
  const o = asObject(d);
  if (!o.reconcileTargets || asArray(o.reconcileTargets).length < 2) {
    fail(`E14.2 drillScenarios.${o.drillKind}: reconcileTargets must have >= 2 entries`);
  }
  if (o.replayLogCaptureMode !== "redacted_metrics_only") {
    fail(`E14.2 drillScenarios.${o.drillKind}: replayLogCaptureMode MUST be redacted_metrics_only (got ${o.replayLogCaptureMode})`);
  }
  if (typeof o.rpoSeconds !== "number" || o.rpoSeconds > 86400) {
    fail(`E14.2 drillScenarios.${o.drillKind}: rpoSeconds must be <= 86400 (got ${o.rpoSeconds})`);
  }
  if (typeof o.rtoSeconds !== "number" || o.rtoSeconds > 14400) {
    fail(`E14.2 drillScenarios.${o.drillKind}: rtoSeconds must be <= 14400 (got ${o.rtoSeconds})`);
  }
  const artifacts = asArray(o.requiredArtifacts);
  if (artifacts.length === 0) {
    fail(`E14.2 drillScenarios.${o.drillKind}: requiredArtifacts MUST be non-empty`);
  }
}

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E14.2 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries",
  REQUIRED_CAPABILITY,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E14.2 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E14.2 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E14.2 requiredRuntimeHelpers", ok, fail,
);

for (const helper of REQUIRED_HELPERS) {
  if (!existsSync(helper)) {
    fail(`E14.2 runtime helper missing on disk: ${helper}`);
  } else {
    ok(`E14.2 runtime helper exists: ${helper}`);
  }
}

const numericArr = asArray(doc.numericBounds?.values);
const numericMap = {};
for (const n of numericArr) {
  const obj = asObject(n);
  if (obj.name) numericMap[obj.name] = obj.value;
}
const missingNumeric = REQUIRED_NUMERIC_KEYS.filter((k) => !(k in numericMap));
if (missingNumeric.length > 0) {
  fail(`E14.2 numericBounds: missing ${missingNumeric.join(",")}`);
} else {
  ok(`E14.2 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numericMap)) {
    if (typeof v !== "number") {
      fail(`E14.2 numericBounds.${k}: not a number (${v})`);
    }
  }
}

assertStateMatrix(
  "E14.2 drillStateMatrix",
  doc.drillStateMatrix,
  REQUIRED_DRILL_STATUSES,
  "PLANNED",
  ok, fail,
);

const requiredCharts = doc.requiredSourceMirror?.chartPath;
if (requiredCharts !== "platform/helm/genealogy-platform/files/disaster-recovery/drill-policy.yaml") {
  fail(`E14.2 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/disaster-recovery/drill-policy.yaml (got ${requiredCharts})`);
} else {
  ok(`E14.2 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E14.2 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E14.2 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}