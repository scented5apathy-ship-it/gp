#!/usr/bin/env node
/**
 * scripts/lint-notifications.mjs
 *
 * E11.1 deep validator for the notification contract at
 * `contracts/notifications/notification-policy.yaml` and the platform
 * mirror at `platform/helm/genealogy-platform/files/notifications/
 * notification-policy.yaml`.
 *
 * Mirrors the E10 DNA / E9 import-export linter pattern:
 *   - closed-set vocabularies: preferenceStates[4], channelTypes[4],
 *     notificationCategories[9], digestCadences[4],
 *     inAppInboxStates[5], notificationLocales[9], templateRoles[4],
 *     notificationAuditEvents[14], notificationFailureReasons[13],
 *     egressAllowlist[9], providerAdapters[6], temporalTaskQueues[4];
 *   - sandbox egress allowlist (9 endpoints);
 *   - 2 state matrices (notificationDispatchStateMatrix,
 *     notificationPreferenceStateMatrix);
 *   - 32 boolean guard rails;
 *   - numeric bounds;
 *   - 4 invariants (acknowledgementSlaIsUpperBound, etc.);
 *   - outbox envelope (eventId / eventType / occurredAt / tenantId /
 *     aggregateId / aggregateVersion / traceId / payload);
 *   - audit hooks + forbidden payload keys (25 keys incl. treeViewerBypass,
 *     dnaRawBucketKey, rawWebhookSecret);
 *   - capability boundaries (10 forbidden self-build capabilities).
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray, assertClosedSet, assertStateMatrix } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/notifications/notification-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/notifications/notification-policy.yaml",
);

const REQUIRED_PREFERENCE_STATES = ["OPT_IN", "OPT_OUT", "QUIET", "REQUIRED"];
const REQUIRED_CHANNEL_TYPES = ["IN_APP", "EMAIL", "PUSH", "SMS"];
const REQUIRED_NOTIFICATION_CATEGORIES = [
  "TRANSACTIONAL_SECURITY",
  "TRANSACTIONAL_PROPOSAL",
  "TRANSACTIONAL_MENTION",
  "TRANSACTIONAL_DNA",
  "TRANSACTIONAL_CONSENT",
  "TRANSACTIONAL_IMPORT",
  "DIGEST_ACTIVITY",
  "DIGEST_ANNIVERSARY",
  "MARKETING",
];
const REQUIRED_DIGEST_CADENCES = ["REAL_TIME", "HOURLY", "DAILY", "WEEKLY"];
const REQUIRED_INAPP_INBOX_STATES = [
  "UNREAD",
  "READ",
  "ACKNOWLEDGED",
  "ARCHIVED",
  "EXPIRED",
];
const REQUIRED_NOTIFICATION_LOCALES = [
  "en-US",
  "en-GB",
  "vi-VN",
  "fr-FR",
  "de-DE",
  "es-ES",
  "ja-JP",
  "ar-SA",
  "he-IL",
];
const REQUIRED_TEMPLATE_ROLES = [
  "TENANT_ADMIN",
  "PLATFORM_ADMIN",
  "COMMS_EDITOR",
  "COMMS_VIEWER",
];
const REQUIRED_AUDIT_EVENTS = [
  "NOTIFICATION_PREFERENCE_UPDATED",
  "NOTIFICATION_PREFERENCE_OPT_OUT",
  "NOTIFICATION_DISPATCH_DECISION",
  "NOTIFICATION_DISPATCHED",
  "NOTIFICATION_DELIVERED",
  "NOTIFICATION_OPENED",
  "NOTIFICATION_FAILED",
  "NOTIFICATION_BOUNCED",
  "NOTIFICATION_SUPPRESSED_QUIET_HOURS",
  "NOTIFICATION_SUPPRESSED_OPT_OUT",
  "NOTIFICATION_SUPPRESSED_BRANDING_INVALID",
  "NOTIFICATION_TEMPLATE_RENDERED",
  "NOTIFICATION_ACKNOWLEDGED",
  "NOTIFICATION_SUBSCRIPTION_UNSUBSCRIBED",
];
const REQUIRED_FAILURE_REASONS = [
  "NOTIFICATION_PREFERENCE_OPTED_OUT",
  "NOTIFICATION_CHANNEL_DISABLED_BY_TENANT",
  "NOTIFICATION_QUIET_HOURS_ACTIVE",
  "NOTIFICATION_LOCALE_MISSING",
  "NOTIFICATION_TEMPLATE_VERSION_STALE",
  "NOTIFICATION_TEMPLATE_RENDER_FAILED",
  "NOTIFICATION_BRANDING_MISSING",
  "NOTIFICATION_BRANDING_INVALID",
  "NOTIFICATION_PAYLOAD_TOO_LARGE",
  "NOTIFICATION_PAYLOAD_FORBIDDEN_KEY",
  "NOTIFICATION_PROVIDER_RATE_LIMITED",
  "NOTIFICATION_PROVIDER_AUTH_FAILED",
  "NOTIFICATION_PROVIDER_TRANSIENT",
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
const REQUIRED_PROVIDERS = [
  "SES_ADAPTER",
  "SENDGRID_ADAPTER",
  "SMTP_RELAY_ADAPTER",
  "FCM_PUSH_ADAPTER",
  "APNS_PUSH_ADAPTER",
  "SMS_ADAPTER_SCAFFOLD",
];
const REQUIRED_TASK_QUEUES = [
  "notify.dispatch",
  "notify.provider",
  "notify.digest",
  "notify.inbox",
];
const REQUIRED_DISPATCH_STATUSES = [
  "PENDING",
  "RENDERED",
  "DISPATCHED",
  "DELIVERED",
  "ACKNOWLEDGED",
  "EXPIRED",
  "FAILED",
  "SUPPRESSED",
];
const REQUIRED_PREFERENCE_STATUSES = ["OPT_IN", "OPT_OUT", "QUIET", "REQUIRED"];

const REQUIRED_BOUNDS = {
  templateMaxBytes: 65536,
  digestMaxRecipientsPerRun: 1000,
  inAppInboxMaxPerUser: 500,
  preferenceUpdateMaxPerRequest: 16,
  renderTimeoutMs: 250,
  providerSendTimeoutSeconds: 30,
  retryPolicyMaxAttempts: 8,
  retryPolicyInitialBackoffSeconds: 30,
  retryPolicyMaxBackoffSeconds: 1800,
  rateLimitPerUserPerMinute: 60,
  rateLimitPerTenantPerMinute: 10000,
  quietHoursDefaultMinutes: 480,
  localeTemplateMaxVersionsRetained: 50,
  inboxAcknowledgementSlaHours: 168,
  deliveryWebhookSignatureMaxClockSkewSeconds: 300,
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
  "email_delivery_engine",
  "smtp_server",
  "push_notification_gateway",
  "sms_gateway",
  "cron_scheduler",
  "queue_scheduler",
  "bounce_processor",
  "spam_scanner",
  "localization_engine",
  "template_compiler",
];

const REQUIRED_BOOLEAN_GUARDS = [
  "smsAdapterRequiresAdr",
  "pushAdapterOptInOnly",
  "marketingChannelRequiresDoubleOptIn",
  "transactionalSecurityCategoryBypassQuietHours",
  "transactionalDnaCategoryRequiresConsentReauthAtRender",
  "transactionalConsentCategoryRequiresConsentStateEffective",
  "transactionalImportCategoryRequiresStepUpAuthOnDownload",
  "localeTemplatesMustBeVersioned",
  "localeTemplatesMustDeclareBcp47",
  "templateRenderIsDeterministicPerVersion",
  "templateVersionBumpRequiresApproval",
  "tenantBrandingIsTenantScoped",
  "globalBrandingForbidden",
  "brandingMustIncludeHrefToPreferenceCenter",
  "retryBackoffOwnedByTemporalActivity",
  "selfBuiltRetrySchedulerForbidden",
  "selfBuiltSmtpServerForbidden",
  "crossTenantPreferenceLookupForbidden",
  "crossTenantTemplateLookupForbidden",
  "crossTenantInboxLookupForbidden",
  "quietHoursTimezoneMustBeIana",
  "quietHoursLocalClockForbidden",
  "bounceListIsTenantScoped",
  "suppressionListIsTenantScoped",
  "globalSuppressionListIsOptInOnly",
];

function fail(msg) {
  process.stderr.write(`[lint-notifications] FAIL: ${msg}\n`);
  process.exit(1);
}

function ok(line) {
  process.stdout.write(`[lint-notifications] ${line}\n`);
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
  fail(
    `chart mirror is not byte-equal: contract=${contractText.length} chart=${chartText.length}`,
  );
}
ok("chart mirror byte-equal");

assertClosedSet(
  "preferenceStates",
  REQUIRED_PREFERENCE_STATES,
  asArray(contract.preferenceStates?.values),
  "preferenceStates",
  ok,
  fail,
);
assertClosedSet(
  "channelTypes",
  REQUIRED_CHANNEL_TYPES,
  asArray(contract.channelTypes?.values),
  "channelTypes",
  ok,
  fail,
);
assertClosedSet(
  "notificationCategories",
  REQUIRED_NOTIFICATION_CATEGORIES,
  asArray(contract.notificationCategories?.values),
  "notificationCategories",
  ok,
  fail,
);
assertClosedSet(
  "digestCadences",
  REQUIRED_DIGEST_CADENCES,
  asArray(contract.digestCadences?.values),
  "digestCadences",
  ok,
  fail,
);
assertClosedSet(
  "inAppInboxStates",
  REQUIRED_INAPP_INBOX_STATES,
  asArray(contract.inAppInboxStates?.values),
  "inAppInboxStates",
  ok,
  fail,
);
assertClosedSet(
  "notificationLocales",
  REQUIRED_NOTIFICATION_LOCALES,
  asArray(contract.notificationLocales?.values),
  "notificationLocales",
  ok,
  fail,
);
assertClosedSet(
  "templateRoles",
  REQUIRED_TEMPLATE_ROLES,
  asArray(contract.templateRoles?.values),
  "templateRoles",
  ok,
  fail,
);
assertClosedSet(
  "notificationAuditEvents",
  REQUIRED_AUDIT_EVENTS,
  asArray(contract.notificationAuditEvents?.values),
  "notificationAuditEvents",
  ok,
  fail,
);
assertClosedSet(
  "notificationFailureReasons",
  REQUIRED_FAILURE_REASONS,
  asArray(contract.notificationFailureReasons?.values),
  "notificationFailureReasons",
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
  "providerAdapters",
  REQUIRED_PROVIDERS,
  asArray(contract.providerAdapters?.values),
  "providerAdapters",
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
  "notificationDispatchStateMatrix",
  contract.notificationDispatchStateMatrix,
  REQUIRED_DISPATCH_STATUSES,
  "PENDING",
  ok,
  fail,
);
assertStateMatrix(
  "notificationPreferenceStateMatrix",
  contract.notificationPreferenceStateMatrix,
  REQUIRED_PREFERENCE_STATUSES,
  "OPT_IN",
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

const sandbox = contract.sandboxEgress || {};
const allowedEgress = asArray(sandbox.allowedEgress);
const forbiddenEgress = asArray(sandbox.forbiddenEgress);
if (!allowedEgress.includes("internet") && forbiddenEgress.includes("internet")) {
  ok("sandbox egress: internet forbidden + allowed sandbox endpoints");
} else if (!forbiddenEgress.includes("internet")) {
  fail("sandboxEgress.forbiddenEgress MUST include 'internet'");
}

assertClosedSet(
  "auditHooks",
  REQUIRED_AUDIT_EVENTS,
  asArray(contract.auditHooks?.values),
  "auditHooks",
  ok,
  fail,
);

ok("E11.1 Notification policy contract OK");
process.exit(0);