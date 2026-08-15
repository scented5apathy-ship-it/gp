#!/usr/bin/env node
/**
 * scripts/lint-dna-raw-upload.mjs
 *
 * E10.4 deep validator for the DNA raw upload and matching contract.
 * Mirrors the E9 / E10.2 / E10.3 linter pattern.
 *
 *   - closed-set vocabularies: dnaProviders[7], dnaFormats[8],
 *     dnaMatchAlgorithms[6], dnaMatchStages[8], dnaMatchStatuses[9],
 *     dnaMatchFailureReasons[16], dnaMatchAuditEvents[14],
 *     dnaMatchEventTypes[3];
 *   - sandbox egress allowlist (7 DNA-sandbox endpoints);
 *   - 2 state matrices (dnaMatchStateMatrix initial=QUEUED,
 *     dnaUploadStateMatrix initial=QUARANTINED);
 *   - 26 boolean guard rails;
 *   - numeric bounds + invariants (decrypt >= 12× quarantine;
 *     segments >= 50× kits; min SNPs >= 71× cM);
 *   - outbox envelope + audit hooks + forbidden payload patterns
 *     + capability boundaries;
 *   - chart mirror byte-equality.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray, assertClosedSet, assertStateMatrix } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/dna/dna-raw-upload-matching-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/dna/dna-raw-upload-matching-policy.yaml",
);

const REQUIRED_PROVIDERS = [
  "ANCESTRYDNA",
  "TWENTY_THREE_AND_ME",
  "MYHERITAGE",
  "FAMILY_TREE_DNA",
  "LIVING_DNA",
  "SELF_UPLOAD_PROVIDED",
  "RESEARCH_PARTNER",
];

const REQUIRED_FORMATS = [
  "CSV_ANCESTRYDNA",
  "CSV_TWENTY_THREE_AND_ME",
  "CSV_MYHERITAGE",
  "CSV_FAMILY_TREE_DNA",
  "FASTQ_ILLUMINA",
  "BAM_ILLUMINA",
  "VCF_GENEVA",
  "VCF_ANCESTRY",
];

const REQUIRED_MATCH_ALGORITHMS = [
  "IBD_SEGMENT_V1",
  "IBD_SEGMENT_V2",
  "IBD_SEGMENT_V2_OPAQUE",
  "KINSHIP_ESTIMATE_V1",
  "KINSHIP_ESTIMATE_V2_OPAQUE",
  "RELATIVE_DISCOVERY_V1",
];

const REQUIRED_MATCH_STAGES = [
  "QUARANTINED",
  "FORMAT_VALIDATED",
  "ENVELOPE_ENCRYPTED",
  "DECRYPTED_IN_MEMORY",
  "SEGMENTED",
  "ESTIMATED",
  "ANNOTATED",
  "COMMITTED",
];

const REQUIRED_MATCH_STATUSES = [
  "QUEUED",
  "RUNNING",
  "AWAITING_CONSENT",
  "AWAITING_INPUT",
  "COMPLETED",
  "FAILED",
  "REVOKED",
  "CANCELLED",
  "BLOCKED",
];

const REQUIRED_MATCH_FAILURE_REASONS = [
  "DNA_UPLOAD_QUARANTINE_TIMEOUT",
  "DNA_FORMAT_UNSUPPORTED",
  "DNA_FORMAT_VALIDATION_FAILED",
  "DNA_ENVELOPE_DECRYPT_FAILED",
  "DNA_ENVELOPE_KEY_REVOKED",
  "DNA_UPLOAD_PAYLOAD_TOO_LARGE",
  "DNA_UPLOAD_PAYLOAD_ENCODING_INVALID",
  "DNA_UPLOAD_PAYLOAD_DEPTH_EXCEEDED",
  "DNA_MATCH_ALGORITHM_VERSION_UNKNOWN",
  "DNA_MATCH_KIT_NOT_FOUND",
  "DNA_MATCH_TENANT_MISMATCH",
  "DNA_MATCH_CONSENT_REVOKED",
  "DNA_MATCH_CONSENT_EXPIRED",
  "DNA_MATCH_WORKER_POOL_VIOLATED",
  "DNA_RAW_DNA_LEAK_DETECTED",
  "DNA_PUBLIC_PREVIEW_FORBIDDEN",
];

const REQUIRED_MATCH_AUDIT_EVENTS = [
  "DNA_UPLOAD_RECEIVED",
  "DNA_UPLOAD_QUARANTINED",
  "DNA_UPLOAD_FORMAT_VALIDATED",
  "DNA_UPLOAD_ENCRYPTED",
  "DNA_UPLOAD_REJECTED",
  "DNA_MATCH_REQUESTED",
  "DNA_MATCH_STARTED",
  "DNA_MATCH_SEGMENTED",
  "DNA_MATCH_ESTIMATED",
  "DNA_MATCH_COMPLETED",
  "DNA_MATCH_FAILED",
  "DNA_MATCH_CONSENT_REVOKED",
  "DNA_RAW_DNA_LEAK_BLOCKED",
  "DNA_PUBLIC_PREVIEW_BLOCKED",
];

const REQUIRED_MATCH_EVENT_TYPES = [
  "gp.dna.v1.KitUploaded",
  "gp.dna.v1.MatchProduced",
  "gp.dna.v1.MatchRevoked",
];

const REQUIRED_SANDBOX_EGRESS = [
  "postgres-dna",
  "vault-agent-dna",
  "s3-dna",
  "openfga-dna",
  "audit-service",
  "kafka-dna",
  "temporal-frontend-dna",
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
  "gp.dna.v1.KitUploaded",
  "gp.dna.v1.MatchProduced",
];

const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = [
  "rawDnaSequence",
  "rawFastq",
  "rawBam",
  "rawVcf",
  "rawAlleleList",
  "rawSnpTable",
  "rawGenotype",
  "rawHaplogroup",
  "rawChromosomeMap",
  "rawSegmentSummary",
  "rawCentiMorganEstimate",
  "rawMatchAlgorithmTrace",
  "rawIdDocument",
  "rawSignatureImage",
  "exifGps",
  "cameraSerial",
  "passportNumber",
  "socialSecurityNumber",
  "rawEmail",
  "rawPhone",
  "rawAddress",
  "rawLivingStatus",
  "rawMinorStatus",
  "rawConsentDocument",
  "rawMedicalRecord",
  "productionPii",
];

const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom durable queue (Temporal namespace + task queue is enough)",
  "Cross-service aggregation into DNA (use Kafka events + publisher resolution)",
  "Custom matching algorithm outside dna-service sandbox",
  "Self-hosted raw-genotype interpreter (per privacy-and-legal-gate.md §14 #2)",
  "Medical interpretation engine (per privacy-and-legal-gate.md §14 #3)",
  "Raw DNA in Kafka / log / trace / metrics / search / preview / public API / notification / webhook / sitemap",
  "Tree-role grant of matching access (requires explicit consent subject)",
  "Cross-region matching without jurisdiction + residency check",
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
    "dnaProviders",
    REQUIRED_PROVIDERS,
    asArray(contract.dnaProviders?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaFormats",
    REQUIRED_FORMATS,
    asArray(contract.dnaFormats?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaMatchAlgorithms",
    REQUIRED_MATCH_ALGORITHMS,
    asArray(contract.dnaMatchAlgorithms?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaMatchStages",
    REQUIRED_MATCH_STAGES,
    asArray(contract.dnaMatchStages?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaMatchStatuses",
    REQUIRED_MATCH_STATUSES,
    asArray(contract.dnaMatchStatuses?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaMatchFailureReasons",
    REQUIRED_MATCH_FAILURE_REASONS,
    asArray(contract.dnaMatchFailureReasons?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaMatchAuditEvents",
    REQUIRED_MATCH_AUDIT_EVENTS,
    asArray(contract.dnaMatchAuditEvents?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaMatchEventTypes",
    REQUIRED_MATCH_EVENT_TYPES,
    asArray(contract.dnaMatchEventTypes?.values),
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
    "dnaMatchStateMatrix",
    contract.dnaMatchStateMatrix,
    REQUIRED_MATCH_STATUSES,
    "QUEUED",
    ok,
    fail,
  );
  assertStateMatrix(
    "dnaUploadStateMatrix",
    contract.dnaUploadStateMatrix,
    REQUIRED_MATCH_STAGES.concat("REJECTED"),
    "QUARANTINED",
    ok,
    fail,
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["uploadQuarantineMandatory", true],
    ["formatValidationRequired", true],
    ["envelopeEncryptionRequired", true],
    ["kmsDekPerUpload", true],
    ["workerPoolIsolation", true],
    ["workerPoolLabelRequired", true],
    ["taskQueueIsolation", true],
    ["algorithmVersionPinned", true],
    ["algorithmVersionVisibleOnlyAsLabel", true],
    ["rawDnaInKafkaForbidden", true],
    ["rawDnaInLogsForbidden", true],
    ["rawDnaInTracesForbidden", true],
    ["rawDnaInMetricsForbidden", true],
    ["rawDnaInSearchForbidden", true],
    ["rawDnaInMediaPreviewForbidden", true],
    ["rawDnaInPublicApiForbidden", true],
    ["rawDnaInNotificationPayloadForbidden", true],
    ["rawDnaInOutboxForbidden", true],
    ["rawDnaInWebhookForbidden", true],
    ["rawDnaInSitemapForbidden", true],
    ["matchOutputIsOpaque", true],
    ["matchSegmentPrivacyRedacted", true],
    ["consentReauthorizeAtActivityTime", true],
    ["workerPoolAdmissionControllerRequired", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["tenantBoundaryOnEveryRepository", true],
    ["auditHookOnEveryUpload", true],
    ["auditHookOnEveryMatch", true],
    ["auditHookOnEveryRevoke", true],
  ];
  for (const [key, expected] of booleanGuards) {
    if (gr[key] !== expected) {
      fail(`guardRails.${key} MUST be ${expected} (got ${gr[key]})`);
    } else {
      ok(`guardRails.${key} = ${expected}`);
    }
  }

  const nb = contract.numericBounds || {};
  const numericGuards = {
    uploadMaxPayloadBytes: 26214400,
    uploadQuarantineTtlSeconds: 3600,
    uploadEnvelopeDecryptTimeoutSeconds: 300,
    matchMaxConcurrentAlgorithms: 8,
    matchMaxKitsPerRequest: 1000,
    matchMaxSegmentsPerKit: 50000,
    matchWorkerPoolSizeMinimum: 2,
    matchWorkerPoolMaxNodes: 16,
    matchAlgorithmVersionLabelMaxBytes: 64,
    matchSegmentMinCentimorgans: 7,
    matchSegmentMinSnps: 500,
    matchEstimateConfidenceFloorPercent: 50,
    rawDnaPayloadBytesAllowed: 0,
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
    uploadMaxPayloadBytes: 26214400,
    uploadQuarantineTtlSeconds: 3600,
    uploadEnvelopeDecryptTimeoutSeconds: 300,
    uploadDecryptToQuarantineMultiplier: 12,
    matchMaxConcurrentAlgorithms: 8,
    matchMaxKitsPerRequest: 1000,
    matchMaxSegmentsPerKit: 50000,
    matchSegmentsToKitsMultiplier: 50,
    matchSegmentMinCentimorgans: 7,
    matchSegmentMinSnps: 500,
    matchSegmentMinSnpsToCentiMultiplier: 71,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (
    nb.uploadEnvelopeDecryptTimeoutSeconds * ri.uploadDecryptToQuarantineMultiplier
    > nb.uploadQuarantineTtlSeconds
  ) {
    fail(
      `upload invariant violated: decryptTimeout=${nb.uploadEnvelopeDecryptTimeoutSeconds}s × ${ri.uploadDecryptToQuarantineMultiplier} MUST be <= quarantineTtl=${nb.uploadQuarantineTtlSeconds}s`,
    );
  } else {
    ok(`upload invariant: ${nb.uploadEnvelopeDecryptTimeoutSeconds} × ${ri.uploadDecryptToQuarantineMultiplier} <= ${nb.uploadQuarantineTtlSeconds}`);
  }
  if (
    nb.matchMaxSegmentsPerKit
    < ri.matchSegmentsToKitsMultiplier * nb.matchMaxKitsPerRequest
  ) {
    fail(
      `segments invariant violated: maxSegments=${nb.matchMaxSegmentsPerKit} MUST be >= ${ri.matchSegmentsToKitsMultiplier} × maxKits=${nb.matchMaxKitsPerRequest}`,
    );
  } else {
    ok(`segments invariant: ${nb.matchMaxSegmentsPerKit} >= ${ri.matchSegmentsToKitsMultiplier} × ${nb.matchMaxKitsPerRequest}`);
  }
  if (
    nb.matchSegmentMinSnps
    < ri.matchSegmentMinSnpsToCentiMultiplier * nb.matchSegmentMinCentimorgans
  ) {
    fail(
      `snps invariant violated: minSnps=${nb.matchSegmentMinSnps} MUST be >= ${ri.matchSegmentMinSnpsToCentiMultiplier} × minCenti=${nb.matchSegmentMinCentimorgans}`,
    );
  } else {
    ok(`snps invariant: ${nb.matchSegmentMinSnps} >= ${ri.matchSegmentMinSnpsToCentiMultiplier} × ${nb.matchSegmentMinCentimorgans}`);
  }
  if (nb.rawDnaPayloadBytesAllowed !== 0) {
    fail(`rawDnaPayloadBytesAllowed MUST equal 0 (got ${nb.rawDnaPayloadBytesAllowed})`);
  } else {
    ok(`rawDnaPayloadBytesAllowed = 0`);
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
    REQUIRED_MATCH_AUDIT_EVENTS,
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
  console.log("\nE10.4 DNA raw upload + matching policy contract OK.");
}

main();