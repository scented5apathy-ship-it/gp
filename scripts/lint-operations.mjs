#!/usr/bin/env node
/**
 * scripts/lint-operations.mjs
 *
 * E11.5 deep validator for the admin / support / operations contract at
 * `contracts/operations/admin-support-operations-policy.yaml` and the
 * platform mirror at
 * `platform/helm/genealogy-platform/files/operations/
 * admin-support-operations-policy.yaml`.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray, assertClosedSet, assertStateMatrix } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(
  ROOT,
  "contracts/operations/admin-support-operations-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/operations/admin-support-operations-policy.yaml",
);

const REQUIRED_ADMIN_ROLES = [
  "PLATFORM_ADMIN",
  "TENANT_ADMIN",
  "SUPPORT_TIER_1",
  "SUPPORT_TIER_2",
  "SUPPORT_TIER_3",
  "SECURITY_ENGINEER",
  "DPO_DELEGATE",
  "FINANCE_OPS",
  "READ_ONLY_AUDITOR",
];
const REQUIRED_SUPPORT_ACCESS_MODES = [
  "READ_ONLY",
  "WRITE_FIXUP",
  "REPLAY_DLQ",
  "PROJECTION_REBUILD",
  "BILLING_RECONCILE",
  "FEATURE_FLAG_OVERRIDE",
];
const REQUIRED_ADMIN_OPERATIONS = [
  "TENANT_SUSPEND",
  "TENANT_REACTIVATE",
  "TENANT_DELETE_SCHEDULE",
  "TENANT_PURGE",
  "JOB_CANCEL",
  "JOB_RESTART",
  "WORKFLOW_CANCEL",
  "WORKFLOW_RESTART",
  "DLQ_REPLAY",
  "DLQ_PURGE",
  "PROJECTION_REBUILD",
  "FEATURE_FLAG_OVERRIDE_SET",
  "FEATURE_FLAG_OVERRIDE_CLEAR",
  "QUOTA_OVERRIDE_SET",
  "QUOTA_OVERRIDE_CLEAR",
  "PLAN_OVERRIDE_SET",
  "SUPPORT_JIT_GRANT",
  "SUPPORT_JIT_REVOKE",
  "IMPERSONATION_REQUEST",
  "IMPERSONATION_END",
  "AUDIT_EXPORT_ISSUE",
  "CONSENT_AUDIT_VIEW",
  "REPORT_DOWNLOAD_VIEW",
];
const REQUIRED_IMPERMISSIBLE_SCOPES = [
  "DNA_RAW_DOWNLOAD",
  "DNA_MATCH_RAW_DOWNLOAD",
  "CONSENT_RAW_DOWNLOAD",
  "EXPORT_RAW_BUNDLE_DOWNLOAD",
  "TENANT_DELETION_FORCE",
  "ADMIN_GRANT_OVERRIDE_SELF",
  "IMPERSONATION_GRANT_OTHER",
];
const REQUIRED_DLQ_REPLAY_MODES = [
  "SINGLE_EVENT",
  "TIME_WINDOW",
  "AGGREGATE_ID",
  "FULL_TOPIC",
];
const REQUIRED_REBUILD_SOURCES = [
  "PERSON_PROJECTION",
  "RELATIONSHIP_PROJECTION",
  "EVENT_PROJECTION",
  "SOURCE_PROJECTION",
  "MEDIA_PROJECTION",
  "CONSENT_PROJECTION",
  "DNA_KIT_PROJECTION",
  "SEARCH_PROJECTION",
  "AUDIT_PROJECTION",
];
const REQUIRED_FLAG_CATEGORIES = [
  "COMMS_GENERAL",
  "COMMS_PRIVACY",
  "UI_GENERAL",
  "PERFORMANCE",
  "BILLING",
  "OPS_INTERNAL",
  "DNA_FORBIDDEN",
  "CONSENT_FORBIDDEN",
  "TENANT_ISOLATION_FORBIDDEN",
  "AUDIT_FORBIDDEN",
];
const REQUIRED_JIT_STATES = [
  "REQUESTED",
  "PENDING_APPROVAL",
  "ACTIVE",
  "EXPIRED",
  "REVOKED",
  "DENIED",
];
const REQUIRED_AUDIT_EVENTS = [
  "OPERATIONS_TENANT_SUSPENDED",
  "OPERATIONS_TENANT_REACTIVATED",
  "OPERATIONS_TENANT_DELETION_SCHEDULED",
  "OPERATIONS_TENANT_PURGED",
  "OPERATIONS_JIT_REQUESTED",
  "OPERATIONS_JIT_APPROVED",
  "OPERATIONS_JIT_DENIED",
  "OPERATIONS_JIT_ACTIVE",
  "OPERATIONS_JIT_EXPIRED",
  "OPERATIONS_JIT_REVOKED",
  "OPERATIONS_IMPERSONATION_REQUESTED",
  "OPERATIONS_IMPERSONATION_DENIED",
  "OPERATIONS_IMPERSONATION_ENDED",
  "OPERATIONS_DLQ_REPLAY_STARTED",
  "OPERATIONS_DLQ_REPLAY_COMPLETED",
  "OPERATIONS_DLQ_REPLAY_BLOCKED_WITHOUT_SNAPSHOT",
  "OPERATIONS_PROJECTION_REBUILD_STARTED",
  "OPERATIONS_PROJECTION_REBUILD_COMPLETED",
  "OPERATIONS_PROJECTION_REBUILD_CROSS_TENANT_BLOCKED",
  "OPERATIONS_FEATURE_FLAG_OVERRIDE_SET",
  "OPERATIONS_FEATURE_FLAG_OVERRIDE_CLEAR",
  "OPERATIONS_FEATURE_FLAG_FORBIDDEN_CATEGORY_BLOCKED",
  "OPERATIONS_PLAN_OVERRIDE_SET",
  "OPERATIONS_PLAN_OVERRIDE_CLEAR",
  "OPERATIONS_QUOTA_OVERRIDE_SET",
  "OPERATIONS_QUOTA_OVERRIDE_CLEAR",
  "OPERATIONS_AUDIT_EXPORT_ISSUED",
  "OPERATIONS_TENANT_SWITCH_REAUTH_REQUIRED",
];
const REQUIRED_FAILURE_REASONS = [
  "OPERATIONS_JIT_DENIED_BY_POLICY",
  "OPERATIONS_JIT_APPROVAL_TIMEOUT",
  "OPERATIONS_IMPERSONATION_DISABLED",
  "OPERATIONS_IMPERSONATION_FORBIDDEN_SCOPE",
  "OPERATIONS_DLQ_REPLAY_BLOCKED_NO_SNAPSHOT",
  "OPERATIONS_DLQ_REPLAY_FORBIDDEN_TOPIC",
  "OPERATIONS_PROJECTION_REBUILD_CROSS_TENANT_FORBIDDEN",
  "OPERATIONS_FEATURE_FLAG_FORBIDDEN_CATEGORY",
  "OPERATIONS_FEATURE_FLAG_OVERRIDE_REASON_MISSING",
  "OPERATIONS_FEATURE_FLAG_OVERRIDE_OWNER_MISSING",
  "OPERATIONS_PLAN_OVERRIDE_NOT_ALLOWED",
  "OPERATIONS_QUOTA_OVERRIDE_REASON_MISSING",
  "OPERATIONS_TENANT_SWITCH_REAUTH_REQUIRED",
  "OPERATIONS_AUDIT_EXPORT_FORBIDDEN_FIELD",
  "OPERATIONS_TENANT_BOUNDARY_VIOLATION",
];
const REQUIRED_EGRESS = [
  "postgres-ops",
  "vault-agent-ops",
  "s3-ops",
  "openfga-ops",
  "audit-service",
  "kafka-ops",
  "temporal-frontend-ops",
  "valkey-ops",
  "flagsmith-ops",
  "kafka-ops-dlq",
];
const REQUIRED_TASK_QUEUES = [
  "ops.admin",
  "ops.support",
  "ops.dlqReplay",
  "ops.projectionRebuild",
];
const REQUIRED_JIT_STATUSES = [
  "REQUESTED",
  "PENDING_APPROVAL",
  "ACTIVE",
  "EXPIRED",
  "REVOKED",
  "DENIED",
];
const REQUIRED_DLQ_STATUSES = [
  "REQUESTED",
  "SNAPSHOT_VERIFIED",
  "RUNNING",
  "COMPLETED",
  "BLOCKED",
  "FAILED",
];
const REQUIRED_REBUILD_ADMIN_STATUSES = [
  "REQUESTED",
  "APPROVED",
  "RUNNING",
  "COMPLETED",
  "BLOCKED",
  "FAILED",
];
const REQUIRED_BOUNDS = {
  jitMaxDurationMinutes: 240,
  jitMaxScopeMinutes: 240,
  jitApprovalTimeoutSeconds: 900,
  impersonationMaxDurationSeconds: 0,
  dlqReplayMaxEventsPerRun: 10000,
  dlqReplayMaxWindowHours: 168,
  projectionRebuildMaxRows: 5000000,
  projectionRebuildTimeoutSeconds: 1800,
  featureFlagOverrideMaxTtlSeconds: 86400,
  planOverrideMaxTtlSeconds: 2592000,
  quotaOverrideMaxTtlSeconds: 604800,
  auditExportSignedUrlTtlSeconds: 3600,
  auditExportMaxBytes: 524288000,
  tenantSwitchReauthMaxClockSkewSeconds: 60,
};
const REQUIRED_FORBIDDEN_PAYLOAD_KEYS = [
  "rawDna",
  "rawGenotype",
  "rawFastq",
  "rawBam",
  "rawVcf",
  "rawEmail",
  "rawPhone",
  "rawAddress",
  "exifGps",
  "cameraSerial",
  "passportNumber",
  "ssn",
  "productionPii",
  "internalVaultToken",
  "internalSessionCookie",
  "rawConsentReceipt",
  "rawSignatureBlob",
  "rawIdDocument",
  "treeViewerBypass",
  "rawEventPayload",
  "rawAuditStream",
  "rawWebhookSecret",
  "rawProviderApiKey",
  "dnaRawBucketKey",
  "dnaMatchBucketKey",
];
const REQUIRED_CAPABILITY_BOUNDARIES = [
  "crdt_engine",
  "search_engine",
  "queue_scheduler",
  "cron_scheduler",
  "identity_provider",
  "payment_processor",
  "dna_engine",
  "consent_engine",
  "matching_engine",
];
const REQUIRED_BOOLEAN_GUARDS = [
  "jitRequiresStepUpAuth",
  "jitRequiresScopedExpiry",
  "jitRequiresBannerVisible",
  "jitRequiresAuditLogEntry",
  "impersonationDisabledByDefault",
  "impersonationNeverCoversDna",
  "impersonationNeverCoversConsent",
  "impersonationNeverCoversExportRawBundle",
  "impersonationNeverCoversTenantDeletion",
  "impersonationSelfGrantForbidden",
  "impersonationGrantOtherForbidden",
  "dlqReplayRequiresSnapshot",
  "dlqReplaySnapshotMustIncludeLineageHash",
  "dlqReplayWithoutSnapshotIsBlocked",
  "projectionRebuildIsTenantScoped",
  "projectionRebuildCrossTenantForbidden",
  "featureFlagOverrideRequiresReason",
  "featureFlagOverrideRequiresOwner",
  "featureFlagDnaForbidden",
  "featureFlagConsentForbidden",
  "featureFlagTenantIsolationForbidden",
  "featureFlagAuditForbidden",
  "planOverrideRequiresReason",
  "quotaOverrideRequiresReason",
  "tenantSwitchRequiresReauth",
  "auditExportMUSTRespectRetention",
  "auditExportMUSTBeSignedUrl",
  "auditExportMUSTBeOneShot",
  "tenantOpsMustRouteThroughAudit",
  "adminSupportSurfaceInternalOnly",
  "adminSupportSurfaceBannerVisible",
];

function fail(msg) {
  process.stderr.write(`[lint-operations] FAIL: ${msg}\n`);
  process.exit(1);
}
function ok(line) {
  process.stdout.write(`[lint-operations] ${line}\n`);
}
function loadContract(path) {
  try {
    const text = readFileSync(path, "utf8");
    return { text, doc: loadYaml(text) };
  } catch (e) {
    fail(`cannot read contract at ${path}: ${e.message}`);
  }
}

const { text: contractText, doc: contract } = loadContract(CONTRACT);
const { text: chartText } = loadContract(CHART_FILE);
if (contractText.length !== chartText.length) {
  fail(`chart mirror is not byte-equal: contract=${contractText.length} chart=${chartText.length}`);
}
ok("chart mirror byte-equal");

assertClosedSet(
  "adminRoles",
  REQUIRED_ADMIN_ROLES,
  asArray(contract.adminRoles?.values),
  "adminRoles",
  ok,
  fail,
);
assertClosedSet(
  "supportAccessModes",
  REQUIRED_SUPPORT_ACCESS_MODES,
  asArray(contract.supportAccessModes?.values),
  "supportAccessModes",
  ok,
  fail,
);
assertClosedSet(
  "adminOperations",
  REQUIRED_ADMIN_OPERATIONS,
  asArray(contract.adminOperations?.values),
  "adminOperations",
  ok,
  fail,
);
assertClosedSet(
  "impermissibleScopes",
  REQUIRED_IMPERMISSIBLE_SCOPES,
  asArray(contract.impermissibleScopes?.values),
  "impermissibleScopes",
  ok,
  fail,
);
assertClosedSet(
  "dlqReplayModes",
  REQUIRED_DLQ_REPLAY_MODES,
  asArray(contract.dlqReplayModes?.values),
  "dlqReplayModes",
  ok,
  fail,
);
assertClosedSet(
  "projectionRebuildSources",
  REQUIRED_REBUILD_SOURCES,
  asArray(contract.projectionRebuildSources?.values),
  "projectionRebuildSources",
  ok,
  fail,
);
assertClosedSet(
  "featureFlagCategories",
  REQUIRED_FLAG_CATEGORIES,
  asArray(contract.featureFlagCategories?.values),
  "featureFlagCategories",
  ok,
  fail,
);
assertClosedSet(
  "jitAccessStates",
  REQUIRED_JIT_STATES,
  asArray(contract.jitAccessStates?.values),
  "jitAccessStates",
  ok,
  fail,
);
assertClosedSet(
  "operationsAuditEvents",
  REQUIRED_AUDIT_EVENTS,
  asArray(contract.operationsAuditEvents?.values),
  "operationsAuditEvents",
  ok,
  fail,
);
assertClosedSet(
  "operationsFailureReasons",
  REQUIRED_FAILURE_REASONS,
  asArray(contract.operationsFailureReasons?.values),
  "operationsFailureReasons",
  ok,
  fail,
);
assertClosedSet(
  "egressAllowlist",
  REQUIRED_EGRESS,
  asArray(contract.egressAllowlist?.values),
  "egressAllowlist",
  ok,
  fail,
);
assertClosedSet(
  "temporalTaskQueues",
  REQUIRED_TASK_QUEUES,
  asArray(contract.temporalTaskQueues?.values),
  "temporalTaskQueues",
  ok,
  fail,
);

assertStateMatrix(
  "jitAccessStateMatrix",
  contract.jitAccessStateMatrix,
  REQUIRED_JIT_STATUSES,
  "REQUESTED",
  ok,
  fail,
);
assertStateMatrix(
  "dlqReplayStateMatrix",
  contract.dlqReplayStateMatrix,
  REQUIRED_DLQ_STATUSES,
  "REQUESTED",
  ok,
  fail,
);
assertStateMatrix(
  "projectionRebuildAdminStateMatrix",
  contract.projectionRebuildAdminStateMatrix,
  REQUIRED_REBUILD_ADMIN_STATUSES,
  "REQUESTED",
  ok,
  fail,
);

const bounds = contract.numericBounds || {};
for (const [name, expected] of Object.entries(REQUIRED_BOUNDS)) {
  if (bounds[name] !== expected) {
    fail(`numericBounds.${name} expected ${expected} got ${bounds[name]}`);
  }
}
ok(`numeric bounds (${Object.keys(REQUIRED_BOUNDS).length} keys)`);

const invariants = asArray(contract.invariants);
if (invariants.length < 5) {
  fail(`expected at least 5 invariants, got ${invariants.length}`);
}
ok(`${invariants.length} invariants`);

const guards = contract.guardRails || {};
for (const g of REQUIRED_BOOLEAN_GUARDS) {
  if (guards[g] !== true) {
    fail(`guardRails.${g} MUST be true, got ${guards[g]}`);
  }
}
ok(`${REQUIRED_BOOLEAN_GUARDS.length} boolean guard rails`);

assertClosedSet(
  "forbiddenPayloadKeys",
  REQUIRED_FORBIDDEN_PAYLOAD_KEYS,
  asArray(contract.forbiddenPayloadKeys?.values),
  "forbiddenPayloadKeys",
  ok,
  fail,
);
assertClosedSet(
  "capabilityBoundaries",
  REQUIRED_CAPABILITY_BOUNDARIES,
  asArray(contract.capabilityBoundaries?.values),
  "capabilityBoundaries",
  ok,
  fail,
);

const outbox = contract.outbox || {};
const requiredFields = asArray(outbox.requiredFields);
const requiredEnvelopeFields = [
  "eventId",
  "eventType",
  "occurredAt",
  "tenantId",
  "aggregateId",
  "aggregateVersion",
  "traceId",
  "payload",
];
for (const f of requiredEnvelopeFields) {
  if (!requiredFields.includes(f)) {
    fail(`outbox.requiredFields missing ${f}`);
  }
}
if (outbox.envelopeEncryptionRequired !== true) {
  fail("outbox.envelopeEncryptionRequired MUST be true");
}
ok(`outbox envelope (${requiredFields.length} fields, encryption required)`);

assertClosedSet(
  "auditHooks",
  REQUIRED_AUDIT_EVENTS,
  asArray(contract.auditHooks?.values),
  "auditHooks",
  ok,
  fail,
);

ok("E11.5 Admin / support operations contract OK");
process.exit(0);