#!/usr/bin/env node
/**
 * scripts/lint-entitlements.mjs
 *
 * E11.4 deep validator for the entitlement/quota/billing contract at
 * `contracts/operations/entitlement-quota-billing-policy.yaml` and the
 * platform mirror at
 * `platform/helm/genealogy-platform/files/operations/
 * entitlement-quota-billing-policy.yaml`.
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
  "contracts/operations/entitlement-quota-billing-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/operations/entitlement-quota-billing-policy.yaml",
);

const REQUIRED_BILLING_PLANS = [
  "FREE",
  "PRO",
  "ENTERPRISE",
  "ON_PREM_COMMUNITY",
  "ON_PREM_ENTERPRISE",
  "TRIAL",
];
const REQUIRED_PLAN_QUOTAS = [
  "TREE_COUNT",
  "PERSON_COUNT",
  "MEDIA_BYTES",
  "DNA_KIT_COUNT",
  "API_REQUESTS_PER_DAY",
  "EXPORT_JOBS_PER_DAY",
  "REPORT_JOBS_PER_DAY",
  "IMPORT_JOBS_PER_DAY",
  "WEBHOOK_DELIVERIES_PER_DAY",
  "ACTIVE_COLLABORATORS",
];
const REQUIRED_QUOTA_SCOPES = [
  "PRE_MUTATION",
  "PRE_JOB_SUBMIT",
  "PRE_BILLING_CHARGE",
  "PRE_EXPORT_DOWNLOAD",
];
const REQUIRED_BILLING_PROVIDERS = [
  "STRIPE_ADAPTER_SAAS",
  "OFFLINE_LICENSE_ON_PREM",
];
const REQUIRED_USAGE_CATEGORIES = [
  "TREE_CREATED",
  "PERSON_CREATED",
  "MEDIA_BYTES_UPLOADED",
  "DNA_KIT_REGISTERED",
  "API_REQUEST",
  "EXPORT_JOB_SUBMITTED",
  "REPORT_JOB_SUBMITTED",
  "IMPORT_JOB_SUBMITTED",
  "WEBHOOK_DELIVERED",
  "COLLABORATOR_INVITED",
];
const REQUIRED_WARNING_LEVELS = [
  "OK",
  "WARNING_50",
  "WARNING_75",
  "WARNING_90",
  "EXCEEDED_HARD",
  "EXCEEDED_SOFT",
];
const REQUIRED_DECISION_LABELS = [
  "ALLOW",
  "ALLOW_WITH_WARNING",
  "DENY_HARD_QUOTA",
  "DENY_PLAN_INACTIVE",
  "DENY_TRIAL_EXPIRED",
  "DENY_FEATURE_FLAG_OFF",
  "DENY_LICENSE_INVALID",
];
const REQUIRED_BILLING_WEBHOOK_EVENTS = [
  "INVOICE_PAID",
  "INVOICE_PAYMENT_FAILED",
  "SUBSCRIPTION_CREATED",
  "SUBSCRIPTION_UPDATED",
  "SUBSCRIPTION_CANCELLED",
  "SUBSCRIPTION_TRIAL_WILL_END",
  "CUSTOMER_CREATED",
  "CUSTOMER_UPDATED",
];
const REQUIRED_AUDIT_EVENTS = [
  "ENTITLEMENT_PLAN_ASSIGNED",
  "ENTITLEMENT_PLAN_UPGRADED",
  "ENTITLEMENT_PLAN_DOWNGRADED",
  "ENTITLEMENT_QUOTA_WARNING_EMITTED",
  "ENTITLEMENT_QUOTA_HARD_LIMIT_REACHED",
  "ENTITLEMENT_FEATURE_FLAG_OVERRIDE_APPLIED",
  "ENTITLEMENT_USAGE_EVENT_EMITTED",
  "ENTITLEMENT_USAGE_EVENT_DEDUPLICATED",
  "ENTITLEMENT_BILLING_WEBHOOK_RECEIVED",
  "ENTITLEMENT_BILLING_WEBHOOK_SIGNATURE_INVALID",
  "ENTITLEMENT_BILLING_WEBHOOK_DROPPED_DUPLICATE",
  "ENTITLEMENT_LICENSE_INSTALLED",
  "ENTITLEMENT_LICENSE_INVALID",
  "ENTITLEMENT_LICENSE_EXPIRED",
];
const REQUIRED_FAILURE_REASONS = [
  "ENTITLEMENT_PLAN_INACTIVE",
  "ENTITLEMENT_QUOTA_HARD_REACHED",
  "ENTITLEMENT_TRIAL_EXPIRED",
  "ENTITLEMENT_FEATURE_FLAG_OFF",
  "ENTITLEMENT_LICENSE_INVALID",
  "ENTITLEMENT_LICENSE_EXPIRED",
  "ENTITLEMENT_BILLING_WEBHOOK_SIGNATURE_INVALID",
  "ENTITLEMENT_BILLING_WEBHOOK_DUPLICATE",
  "ENTITLEMENT_USAGE_EVENT_FORBIDDEN_KEY",
  "ENTITLEMENT_KONG_IS_NOT_SOURCE_OF_TRUTH",
  "ENTITLEMENT_TENANT_MISMATCH",
  "ENTITLEMENT_DENY_BY_ADMIN_OVERRIDE",
  "ENTITLEMENT_INTERNAL_CONSISTENCY_VIOLATION",
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
  "billing-provider-saas",
];
const REQUIRED_TASK_QUEUES = [
  "ops.entitlement",
  "ops.quota",
  "ops.billingWebhook",
  "ops.licenseValidation",
];
const REQUIRED_DECISION_STATUSES = ["PENDING", "ALLOW", "ALLOW_WITH_WARNING", "DENY"];
const REQUIRED_WEBHOOK_STATUSES = [
  "RECEIVED",
  "SIGNATURE_VERIFIED",
  "APPLIED",
  "DUPLICATE",
  "INVALID",
];
const REQUIRED_BOUNDS = {
  planChangePropagationTimeoutSeconds: 30,
  quotaWarningEmissionIntervalSeconds: 300,
  billingWebhookReplayWindowSeconds: 300,
  usageEventMaxBytes: 4096,
  licenseMaxBytes: 65536,
  licenseClockSkewSeconds: 300,
  quotaResetCadenceDays: 30,
  freeTierTreeLimit: 3,
  freeTierPersonLimit: 250,
  freeTierMediaBytes: 524288000,
  proTierTreeLimit: 50,
  proTierPersonLimit: 25000,
  proTierMediaBytes: 107374182400,
  enterpriseTreeLimit: 10000,
  enterprisePersonLimit: 10000000,
  enterpriseMediaBytes: 10995116277760,
  webhookDeliveryDailyLimit: 1000000,
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
  "rawStripeApiKey",
  "rawLicenseFile",
];
const REQUIRED_CAPABILITY_BOUNDARIES = [
  "payment_processor",
  "billing_engine",
  "invoice_renderer",
  "license_server",
  "dunning_engine",
  "tax_engine",
];
const REQUIRED_BOOLEAN_GUARDS = [
  "domainEntitlementSourceOfTruthIsServiceOwned",
  "kongRateLimitIsNotSourceOfTruth",
  "quotaEnforcedPreMutation",
  "quotaEnforcedPreJobSubmit",
  "quotaEnforcedPreBillingCharge",
  "quotaEnforcedPreExportDownload",
  "usageEventsMustNotContainRawDna",
  "usageEventsMustNotContainRawMedia",
  "usageEventsMustNotContainRawSensitiveContent",
  "usageEventsMustUseOpaqueAggregateIds",
  "usageEventsIdempotencyKeyRequired",
  "usageEventsDeDuplicatedByIdempotencyKey",
  "billingWebhookSignatureRequired",
  "billingWebhookSignatureHmacSha256",
  "billingWebhookSecretRotatedPerAdr",
  "licenseFingerprintMustMatchTenant",
  "licenseMustBeOfflineForOnPrem",
  "licenseMustHaveGracePeriod",
  "planUpgradeEffectiveImmediately",
  "planDowngradeEffectiveAfterPeriodEnd",
  "featureFlagOverrideRequiresReasonAndAudit",
];

function fail(msg) {
  process.stderr.write(`[lint-entitlements] FAIL: ${msg}\n`);
  process.exit(1);
}
function ok(line) {
  process.stdout.write(`[lint-entitlements] ${line}\n`);
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
  "billingPlans",
  REQUIRED_BILLING_PLANS,
  asArray(contract.billingPlans?.values),
  "billingPlans",
  ok,
  fail,
);
assertClosedSet(
  "planQuotas",
  REQUIRED_PLAN_QUOTAS,
  asArray(contract.planQuotas?.values),
  "planQuotas",
  ok,
  fail,
);
assertClosedSet(
  "quotaEnforcementScopes",
  REQUIRED_QUOTA_SCOPES,
  asArray(contract.quotaEnforcementScopes?.values),
  "quotaEnforcementScopes",
  ok,
  fail,
);
assertClosedSet(
  "billingProviders",
  REQUIRED_BILLING_PROVIDERS,
  asArray(contract.billingProviders?.values),
  "billingProviders",
  ok,
  fail,
);
assertClosedSet(
  "usageEventCategories",
  REQUIRED_USAGE_CATEGORIES,
  asArray(contract.usageEventCategories?.values),
  "usageEventCategories",
  ok,
  fail,
);
assertClosedSet(
  "quotaWarningLevels",
  REQUIRED_WARNING_LEVELS,
  asArray(contract.quotaWarningLevels?.values),
  "quotaWarningLevels",
  ok,
  fail,
);
assertClosedSet(
  "entitlementDecisionLabels",
  REQUIRED_DECISION_LABELS,
  asArray(contract.entitlementDecisionLabels?.values),
  "entitlementDecisionLabels",
  ok,
  fail,
);
assertClosedSet(
  "billingWebhookEvents",
  REQUIRED_BILLING_WEBHOOK_EVENTS,
  asArray(contract.billingWebhookEvents?.values),
  "billingWebhookEvents",
  ok,
  fail,
);
assertClosedSet(
  "entitlementAuditEvents",
  REQUIRED_AUDIT_EVENTS,
  asArray(contract.entitlementAuditEvents?.values),
  "entitlementAuditEvents",
  ok,
  fail,
);
assertClosedSet(
  "entitlementFailureReasons",
  REQUIRED_FAILURE_REASONS,
  asArray(contract.entitlementFailureReasons?.values),
  "entitlementFailureReasons",
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
  "entitlementDecisionStateMatrix",
  contract.entitlementDecisionStateMatrix,
  REQUIRED_DECISION_STATUSES,
  "PENDING",
  ok,
  fail,
);
assertStateMatrix(
  "billingWebhookStateMatrix",
  contract.billingWebhookStateMatrix,
  REQUIRED_WEBHOOK_STATUSES,
  "RECEIVED",
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

ok("E11.4 Entitlement / quota / billing contract OK");
process.exit(0);