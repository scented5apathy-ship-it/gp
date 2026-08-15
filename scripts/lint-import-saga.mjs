#!/usr/bin/env node
/**
 * scripts/lint-import-saga.mjs
 *
 * E9.3 deep validator for the import saga policy contract under
 * `contracts/importexport/import-saga-policy.yaml` and the platform
 * mirror under
 * `platform/helm/genealogy-platform/files/import-saga-policy.yaml`.
 *
 * Closed-set vocabularies: importSagaPhases[10],
 * importSagaStatuses[12], importMappingStrategies[6],
 * importDedupOutcomes[6], importChunkCommitStrategies[4],
 * importReconciliationTargets[5], importSagaFailureReasons[14],
 * importSagaAuditEvents[13], importSagaConfirmationModes[5],
 * importSagaCompensationActions[5]; sandbox egress allowlist;
 * 1 state matrix (initial=QUEUED); 19 boolean guards; 24 numeric
 * bounds; 3 invariants (chunk commit timeout ≥ 10×heartbeat,
 * compensation timeout ≥ 20×heartbeat, reconciliation timeout
 * ≥ 15×heartbeat); outbox envelope; audit hooks; forbidden
 * payload patterns; capability boundaries; chart mirror
 * byte-equality.
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

const CONTRACT = join(ROOT, "contracts/importexport/import-saga-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/import-saga-policy.yaml");

const REQUIRED_PHASES = ["PREVIEWING", "MAPPING", "DEDUPING", "CONFIRMING", "CHUNKING", "COMMITTING", "RECONCILING", "COMPENSATING", "CANCELLED", "FINALIZED"];
const REQUIRED_STATUSES = ["QUEUED", "PREVIEWING", "PREVIEW_READY", "AWAITING_CONFIRMATION", "CHUNKING", "CHUNK_COMMITTING", "RECONCILING", "COMPLETED", "CANCELLED", "COMPENSATING", "COMPENSATED", "FAILED", "BLOCKED"];
const REQUIRED_MAPPING_STRATEGIES = ["EXACT_KEY_MATCH", "FUZZY_NAME_MATCH", "PLACE_NORMALIZED_MATCH", "DATE_NORMALIZED_MATCH", "CITATION_PROVENANCE_MATCH", "MANUAL_OVERRIDE"];
const REQUIRED_DEDUP_OUTCOMES = ["NEW", "DUPLICATE_AUTO_MERGE", "DUPLICATE_CANDIDATE", "DUPLICATE_USER_MERGED", "DUPLICATE_REJECTED", "DRY_RUN_REPORT_ONLY"];
const REQUIRED_CHUNK_STRATEGIES = ["SEQUENTIAL", "PARALLEL_BOUNDED", "RATE_LIMITED", "TENANT_SCOPED"];
const REQUIRED_RECONCILIATION_TARGETS = ["SEARCH_PROJECTION", "COLLABORATION_TIMELINE", "MEDIA_INDEX", "TREE_RENDERER_CACHE", "PUBLIC_PROJECTION"];
const REQUIRED_FAILURE_REASONS = ["IMPORT_PREVIEW_NOT_FOUND", "IMPORT_PREVIEW_EXPIRED", "IMPORT_DEDUP_CANDIDATES_TOO_MANY", "IMPORT_DEDUP_STRATEGY_UNKNOWN", "IMPORT_CHUNK_TOO_LARGE", "IMPORT_CHUNK_COMMIT_TIMEOUT", "IMPORT_CHUNK_COMMIT_FAILED", "IMPORT_COMPENSATION_FAILED", "IMPORT_DUPLICATE_RUN_REJECTED", "IMPORT_RECONCILIATION_FAILED", "IMPORT_DNA_BUCKET_FORBIDDEN", "IMPORT_PII_LEAK_DETECTED", "IMPORT_TENANT_MISMATCH", "IMPORT_USER_PROVIDED_PAYLOAD_TOO_LARGE"];
const REQUIRED_AUDIT_EVENTS = ["IMPORT_PREVIEW_QUEUED", "IMPORT_PREVIEW_READY", "IMPORT_DEDUP_CANDIDATES_READY", "IMPORT_CONFIRMATION_RECEIVED", "IMPORT_CHUNK_STARTED", "IMPORT_CHUNK_COMMITTED", "IMPORT_CHUNK_COMPENSATED", "IMPORT_RECONCILIATION_STARTED", "IMPORT_RECONCILIATION_FINISHED", "IMPORT_DNA_BUCKET_REFUSED", "IMPORT_PII_LEAK_REFUSED", "IMPORT_FINALIZED", "IMPORT_FAILED"];
const REQUIRED_CONFIRMATION_MODES = ["USER_ACCEPT_ALL", "USER_ACCEPT_PER_CANDIDATE", "USER_ACCEPT_PER_RECORD", "ADMIN_FORCE_COMMIT", "DRY_RUN_ONLY"];
const REQUIRED_COMPENSATION_ACTIONS = ["DELETE_IMPORTED_CHUNK", "UNDO_DEDUP_MERGE", "REVERT_PROJECTION_RECONCILIATION", "REVOKE_TEMP_CREDENTIAL", "RESTORE_PREVIEW_EXPIRY"];
const REQUIRED_SANDBOX_EGRESS = ["postgres", "apicurio", "vault-agent", "openfga", "audit-service", "kafka-broker", "temporal-frontend"];
const REQUIRED_DNA_BUCKET_PREFIXES = ["dna/raw", "dna/match", "dna/consent"];
const REQUIRED_OUTBOX_FIELDS = ["eventId", "eventType", "occurredAt", "tenantId", "aggregateId", "aggregateVersion", "traceId", "payload"];
const REQUIRED_OUTBOX_TYPES = ["gp.importexport.v1.ImportPreviewReady", "gp.importexport.v1.ImportChunkCommitted", "gp.importexport.v1.ImportReconciled"];
const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = ["rawDnaSequence", "rawFastq", "rawBam", "rawVcf", "exifGps", "cameraSerial", "passportNumber", "socialSecurityNumber", "nameOnBirth", "rawEmail", "rawPhone", "rawAddress", "biometricTemplate", "rawFacialEmbedding", "rawLivingStatus", "rawMinorStatus", "rawConsentDocument", "rawSocialSecurityNumber", "rawPassport", "rawDriverLicense", "rawTaxId", "rawMedicalRecord", "rawPaymentInstrument", "productionPii"];
const REQUIRED_CAPABILITY_FORBIDDEN = ["Custom dedup matcher (use OpenSearch trigram or PostgreSQL pg_trgm)", "Distributed transaction (outbox relay + chunked commit is enough)", "Generic job-state / retry scheduler (Temporal per ADR-E0.5-07)", "Custom preview approval UI (re-use platform components)", "Custom long-running transaction (chunked commit + checkpoint is enough)", "Cross-service aggregation (use Kafka events + publisher resolution)"];

const violations = [];
const ok = (m) => console.log(`OK  ${m}`);
const fail = (m) => { violations.push(m); console.error(`FAIL ${m}`); };

function main() {
  let contract;
  try { contract = loadYaml(readFileSync(CONTRACT, "utf8")); } catch (err) { fail(`could not read contract: ${err.message}`); process.exit(2); }
  if (!contract || typeof contract !== "object") { fail("contract empty"); process.exit(2); }

  assertClosedSet("importSagaPhases", REQUIRED_PHASES, asArray(contract.importSagaPhases?.values), undefined, ok, fail);
  assertClosedSet("importSagaStatuses", REQUIRED_STATUSES, asArray(contract.importSagaStatuses?.values), undefined, ok, fail);
  assertClosedSet("importMappingStrategies", REQUIRED_MAPPING_STRATEGIES, asArray(contract.importMappingStrategies?.values), undefined, ok, fail);
  assertClosedSet("importDedupOutcomes", REQUIRED_DEDUP_OUTCOMES, asArray(contract.importDedupOutcomes?.values), undefined, ok, fail);
  assertClosedSet("importChunkCommitStrategies", REQUIRED_CHUNK_STRATEGIES, asArray(contract.importChunkCommitStrategies?.values), undefined, ok, fail);
  assertClosedSet("importReconciliationTargets", REQUIRED_RECONCILIATION_TARGETS, asArray(contract.importReconciliationTargets?.values), undefined, ok, fail);
  assertClosedSet("importSagaFailureReasons", REQUIRED_FAILURE_REASONS, asArray(contract.importSagaFailureReasons?.values), undefined, ok, fail);
  assertClosedSet("importSagaAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(contract.importSagaAuditEvents?.values), undefined, ok, fail);
  assertClosedSet("importSagaConfirmationModes", REQUIRED_CONFIRMATION_MODES, asArray(contract.importSagaConfirmationModes?.values), undefined, ok, fail);
  assertClosedSet("importSagaCompensationActions", REQUIRED_COMPENSATION_ACTIONS, asArray(contract.importSagaCompensationActions?.values), undefined, ok, fail);
  assertClosedSet("sandboxEgressAllowlist", REQUIRED_SANDBOX_EGRESS, asArray(contract.sandboxEgressAllowlist?.values), "sandbox egress allowlist", ok, fail);

  assertStateMatrix("importSagaStateMatrix", contract.importSagaStateMatrix, REQUIRED_STATUSES, "QUEUED", ok, fail);

  const gr = contract.guardRails || {};
  for (const [k, v] of [["previewBeforeCommitRequired", true], ["userConfirmationRequired", true], ["dryRunBeforeChunking", true], ["chunkCommitBoundedParallelism", true], ["compensationReversible", true], ["compensationIdempotent", true], ["checkpointVersioned", true], ["outboxRelaySeparated", true], ["crossServiceReferencesAreOpaque", true], ["crossServiceReferencesRequirePublisherResolution", true], ["tenantBoundaryOnEveryRepository", true], ["longTransactionForbidden", true], ["secretInPayloadForbidden", true], ["auditHookOnEveryChunkCommit", true], ["auditHookOnEveryCompensation", true], ["adrRequiredBeforeExternalMatcher", true]]) {
    if (gr[k] !== v) fail(`guardRails.${k} MUST be ${v} (got ${gr[k]})`); else ok(`guardRails.${k} = ${v}`);
  }
  if (gr.dnaBucketAccess !== "FORBIDDEN") fail(`guardRails.dnaBucketAccess MUST equal FORBIDDEN (got ${gr.dnaBucketAccess})`); else ok("guardRails.dnaBucketAccess = FORBIDDEN");
  assertClosedSet("guardRails.dnaBucketPrefixes", REQUIRED_DNA_BUCKET_PREFIXES, asArray(gr.dnaBucketPrefixes), "DNA bucket prefixes", ok, fail);

  const nb = contract.numericBounds || {};
  const expected = {
    importPreviewMaxRecords: 1000000,
    importPreviewMaxCandidatesPerRecord: 50,
    importPreviewExpirySeconds: 604800,
    importChunkMaxRecords: 1000,
    importChunkMaxBytes: 10485760,
    importChunkMaxConcurrentCommits: 8,
    importChunkCommitTimeoutSeconds: 300,
    importChunkCommitHeartbeatSeconds: 30,
    importChunkBackoffCoefficient: 2,
    importChunkBackoffInitialSeconds: 5,
    importChunkBackoffMaximumSeconds: 60,
    importCompensationTimeoutSeconds: 600,
    importCompensationHeartbeatSeconds: 30,
    importReconciliationTimeoutSeconds: 900,
    importReconciliationHeartbeatSeconds: 60,
    importDedupFuzzyThreshold: 0.85,
    importNameMaxLength: 256,
    importAliasMaxLength: 256,
    importAliasPerRecordMax: 64,
    importNoteMaxLength: 4096,
    importSagaIdLength: 64,
    importActorPseudoIdLength: 64,
    importTenantPseudoIdLength: 64,
    importCorrelationIdLength: 128,
    importUserPayloadMaxBytes: 52428800,
  };
  for (const [k, v] of Object.entries(expected)) if (nb[k] !== v) fail(`numericBounds.${k} MUST equal ${v} (got ${nb[k]})`); else ok(`numericBounds.${k} = ${v}`);

  const ri = contract.reconciliationInvariants || {};
  const inv = { importChunkCommitTimeoutSeconds: 300, importChunkCommitHeartbeatSeconds: 30, importChunkCommitHeartbeatMultiplier: 10, importCompensationTimeoutSeconds: 600, importCompensationHeartbeatSeconds: 30, importCompensationHeartbeatMultiplier: 20, importReconciliationTimeoutSeconds: 900, importReconciliationHeartbeatSeconds: 60, importReconciliationHeartbeatMultiplier: 15 };
  for (const [k, v] of Object.entries(inv)) if (ri[k] !== v) fail(`reconciliationInvariants.${k} MUST equal ${v} (got ${ri[k]})`); else ok(`reconciliationInvariants.${k} = ${v}`);
  if (nb.importChunkCommitTimeoutSeconds < ri.importChunkCommitHeartbeatMultiplier * nb.importChunkCommitHeartbeatSeconds) fail(`chunk commit invariant violated: timeout=${nb.importChunkCommitTimeoutSeconds}s MUST be >= ${ri.importChunkCommitHeartbeatMultiplier} × heartbeat=${nb.importChunkCommitHeartbeatSeconds}s`);
  else ok(`chunk commit invariant: timeout=${nb.importChunkCommitTimeoutSeconds} >= ${ri.importChunkCommitHeartbeatMultiplier} × ${nb.importChunkCommitHeartbeatSeconds}`);
  if (nb.importCompensationTimeoutSeconds < ri.importCompensationHeartbeatMultiplier * nb.importCompensationHeartbeatSeconds) fail(`compensation invariant violated: timeout=${nb.importCompensationTimeoutSeconds}s MUST be >= ${ri.importCompensationHeartbeatMultiplier} × heartbeat=${nb.importCompensationHeartbeatSeconds}s`);
  else ok(`compensation invariant: timeout=${nb.importCompensationTimeoutSeconds} >= ${ri.importCompensationHeartbeatMultiplier} × ${nb.importCompensationHeartbeatSeconds}`);
  if (nb.importReconciliationTimeoutSeconds < ri.importReconciliationHeartbeatMultiplier * nb.importReconciliationHeartbeatSeconds) fail(`reconciliation invariant violated: timeout=${nb.importReconciliationTimeoutSeconds}s MUST be >= ${ri.importReconciliationHeartbeatMultiplier} × heartbeat=${nb.importReconciliationHeartbeatSeconds}s`);
  else ok(`reconciliation invariant: timeout=${nb.importReconciliationTimeoutSeconds} >= ${ri.importReconciliationHeartbeatMultiplier} × ${nb.importReconciliationHeartbeatSeconds}`);

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

  assertClosedSet("auditHooks.auditRequired", REQUIRED_AUDIT_EVENTS, asArray(contract.auditHooks?.auditRequired), "auditHooks.auditRequired", ok, fail);
  assertClosedSet("forbiddenPayloadPatterns", REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS, asArray(contract.forbiddenPayloadPatterns), "forbidden payload patterns", ok, fail);
  assertClosedSet("capabilityBoundaries.forbiddenSelfBuilt", REQUIRED_CAPABILITY_FORBIDDEN, asArray(contract.capabilityBoundaries?.forbiddenSelfBuilt), "capability boundaries", ok, fail);

  try {
    const a = readFileSync(CONTRACT, "utf8");
    const b = readFileSync(CHART_FILE, "utf8");
    if (a !== b) fail(`chart mirror drift`); else ok(`chart mirror byte-equal (${a.length} bytes)`);
  } catch (err) { fail(`chart mirror check failed: ${err.message}`); }

  if (violations.length > 0) { console.error(`\n${violations.length} violation(s).`); process.exit(1); }
  console.log("\nE9.3 import saga policy contract OK.");
}

main();