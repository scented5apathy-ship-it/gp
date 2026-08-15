#!/usr/bin/env node
/**
 * scripts/lint-dna-revoke.mjs
 *
 * E10.5 deep validator for the DNA revoke / export / delete contract.
 * Mirrors the E9 / E10.2 / E10.3 / E10.4 linter pattern.
 *
 *   - closed-set vocabularies: dnaRevokeTriggers[8], dnaRevokePhases[9],
 *     dnaRevokeStatuses[8], dnaRevokeCompensationActions[12],
 *     dnaExportFormats[6], dnaExportRedactionLevels[7],
 *     dnaExportRetentionPolicies[4], dnaRevokeFailureReasons[14],
 *     dnaRevokeAuditEvents[13];
 *   - sandbox egress allowlist (7 DNA-sandbox endpoints);
 *   - 2 state matrices (dnaRevokeStateMatrix initial=QUEUED,
 *     dnaExportStateMatrix initial=REQUESTED);
 *   - 27 boolean guard rails;
 *   - numeric bounds + invariants (deadline >= 5× grace,
 *     signed URL TTL >= 120× revocation, evidence <= bundle/500,
 *     hard-delete <= retention/105120);
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

const CONTRACT = join(ROOT, "contracts/dna/dna-revoke-export-delete-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/dna/dna-revoke-export-delete-policy.yaml",
);

const REQUIRED_TRIGGERS = [
  "CONSENT_REVOKED",
  "MEMBERSHIP_REVOKED",
  "DATA_SUBJECT_DELETION_REQUEST",
  "DATA_SUBJECT_PORTABILITY_REQUEST",
  "LEGAL_HOLD_RELEASED",
  "JURISDICTION_BAN_TRIGGERED",
  "PROVIDER_TERMINATION_TRIGGERED",
  "ADMIN_REVOKE_WITH_LEGAL_APPROVAL",
];

const REQUIRED_PHASES = [
  "TRIGGERED",
  "IN_FLIGHT_CANCELLED",
  "SHARING_REVOKED",
  "DERIVED_PURGED",
  "RAW_DELETED",
  "LEGAL_HOLD_OVERRIDE",
  "EVIDENCE_ISSUED",
  "REVOKE_COMPLETED",
  "REVOKE_FAILED",
];

const REQUIRED_STATUSES = [
  "QUEUED",
  "RUNNING",
  "AWAITING_STEP_UP",
  "AWAITING_LEGAL_APPROVAL",
  "COMPLETED",
  "FAILED",
  "BLOCKED",
  "CANCELLED",
];

const REQUIRED_COMPENSATION_ACTIONS = [
  "CANCEL_IN_FLIGHT_MATCHES",
  "REVOKE_KIT_SHARING_LINKS",
  "DELETE_MATCH_SEGMENTS",
  "DELETE_KINSHIP_ESTIMATES",
  "DELETE_RELATIVE_DISCOVERY_ROWS",
  "DELETE_NOTES_FOR_KIT",
  "DELETE_KIT_REGISTRATION",
  "DELETE_RAW_ENCRYPTED_OBJECT",
  "PURGE_DNA_BUCKET_PREFIX",
  "REVOKE_SIGNED_EXPORT_URL",
  "ROTATE_DEK",
  "DEACTIVATE_DATA_KEY",
];

const REQUIRED_EXPORT_FORMATS = [
  "JSON_OPAQUE_AGGREGATES",
  "CSV_OPAQUE_AGGREGATES",
  "PDF_REDACTED_SUMMARY",
  "PDF_LEGAL_HOLD_REPORT",
  "MEDIA_BUNDLE_REDACTED",
  "SELF_PORTABILITY_ZIP",
];

const REQUIRED_REDACTION_LEVELS = [
  "NONE",
  "LIVING_ONLY",
  "MINOR_ONLY",
  "LIVING_AND_MINOR",
  "SENSITIVE_FULL",
  "DNA_DEFAULT_OFF",
  "CONSENT_REQUIRED",
];

const REQUIRED_RETENTION_POLICIES = [
  "SINGLE_DOWNLOAD",
  "TIME_BOXED",
  "LEGAL_HOLD_BLOCKED",
  "IMMEDIATE_REVOKE",
];

const REQUIRED_FAILURE_REASONS = [
  "DNA_REVOKE_NOT_FOUND",
  "DNA_REVOKE_TENANT_MISMATCH",
  "DNA_REVOKE_TRIGGER_UNKNOWN",
  "DNA_REVOKE_KIT_NOT_FOUND",
  "DNA_REVOKE_STEP_UP_AUTH_REQUIRED",
  "DNA_REVOKE_STEP_UP_AUTH_FAILED",
  "DNA_REVOKE_LEGAL_HOLD_BLOCKED",
  "DNA_REVOKE_LEGAL_HOLD_OVERRIDE_INVALID",
  "DNA_REVOKE_DERIVED_PURGE_FAILED",
  "DNA_REVOKE_RAW_DELETE_FAILED",
  "DNA_REVOKE_SIGNED_URL_REVOKE_FAILED",
  "DNA_REVOKE_DEK_ROTATION_FAILED",
  "DNA_REVOKE_EVIDENCE_INCOMPLETE",
  "DNA_REVOKE_DEADLINE_EXCEEDED",
];

const REQUIRED_AUDIT_EVENTS = [
  "DNA_REVOKE_TRIGGERED",
  "DNA_REVOKE_MATCHES_CANCELLED",
  "DNA_REVOKE_SHARING_REVOKED",
  "DNA_REVOKE_DERIVED_PURGED",
  "DNA_REVOKE_RAW_DELETED",
  "DNA_REVOKE_LEGAL_HOLD_APPLIED",
  "DNA_REVOKE_LEGAL_HOLD_RELEASED",
  "DNA_REVOKE_EVIDENCE_ISSUED",
  "DNA_REVOKE_COMPLETED",
  "DNA_REVOKE_FAILED",
  "DNA_EXPORT_SIGNED_URL_ISSUED",
  "DNA_EXPORT_SIGNED_URL_REVOKED",
  "DNA_EXPORT_DOWNLOAD_COMPLETED",
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
  "gp.dna.v1.ConsentRevoked",
  "gp.dna.v1.KitDeleted",
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
  "Custom deletion scheduler (use Temporal with object-lock release)",
  "Re-derivation of DNA data after revocation",
  "Permanent public export link for DNA data",
  "Medical interpretation engine (per privacy-and-legal-gate.md §14 #3)",
  "Bypass of legal hold for DNA deletion",
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
    "dnaRevokeTriggers",
    REQUIRED_TRIGGERS,
    asArray(contract.dnaRevokeTriggers?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaRevokePhases",
    REQUIRED_PHASES,
    asArray(contract.dnaRevokePhases?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaRevokeStatuses",
    REQUIRED_STATUSES,
    asArray(contract.dnaRevokeStatuses?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaRevokeCompensationActions",
    REQUIRED_COMPENSATION_ACTIONS,
    asArray(contract.dnaRevokeCompensationActions?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaExportFormats",
    REQUIRED_EXPORT_FORMATS,
    asArray(contract.dnaExportFormats?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaExportRedactionLevels",
    REQUIRED_REDACTION_LEVELS,
    asArray(contract.dnaExportRedactionLevels?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaExportRetentionPolicies",
    REQUIRED_RETENTION_POLICIES,
    asArray(contract.dnaExportRetentionPolicies?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaRevokeFailureReasons",
    REQUIRED_FAILURE_REASONS,
    asArray(contract.dnaRevokeFailureReasons?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaRevokeAuditEvents",
    REQUIRED_AUDIT_EVENTS,
    asArray(contract.dnaRevokeAuditEvents?.values),
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
    "dnaRevokeStateMatrix",
    contract.dnaRevokeStateMatrix,
    REQUIRED_STATUSES,
    "QUEUED",
    ok,
    fail,
  );
  assertStateMatrix(
    "dnaExportStateMatrix",
    contract.dnaExportStateMatrix,
    ["REQUESTED", "SIGNED", "DOWNLOADED", "REVOKED", "EXPIRED", "REJECTED"],
    "REQUESTED",
    ok,
    fail,
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["revokeWorkflowMandatory", true],
    ["revokeCancelsInFlightMatches", true],
    ["revokeCancelsInFlightUploads", true],
    ["revokeRevokesSharing", true],
    ["revokePurgesDerivedData", true],
    ["revokeDeletesRawData", true],
    ["revokeRespectsLegalHold", true],
    ["revokeLegalHoldOverridesExpiry", true],
    ["revokeEvidenceExcludesDeletedContent", true],
    ["exportRequiresStepUpAuth", true],
    ["exportRequiresConsentPurpose", true],
    ["exportUsesSignedShortLivedUrl", true],
    ["exportRevocableBeforeExpiry", true],
    ["exportRedactionByDefault", true],
    ["exportRedactionFloorLivingOrDnaOff", true],
    ["exportDnaDefaultOff", true],
    ["exportLegalHoldBlocksBundle", true],
    ["deletionUsesObjectLockRelease", true],
    ["deletionRevokesSignedUrl", true],
    ["deletionRotatesDek", true],
    ["deletionGeneratesEvidence", true],
    ["revocationPropagationBounded", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["tenantBoundaryOnEveryRepository", true],
    ["auditHookOnEveryRevoke", true],
    ["auditHookOnEveryExport", true],
    ["auditHookOnEveryDelete", true],
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
    revokeTerminationGraceSeconds: 60,
    revokePropagationDeadlineSeconds: 300,
    revokeMaxConcurrentJobsPerTenant: 4,
    revokeMaxDurationSeconds: 3600,
    exportSignedUrlTtlSeconds: 3600,
    exportRevocationPropagationSeconds: 30,
    exportBundleMaxBytes: 524288000,
    exportEvidenceMaxBytes: 1048576,
    deletionHardDeleteTimeoutSeconds: 600,
    deletionLegalHoldMinRetentionSeconds: 63072000,
    revokeCompensationMaxRetries: 5,
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
    revokeTerminationGraceSeconds: 60,
    revokePropagationDeadlineSeconds: 300,
    revokeDeadlineToGraceMultiplier: 5,
    exportSignedUrlTtlSeconds: 3600,
    exportRevocationPropagationSeconds: 30,
    exportSignedUrlToRevocationMultiplier: 120,
    exportBundleMaxBytes: 524288000,
    exportEvidenceMaxBytes: 1048576,
    exportEvidenceToBundleMultiplier: 500,
    deletionLegalHoldMinRetentionSeconds: 63072000,
    deletionHardDeleteTimeoutSeconds: 600,
    deletionTimeoutToRetentionMultiplier: 105120,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (
    nb.revokePropagationDeadlineSeconds
    < ri.revokeDeadlineToGraceMultiplier * nb.revokeTerminationGraceSeconds
  ) {
    fail(
      `revoke invariant violated: deadline=${nb.revokePropagationDeadlineSeconds}s MUST be >= ${ri.revokeDeadlineToGraceMultiplier} × grace=${nb.revokeTerminationGraceSeconds}s`,
    );
  } else {
    ok(`revoke invariant: ${nb.revokePropagationDeadlineSeconds} >= ${ri.revokeDeadlineToGraceMultiplier} × ${nb.revokeTerminationGraceSeconds}`);
  }
  if (
    nb.exportSignedUrlTtlSeconds
    < ri.exportSignedUrlToRevocationMultiplier * nb.exportRevocationPropagationSeconds
  ) {
    fail(
      `signed-url invariant violated: signedUrlTtl=${nb.exportSignedUrlTtlSeconds}s MUST be >= ${ri.exportSignedUrlToRevocationMultiplier} × revocationPropagation=${nb.exportRevocationPropagationSeconds}s`,
    );
  } else {
    ok(`signed-url invariant: ${nb.exportSignedUrlTtlSeconds} >= ${ri.exportSignedUrlToRevocationMultiplier} × ${nb.exportRevocationPropagationSeconds}`);
  }
  if (
    nb.exportBundleMaxBytes
    < ri.exportEvidenceToBundleMultiplier * nb.exportEvidenceMaxBytes
  ) {
    fail(
      `evidence invariant violated: bundleMaxBytes=${nb.exportBundleMaxBytes} MUST be >= ${ri.exportEvidenceToBundleMultiplier} × evidenceMaxBytes=${nb.exportEvidenceMaxBytes}`,
    );
  } else {
    ok(`evidence invariant: ${nb.exportBundleMaxBytes} >= ${ri.exportEvidenceToBundleMultiplier} × ${nb.exportEvidenceMaxBytes}`);
  }
  if (
    nb.deletionHardDeleteTimeoutSeconds
    > nb.deletionLegalHoldMinRetentionSeconds / ri.deletionTimeoutToRetentionMultiplier
  ) {
    fail(
      `deletion invariant violated: hardDeleteTimeout=${nb.deletionHardDeleteTimeoutSeconds}s MUST be <= legalHoldMinRetention=${nb.deletionLegalHoldMinRetentionSeconds}s / ${ri.deletionTimeoutToRetentionMultiplier}`,
    );
  } else {
    ok(`deletion invariant: ${nb.deletionHardDeleteTimeoutSeconds}s <= ${nb.deletionLegalHoldMinRetentionSeconds}s / ${ri.deletionTimeoutToRetentionMultiplier}`);
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
  console.log("\nE10.5 DNA revoke / export / delete policy contract OK.");
}

main();