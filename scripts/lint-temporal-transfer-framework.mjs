#!/usr/bin/env node
/**
 * scripts/lint-temporal-transfer-framework.mjs
 *
 * E9.1 deep validator for the Temporal transfer framework contract
 * under `contracts/importexport/temporal-transfer-framework-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/temporal-transfer-framework-policy.yaml`.
 *
 * Mirrors the E8 search contract validators (lint-search-projection.mjs,
 * lint-authorized-search.mjs, lint-public-projection.mjs,
 * lint-benchmark-evolution-gate.mjs):
 *   - closed-set vocabularies: transferWorkflowKinds[11],
 *     transferSignals[7], transferQueries[6], transferPhases[10],
 *     transferStatuses[10], transferCheckpointStates[6],
 *     transferActivityKinds[19], transferCompensationActions[7],
 *     transferFailureReasons[23], transferAuditEvents[18],
 *     transferActivityStatuses[8], transferIdempotencyOutcomes[5];
 *   - sandbox egress allowlist (postgres, apicurio, vault-agent,
 *     openfga, audit-service, kafka-broker, temporal-frontend);
 *   - 3 state matrices (transferRunStateMatrix initial=QUEUED,
 *     transferCheckpointStateMatrix initial=NONE,
 *     transferActivityStateMatrix initial=SCHEDULED);
 *   - 27 boolean guard rails;
 *   - numeric bounds (workflowMaxSignalsPerSecond=10,
 *     workflowMaxQueriesPerSecond=50,
 *     workflowMaxActivityAttempts=8,
 *     workflowMaxConcurrentActivities=16,
 *     workflowMaxHistoryLengthEvents=50000,
 *     workflowMaxExecutionDurationSeconds=86400,
 *     workflowMaxContinueAsNewHistoryEvents=10000,
 *     workflowMaxCheckpointBytes=1048576,
 *     workflowMaxCompensationLogEntries=1024,
 *     activityScheduleToCloseTimeoutSeconds=600,
 *     activityStartToCloseTimeoutSeconds=300,
 *     activityHeartbeatIntervalSeconds=30,
 *     activityHeartbeatTimeoutMultiplier=3,
 *     activityRetryBackoffCoefficient=2,
 *     activityRetryInitialIntervalSeconds=5,
 *     activityRetryMaximumIntervalSeconds=60,
 *     activityRetryMaximumAttempts=8,
 *     workflowIdempotencyKeyLength=128,
 *     workflowIdempotencyKeyTtlSeconds=604800,
 *     transferRunIdLength=64,
 *     transferCorrelationIdLength=128,
 *     transferTenantPseudoIdLength=64,
 *     transferActorPseudoIdLength=64,
 *     transferSecretMaximumBytes=0);
 *   - invariants: activityStartToClose ≤ multiplier × heartbeat,
 *     retry backoff bound, etc;
 *   - outbox envelope (eventId / eventType / occurredAt / tenantId /
 *     aggregateId / aggregateVersion / traceId / payload);
 *   - audit hooks + forbidden payload patterns;
 *   - capability boundaries;
 *   - chart mirror byte-equality.
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

const CONTRACT = join(
  ROOT,
  "contracts/importexport/temporal-transfer-framework-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/temporal-transfer-framework-policy.yaml",
);

const REQUIRED_WORKFLOW_KINDS = [
  "IMPORT_GEDCOM",
  "IMPORT_PREVIEW",
  "IMPORT_DEDUP",
  "IMPORT_CONFIRM",
  "IMPORT_CHUNK",
  "IMPORT_RECONCILE",
  "EXPORT_BRANCH",
  "EXPORT_FULL",
  "EXPORT_REDACTED_PREVIEW",
  "PUBLIC_API_REQUEST",
  "WEBHOOK_DISPATCH",
];

const REQUIRED_SIGNALS = [
  "CANCEL",
  "PROGRESS_QUERY",
  "CHECKPOINT_FLUSH",
  "COMPENSATE",
  "RESUME",
  "FINALIZE",
  "PRIORITY_CHANGE",
];

const REQUIRED_QUERIES = [
  "STATUS",
  "PROGRESS_PERCENT",
  "CHECKPOINT_STATE",
  "COMPENSATION_LOG",
  "HEARTBEAT_LAST_SEEN",
  "FINAL_REPORT",
];

const REQUIRED_PHASES = [
  "VALIDATING",
  "PREVIEWING",
  "MAPPING",
  "DEDUPING",
  "CONFIRMING",
  "CHUNKING",
  "COMMITTING",
  "COMPENSATING",
  "CANCELLED",
  "FINALIZED",
];

const REQUIRED_STATUSES = [
  "QUEUED",
  "RUNNING",
  "AWAITING_INPUT",
  "AWAITING_CONFIRMATION",
  "CANCELLED",
  "COMPENSATING",
  "COMPENSATED",
  "COMPLETED",
  "FAILED",
  "BLOCKED",
];

const REQUIRED_CHECKPOINT_STATES = [
  "NONE",
  "CAPTURED",
  "FLUSHING",
  "PERSISTED",
  "RECOVERED",
  "INVALIDATED",
];

const REQUIRED_ACTIVITY_KINDS = [
  "PARSE_RECORD",
  "MAP_RECORD",
  "NORMALIZE_RECORD",
  "DEDUP_RECORD",
  "VALIDATE_RECORD",
  "COMMIT_CHUNK",
  "EMIT_OUTBOX",
  "DECRYPT_PAYLOAD",
  "GENERATE_PDF",
  "GENERATE_GEDCOM",
  "GENERATE_CSV",
  "GENERATE_JSON",
  "GENERATE_MEDIA_BUNDLE",
  "COMPUTE_CHECKSUM",
  "SIGN_DOWNLOAD",
  "SIGN_WEBHOOK_PAYLOAD",
  "PUBLISH_PUBLIC_API_RESPONSE",
  "BUILD_SITEMAP_ENTRY",
  "PURGE_PUBLIC_PROJECTION",
];

const REQUIRED_COMPENSATION_ACTIONS = [
  "DELETE_IMPORTED_CHUNK",
  "UNDO_DEDUP_MERGE",
  "REVOKE_TEMP_CREDENTIAL",
  "DELETE_GENERATED_BUNDLE",
  "REVOKE_SIGNED_URL",
  "DELETE_WEBHOOK_DELIVERY",
  "DELETE_PUBLIC_PROJECTION",
];

const REQUIRED_FAILURE_REASONS = [
  "TRANSFER_NOT_FOUND",
  "TRANSFER_TENANT_MISMATCH",
  "TRANSFER_KIND_UNKNOWN",
  "TRANSFER_SIGNAL_UNKNOWN",
  "TRANSFER_QUERY_UNKNOWN",
  "TRANSFER_PHASE_UNKNOWN",
  "TRANSFER_STATUS_INVALID_TRANSITION",
  "TRANSFER_ACTIVITY_NOT_IDEMPOTENT",
  "TRANSFER_ACTIVITY_HEARTBEAT_TIMEOUT",
  "TRANSFER_ACTIVITY_TIMEOUT",
  "TRANSFER_ACTIVITY_NON_DETERMINISTIC",
  "TRANSFER_CHECKPOINT_INVALID",
  "TRANSFER_CHECKPOINT_VERSION_DRIFT",
  "TRANSFER_COMPENSATION_FAILED",
  "TRANSFER_DUPLICATE_RUN_REJECTED",
  "TRANSFER_TEMPORAL_NAMESPACE_BLOCKED",
  "TRANSFER_DNA_BUCKET_FORBIDDEN",
  "TRANSFER_PII_LEAK_DETECTED",
  "TRANSFER_SECRETS_IN_PAYLOAD",
  "TRANSFER_USER_PROVIDED_PAYLOAD_TOO_LARGE",
  "TRANSFER_USER_PROVIDED_PAYLOAD_ENCODING_INVALID",
  "TRANSFER_USER_PROVIDED_PAYLOAD_DEPTH_EXCEEDED",
  "TRANSFER_USER_PROVIDED_PAYLOAD_COUNT_EXCEEDED",
];

const REQUIRED_AUDIT_EVENTS = [
  "TRANSFER_QUEUED",
  "TRANSFER_STARTED",
  "TRANSFER_SIGNAL_RECEIVED",
  "TRANSFER_QUERY_SERVED",
  "TRANSFER_PHASE_ADVANCED",
  "TRANSFER_CHECKPOINT_CAPTURED",
  "TRANSFER_CHECKPOINT_PERSISTED",
  "TRANSFER_CHECKPOINT_RECOVERED",
  "TRANSFER_COMPENSATION_TRIGGERED",
  "TRANSFER_COMPENSATION_COMPLETED",
  "TRANSFER_ACTIVITY_HEARTBEAT",
  "TRANSFER_ACTIVITY_TIMEOUT",
  "TRANSFER_ACTIVITY_DUPLICATE_DROPPED",
  "TRANSFER_DNA_BUCKET_REFUSED",
  "TRANSFER_PII_LEAK_REFUSED",
  "TRANSFER_SECRET_REFUSED",
  "TRANSFER_FINALIZED",
  "TRANSFER_FAILED",
];

const REQUIRED_ACTIVITY_STATUSES = [
  "SCHEDULED",
  "STARTED",
  "HEARTBEAT_RECEIVED",
  "COMPLETED",
  "FAILED_RETRYABLE",
  "FAILED_NON_RETRYABLE",
  "DUPLICATE_DROPPED",
  "CANCELLED",
];

const REQUIRED_IDEMPOTENCY_OUTCOMES = [
  "NEW",
  "IN_PROGRESS_REPLAY",
  "COMPLETED_REPLAY",
  "CONFLICT_REJECTED",
  "EXPIRED",
];

const REQUIRED_SANDBOX_EGRESS = [
  "postgres",
  "apicurio",
  "vault-agent",
  "openfga",
  "audit-service",
  "kafka-broker",
  "temporal-frontend",
];

const REQUIRED_DNA_BUCKET_PREFIXES = [
  "dna/raw",
  "dna/match",
  "dna/consent",
];

const REQUIRED_OUTBOX_FIELDS = [
  "eventId",
  "eventType",
  "occurredAt",
  "tenantId",
  "aggregateId",
  "aggregateVersion",
  "traceId",
  "payload",
];

const REQUIRED_OUTBOX_TYPES = [
  "gp.importexport.v1.TransferQueued",
  "gp.importexport.v1.TransferFinalized",
];

const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = [
  "rawDnaSequence",
  "rawFastq",
  "rawBam",
  "rawVcf",
  "exifGps",
  "cameraSerial",
  "passportNumber",
  "socialSecurityNumber",
  "nameOnBirth",
  "rawEmail",
  "rawPhone",
  "rawAddress",
  "biometricTemplate",
  "rawFacialEmbedding",
  "rawLivingStatus",
  "rawMinorStatus",
  "rawConsentDocument",
  "rawSocialSecurityNumber",
  "rawPassport",
  "rawDriverLicense",
  "rawTaxId",
  "rawMedicalRecord",
  "rawPaymentInstrument",
  "productionPii",
];

const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom durable queue (Temporal namespace + task queue is enough)",
  "Custom cron scheduler (Temporal schedule is enough)",
  "Cross-service aggregation (use Kafka events + publisher resolution)",
  "Custom workflow replay tool (Temporal already provides)",
];

const violations = [];
const ok = (msg) => {
  // eslint-disable-next-line no-console
  console.log(`OK  ${msg}`);
};

const fail = (msg) => {
  violations.push(msg);
  // eslint-disable-next-line no-console
  console.error(`FAIL ${msg}`);
};

function main() {
  let contract;
  try {
    const raw = readFileSync(CONTRACT, "utf8");
    contract = loadYaml(raw);
  } catch (err) {
    fail(`could not read contract ${CONTRACT}: ${err.message}`);
    process.exit(2);
  }
  if (!contract || typeof contract !== "object") {
    fail(`contract ${CONTRACT} is empty or malformed`);
    process.exit(2);
  }

  assertClosedSet(
    "transferWorkflowKinds",
    REQUIRED_WORKFLOW_KINDS,
    asArray(contract.transferWorkflowKinds?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferSignals",
    REQUIRED_SIGNALS,
    asArray(contract.transferSignals?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferQueries",
    REQUIRED_QUERIES,
    asArray(contract.transferQueries?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferPhases",
    REQUIRED_PHASES,
    asArray(contract.transferPhases?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferStatuses",
    REQUIRED_STATUSES,
    asArray(contract.transferStatuses?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferCheckpointStates",
    REQUIRED_CHECKPOINT_STATES,
    asArray(contract.transferCheckpointStates?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferActivityKinds",
    REQUIRED_ACTIVITY_KINDS,
    asArray(contract.transferActivityKinds?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferCompensationActions",
    REQUIRED_COMPENSATION_ACTIONS,
    asArray(contract.transferCompensationActions?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferFailureReasons",
    REQUIRED_FAILURE_REASONS,
    asArray(contract.transferFailureReasons?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferAuditEvents",
    REQUIRED_AUDIT_EVENTS,
    asArray(contract.transferAuditEvents?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferActivityStatuses",
    REQUIRED_ACTIVITY_STATUSES,
    asArray(contract.transferActivityStatuses?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "transferIdempotencyOutcomes",
    REQUIRED_IDEMPOTENCY_OUTCOMES,
    asArray(contract.transferIdempotencyOutcomes?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "sandboxEgressAllowlist",
    REQUIRED_SANDBOX_EGRESS,
    asArray(contract.sandboxEgressAllowlist?.values),
    "sandbox egress allowlist",
    ok,
    fail,
  );

  assertStateMatrix(
    "transferRunStateMatrix",
    contract.transferRunStateMatrix,
    REQUIRED_STATUSES,
    "QUEUED",
    ok,
    fail,
  );
  assertStateMatrix(
    "transferCheckpointStateMatrix",
    contract.transferCheckpointStateMatrix,
    REQUIRED_CHECKPOINT_STATES,
    "NONE",
    ok,
    fail,
  );
  assertStateMatrix(
    "transferActivityStateMatrix",
    contract.transferActivityStateMatrix,
    REQUIRED_ACTIVITY_STATUSES,
    "SCHEDULED",
    ok,
    fail,
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["temporalIsOnlyScheduler", true],
    ["customJobStateDatabaseForbidden", true],
    ["customRetryDatabaseForbidden", true],
    ["workflowDeterministicRequired", true],
    ["workflowIdempotencyKeyRequired", true],
    ["activityIdempotencyRequired", true],
    ["activityHeartbeatRequired", true],
    ["activityTimeoutConfigured", true],
    ["activityCancellationPropagated", true],
    ["signalCancellationHonored", true],
    ["checkpointVersioned", true],
    ["checkpointEncrypted", true],
    ["compensationReversible", true],
    ["compensationIdempotent", true],
    ["duplicateDetectionOnActivity", true],
    ["duplicateDetectionOnRun", true],
    ["outboxRelaySeparated", true],
    ["rawPayloadInWorkflowInputForbidden", true],
    ["rawPayloadInEventForbidden", true],
    ["secretInWorkflowInputForbidden", true],
    ["secretInEventForbidden", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["tenantBoundaryOnEveryRepository", true],
    ["auditHookOnEveryPhaseAdvance", true],
    ["auditHookOnEveryCompensation", true],
    ["adrRequiredBeforeExternalScheduler", true],
  ];
  for (const [key, expected] of booleanGuards) {
    if (gr[key] !== expected) {
      fail(`guardRails.${key} MUST be ${expected} (got ${gr[key]})`);
    } else {
      ok(`guardRails.${key} = ${expected}`);
    }
  }
  if (gr.dnaBucketAccess !== "FORBIDDEN") {
    fail(`guardRails.dnaBucketAccess MUST equal FORBIDDEN (got ${gr.dnaBucketAccess})`);
  } else {
    ok("guardRails.dnaBucketAccess = FORBIDDEN");
  }
  assertClosedSet(
    "guardRails.dnaBucketPrefixes",
    REQUIRED_DNA_BUCKET_PREFIXES,
    asArray(gr.dnaBucketPrefixes),
    "DNA bucket prefixes",
    ok,
    fail,
  );

  const nb = contract.numericBounds || {};
  const numericGuards = {
    workflowMaxSignalsPerSecond: 10,
    workflowMaxQueriesPerSecond: 50,
    workflowMaxActivityAttempts: 8,
    workflowMaxConcurrentActivities: 16,
    workflowMaxHistoryLengthEvents: 50000,
    workflowMaxExecutionDurationSeconds: 86400,
    workflowMaxContinueAsNewHistoryEvents: 10000,
    workflowMaxCheckpointBytes: 1048576,
    workflowMaxCompensationLogEntries: 1024,
    activityScheduleToCloseTimeoutSeconds: 600,
    activityStartToCloseTimeoutSeconds: 310,
    activityHeartbeatIntervalSeconds: 30,
    activityHeartbeatTimeoutMultiplier: 3,
    activityRetryBackoffCoefficient: 2,
    activityRetryInitialIntervalSeconds: 5,
    activityRetryMaximumIntervalSeconds: 60,
    activityRetryMaximumAttempts: 8,
    workflowIdempotencyKeyLength: 128,
    workflowIdempotencyKeyTtlSeconds: 604800,
    transferRunIdLength: 64,
    transferCorrelationIdLength: 128,
    transferTenantPseudoIdLength: 64,
    transferActorPseudoIdLength: 64,
    transferSecretMaximumBytes: 0,
  };
  for (const [key, expected] of Object.entries(numericGuards)) {
    const actual = nb[key];
    if (actual !== expected) {
      fail(`numericBounds.${key} MUST equal ${expected} (got ${actual})`);
    } else {
      ok(`numericBounds.${key} = ${expected}`);
    }
  }

  const ri = contract.reconciliationInvariants || {};
  const invariants = {
    activityStartToCloseBudgetSeconds: 300,
    activityHeartbeatIntervalSeconds: 30,
    activityHeartbeatTimeoutMultiplier: 3,
    activityStartToCloseToHeartbeatMultiplier: 10,
    workflowRetryBackoffInitialSeconds: 5,
    workflowRetryBackoffCoefficient: 2,
    workflowRetryMaximumAttempts: 8,
    workflowRetryBackoffMultiplier: 12,
    workflowCheckpointHeartbeatMultiplier: 2,
    workflowExecutionToHeartbeatMultiplier: 2880,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (
    nb.activityStartToCloseTimeoutSeconds
    < ri.activityStartToCloseToHeartbeatMultiplier * nb.activityHeartbeatIntervalSeconds
  ) {
    fail(
      `activity invariant violated: startToClose=${nb.activityStartToCloseTimeoutSeconds}s MUST be >= ${ri.activityStartToCloseToHeartbeatMultiplier} × heartbeat=${nb.activityHeartbeatIntervalSeconds}s`,
    );
  } else {
    ok(`activity invariant: startToClose=${nb.activityStartToCloseTimeoutSeconds} >= ${ri.activityStartToCloseToHeartbeatMultiplier} × ${nb.activityHeartbeatIntervalSeconds}`);
  }

  const outbox = asArray(contract.outboxEvents?.items);
  if (outbox.length === 0) {
    fail("outboxEvents.items MUST declare at least one event");
  } else {
    const declaredTypes = new Set();
    for (const evt of outbox) {
      if (!evt || typeof evt !== "object" || typeof evt.type !== "string") {
        fail(`outboxEvents.items: invalid entry ${JSON.stringify(evt)}`);
        continue;
      }
      declaredTypes.add(evt.type);
      const fields = asArray(evt.envelopeFields);
      for (const required of REQUIRED_OUTBOX_FIELDS) {
        if (!fields.includes(required)) {
          fail(`outboxEvents.items[${evt.type}] MUST declare envelope field '${required}'`);
        }
      }
      ok(`outboxEvents.items[${evt.type}] envelope fields ok`);
    }
    for (const required of REQUIRED_OUTBOX_TYPES) {
      if (!declaredTypes.has(required)) {
        fail(`outboxEvents.items missing required event type '${required}'`);
      } else {
        ok(`outboxEvents.items has ${required}`);
      }
    }
  }

  const audit = contract.auditHooks || {};
  assertClosedSet(
    "auditHooks.auditRequired",
    REQUIRED_AUDIT_EVENTS,
    asArray(audit.auditRequired),
    "auditHooks.auditRequired",
    ok,
    fail,
  );

  assertClosedSet(
    "forbiddenPayloadPatterns",
    REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS,
    asArray(contract.forbiddenPayloadPatterns),
    "forbidden payload patterns",
    ok,
    fail,
  );

  const cb = contract.capabilityBoundaries || {};
  assertClosedSet(
    "capabilityBoundaries.forbiddenSelfBuilt",
    REQUIRED_CAPABILITY_FORBIDDEN,
    asArray(cb.forbiddenSelfBuilt),
    "capability boundaries",
    ok,
    fail,
  );

  try {
    const a = readFileSync(CONTRACT, "utf8");
    const b = readFileSync(CHART_FILE, "utf8");
    if (a !== b) {
      fail(`chart mirror drift: ${CONTRACT} !== ${CHART_FILE}`);
    } else {
      ok(`chart mirror byte-equal (${a.length} bytes)`);
    }
  } catch (err) {
    fail(`chart mirror check failed: ${err.message}`);
  }

  if (violations.length > 0) {
    // eslint-disable-next-line no-console
    console.error(`\n${violations.length} violation(s).`);
    process.exit(1);
  }
  // eslint-disable-next-line no-console
  console.log("\nE9.1 temporal transfer framework policy contract OK.");
}

main();