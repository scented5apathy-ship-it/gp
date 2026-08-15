#!/usr/bin/env node
/**
 * scripts/lint-recovery-rollback-config.mjs
 *
 * E14.4 deep validator for the upgrade / rollback contract
 * at
 * `contracts/disaster-recovery/recovery-rollback-policy.yaml`
 * and the platform mirror at
 * `platform/helm/genealogy-platform/files/disaster-recovery/
 *  recovery-rollback-policy.yaml`.
 *
 * Asserts:
 *   - 5 supported previous versions;
 *   - 6 Flyway migration kinds;
 *   - 4 API / event compatibility kinds;
 *   - 4 Argo Rollouts abort rule kinds;
 *   - 6 pre-upgrade checks + 7 post-upgrade checks;
 *   - 6 rollback constraints;
 *   - 1 state matrix (upgradeStateMatrix initial PLANNED,
 *     10 statuses incl. 3 terminal);
 *   - 11 numeric bounds, 18 invariants, 7 capability
 *     boundaries, 31 forbidden keywords, 5 runtime helpers;
 *   - byte-identity between contract file and helm chart
 *     mirror.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration
 * error.
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
  "contracts/disaster-recovery/recovery-rollback-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/disaster-recovery/recovery-rollback-policy.yaml",
);

const REQUIRED_PREVIOUS_VERSIONS = [
  "2025.10", "2025.12", "2026.02", "2026.04", "2026.06",
];
const REQUIRED_MIGRATION_KINDS = [
  "expand_column_add", "expand_table_create",
  "expand_index_create", "expand_backfill", "expand_switch",
  "deprecated_drop_followup",
];
const REQUIRED_COMPAT_KINDS = [
  "BACKWARD", "BACKWARD_TRANSITIVE", "FULL",
  "NONE_BREAKING_SUPERSEDED_BY_ADR",
];
const REQUIRED_ABORT_KINDS = [
  "five_xx_ratio_exceeded", "p95_latency_regression",
  "error_rate_spike", "privacy_finding_detected",
];
const REQUIRED_PRE_CHECKS = [
  "flyway_no_destructive", "schema_compatibility_checked",
  "event_compatibility_checked", "rollback_plan_attached",
  "feature_flag_kill_switch_attached", "preflight_passed",
];
const REQUIRED_POST_CHECKS = [
  "flyway_migration_applied", "red_metrics_under_budget",
  "workflow_completion_under_budget", "search_projection_fresh",
  "audit_pipeline_receiving", "reconcile_targets_stable",
  "signoff_attached",
];
const REQUIRED_ROLLBACK_CONSTRAINTS = [
  "maxOneRollbackPerTenant", "noCrossTenantRollback",
  "rollbackToSupportedPreviousVersionOnly",
  "rollbackRequiresApprovalTicket",
  "rollbackEvacuatesActiveMutations",
  "rollbackRunsFeatureFlagKillSwitch",
];
const REQUIRED_UPGRADE_STATUSES = [
  "PLANNED", "PRECHECK_RUNNING", "APPLYING",
  "POSTCHECK_RUNNING", "CANCELLED", "SUCCEEDED",
  "FAILED", "ROLLING_BACK", "ROLLED_BACK", "SUPERSEDED",
];
const REQUIRED_INVARIANTS = [
  "flywayExpandContractMandatory",
  "backwardCompatibilityMandatory",
  "argoRolloutsAbortWiredToE13_4",
  "preUpgradeChecksAllPresent",
  "postUpgradeChecksAllPresent",
  "rollbackToSupportedPreviousVersion",
  "rollbackRequiresApprovalTicket",
  "maxOneRollbackPerTenant",
  "noCrossTenantRollback",
  "featureFlagKillSwitchAttached",
  "upgradeTestCoverageRespected",
  "noDestructiveMigrationInReleaseWindow",
  "schemaCompatibilityChecked",
  "eventCompatibilityChecked",
  "reconcileTargetsStableBeforeSuccess",
  "auditPipelineReceivingBeforeSuccess",
  "signoffFromSreAndProductOwners",
  "rawDnaBytesNeverInUpgradeBundle",
];
const REQUIRED_CAPABILITY = [
  "progressive_delivery argo_rollouts_only",
  "migration_engine flyway_only",
  "schema_compatibility apicurio_only",
  "feature_flag_kill_switch flagsmith_or_openfeature_only",
  "no_custom_progressive_delivery forbidden",
  "no_custom_migration_engine forbidden",
  "no_custom_kill_switch forbidden",
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
  "skip_precheck", "skip_postcheck", "skip_rollback_plan",
  "skip_feature_flag_kill_switch", "destructive_migration",
  "breaking_change_without_adr",
];
const REQUIRED_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/recovery/RecoveryGuard.java",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/recovery/E14RecoveryLimits.java",
  "runbook/upgrade-rollback.md",
  "tools/upgrade/simulate-upgrade.sh",
  "platform/argocd/canary/abort-rules.yaml",
];
const REQUIRED_NUMERIC_KEYS = [
  "maxSupportedPreviousVersions", "minSupportedPreviousVersions",
  "precheckTimeoutSeconds", "applyTimeoutSeconds",
  "postcheckTimeoutSeconds", "rollbackTimeoutSeconds",
  "maxActiveRollbacksPerTenant",
  "upgradeTestCoverageRequiredVersions",
  "maxBackwardsCompatWindowReleases",
  "minFeatureFlagKillSwitchLatencySeconds",
  "destructiveMigrationWindowReleases",
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

assertClosedSet(
  "supportedPreviousVersions", REQUIRED_PREVIOUS_VERSIONS,
  asArray(doc.supportedPreviousVersions?.values),
  "E14.4 supportedPreviousVersions", ok, fail,
);

assertClosedSet(
  "migrationKinds", REQUIRED_MIGRATION_KINDS,
  asArray(doc.migrationKinds?.values),
  "E14.4 migrationKinds", ok, fail,
);

assertClosedSet(
  "compatibilityKinds", REQUIRED_COMPAT_KINDS,
  asArray(doc.compatibilityKinds?.values),
  "E14.4 compatibilityKinds", ok, fail,
);

assertClosedSet(
  "abortRuleKinds", REQUIRED_ABORT_KINDS,
  asArray(doc.abortRuleKinds?.values),
  "E14.4 abortRuleKinds", ok, fail,
);

assertClosedSet(
  "preUpgradeChecks", REQUIRED_PRE_CHECKS,
  asArray(doc.preUpgradeChecks?.values),
  "E14.4 preUpgradeChecks", ok, fail,
);

assertClosedSet(
  "postUpgradeChecks", REQUIRED_POST_CHECKS,
  asArray(doc.postUpgradeChecks?.values),
  "E14.4 postUpgradeChecks", ok, fail,
);

assertClosedSet(
  "rollbackConstraints", REQUIRED_ROLLBACK_CONSTRAINTS,
  asArray(doc.rollbackConstraints?.values),
  "E14.4 rollbackConstraints", ok, fail,
);

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E14.4 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries",
  REQUIRED_CAPABILITY,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E14.4 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E14.4 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E14.4 requiredRuntimeHelpers", ok, fail,
);

for (const helper of REQUIRED_HELPERS) {
  if (!existsSync(helper)) {
    fail(`E14.4 runtime helper missing on disk: ${helper}`);
  } else {
    ok(`E14.4 runtime helper exists: ${helper}`);
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
  fail(`E14.4 numericBounds: missing ${missingNumeric.join(",")}`);
} else {
  ok(`E14.4 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numericMap)) {
    if (typeof v !== "number") {
      fail(`E14.4 numericBounds.${k}: not a number (${v})`);
    }
  }
}

assertStateMatrix(
  "E14.4 upgradeStateMatrix",
  doc.upgradeStateMatrix,
  REQUIRED_UPGRADE_STATUSES,
  "PLANNED",
  ok, fail,
);

const requiredCharts = doc.requiredSourceMirror?.chartPath;
if (requiredCharts !== "platform/helm/genealogy-platform/files/disaster-recovery/recovery-rollback-policy.yaml") {
  fail(`E14.4 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/disaster-recovery/recovery-rollback-policy.yaml (got ${requiredCharts})`);
} else {
  ok(`E14.4 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E14.4 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E14.4 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}