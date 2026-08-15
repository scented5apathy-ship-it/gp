#!/usr/bin/env node
/**
 * scripts/lint-notification-delivery.mjs
 *
 * E11.2 deep validator for the privacy-safe delivery contract at
 * `contracts/notifications/notification-privacy-delivery-policy.yaml`
 * and the platform mirror at
 * `platform/helm/genealogy-platform/files/notifications/
 * notification-privacy-delivery-policy.yaml`.
 *
 * Mirrors the E11.1 linter pattern with the same closed-set vocabularies
 * + state matrices + boolean guard rails + invariants.
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
  "contracts/notifications/notification-privacy-delivery-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/notifications/notification-privacy-delivery-policy.yaml",
);

const REQUIRED_SENSITIVE_EVENT_CATEGORIES = [
  "DNA_KIT_REGISTERED",
  "DNA_MATCH_DISCOVERED",
  "DNA_RELATIVE_DISCOVERY",
  "CONSENT_REVOKED",
  "CONSENT_LEGAL_HOLD",
  "GUARDIAN_ACTION",
  "SUPPORT_JIT_ACCESS",
  "DELETION_REQUESTED",
  "DELETION_COMPLETED",
  "PORTABILITY_EXPORT_READY",
  "IMPERSONATION_STARTED",
  "ADMIN_TENANT_SUSPENDED",
  "TENANT_DELETION_SCHEDULED",
];
const REQUIRED_GENERIC_TEMPLATES = [
  "GENERIC_DNA_EVENT",
  "GENERIC_CONSENT_EVENT",
  "GENERIC_GUARDIAN_EVENT",
  "GENERIC_SUPPORT_EVENT",
  "GENERIC_DELETION_EVENT",
  "GENERIC_PORTABILITY_EVENT",
  "GENERIC_IMPERSONATION_EVENT",
  "GENERIC_TENANT_EVENT",
];
const REQUIRED_REAUTHORIZATION_TRIGGERS = [
  "PRIVACY_LEVEL_CHANGED",
  "LIVING_STATUS_CHANGED",
  "CONSENT_REVOKED",
  "CONSENT_EXPIRED",
  "TENANT_VISIBILITY_CHANGED",
  "RELATIONSHIP_REMOVED",
  "SCOPE_NARROWED",
  "SESSION_PRIVILEGE_DEMOTED",
];
const REQUIRED_ABAC_LABELS = [
  "ALLOW",
  "ALLOW_WITH_GENERIC_TEXT",
  "DENY",
  "DENY_DUE_TO_DNA_SCOPE",
  "DENY_DUE_TO_LIVING_PROTECTION",
  "DENY_DUE_TO_CONSENT_REVOKED",
  "DENY_DUE_TO_SCOPE_NARROWED",
];
const REQUIRED_BOUNCE_REASONS = [
  "BOUNCE_HARD",
  "BOUNCE_SOFT",
  "COMPLAINT",
  "SUPPRESSED_BY_USER",
  "SUPPRESSED_BY_ADMIN",
  "SUPPRESSED_BY_COMPLIANCE",
  "INVALID_ADDRESS",
];
const REQUIRED_PROVIDER_HEADERS = [
  "List-Unsubscribe",
  "List-Unsubscribe-Post",
  "List-Id",
  "X-Entity-Ref-ID",
  "X-Genealogy-Tenant-Pseudo",
  "X-Genealogy-Delivery-Decision",
];
const REQUIRED_TENANT_BRANDING_FIELDS = [
  "tenantPseudoId",
  "tenantDisplayName",
  "preferenceCenterUrl",
  "contactEmail",
  "logoUrl",
  "colorScheme",
  "footerDisclosure",
  "locale",
];
const REQUIRED_AUDIT_EVENTS = [
  "PRIVACY_DELIVERY_ABAC_ALLOW",
  "PRIVACY_DELIVERY_ABAC_ALLOW_WITH_GENERIC_TEXT",
  "PRIVACY_DELIVERY_ABAC_DENY",
  "PRIVACY_DELIVERY_GENERIC_TEXT_APPLIED",
  "PRIVACY_DELIVERY_BRANDING_VALIDATED",
  "PRIVACY_DELIVERY_BRANDING_INVALID",
  "PRIVACY_DELIVERY_UNSUBSCRIBE_CLICKED",
  "PRIVACY_DELIVERY_BOUNCE_RECORDED",
  "PRIVACY_DELIVERY_COMPLAINT_RECORDED",
  "PRIVACY_DELIVERY_SUPPRESSION_LIST_HIT",
  "PRIVACY_DELIVERY_PROVIDER_SWITCH_BLOCKED_WITHOUT_ADR",
  "PRIVACY_DELIVERY_REAUTHORIZATION_TRIGGERED",
  "PRIVACY_DELIVERY_DNA_PAYLOAD_REDACTED",
  "PRIVACY_DELIVERY_CONSENT_PAYLOAD_REDACTED",
];
const REQUIRED_FAILURE_REASONS = [
  "PRIVACY_ABAC_DENY_DNA_SCOPE",
  "PRIVACY_ABAC_DENY_LIVING_PROTECTION",
  "PRIVACY_ABAC_DENY_CONSENT_REVOKED",
  "PRIVACY_ABAC_DENY_SCOPE_NARROWED",
  "PRIVACY_GENERIC_TEXT_MISSING",
  "PRIVACY_BRANDING_MISSING",
  "PRIVACY_BRANDING_INVALID",
  "PRIVACY_PROVIDER_SWITCH_BLOCKED",
  "PRIVACY_UNSUBSCRIBE_HEADER_MISSING",
  "PRIVACY_BODY_CONTAINS_FORBIDDEN_KEY",
  "PRIVACY_DEEP_LINK_TOKEN_EXPIRED",
  "PRIVACY_DEEP_LINK_TOKEN_REUSED",
  "PRIVACY_PII_MINIMIZATION_VIOLATED",
];
const REQUIRED_EGRESS = [
  "postgres-notify",
  "vault-agent-notify",
  "s3-notify",
  "openfga-notify",
  "audit-service",
  "kafka-notify",
  "temporal-frontend-notify",
  "valkey-notify",
  "flagsmith-notify",
];
const REQUIRED_TASK_QUEUES = [
  "notify.dispatch",
  "notify.provider",
  "notify.digest",
  "notify.inbox",
  "notify.privacy",
];
const REQUIRED_DELIVERY_STATUSES = [
  "ENQUEUED",
  "ABAC_RECHECKED_ALLOW",
  "ABAC_RECHECKED_ALLOW_WITH_GENERIC_TEXT",
  "ABAC_RECHECKED_DENY",
  "RENDERED",
  "DELIVERED",
  "UNSUBSCRIBED",
  "SUPPRESSED",
  "FAILED",
];
const REQUIRED_BOUNCE_STATUSES = [
  "ACTIVE",
  "SUPPRESSED_SOFT",
  "SUPPRESSED_HARD",
  "COMPLAINT_HOLD",
  "EXPIRED",
];
const REQUIRED_BOUNDS = {
  deepLinkTokenTtlSeconds: 3600,
  unsubscribeClickTokenTtlSeconds: 86400,
  bounceRetentionDays: 30,
  complaintRetentionDays: 365,
  suppressionRetentionDays: 365,
  brandingRefreshIntervalSeconds: 3600,
  abacRecheckTimeoutMs: 250,
  providerSendTimeoutSeconds: 30,
  genericTextMaxBytes: 4096,
  deepLinkTokenMaxBytes: 512,
  abacDecisionCacheTtlSeconds: 60,
  privacyPayloadRedactionTimeoutMs: 100,
  outboundBodyMaxBytes: 65536,
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
  "rawGuardianReason",
  "rawSupportReason",
  "rawDeletionReason",
];
const REQUIRED_CAPABILITY_BOUNDARIES = [
  "email_delivery_engine",
  "smtp_server",
  "abac_engine",
  "consent_engine",
  "spam_scanner",
  "bounce_processor",
  "template_compiler",
  "token_mint_service",
];
const REQUIRED_BOOLEAN_GUARDS = [
  "abacRecheckAtRender",
  "abacRecheckAtDelivery",
  "genericTextRequiredForSensitiveEvents",
  "rawDnaForbiddenInThirdPartyProviderPayload",
  "rawPersonPayloadForbiddenInThirdPartyProviderPayload",
  "deepLinkTokenMustBeSingleUse",
  "deepLinkTokenMustBeShortLived",
  "unsubscribeMustBeOneClick",
  "unsubscribeMustBeListUnsubscribeHeader",
  "unsubscribeMustBeListUnsubscribePostRfc8058",
  "preferenceCentreUrlMustBeHttps",
  "preferenceCentreUrlMustBeTenantBranded",
  "bounceListIsTenantScoped",
  "suppressionListIsTenantScoped",
  "complaintListIsTenantScoped",
  "tenantBrandingIsTenantScoped",
  "globalBrandingForbidden",
  "providerSwitchRequiresAdr",
  "reAuthorizationTriggersMustBeEvaluated",
  "authorizationRecheckMustBeLogged",
  "abacDecisionMustBeAuditLogged",
  "dnaCategoryOpaquePayloadOnly",
  "consentCategoryOpaquePayloadOnly",
  "guardianCategoryOpaquePayloadOnly",
  "deletionCategoryOpaquePayloadOnly",
  "crossTenantBrandingForbidden",
  "crossTenantSuppressionForbidden",
  "outboundAttachmentForbidden",
  "outboundInlineImageRequiresSignedUrl",
  "localeFallbackMustBeTenantDefault",
  "localeFallbackMustBeApprovedLocale",
];

function fail(msg) {
  process.stderr.write(`[lint-notification-delivery] FAIL: ${msg}\n`);
  process.exit(1);
}
function ok(line) {
  process.stdout.write(`[lint-notification-delivery] ${line}\n`);
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
  "sensitiveEventCategories",
  REQUIRED_SENSITIVE_EVENT_CATEGORIES,
  asArray(contract.sensitiveEventCategories?.values),
  "sensitiveEventCategories",
  ok,
  fail,
);
assertClosedSet(
  "genericTextTemplates",
  REQUIRED_GENERIC_TEMPLATES,
  asArray(contract.genericTextTemplates?.values),
  "genericTextTemplates",
  ok,
  fail,
);
assertClosedSet(
  "reAuthorizationTriggers",
  REQUIRED_REAUTHORIZATION_TRIGGERS,
  asArray(contract.reAuthorizationTriggers?.values),
  "reAuthorizationTriggers",
  ok,
  fail,
);
assertClosedSet(
  "abacDecisionLabels",
  REQUIRED_ABAC_LABELS,
  asArray(contract.abacDecisionLabels?.values),
  "abacDecisionLabels",
  ok,
  fail,
);
assertClosedSet(
  "bounceReasons",
  REQUIRED_BOUNCE_REASONS,
  asArray(contract.bounceReasons?.values),
  "bounceReasons",
  ok,
  fail,
);
assertClosedSet(
  "providerHeaders",
  REQUIRED_PROVIDER_HEADERS,
  asArray(contract.providerHeaders?.values),
  "providerHeaders",
  ok,
  fail,
);
assertClosedSet(
  "tenantBrandingFields",
  REQUIRED_TENANT_BRANDING_FIELDS,
  asArray(contract.tenantBrandingFields?.values),
  "tenantBrandingFields",
  ok,
  fail,
);
assertClosedSet(
  "privacySafeDeliveryAuditEvents",
  REQUIRED_AUDIT_EVENTS,
  asArray(contract.privacySafeDeliveryAuditEvents?.values),
  "privacySafeDeliveryAuditEvents",
  ok,
  fail,
);
assertClosedSet(
  "privacySafeDeliveryFailureReasons",
  REQUIRED_FAILURE_REASONS,
  asArray(contract.privacySafeDeliveryFailureReasons?.values),
  "privacySafeDeliveryFailureReasons",
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
  "privacySafeDeliveryStateMatrix",
  contract.privacySafeDeliveryStateMatrix,
  REQUIRED_DELIVERY_STATUSES,
  "ENQUEUED",
  ok,
  fail,
);
assertStateMatrix(
  "bounceSuppressionStateMatrix",
  contract.bounceSuppressionStateMatrix,
  REQUIRED_BOUNCE_STATUSES,
  "ACTIVE",
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

ok("E11.2 Privacy-safe delivery contract OK");
process.exit(0);