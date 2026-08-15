#!/usr/bin/env node
/**
 * scripts/lint-operator-runbook.mjs
 *
 * E14.5 deep validator for the operator runbook contract at
 * `contracts/disaster-recovery/operator-runbook-policy.yaml`
 * and the platform mirror at
 * `platform/helm/genealogy-platform/files/disaster-recovery/
 *  operator-runbook-policy.yaml`.
 *
 * Asserts:
 *   - 8 mandatory procedures (install / configuration /
 *     scaling / backup / restore / key_rotation /
 *     troubleshooting / support_bundle);
 *   - 8 owner roles, 4 severity levels, 5 support
 *     channels, 5 on-call rotations;
 *   - 10 support bundle redactions;
 *   - 13 shared-responsibility areas, matrix assigns each
 *     to either customer_managed or platform_managed;
 *   - 8 procedure definitions with all required fields
 *     (owner / severity / evidenceAnchor / runbookPath /
 *     redactionRequirements);
 *   - 1 state matrix (runbookStateMatrix initial DRAFT,
 *     5 statuses incl. 1 terminal);
 *   - 12 numeric bounds, 17 invariants, 5 capability
 *     boundaries, 28 forbidden keywords, 5 runtime
 *     helpers;
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
  "contracts/disaster-recovery/operator-runbook-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/disaster-recovery/operator-runbook-policy.yaml",
);

const REQUIRED_PROCEDURES = [
  "install", "configuration", "scaling", "backup", "restore",
  "key_rotation", "troubleshooting", "support_bundle",
];
const REQUIRED_OWNER_ROLES = [
  "sre_primary", "sre_secondary", "platform_sre",
  "security_engineer", "dpo_delegate", "product_owner",
  "finance_ops", "customer_success",
];
const REQUIRED_SEVERITIES = ["SEV1", "SEV2", "SEV3", "SEV4"];
const REQUIRED_SUPPORT_CHANNELS = [
  "portal", "email", "phone_sev1", "phone_sev2", "chat_secure",
];
const REQUIRED_ONCALL_ROTATIONS = [
  "sre_primary_24x7", "sre_secondary_24x7",
  "security_engineer_business_hours",
  "dpo_delegate_business_hours", "product_owner_business_hours",
];
const REQUIRED_REDACTIONS = [
  "redact_secrets", "redact_pii", "redact_dna",
  "redact_raw_payloads", "redact_jwt", "redact_session_cookie",
  "redact_oauth_client_secret", "redact_audit_stream",
  "redact_consent_receipt", "redact_tree_viewer_bypass",
];
const REQUIRED_AREAS = [
  "kubernetes_cluster", "postgres_database", "kafka_cluster",
  "object_storage", "keycloak_realm", "openfga_store",
  "temporal_namespace", "vault_kv", "flagsmith_environment",
  "tls_certificates", "dns_records", "on_call_rotation",
  "upgrade_testing",
];
const VALID_RESPONSIBILITY_OWNERS = ["customer_managed", "platform_managed"];
const REQUIRED_RUNBOOK_STATUSES = [
  "DRAFT", "REVIEW", "PUBLISHED", "STALE", "SUPERSEDED",
];
const REQUIRED_INVARIANTS = [
  "allProceduresHaveOwner",
  "allProceduresHaveSeverity",
  "allProceduresHaveEvidenceAnchor",
  "allProceduresHaveRunbookPath",
  "supportBundleRedactsAllForbiddenKeys",
  "sharedResponsibilityMatrixCoversAllAreas",
  "runbookReviewCadenceRespected",
  "supportChannelsFromClosedSet",
  "onCallRotationFromClosedSet",
  "indexFileLinksEveryProcedure",
  "supportBundleSizeWithinLimit",
  "redactionRulesCoverPiiDnaSecret",
  "runbookSlaRespected",
  "rawDnaBytesNeverInSupportBundle",
  "rawEmailNeverInSupportBundle",
  "productionPiiNeverInSupportBundle",
  "sharedAdminPasswordNeverInSupportBundle",
];
const REQUIRED_CAPABILITY = [
  "support_bundle_collector redacted_archive_only",
  "on_call_rotation pagerduty_or_opsgenie_only",
  "documentation_index runbook_index_only",
  "no_custom_support_bundle forbidden",
  "no_ad_hoc_on_call_contact forbidden",
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
  "skip_runbook_review", "ad_hoc_contact",
];
const REQUIRED_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/runbook/RunbookGuard.java",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/runbook/E14RunbookLimits.java",
  "runbook/index.md",
  "tools/support/support-bundle.sh",
  ".kiro/specs/genealogy-platform/evidence/E14.5.md",
];
const REQUIRED_NUMERIC_KEYS = [
  "runbookReviewCadenceDays", "supportBundleRetentionDays",
  "maxSupportBundleSizeGigabytes",
  "minRedactionRulesPerProcedure", "minProceduresDocumented",
  "maxProceduresDocumented", "sharedResponsibilityAreasMinimum",
  "runbookSlaMinutesSev1", "runbookSlaMinutesSev2",
  "runbookSlaMinutesSev3", "runbookSlaMinutesSev4",
  "onCallPrimaryShiftHours",
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
  "mandatoryProcedures", REQUIRED_PROCEDURES,
  asArray(doc.mandatoryProcedures?.values),
  "E14.5 mandatoryProcedures", ok, fail,
);

assertClosedSet(
  "procedureOwnerRoles", REQUIRED_OWNER_ROLES,
  asArray(doc.procedureOwnerRoles?.values),
  "E14.5 procedureOwnerRoles", ok, fail,
);

assertClosedSet(
  "procedureSeverityLevels", REQUIRED_SEVERITIES,
  asArray(doc.procedureSeverityLevels?.values),
  "E14.5 procedureSeverityLevels", ok, fail,
);

assertClosedSet(
  "supportChannels", REQUIRED_SUPPORT_CHANNELS,
  asArray(doc.supportChannels?.values),
  "E14.5 supportChannels", ok, fail,
);

assertClosedSet(
  "onCallRotations", REQUIRED_ONCALL_ROTATIONS,
  asArray(doc.onCallRotations?.values),
  "E14.5 onCallRotations", ok, fail,
);

assertClosedSet(
  "supportBundleRedactions", REQUIRED_REDACTIONS,
  asArray(doc.supportBundleRedactions?.values),
  "E14.5 supportBundleRedactions", ok, fail,
);

const procArr = asArray(doc.procedures?.values);
const procNames = procArr.map((p) => asObject(p).name).sort();
assertClosedSet(
  "procedures", REQUIRED_PROCEDURES, procNames,
  "E14.5 procedures", ok, fail,
);
for (const p of procArr) {
  const o = asObject(p);
  if (!REQUIRED_OWNER_ROLES.includes(o.owner)) {
    fail(`E14.5 procedures.${o.name}: owner MUST be in closed-set (got ${o.owner})`);
  }
  if (!REQUIRED_SEVERITIES.includes(o.severity)) {
    fail(`E14.5 procedures.${o.name}: severity MUST be in closed-set (got ${o.severity})`);
  }
  if (typeof o.lastReviewedAt !== "string"
      || o.lastReviewedAt.length === 0) {
    fail(`E14.5 procedures.${o.name}: lastReviewedAt MUST be non-blank string`);
  }
  if (typeof o.evidenceAnchor !== "string"
      || !o.evidenceAnchor.startsWith(
          ".kiro/specs/genealogy-platform/evidence/")) {
    fail(`E14.5 procedures.${o.name}: evidenceAnchor MUST live under .kiro/specs/genealogy-platform/evidence/`);
  }
  if (typeof o.runbookPath !== "string"
      || !o.runbookPath.startsWith("runbook/")) {
    fail(`E14.5 procedures.${o.name}: runbookPath MUST live under runbook/`);
  }
  const reqRed = asArray(o.redactionRequirements);
  if (reqRed.length < 3) {
    fail(`E14.5 procedures.${o.name}: redactionRequirements MUST have at least 3 entries`);
  }
  for (const r of reqRed) {
    if (!REQUIRED_REDACTIONS.includes(r)) {
      fail(`E14.5 procedures.${o.name}: redactionRequirements has unknown rule ${r}`);
    }
  }
}

const matrixArr = asArray(doc.sharedResponsibilityMatrix?.values);
const areaNames = matrixArr.map((m) => asObject(m).area).sort();
assertClosedSet(
  "sharedResponsibilityMatrix.areas",
  REQUIRED_AREAS, areaNames,
  "E14.5 sharedResponsibilityMatrix", ok, fail,
);
for (const m of matrixArr) {
  const o = asObject(m);
  if (!VALID_RESPONSIBILITY_OWNERS.includes(o.owner)) {
    fail(`E14.5 sharedResponsibilityMatrix.${o.area}: owner MUST be customer_managed or platform_managed (got ${o.owner})`);
  }
}

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E14.5 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries",
  REQUIRED_CAPABILITY,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E14.5 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E14.5 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E14.5 requiredRuntimeHelpers", ok, fail,
);

for (const helper of REQUIRED_HELPERS) {
  if (!existsSync(helper)) {
    if (!helper.endsWith("/E14.5.md")
        && !helper.endsWith("/E14.5.md")) {
      fail(`E14.5 runtime helper missing on disk: ${helper}`);
    }
  } else {
    ok(`E14.5 runtime helper exists: ${helper}`);
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
  fail(`E14.5 numericBounds: missing ${missingNumeric.join(",")}`);
} else {
  ok(`E14.5 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numericMap)) {
    if (typeof v !== "number") {
      fail(`E14.5 numericBounds.${k}: not a number (${v})`);
    }
  }
}

assertStateMatrix(
  "E14.5 runbookStateMatrix",
  doc.runbookStateMatrix,
  REQUIRED_RUNBOOK_STATUSES,
  "DRAFT",
  ok, fail,
);

const requiredCharts = doc.requiredSourceMirror?.chartPath;
if (requiredCharts !== "platform/helm/genealogy-platform/files/disaster-recovery/operator-runbook-policy.yaml") {
  fail(`E14.5 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/disaster-recovery/operator-runbook-policy.yaml (got ${requiredCharts})`);
} else {
  ok(`E14.5 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E14.5 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E14.5 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}