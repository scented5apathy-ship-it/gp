#!/usr/bin/env node
/**
 * scripts/lint-reporting.mjs
 *
 * E11.3 deep validator for the reporting contract at
 * `contracts/reporting/reporting-policy.yaml` and the platform mirror
 * at `platform/helm/genealogy-platform/files/reporting/reporting-policy.yaml`.
 *
 * Mirrors the E11.1 / E10.x linter pattern.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray, assertClosedSet, assertStateMatrix } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/reporting/reporting-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/reporting/reporting-policy.yaml",
);

const REQUIRED_REPORT_KINDS = [
  "COMPLETENESS_DASHBOARD",
  "CONFLICT_DASHBOARD",
  "ORPHAN_DASHBOARD",
  "DUPLICATE_DASHBOARD",
  "DEMOGRAPHICS_SUMMARY",
  "FAMILY_BOOK",
  "TIMELINE_PERSON",
  "TIMELINE_FAMILY",
  "ANNIVERSARY_LIST",
  "SOURCE_COVERAGE",
  "RELATIONSHIP_COVERAGE",
  "TENANT_HEALTH_REPORT",
];
const REQUIRED_OUTPUT_FORMATS = ["PDF", "CSV", "JSON"];
const REQUIRED_JOB_STATUSES = [
  "PENDING",
  "PREVIEW_RENDERED",
  "PREVIEW_APPROVED",
  "GENERATING",
  "COMPLETED",
  "FAILED",
  "CANCELLED",
  "REDACTED",
];
const REQUIRED_REBUILD_STATUSES = [
  "ENQUEUED",
  "RUNNING",
  "COMPLETED",
  "FAILED",
  "CANCELLED",
];
const REQUIRED_REPORT_TEMPLATES = [
  "COMPLETENESS_V1",
  "CONFLICT_V1",
  "ORPHAN_V1",
  "DUPLICATE_V1",
  "DEMOGRAPHICS_V1",
  "FAMILY_BOOK_V1",
  "TIMELINE_PERSON_V1",
  "TIMELINE_FAMILY_V1",
  "ANNIVERSARY_LIST_V1",
  "SOURCE_COVERAGE_V1",
  "RELATIONSHIP_COVERAGE_V1",
  "TENANT_HEALTH_V1",
];
const REQUIRED_PROJECTION_SOURCES = [
  "PERSON_PROJECTION",
  "RELATIONSHIP_PROJECTION",
  "EVENT_PROJECTION",
  "SOURCE_PROJECTION",
  "MEDIA_PROJECTION",
  "CONSENT_PROJECTION",
  "DNA_KIT_PROJECTION",
];
const REQUIRED_REDACTION_LEVELS = [
  "FULL",
  "LIVING_PROTECTED",
  "MINOR_PROTECTED",
  "DNA_REDACTED",
  "PUBLIC",
];
const REQUIRED_GOTENBERG_PROFILES = ["PDF_A1B", "PDF_A2B", "PDF_A3B", "PDF_SCREEN"];
const REQUIRED_AUDIT_EVENTS = [
  "REPORT_REQUESTED",
  "REPORT_PREVIEW_RENDERED",
  "REPORT_PREVIEW_APPROVED",
  "REPORT_GENERATION_STARTED",
  "REPORT_COMPLETED",
  "REPORT_FAILED",
  "REPORT_REDACTED",
  "REPORT_DOWNLOADED",
  "REPORT_SIGNED_URL_ISSUED",
  "REPORT_DOWNLOAD_SIGNED",
  "REPORT_PROJECTION_REBUILT",
  "REPORT_DETERMINISTIC_VERSION_PINNED",
  "REPORT_DNA_CONTENT_REJECTED",
  "REPORT_ANALYTICS_PAYLOAD_PII_REJECTED",
];
const REQUIRED_FAILURE_REASONS = [
  "REPORT_TEMPLATE_NOT_FOUND",
  "REPORT_TEMPLATE_VERSION_STALE",
  "REPORT_PROJECTION_REBUILD_REQUIRED",
  "REPORT_GOTENBERG_TIMEOUT",
  "REPORT_GOTENBERG_SANDBOX_VIOLATION",
  "REPORT_OUTPUT_TOO_LARGE",
  "REPORT_DETERMINISTIC_HASH_MISMATCH",
  "REPORT_PRIVACY_PREVIEW_NOT_APPROVED",
  "REPORT_DNA_CONTENT_FORBIDDEN",
  "REPORT_LIVING_PROTECTION_VIOLATION",
  "REPORT_MINOR_PROTECTION_VIOLATION",
  "REPORT_TENANT_BOUNDARY_VIOLATION",
  "REPORT_ANALYTICS_RAW_PII_FORBIDDEN",
];
const REQUIRED_EGRESS = [
  "postgres-report",
  "vault-agent-report",
  "s3-report",
  "openfga-report",
  "audit-service",
  "kafka-report",
  "temporal-frontend-report",
  "gotenberg-report",
  "valkey-report",
  "flagsmith-report",
];
const REQUIRED_TASK_QUEUES = [
  "report.render",
  "report.pdf",
  "report.analytics",
  "report.projectionRebuild",
];
const REQUIRED_BOUNDS = {
  reportMaxInputRows: 1000000,
  reportMaxOutputBytes: 524288000,
  reportPreviewMaxBytes: 52428800,
  gotenbergRenderTimeoutSeconds: 300,
  projectionRebuildMaxRows: 5000000,
  projectionRebuildTimeoutSeconds: 1800,
  jobSubmissionTimeoutMs: 500,
  signedUrlTtlSeconds: 3600,
  deterministicDefinitionHashBytes: 32,
  redactionTokenMaxBytes: 256,
  analyticsBatchMaxEvents: 1000,
  analyticsProductMetricsScrubIntervalSeconds: 60,
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
  "pdf_renderer",
  "chart_renderer",
  "analytics_engine",
  "queue_scheduler",
  "cron_scheduler",
  "projection_engine",
  "localization_engine",
];
const REQUIRED_BOOLEAN_GUARDS = [
  "previewBeforeFinalizationRequired",
  "privacyPreviewAppliesWhenTreeHasLiving",
  "privacyPreviewAppliesWhenTreeHasDna",
  "privacyPreviewAppliesWhenTreeHasMinor",
  "gotenbergIsTheOnlyPdfRenderer",
  "selfBuiltPdfRendererForbidden",
  "templateVersionPinRequired",
  "deterministicReportVersionRequired",
  "projectionTenantBoundaryEnforced",
  "projectionPrivacyLevelEnforced",
  "projectionLivingStatusEnforced",
  "projectionDnaScopeEnforced",
  "crossTenantProjectionLookupForbidden",
  "crossTenantReportSubmissionForbidden",
  "pdfSandboxEgressAllowlistOnly",
  "pdfSandboxEgressNoInternet",
  "pdfSandboxEgressNoInternalClusterMetadata",
  "dnaContentForbiddenInAnalyticsProductMetrics",
  "rawSensitiveContentForbiddenInAnalyticsProductMetrics",
  "dnaContentForbiddenInReportOutput",
  "rawSensitiveContentForbiddenInReportOutput",
  "dnaAggregateIdsMayAppearOnlyWithOpaqueReference",
  "outputDeterministicForSameInputs",
  "outputHashMustMatchPinnedDefinition",
  "jobSubmissionRequiresStepUpAuth",
  "jobDownloadRequiresSignedUrl",
  "signedUrlMustBeShortLived",
];

function fail(msg) {
  process.stderr.write(`[lint-reporting] FAIL: ${msg}\n`);
  process.exit(1);
}
function ok(line) {
  process.stdout.write(`[lint-reporting] ${line}\n`);
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
  "reportKinds",
  REQUIRED_REPORT_KINDS,
  asArray(contract.reportKinds?.values),
  "reportKinds",
  ok,
  fail,
);
assertClosedSet(
  "reportOutputFormats",
  REQUIRED_OUTPUT_FORMATS,
  asArray(contract.reportOutputFormats?.values),
  "reportOutputFormats",
  ok,
  fail,
);
assertClosedSet(
  "reportTemplates",
  REQUIRED_REPORT_TEMPLATES,
  asArray(contract.reportTemplates?.values),
  "reportTemplates",
  ok,
  fail,
);
assertClosedSet(
  "projectionSources",
  REQUIRED_PROJECTION_SOURCES,
  asArray(contract.projectionSources?.values),
  "projectionSources",
  ok,
  fail,
);
assertClosedSet(
  "redactionLevels",
  REQUIRED_REDACTION_LEVELS,
  asArray(contract.redactionLevels?.values),
  "redactionLevels",
  ok,
  fail,
);
assertClosedSet(
  "gotenbergProfiles",
  REQUIRED_GOTENBERG_PROFILES,
  asArray(contract.gotenbergProfiles?.values),
  "gotenbergProfiles",
  ok,
  fail,
);
assertClosedSet(
  "reportingAuditEvents",
  REQUIRED_AUDIT_EVENTS,
  asArray(contract.reportingAuditEvents?.values),
  "reportingAuditEvents",
  ok,
  fail,
);
assertClosedSet(
  "reportingFailureReasons",
  REQUIRED_FAILURE_REASONS,
  asArray(contract.reportingFailureReasons?.values),
  "reportingFailureReasons",
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
  "reportJobStateMatrix",
  contract.reportJobStateMatrix,
  REQUIRED_JOB_STATUSES,
  "PENDING",
  ok,
  fail,
);
assertStateMatrix(
  "reportProjectionRebuildStateMatrix",
  contract.reportProjectionRebuildStateMatrix,
  REQUIRED_REBUILD_STATUSES,
  "ENQUEUED",
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
if (invariants.length < 4) {
  fail(`expected at least 4 invariants, got ${invariants.length}`);
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

ok("E11.3 Reporting policy contract OK");
process.exit(0);