#!/usr/bin/env node
/**
 * scripts/lint-backup-matrix.mjs
 *
 * E14.1 deep validator for the backup matrix contract at
 * `contracts/disaster-recovery/backup-matrix-policy.yaml` and
 * the platform mirror at
 * `platform/helm/genealogy-platform/files/disaster-recovery/
 *  backup-matrix-policy.yaml`.
 *
 * Asserts:
 *   - 8 mandatory components (postgresql / kafka / object_storage
 *     / keycloak / openfga / temporal / vault / flagsmith);
 *   - 6 restore order ranks (1..6) covering every component;
 *   - 5 cadence kinds, 3 encryption methods, 8 key custody roles;
 *   - 8 RPO/RTO budget rows;
 *   - 1 state matrix (backupStateMatrix initial ENROLLED,
 *     8 statuses incl. 3 terminal);
 *   - 12 numeric bounds, 18 invariants, 10 capability boundaries,
 *     30 forbidden keywords, 5 runtime helpers;
 *   - byte-identity between contract file and helm chart mirror.
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
  "contracts/disaster-recovery/backup-matrix-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/disaster-recovery/backup-matrix-policy.yaml",
);

const REQUIRED_COMPONENTS = [
  "postgresql", "kafka", "object_storage", "keycloak",
  "openfga", "temporal", "vault", "flagsmith",
];
const REQUIRED_RESTORE_RANKS = [1, 2, 3, 4, 5, 6];
const REQUIRED_CADENCE_KINDS = [
  "continuous_archiving_wal",
  "continuous_cross_region_replication",
  "daily_snapshot",
  "weekly_snapshot",
  "monthly_snapshot",
];
const REQUIRED_ENCRYPTION_METHODS = [
  "aes_256_gcm",
  "cloud_kms_managed_envelope",
  "vault_transit_rewrap",
];
const REQUIRED_KEY_CUSTODY_ROLES = [
  "vault_kv_v2_backup_pg",
  "vault_kv_v2_backup_kafka",
  "vault_kv_v2_backup_s3",
  "vault_kv_v2_backup_keycloak",
  "vault_kv_v2_backup_openfga",
  "vault_kv_v2_backup_temporal",
  "vault_kv_v2_backup_self",
  "vault_kv_v2_backup_flagsmith",
];
const REQUIRED_BACKUP_STATUSES = [
  "ENROLLED", "SNAPSHOTTING", "RESTORING",
  "RESTORED_VERIFIED", "SUPERSEDED", "FAILED",
  "REVOKED", "EXPIRED",
];
const REQUIRED_INVARIANTS = [
  "everyComponentHasBackupRow",
  "encryptionAtRestMandatory",
  "restoreTestedMandatory",
  "retentionDailyMonthlyYearlyAllPresent",
  "restoreOrderMatchesDependencyGraph",
  "rpoRtoBudgetMatchesSloContract",
  "offsiteCopyRequiredForSaasComponents",
  "keyCustodyNeverCrossTenant",
  "restoreEvidencePathUnderEvidenceDir",
  "manualBackupCadenceForbidden",
  "plainTextBackupForbidden",
  "restoreDrillCadenceQuarterly",
  "keyRotationQuarterly",
  "backupArtifactIntegrityCheckDaily",
  "backupSignalsRecordedToTelemetry",
  "sharedAdminPasswordForbiddenInBackup",
  "rawDnaBytesNeverInBackupArtifact",
  "rawEmailNeverInBackupArtifact",
];
const REQUIRED_CAPABILITY = [
  "object_storage s3_or_minio_only",
  "secret_kv vault_only",
  "kms cloud_kms_or_vault_transit_only",
  "identity_backup keycloak_export_only",
  "authorization_backup openfga_export_only",
  "workflow_backup temporal_cli_only",
  "feature_flag_backup flagsmith_api_only",
  "no_custom_backup_engine forbidden",
  "no_inline_secret_in_backup forbidden",
  "no_cross_tenant_key_custody forbidden",
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
  "skip_encryption_at_rest", "skip_offsite_copy",
  "skip_restore_drill", "manual_backup_only",
];
const REQUIRED_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/backup/BackupGuard.java",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/backup/E14BackupLimits.java",
  "runbook/backup.md",
  ".kiro/specs/genealogy-platform/evidence/backup/postgresql-restore.md",
  ".kiro/specs/genealogy-platform/evidence/backup/vault-restore.md",
];
const REQUIRED_NUMERIC_KEYS = [
  "minimumRetentionDays", "retentionDailyMinimum",
  "retentionMonthlyMinimum", "retentionYearlyMinimum",
  "saasRpoSecondsMaximum", "saasRtoSecondsMaximum",
  "restoreDrillCadenceDays", "offsiteCopyMinimumRegions",
  "keyRotationDays", "encryptionMinimumKeyBits",
  "maxRestoreWindowMinutes",
  "backupArtifactIntegrityCheckPeriodHours",
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

const compArr = asArray(doc.components?.values);
const compNames = compArr.map((c) => asObject(c).name).sort();
assertClosedSet(
  "components", REQUIRED_COMPONENTS, compNames,
  "E14.1 components", ok, fail,
);
for (const c of compArr) {
  const o = asObject(c);
  if (typeof o.retentionDays !== "number" || o.retentionDays < 30) {
    fail(`E14.1 components.${o.name}: retentionDays must be >= 30 (got ${o.retentionDays})`);
  }
  if (typeof o.rpoSeconds !== "number" || o.rpoSeconds <= 0) {
    fail(`E14.1 components.${o.name}: rpoSeconds must be > 0 (got ${o.rpoSeconds})`);
  }
  if (typeof o.rtoSeconds !== "number" || o.rtoSeconds <= 0) {
    fail(`E14.1 components.${o.name}: rtoSeconds must be > 0 (got ${o.rtoSeconds})`);
  }
  if (o.restoreTested !== true) {
    fail(`E14.1 components.${o.name}: restoreTested MUST be true (got ${o.restoreTested})`);
  }
  if (typeof o.orderingRank !== "number"
      || o.orderingRank < 1 || o.orderingRank > 6) {
    fail(`E14.1 components.${o.name}: orderingRank must be 1..6 (got ${o.orderingRank})`);
  }
  if (typeof o.restoreEvidence !== "string"
      || !o.restoreEvidence.startsWith(".kiro/specs/genealogy-platform/evidence/backup/")) {
    fail(`E14.1 components.${o.name}: restoreEvidence MUST live under .kiro/specs/genealogy-platform/evidence/backup/ (got ${o.restoreEvidence})`);
  }
}

assertClosedSet(
  "restoreOrder", REQUIRED_RESTORE_RANKS,
  asArray(doc.restoreOrder?.values),
  "E14.1 restoreOrder", ok, fail,
);

assertClosedSet(
  "cadenceKinds", REQUIRED_CADENCE_KINDS,
  asArray(doc.cadenceKinds?.values),
  "E14.1 cadenceKinds", ok, fail,
);

assertClosedSet(
  "encryptionMethods", REQUIRED_ENCRYPTION_METHODS,
  asArray(doc.encryptionMethods?.values),
  "E14.1 encryptionMethods", ok, fail,
);

assertClosedSet(
  "keyCustodyRoles", REQUIRED_KEY_CUSTODY_ROLES,
  asArray(doc.keyCustodyRoles?.values),
  "E14.1 keyCustodyRoles", ok, fail,
);

const rpoArr = asArray(doc.rpoRtoBudget?.values);
const rpoComps = rpoArr.map((r) => asObject(r).component).sort();
assertClosedSet(
  "rpoRtoBudget.components", REQUIRED_COMPONENTS, rpoComps,
  "E14.1 rpoRtoBudget.components", ok, fail,
);
const compRpoMap = {};
for (const c of compArr) {
  const o = asObject(c);
  if (o.name) compRpoMap[o.name] = { rpo: o.rpoSeconds, rto: o.rtoSeconds };
}
for (const r of rpoArr) {
  const o = asObject(r);
  if (typeof o.rpoSeconds !== "number" || o.rpoSeconds <= 0) {
    fail(`E14.1 rpoRtoBudget.${o.component}: rpoSeconds must be > 0 (got ${o.rpoSeconds})`);
  }
  if (typeof o.rtoSeconds !== "number" || o.rtoSeconds > 14400) {
    fail(`E14.1 rpoRtoBudget.${o.component}: rtoSeconds must be <= 14400 (got ${o.rtoSeconds})`);
  }
  const expected = compRpoMap[o.component];
  if (expected) {
    if (expected.rpo !== o.rpoSeconds) {
      fail(`E14.1 rpoRtoBudget.${o.component}: rpoSeconds (${o.rpoSeconds}) must equal components row (${expected.rpo})`);
    }
    if (expected.rto !== o.rtoSeconds) {
      fail(`E14.1 rpoRtoBudget.${o.component}: rtoSeconds (${o.rtoSeconds}) must equal components row (${expected.rto})`);
    }
  }
}

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E14.1 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries",
  REQUIRED_CAPABILITY,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E14.1 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E14.1 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E14.1 requiredRuntimeHelpers", ok, fail,
);

for (const helper of REQUIRED_HELPERS) {
  if (!existsSync(helper)) {
    fail(`E14.1 runtime helper missing on disk: ${helper}`);
  } else {
    ok(`E14.1 runtime helper exists: ${helper}`);
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
  fail(`E14.1 numericBounds: missing ${missingNumeric.join(",")}`);
} else {
  ok(`E14.1 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numericMap)) {
    if (typeof v !== "number") {
      fail(`E14.1 numericBounds.${k}: not a number (${v})`);
    }
  }
}

assertStateMatrix(
  "E14.1 backupStateMatrix",
  doc.backupStateMatrix,
  REQUIRED_BACKUP_STATUSES,
  "ENROLLED",
  ok, fail,
);

const requiredCharts = doc.requiredSourceMirror?.chartPath;
if (requiredCharts !== "platform/helm/genealogy-platform/files/disaster-recovery/backup-matrix-policy.yaml") {
  fail(`E14.1 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/disaster-recovery/backup-matrix-policy.yaml (got ${requiredCharts})`);
} else {
  ok(`E14.1 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E14.1 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E14.1 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}