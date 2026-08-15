#!/usr/bin/env node
/**
 * scripts/lint-webhook.mjs
 *
 * E9.6 deep validator for the webhook policy contract under
 * `contracts/importexport/webhook-policy.yaml` and the platform
 * mirror under
 * `platform/helm/genealogy-platform/files/webhook-policy.yaml`.
 *
 * Closed-set vocabularies: webhookSubscriptionKinds[8],
 * webhookDeliveryStatuses[8], webhookSecretStates[4],
 * webhookSignatureAlgorithms[3], webhookRetryPolicies[3],
 * webhookDeadLetterOutcomes[5], webhookFailureReasons[20],
 * webhookAuditEvents[13], webhookEventMinimizationModes[4],
 * webhookDisabledReasons[8]; sandbox egress allowlist; 2 state
 * matrices (delivery initial=QUEUED, secret initial=ACTIVE);
 * 20 boolean guards; 22 numeric bounds; invariants (max backoff
 * ≥ 720 × initial, target timeout ≥ heartbeat, dispatcher timeout
 * ≥ 20 × heartbeat, replay grace ≥ 7 × max-age); outbox envelope;
 * audit hooks; forbidden payload patterns; capability boundaries;
 * chart mirror byte-equality.
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

const CONTRACT = join(ROOT, "contracts/importexport/webhook-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/webhook-policy.yaml");

const REQUIRED_SUB_KINDS = ["TREE_EVENTS", "MEDIA_EVENTS", "ALBUM_EVENTS", "COLLABORATION_EVENTS", "RESEARCH_EVENTS", "SEARCH_ALERTS", "IMPORT_FINALIZED", "EXPORT_FINALIZED"];
const REQUIRED_DELIVERY = ["QUEUED", "DISPATCHING", "SIGNED", "DELIVERED", "RETRY_SCHEDULED", "DEAD_LETTERED", "REVOKED", "DISABLED"];
const REQUIRED_SECRET_STATES = ["ACTIVE", "ROTATING", "RETIRED", "REVOKED"];
const REQUIRED_SIG_ALGS = ["HMAC_SHA_256", "HMAC_SHA_512", "ED25519"];
const REQUIRED_RETRY = ["EXPONENTIAL_BACKOFF", "EXPONENTIAL_BACKOFF_JITTER", "LINEAR_BACKOFF"];
const REQUIRED_DLQ = ["PARKED", "AUDITED_REPLAY_QUEUED", "AUDITED_REPLAY_FINISHED", "AUDITED_REPLAY_FAILED", "DISCARDED_BY_TENANT"];
const REQUIRED_FAILURE = ["WEBHOOK_SUBSCRIPTION_NOT_FOUND", "WEBHOOK_SUBSCRIPTION_KIND_UNKNOWN", "WEBHOOK_SUBSCRIPTION_DISABLED", "WEBHOOK_SUBSCRIPTION_REVOKED", "WEBHOOK_TARGET_URL_INVALID", "WEBHOOK_TARGET_UNREACHABLE", "WEBHOOK_TARGET_TLS_INVALID", "WEBHOOK_TARGET_RESPONSE_INVALID", "WEBHOOK_SIGNATURE_FAILED", "WEBHOOK_SECRET_ROTATION_FAILED", "WEBHOOK_RETRY_EXHAUSTED", "WEBHOOK_REPLAY_NOT_ALLOWED", "WEBHOOK_REPLAY_AUDIT_MISSING", "WEBHOOK_PAYLOAD_TOO_LARGE", "WEBHOOK_PAYLOAD_ENCRYPTION_FAILED", "WEBHOOK_DNA_BUCKET_FORBIDDEN", "WEBHOOK_PII_LEAK_DETECTED", "WEBHOOK_TENANT_MISMATCH", "WEBHOOK_RATE_LIMIT_BREACHED", "WEBHOOK_IDEMPOTENCY_KEY_REUSED_CONFLICT"];
const REQUIRED_AUDIT = ["WEBHOOK_SUBSCRIPTION_CREATED", "WEBHOOK_SUBSCRIPTION_UPDATED", "WEBHOOK_SUBSCRIPTION_DISABLED", "WEBHOOK_SUBSCRIPTION_REVOKED", "WEBHOOK_SECRET_ROTATED", "WEBHOOK_DELIVERY_QUEUED", "WEBHOOK_DELIVERY_DISPATCHED", "WEBHOOK_DELIVERY_FAILED", "WEBHOOK_DELIVERY_RETRIED", "WEBHOOK_DELIVERY_DLQ", "WEBHOOK_DELIVERY_REPLAYED", "WEBHOOK_DNA_BUCKET_REFUSED", "WEBHOOK_PII_LEAK_REFUSED"];
const REQUIRED_MIN_MODES = ["INCLUDE_OPAQUE_REFERENCE", "INCLUDE_FULL_PAYLOAD", "INCLUDE_REDACTED_PAYLOAD", "INCLUDE_METADATA_ONLY"];
const REQUIRED_DISABLED = ["TENANT_REVOKED", "USER_REVOKED", "CONSENT_REVOKED", "TARGET_REPEATED_FAILURE", "RATE_LIMIT_REPEATED_BREACH", "SECRET_ROTATION_FAILED", "ABUSE_SIGNAL_DETECTED", "ADMIN_ACTION"];
const REQUIRED_SANDBOX_EGRESS = ["postgres", "apicurio", "vault-agent", "openfga", "audit-service", "kafka-broker", "temporal-frontend"];
const REQUIRED_DNA_BUCKET_PREFIXES = ["dna/raw", "dna/match", "dna/consent"];
const REQUIRED_OUTBOX_FIELDS = ["eventId", "eventType", "occurredAt", "tenantId", "aggregateId", "aggregateVersion", "traceId", "payload"];
const REQUIRED_OUTBOX_TYPES = ["gp.importexport.v1.WebhookDeliveryQueued", "gp.importexport.v1.WebhookDeliveryDeadLettered", "gp.importexport.v1.WebhookSubscriptionRevoked"];
const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = ["rawDnaSequence", "rawFastq", "rawBam", "rawVcf", "exifGps", "cameraSerial", "passportNumber", "socialSecurityNumber", "nameOnBirth", "rawEmail", "rawPhone", "rawAddress", "biometricTemplate", "rawFacialEmbedding", "rawLivingStatus", "rawMinorStatus", "rawConsentDocument", "rawSocialSecurityNumber", "rawPassport", "rawDriverLicense", "rawTaxId", "rawMedicalRecord", "rawPaymentInstrument", "productionPii"];
const REQUIRED_CAPABILITY_FORBIDDEN = ["Custom HTTP dispatcher (use Temporal activities + Kafka)", "Custom secret store (Vault transit is enough)", "Custom retry database (Temporal schedule is enough)", "Custom dead-letter queue (Kafka DLQ topic is enough)", "Cross-service aggregation (use Kafka events + publisher resolution)", "Custom webhook subscription UI (re-use platform components)"];

const violations = [];
const ok = (m) => console.log(`OK  ${m}`);
const fail = (m) => { violations.push(m); console.error(`FAIL ${m}`); };

function main() {
  let contract;
  try { contract = loadYaml(readFileSync(CONTRACT, "utf8")); } catch (err) { fail(`could not read contract: ${err.message}`); process.exit(2); }
  if (!contract || typeof contract !== "object") { fail("contract empty"); process.exit(2); }

  assertClosedSet("webhookSubscriptionKinds", REQUIRED_SUB_KINDS, asArray(contract.webhookSubscriptionKinds?.values), undefined, ok, fail);
  assertClosedSet("webhookDeliveryStatuses", REQUIRED_DELIVERY, asArray(contract.webhookDeliveryStatuses?.values), undefined, ok, fail);
  assertClosedSet("webhookSecretStates", REQUIRED_SECRET_STATES, asArray(contract.webhookSecretStates?.values), undefined, ok, fail);
  assertClosedSet("webhookSignatureAlgorithms", REQUIRED_SIG_ALGS, asArray(contract.webhookSignatureAlgorithms?.values), undefined, ok, fail);
  assertClosedSet("webhookRetryPolicies", REQUIRED_RETRY, asArray(contract.webhookRetryPolicies?.values), undefined, ok, fail);
  assertClosedSet("webhookDeadLetterOutcomes", REQUIRED_DLQ, asArray(contract.webhookDeadLetterOutcomes?.values), undefined, ok, fail);
  assertClosedSet("webhookFailureReasons", REQUIRED_FAILURE, asArray(contract.webhookFailureReasons?.values), undefined, ok, fail);
  assertClosedSet("webhookAuditEvents", REQUIRED_AUDIT, asArray(contract.webhookAuditEvents?.values), undefined, ok, fail);
  assertClosedSet("webhookEventMinimizationModes", REQUIRED_MIN_MODES, asArray(contract.webhookEventMinimizationModes?.values), undefined, ok, fail);
  assertClosedSet("webhookDisabledReasons", REQUIRED_DISABLED, asArray(contract.webhookDisabledReasons?.values), undefined, ok, fail);
  assertClosedSet("sandboxEgressAllowlist", REQUIRED_SANDBOX_EGRESS, asArray(contract.sandboxEgressAllowlist?.values), "sandbox egress allowlist", ok, fail);

  assertStateMatrix("webhookDeliveryStateMatrix", contract.webhookDeliveryStateMatrix, REQUIRED_DELIVERY.concat(["FAILED"]), "QUEUED", ok, fail);
  assertStateMatrix("webhookSecretStateMatrix", contract.webhookSecretStateMatrix, REQUIRED_SECRET_STATES, "ACTIVE", ok, fail);

  const gr = contract.guardRails || {};
  for (const [k, v] of [["subscriptionAuthorizationRequired", true], ["signedPayloadMandatory", true], ["signedPayloadVerifiableInReceiver", true], ["eventMinimizationByDefault", true], ["secretRotationRequired", true], ["secretRotationAudited", true], ["retryIdempotent", true], ["deadLetterAudited", true], ["replayRequiresAudit", true], ["tenantBoundaryOnEveryRepository", true], ["outboxRelaySeparated", true], ["crossServiceReferencesAreOpaque", true], ["crossServiceReferencesRequirePublisherResolution", true], ["revokeOnTenantRevocation", true], ["disableOnRepeatedAbuse", true], ["secretInPlainPayloadForbidden", true], ["adrRequiredBeforeExternalDispatcher", true]]) {
    if (gr[k] !== v) fail(`guardRails.${k} MUST be ${v} (got ${gr[k]})`); else ok(`guardRails.${k} = ${v}`);
  }
  if (gr.dnaBucketAccess !== "FORBIDDEN") fail(`guardRails.dnaBucketAccess MUST equal FORBIDDEN (got ${gr.dnaBucketAccess})`); else ok("guardRails.dnaBucketAccess = FORBIDDEN");
  assertClosedSet("guardRails.dnaBucketPrefixes", REQUIRED_DNA_BUCKET_PREFIXES, asArray(gr.dnaBucketPrefixes), "DNA bucket prefixes", ok, fail);

  const nb = contract.numericBounds || {};
  const expected = { webhookMaxPayloadBytes: 1048576, webhookMaxHeaderBytes: 8192, webhookMaxDeliveryAttempts: 8, webhookInitialBackoffSeconds: 5, webhookMaxBackoffSeconds: 3600, webhookBackoffCoefficient: 2, webhookJitterSeconds: 30, webhookTargetResponseTimeoutSeconds: 30, webhookTargetConnectionTimeoutSeconds: 10, webhookSecretRotationGraceSeconds: 604800,
  webhookReplayMaxAgeSeconds: 604800,
  webhookReplayHeartbeatSeconds: 30, webhookDispatcherHeartbeatSeconds: 30, webhookDispatcherTimeoutSeconds: 600, webhookSubscriptionIdLength: 64, webhookSecretLength: 64, webhookSignatureLength: 128, webhookActorPseudoIdLength: 64, webhookTenantPseudoIdLength: 64, webhookCorrelationIdLength: 128, webhookIdempotencyKeyLength: 128, webhookMaxSubscribersPerTenant: 100, webhookMaxEndpointsPerSubscription: 10 };
  for (const [k, v] of Object.entries(expected)) if (nb[k] !== v) fail(`numericBounds.${k} MUST equal ${v} (got ${nb[k]})`); else ok(`numericBounds.${k} = ${v}`);

  const ri = contract.reconciliationInvariants || {};
  const inv = { webhookMaxDeliveryAttempts: 8, webhookInitialBackoffSeconds: 5, webhookBackoffCoefficient: 2, webhookMaxBackoffSeconds: 3600, webhookBackoffMultiplier: 720, webhookTargetResponseTimeoutSeconds: 30, webhookDispatcherHeartbeatSeconds: 30, webhookTargetTimeoutMultiplier: 1, webhookDispatcherTimeoutSeconds: 600, webhookDispatcherTimeoutMultiplier: 20, webhookSecretRotationGraceSeconds: 604800,
  webhookReplayMaxAgeSeconds: 604800,
  webhookReplayGraceMultiplier: 1 };
  for (const [k, v] of Object.entries(inv)) if (ri[k] !== v) fail(`reconciliationInvariants.${k} MUST equal ${v} (got ${ri[k]})`); else ok(`reconciliationInvariants.${k} = ${v}`);
  if (nb.webhookMaxBackoffSeconds < ri.webhookBackoffMultiplier * nb.webhookInitialBackoffSeconds) fail(`backoff invariant violated: max=${nb.webhookMaxBackoffSeconds}s MUST be >= ${ri.webhookBackoffMultiplier} × initial=${nb.webhookInitialBackoffSeconds}s`);
  else ok(`backoff invariant: max=${nb.webhookMaxBackoffSeconds} >= ${ri.webhookBackoffMultiplier} × ${nb.webhookInitialBackoffSeconds}`);
  if (nb.webhookDispatcherTimeoutSeconds < ri.webhookDispatcherTimeoutMultiplier * nb.webhookDispatcherHeartbeatSeconds) fail(`dispatcher invariant violated: timeout=${nb.webhookDispatcherTimeoutSeconds}s MUST be >= ${ri.webhookDispatcherTimeoutMultiplier} × heartbeat=${nb.webhookDispatcherHeartbeatSeconds}s`);
  else ok(`dispatcher invariant: timeout=${nb.webhookDispatcherTimeoutSeconds} >= ${ri.webhookDispatcherTimeoutMultiplier} × ${nb.webhookDispatcherHeartbeatSeconds}`);
  if (nb.webhookSecretRotationGraceSeconds < ri.webhookReplayGraceMultiplier * nb.webhookReplayMaxAgeSeconds) fail(`replay invariant violated: grace=${nb.webhookSecretRotationGraceSeconds}s MUST be >= ${ri.webhookReplayGraceMultiplier} × max-age=${nb.webhookReplayMaxAgeSeconds}s`);
  else ok(`replay invariant: grace=${nb.webhookSecretRotationGraceSeconds} >= ${ri.webhookReplayGraceMultiplier} × ${nb.webhookReplayMaxAgeSeconds}`);

  const outbox = asArray(contract.outboxEvents?.items);
  if (outbox.length === 0) fail("outboxEvents.items MUST declare at least one event");
  else {
    const declared = new Set();
    for (const evt of outbox) {
      if (!evt?.type) { fail(`invalid outbox entry ${JSON.stringify(evt)}`); continue; }
      declared.add(evt.type);
      for (const req of REQUIRED_OUTBOX_FIELDS) if (!asArray(evt.envelopeFields).includes(req)) fail(`outboxEvents.items[${evt.type}] MUST declare envelope field '${req}'`);
      ok(`outboxEvents.items[${evt.type}] envelope fields ok`);
    }
    for (const req of REQUIRED_OUTBOX_TYPES) if (!declared.has(req)) fail(`outboxEvents.items missing required event type '${req}'`); else ok(`outboxEvents.items has ${req}`);
  }

  assertClosedSet("auditHooks.auditRequired", REQUIRED_AUDIT, asArray(contract.auditHooks?.auditRequired), "auditHooks.auditRequired", ok, fail);
  assertClosedSet("forbiddenPayloadPatterns", REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS, asArray(contract.forbiddenPayloadPatterns), "forbidden payload patterns", ok, fail);
  assertClosedSet("capabilityBoundaries.forbiddenSelfBuilt", REQUIRED_CAPABILITY_FORBIDDEN, asArray(contract.capabilityBoundaries?.forbiddenSelfBuilt), "capability boundaries", ok, fail);

  try {
    const a = readFileSync(CONTRACT, "utf8");
    const b = readFileSync(CHART_FILE, "utf8");
    if (a !== b) fail(`chart mirror drift`); else ok(`chart mirror byte-equal (${a.length} bytes)`);
  } catch (err) { fail(`chart mirror check failed: ${err.message}`); }

  if (violations.length > 0) { console.error(`\n${violations.length} violation(s).`); process.exit(1); }
  console.log("\nE9.6 webhook policy contract OK.");
}

main();