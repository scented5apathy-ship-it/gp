#!/usr/bin/env node
/**
 * scripts/lint-mutation-queue.mjs
 *
 * E12.2 deep validator for the mutation queue contract at
 * `contracts/pwa/mutation-queue-policy.yaml` and the platform
 * mirror at
 * `platform/helm/genealogy-platform/files/pwa/mutation-queue-policy.yaml`.
 *
 * Validates:
 *   - closed-set vocabularies: mutationKinds[20],
 *     mutationEntityKinds[11], mutationStates[9],
 *     mutationConflictReasons[13], syncTriggers[7],
 *     syncStrategies[4], mutationAuditEvents[13],
 *     mutationFailureReasons[11], storageTargets[1],
 *     mutationForbiddenPayloadKeys[25], egressAllowlist[2];
 *   - 2 state matrices (mutationQueueStateMatrix initial DRAFT,
 *     syncRunnerStateMatrix initial IDLE) — terminals PURGED /
 *     DISCARDED / ACCEPTED have empty transitions;
 *   - numeric bounds (13 numeric invariants);
 *   - 17 invariants (operationIdRequired, baseVersionRequired,
 *     tenantIdRequired, indexeddbOnly, localStorageForbidden,
 *     backgroundSyncAssumed=false, idempotencyHeaderMandatory,
 *     rateLimitHonored, tenantBoundaryEnforced,
 *     permissionVersionEnforced, openfgaRelationshipRecheck,
 *     abacRecheckAtSubmit, threeWayMergeOffered,
 *     dlqNotSilentlyDropped, auditEveryTransition,
 *     forbiddenPayloadKeysEnforced, foregroundRetry,
 *     serverSourceOfTruth);
 *   - capability boundaries — mutation-queue.ts MUST persist to
 *     IndexedDB only, MUST NOT use LocalStorage;
 *   - parity between contract file and helm chart mirror.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import {
  loadYaml,
  asArray,
  assertClosedSet,
  assertStateMatrix,
} from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT
  ? resolve(process.env.LINT_ROOT)
  : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/pwa/mutation-queue-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/pwa/mutation-queue-policy.yaml",
);

const REQUIRED_MUTATION_KINDS = [
  "PERSON_PATCH",
  "RELATIONSHIP_PATCH",
  "NAME_APPEND",
  "NAME_REPLACE",
  "EVENT_APPEND",
  "EVENT_REPLACE",
  "CITATION_APPEND",
  "CITATION_REPLACE",
  "ALBUM_LINK",
  "ALBUM_UNLINK",
  "NOTE_APPEND",
  "NOTE_REPLACE",
  "COMMENT_POST",
  "COMMENT_EDIT",
  "COMMENT_RESOLVE",
  "COLLABORATION_APPROVE",
  "COLLABORATION_REJECT",
  "COLLABORATION_REQUEST",
  "DRAFT_TREE_CREATE",
  "DRAFT_PERSON_CREATE",
];

const REQUIRED_ENTITY_KINDS = [
  "person",
  "relationship",
  "name",
  "event",
  "citation",
  "album",
  "note",
  "comment",
  "collaboration_request",
  "draft_tree",
  "draft_person",
];

const REQUIRED_MUTATION_STATES = [
  "DRAFT",
  "QUEUED",
  "SUBMITTING",
  "ACCEPTED",
  "CONFLICTING",
  "REJECTED",
  "DLQ",
  "DISCARDED",
  "PURGED",
];

const REQUIRED_CONFLICT_REASONS = [
  "VERSION_CONFLICT",
  "REDACTION_DENIED",
  "TENANT_MISMATCH",
  "PERMISSION_REVOKED",
  "SCHEMA_STALE",
  "RATE_LIMITED",
  "LIVING_RULE_CHANGED",
  "DNA_RULE_CHANGED",
  "CONSENT_REVOKED",
  "ENTITY_DELETED",
  "DUPLICATE_OPERATION_ID",
  "VALIDATION_FAILED",
  "ENCRYPTION_KEY_ROTATED",
];

const REQUIRED_SYNC_TRIGGERS = [
  "online",
  "visibilitychange",
  "pageshow",
  "periodic-timer",
  "manual-user",
  "mutation-queued",
  "app-foreground",
];

const REQUIRED_SYNC_STRATEGIES = [
  "IDEMPOTENT_SUBMIT",
  "CONFLICT_PRESERVE_LOCAL",
  "CONFLICT_PRESERVE_SERVER",
  "CONFLICT_THREE_WAY_MERGE",
];

const REQUIRED_AUDIT_EVENTS = [
  "mutation.enqueue",
  "mutation.dequeue",
  "mutation.submit",
  "mutation.accept",
  "mutation.conflict",
  "mutation.reject",
  "mutation.retry",
  "mutation.dlq",
  "mutation.discard",
  "mutation.purge",
  "mutation.idempotencyReplay",
  "mutation.versionMismatch",
  "mutation.permissionRevoked",
];

const REQUIRED_FAILURE_REASONS = [
  "QUEUE_FULL",
  "OPERATION_ID_MISSING",
  "BASE_VERSION_MISSING",
  "ENTITY_KIND_UNKNOWN",
  "MUTATION_KIND_UNKNOWN",
  "TENANT_ID_MISSING",
  "INDEXED_DB_UNAVAILABLE",
  "SERIALIZATION_FAILED",
  "SYNC_ALREADY_RUNNING",
  "OFFLINE_BACKOFF_ACTIVE",
  "PERMISSION_VERSION_STALE",
];

const REQUIRED_STORAGE_TARGETS = ["indexeddb"];

const REQUIRED_FORBIDDEN_PAYLOAD_KEYS = [
  "rawDna",
  "rawMedia",
  "dnaRawBytes",
  "dnaMatchResult",
  "signedUrlSecret",
  "oidcAccessToken",
  "oidcRefreshToken",
  "oidcIdToken",
  "rawWebhookSecret",
  "rawProviderApiKey",
  "rawKmsKey",
  "rawVaultToken",
  "rawSessionCookie",
  "rawPin",
  "rawBiometric",
  "rawDnaConsentToken",
  "rawExportToken",
  "rawS3AccessKey",
  "rawS3Secret",
  "treeViewerBypass",
  "rawGuardianReason",
  "rawSupportReason",
  "rawDeletionReason",
  "rawOnboardingToken",
  "rawOidcClientSecret",
];

const REQUIRED_EGRESS = [
  "api.genealogy-platform.example",
  "bff.genealogy-platform.example",
];

const QUEUE_STATE_STATUSES = [
  "DRAFT",
  "QUEUED",
  "SUBMITTING",
  "ACCEPTED",
  "CONFLICTING",
  "REJECTED",
  "DLQ",
  "DISCARDED",
  "PURGED",
];

const SYNC_STATE_STATUSES = [
  "IDLE",
  "DEBOUNCING",
  "RUNNING",
  "BACKING_OFF",
  "COOLDOWN",
  "SUSPENDED",
  "FAILED",
];

const NUMERIC_BOUNDS = [
  "maxQueueSize",
  "maxMutationBytes",
  "maxAttempts",
  "baseBackoffSeconds",
  "maxBackoffSeconds",
  "jitterRatio",
  "periodicTimerSeconds",
  "onlineDebounceMs",
  "visibilityDebounceMs",
  "idempotencyReplayTtlSeconds",
  "dlqRetentionDays",
  "conflictResolutionTimeoutSeconds",
];

const INVARIANTS = [
  "operationIdRequired",
  "baseVersionRequired",
  "tenantIdRequired",
  "indexeddbOnly",
  "localStorageForbidden",
  "backgroundSyncAssumed",
  "idempotencyHeaderMandatory",
  "rateLimitHonored",
  "tenantBoundaryEnforced",
  "permissionVersionEnforced",
  "openfgaRelationshipRecheck",
  "abacRecheckAtSubmit",
  "threeWayMergeOffered",
  "dlqNotSilentlyDropped",
  "auditEveryTransition",
  "forbiddenPayloadKeysEnforced",
  "foregroundRetry",
  "serverSourceOfTruth",
];

let violations = 0;

function ok(message) {
  process.stdout.write(`  ok  ${message}\n`);
}

function fail(message) {
  violations += 1;
  process.stderr.write(`  fail  ${message}\n`);
}

function readBoth() {
  const contractText = readFileSync(CONTRACT, "utf8");
  const chartText = readFileSync(CHART_FILE, "utf8");
  return { contractText, chartText, contract: loadYaml(contractText), chart: loadYaml(chartText) };
}

function checkParity(contractText, chartText) {
  if (contractText !== chartText) {
    fail("contract <-> helm chart mirror mismatch — byte-equal copy required");
    return;
  }
  ok("contract <-> helm chart mirror byte-equal");
}

function checkClosedSets(doc) {
  assertClosedSet("mutationKinds", REQUIRED_MUTATION_KINDS, asArray(doc.mutationKinds?.values), "E12.2 mutationKinds", ok, fail);
  assertClosedSet("mutationEntityKinds", REQUIRED_ENTITY_KINDS, asArray(doc.mutationEntityKinds?.values), "E12.2 mutationEntityKinds", ok, fail);
  assertClosedSet("mutationStates", REQUIRED_MUTATION_STATES, asArray(doc.mutationStates?.values), "E12.2 mutationStates", ok, fail);
  assertClosedSet("mutationConflictReasons", REQUIRED_CONFLICT_REASONS, asArray(doc.mutationConflictReasons?.values), "E12.2 mutationConflictReasons", ok, fail);
  assertClosedSet("syncTriggers", REQUIRED_SYNC_TRIGGERS, asArray(doc.syncTriggers?.values), "E12.2 syncTriggers", ok, fail);
  assertClosedSet("syncStrategies", REQUIRED_SYNC_STRATEGIES, asArray(doc.syncStrategies?.values), "E12.2 syncStrategies", ok, fail);
  assertClosedSet("mutationAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(doc.mutationAuditEvents?.values), "E12.2 mutationAuditEvents", ok, fail);
  assertClosedSet("mutationFailureReasons", REQUIRED_FAILURE_REASONS, asArray(doc.mutationFailureReasons?.values), "E12.2 mutationFailureReasons", ok, fail);
  assertClosedSet("storageTargets", REQUIRED_STORAGE_TARGETS, asArray(doc.storageTargets?.values), "E12.2 storageTargets", ok, fail);
  assertClosedSet("mutationForbiddenPayloadKeys", REQUIRED_FORBIDDEN_PAYLOAD_KEYS, asArray(doc.mutationForbiddenPayloadKeys?.values), "E12.2 mutationForbiddenPayloadKeys", ok, fail);
  assertClosedSet("egressAllowlist", REQUIRED_EGRESS, asArray(doc.egressAllowlist?.values), "E12.2 egressAllowlist", ok, fail);
}

function checkStateMatrices(doc) {
  const queueMatrix = doc.mutationQueueStateMatrix;
  assertStateMatrix(
    "E12.2 mutationQueueStateMatrix",
    queueMatrix,
    QUEUE_STATE_STATUSES,
    "DRAFT",
    ok,
    fail,
  );
  const syncMatrix = doc.syncRunnerStateMatrix;
  assertStateMatrix(
    "E12.2 syncRunnerStateMatrix",
    syncMatrix,
    SYNC_STATE_STATUSES,
    "IDLE",
    ok,
    fail,
  );
}

function checkNumericBounds(doc) {
  const bounds = doc.numericBounds || {};
  for (const key of NUMERIC_BOUNDS) {
    if (bounds[key] === undefined) {
      fail(`numericBounds.${key} missing`);
    }
  }
  if (bounds.maxBackoffSeconds !== undefined && bounds.baseBackoffSeconds !== undefined && bounds.maxBackoffSeconds < bounds.baseBackoffSeconds) {
    fail(`numericBounds.maxBackoffSeconds (${bounds.maxBackoffSeconds}) MUST be >= baseBackoffSeconds (${bounds.baseBackoffSeconds})`);
  }
  if (bounds.jitterRatio !== undefined && (bounds.jitterRatio < 0 || bounds.jitterRatio > 1)) {
    fail(`numericBounds.jitterRatio (${bounds.jitterRatio}) MUST be in [0, 1]`);
  }
  ok(`numericBounds (${NUMERIC_BOUNDS.length} entries)`);
}

function checkInvariants(doc) {
  const inv = doc.invariants || {};
  for (const key of INVARIANTS) {
    if (inv[key] === undefined) {
      fail(`invariants.${key} missing`);
    }
  }
  if (inv.operationIdRequired !== true) fail("invariants.operationIdRequired MUST be true");
  if (inv.baseVersionRequired !== true) fail("invariants.baseVersionRequired MUST be true");
  if (inv.tenantIdRequired !== true) fail("invariants.tenantIdRequired MUST be true");
  if (inv.indexeddbOnly !== true) fail("invariants.indexeddbOnly MUST be true");
  if (inv.localStorageForbidden !== true) fail("invariants.localStorageForbidden MUST be true");
  if (inv.backgroundSyncAssumed !== false) fail("invariants.backgroundSyncAssumed MUST be false");
  if (inv.idempotencyHeaderMandatory !== true) fail("invariants.idempotencyHeaderMandatory MUST be true");
  if (inv.threeWayMergeOffered !== true) fail("invariants.threeWayMergeOffered MUST be true");
  if (inv.dlqNotSilentlyDropped !== true) fail("invariants.dlqNotSilentlyDropped MUST be true");
  if (inv.auditEveryTransition !== true) fail("invariants.auditEveryTransition MUST be true");
  if (inv.foregroundRetry !== true) fail("invariants.foregroundRetry MUST be true");
  if (inv.serverSourceOfTruth !== true) fail("invariants.serverSourceOfTruth MUST be true");
  ok(`invariants (${INVARIANTS.length} invariants)`);
}

function checkStorageBackend() {
  const files = [
    "apps/web/src/lib/pwa/mutation-queue.ts",
    "apps/web/src/lib/pwa/idempotency.ts",
    "apps/web/src/lib/pwa/sync-runner.ts",
    "apps/web/src/lib/pwa/conflict-resolution.ts",
  ];
  let scanned = 0;
  for (const rel of files) {
    const abs = join(ROOT, rel);
    let text;
    try {
      text = readFileSync(abs, "utf8");
    } catch (err) {
      fail(`${rel} missing (${err.code})`);
      continue;
    }
    scanned += 1;
    if (/localStorage/.test(text)) {
      fail(`${rel} MUST NOT reference localStorage (queue is IndexedDB-only)`);
    }
  }
  if (scanned === files.length) {
    ok(`scanned ${scanned} queue modules (no localStorage references)`);
  }
}

function checkIdempotencyHeader() {
  const helper = join(ROOT, "apps/web/src/lib/pwa/idempotency.ts");
  let text;
  try {
    text = readFileSync(helper, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/pwa/idempotency.ts missing (${err.code})`);
    return;
  }
  if (!/Idempotency-Key/.test(text)) {
    fail("idempotency.ts MUST use Idempotency-Key header (E1.3)");
  }
  if (!/operationId/.test(text)) {
    fail("idempotency.ts MUST bind the header to operationId");
  }
  ok("idempotency.ts uses Idempotency-Key header bound to operationId");
}

function checkSyncRunnerTriggers() {
  const runner = join(ROOT, "apps/web/src/lib/pwa/sync-runner.ts");
  let text;
  try {
    text = readFileSync(runner, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/pwa/sync-runner.ts missing (${err.code})`);
    return;
  }
  for (const trigger of REQUIRED_SYNC_TRIGGERS) {
    if (!text.includes(trigger)) {
      fail(`sync-runner.ts MUST handle trigger "${trigger}"`);
    }
  }
  ok("sync-runner.ts handles all 7 sync triggers");
}

function checkConflictResolution() {
  const conflict = join(ROOT, "apps/web/src/lib/pwa/conflict-resolution.ts");
  let text;
  try {
    text = readFileSync(conflict, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/pwa/conflict-resolution.ts missing (${err.code})`);
    return;
  }
  for (const reason of ["VERSION_CONFLICT", "REDACTION_DENIED", "TENANT_MISMATCH", "PERMISSION_REVOKED"]) {
    if (!text.includes(reason)) {
      fail(`conflict-resolution.ts MUST handle reason "${reason}"`);
    }
  }
  if (!/threeWay|three-way/.test(text)) {
    fail("conflict-resolution.ts MUST implement three-way merge");
  }
  ok("conflict-resolution.ts covers 4 conflict reasons + three-way merge");
}

function main() {
  let data;
  try {
    data = readBoth();
  } catch (err) {
    process.stderr.write(`config error: ${err.message}\n`);
    process.exit(2);
  }

  process.stdout.write("E12.2 mutation-queue linter\n");
  checkParity(data.contractText, data.chartText);
  checkClosedSets(data.contract);
  checkStateMatrices(data.contract);
  checkNumericBounds(data.contract);
  checkInvariants(data.contract);
  checkStorageBackend();
  checkIdempotencyHeader();
  checkSyncRunnerTriggers();
  checkConflictResolution();

  process.stdout.write(`\nE12.2 summary: ${violations === 0 ? "OK" : `${violations} violation(s)`}\n`);
  process.exit(violations === 0 ? 0 : 1);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}