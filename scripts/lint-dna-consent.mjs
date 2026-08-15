#!/usr/bin/env node
/**
 * scripts/lint-dna-consent.mjs
 *
 * E10.3 deep validator for the DNA consent engine contract under
 * `contracts/dna/dna-consent-policy.yaml` and the platform mirror
 * under `platform/helm/genealogy-platform/files/dna/dna-consent-policy.yaml`.
 *
 * Mirrors the E9 / E10.2 linter pattern:
 *   - closed-set vocabularies: consentSubjects[5], consentPurposes[10],
 *     consentActions[8], consentLegalBases[7], consentPolicyVersions[3],
 *     consentStates[8], consentReceiptEvents[14], consentRaceOutcomes[5],
 *     consentFailureReasons[13], consentAuditEvents[11];
 *   - sandbox egress allowlist (7 DNA-sandbox endpoints);
 *   - 2 state matrices (consentStateMatrix initial=DRAFT,
 *     consentReauthorizeStateMatrix initial=REQUESTED);
 *   - 28 boolean guard rails;
 *   - numeric bounds + invariants (legalHold >= ½ retention,
 *     reauthorize <= ¼ race window, guardian >= 96× step-up);
 *   - outbox envelope (8 fields);
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

const CONTRACT = join(ROOT, "contracts/dna/dna-consent-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/dna/dna-consent-policy.yaml",
);

const REQUIRED_CONSENT_SUBJECTS = [
  "SELF",
  "GUARDIAN_ON_BEHALF_OF_MINOR",
  "GUARDIAN_ON_BEHALF_OF_INCAPACITATED",
  "COURT_APPOINTED_REPRESENTATIVE",
  "DECEASED_DATA_SUBJECT_NEXT_OF_KIN",
];

const REQUIRED_CONSENT_PURPOSES = [
  "DNA_KIT_REGISTRATION",
  "DNA_RAW_UPLOAD",
  "DNA_MATCHING",
  "DNA_RELATIVE_DISCOVERY",
  "DNA_RESEARCH_OPT_IN",
  "DNA_EXPORT_RAW",
  "DNA_EXPORT_MATCHES",
  "DNA_PORTABILITY_REQUEST",
  "DNA_DELETION_REQUEST",
  "DNA_LEGAL_HOLD_OVERRIDE",
];

const REQUIRED_CONSENT_ACTIONS = [
  "ALLOW",
  "ALLOW_ONCE",
  "DENY",
  "DENY_PERMANENT",
  "REQUIRE_GUARDIAN",
  "REQUIRE_STEP_UP_AUTH",
  "REQUIRE_LEGAL_HOLD",
  "REQUIRE_NOTIFICATION",
];

const REQUIRED_CONSENT_LEGAL_BASES = [
  "GDPR_ART_9_2_A_EXPLICIT_CONSENT",
  "GDPR_ART_9_2_G_SUBSTANTIAL_PUBLIC_INTEREST",
  "CCPA_SENSITIVE_PI_OPT_IN",
  "GINA_FEDERAL",
  "GINA_FLORIDA",
  "GIPA_ILLINOIS",
  "CCPA_CPRA_SENSITIVE_PI",
];

const REQUIRED_CONSENT_POLICY_VERSIONS = [
  "DNA_POLICY_V1_2026",
  "DNA_POLICY_V1_2026_GUARDIAN",
  "DNA_POLICY_V1_2026_RESEARCH",
];

const REQUIRED_CONSENT_STATES = [
  "DRAFT",
  "PENDING",
  "EFFECTIVE",
  "EXPIRED",
  "REVOKED",
  "SUPERSEDED",
  "LEGAL_HOLD",
  "REJECTED",
];

const REQUIRED_CONSENT_RECEIPT_EVENTS = [
  "CONSENT_GRANT_REQUESTED",
  "CONSENT_GRANTED",
  "CONSENT_GRANT_REJECTED",
  "CONSENT_REVOKE_REQUESTED",
  "CONSENT_REVOKED",
  "CONSENT_REAUTHORIZED",
  "CONSENT_EXPIRED",
  "CONSENT_LEGAL_HOLD_PLACED",
  "CONSENT_LEGAL_HOLD_RELEASED",
  "CONSENT_SUPERSEDED",
  "CONSENT_RECEIPT_ISSUED",
  "CONSENT_ACCESS_GRANTED",
  "CONSENT_EXPORT_GRANTED",
  "CONSENT_RACE_DETECTED",
];

const REQUIRED_CONSENT_RACE_OUTCOMES = [
  "FIRST_WRITER_WINS",
  "LAST_WRITER_WINS",
  "REJECT_CONFLICTING",
  "QUEUE_FOR_REVIEW",
  "NOTIFY_GUARDIAN",
];

const REQUIRED_CONSENT_FAILURE_REASONS = [
  "CONSENT_NOT_FOUND",
  "CONSENT_TENANT_MISMATCH",
  "CONSENT_VERSION_MISMATCH",
  "CONSENT_SUBJECT_MISMATCH",
  "CONSENT_PURPOSE_DENIED",
  "CONSENT_EXPIRED",
  "CONSENT_REVOKED",
  "CONSENT_GUARDIAN_REQUIRED",
  "CONSENT_MINOR_WORKFLOW_MISSING",
  "CONSENT_STEP_UP_AUTH_REQUIRED",
  "CONSENT_LEGAL_HOLD_REQUIRED",
  "CONSENT_RACE_REJECTED",
  "CONSENT_POLICY_VERSION_UNKNOWN",
];

const REQUIRED_CONSENT_AUDIT_EVENTS = [
  "CONSENT_ENGINE_OK",
  "CONSENT_ENGINE_VIOLATION",
  "CONSENT_GRANTED",
  "CONSENT_REVOKED",
  "CONSENT_REAUTHORIZED",
  "CONSENT_ACCESS_GRANTED",
  "CONSENT_EXPORT_GRANTED",
  "CONSENT_RACE_DETECTED",
  "CONSENT_RECEIPT_ISSUED",
  "CONSENT_LEGAL_HOLD_APPLIED",
  "CONSENT_LEGAL_HOLD_RELEASED",
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
  "gp.dna.v1.ConsentGranted",
  "gp.dna.v1.ConsentRevoked",
];

const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = [
  "rawDnaSequence",
  "rawFastq",
  "rawBam",
  "rawVcf",
  "rawConsentDocument",
  "rawSignatureImage",
  "rawIdDocument",
  "rawFacePhoto",
  "exifGps",
  "cameraSerial",
  "passportNumber",
  "socialSecurityNumber",
  "rawEmail",
  "rawPhone",
  "rawAddress",
  "rawLivingStatus",
  "rawMinorStatus",
  "rawMedicalRecord",
  "productionPii",
];

const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom durable queue (Temporal namespace + task queue is enough)",
  "Cross-service aggregation into DNA (use Kafka events + publisher resolution)",
  "Custom policy engine for consent (use consent ledger + ABAC overlay)",
  "Mutable consent record (consent ledger is append-only)",
  "Tree role grant/revoke of DNA consent (requires explicit consent subject)",
  "Background re-authorization (must run inside activity time per R13)",
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
    "consentSubjects",
    REQUIRED_CONSENT_SUBJECTS,
    asArray(contract.consentSubjects?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentPurposes",
    REQUIRED_CONSENT_PURPOSES,
    asArray(contract.consentPurposes?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentActions",
    REQUIRED_CONSENT_ACTIONS,
    asArray(contract.consentActions?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentLegalBases",
    REQUIRED_CONSENT_LEGAL_BASES,
    asArray(contract.consentLegalBases?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentPolicyVersions",
    REQUIRED_CONSENT_POLICY_VERSIONS,
    asArray(contract.consentPolicyVersions?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentStates",
    REQUIRED_CONSENT_STATES,
    asArray(contract.consentStates?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentReceiptEvents",
    REQUIRED_CONSENT_RECEIPT_EVENTS,
    asArray(contract.consentReceiptEvents?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentRaceOutcomes",
    REQUIRED_CONSENT_RACE_OUTCOMES,
    asArray(contract.consentRaceOutcomes?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentFailureReasons",
    REQUIRED_CONSENT_FAILURE_REASONS,
    asArray(contract.consentFailureReasons?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "consentAuditEvents",
    REQUIRED_CONSENT_AUDIT_EVENTS,
    asArray(contract.consentAuditEvents?.values),
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
    "consentStateMatrix",
    contract.consentStateMatrix,
    REQUIRED_CONSENT_STATES,
    "DRAFT",
    ok,
    fail,
  );
  assertStateMatrix(
    "consentReauthorizeStateMatrix",
    contract.consentReauthorizeStateMatrix,
    ["REQUESTED", "GRANTED", "DENIED", "REJECTED"],
    "REQUESTED",
    ok,
    fail,
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["consentLedgerAppendOnly", true],
    ["consentReceiptSigned", true],
    ["consentReauthorizeOnActivity", true],
    ["consentReauthorizeOnExport", true],
    ["consentReauthorizeOnRevoke", true],
    ["consentVersioningRequired", true],
    ["consentLegalBasisRequired", true],
    ["consentGuardianRequiredForMinor", true],
    ["consentGuardianRequiredForIncapacitated", true],
    ["consentStepUpAuthRequiredForExport", true],
    ["consentStepUpAuthRequiredForDeletion", true],
    ["consentLegalHoldOverridesExpiry", true],
    ["consentLegalHoldDoesNotExtendExpiry", true],
    ["consentExpiryRequired", true],
    ["consentEffectiveTimeRequired", true],
    ["consentRaceDetectionEnabled", true],
    ["consentRaceFirstWriterWinsByDefault", true],
    ["consentCannotBeGrantedByTreeRole", true],
    ["consentCannotBeRevokedByTreeRole", true],
    ["rawConsentDocumentForbiddenInPayload", true],
    ["rawConsentReceiptForbiddenInLogs", true],
    ["rawConsentReceiptForbiddenInTraces", true],
    ["rawConsentReceiptForbiddenInEvents", true],
    ["rawConsentReceiptForbiddenInSearch", true],
    ["rawConsentReceiptForbiddenInPublicApi", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["tenantBoundaryOnEveryRepository", true],
    ["auditHookOnEveryGrant", true],
    ["auditHookOnEveryRevoke", true],
    ["auditHookOnEveryAccess", true],
    ["auditHookOnEveryExport", true],
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
    consentMaxPurposesPerSubject: 16,
    consentReceiptMaxBytes: 65536,
    consentRetentionSeconds: 63072000,
    consentMinVersionLength: 1,
    consentMaxVersionLength: 32,
    consentReauthorizeTimeoutMs: 250,
    consentRaceDetectionWindowMs: 1000,
    consentGuardianWindowSeconds: 86400,
    consentStepUpAuthTtlSeconds: 900,
    consentLegalHoldMinRetentionSeconds: 63072000,
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
    consentRetentionSeconds: 63072000,
    consentLegalHoldMinRetentionSeconds: 63072000,
    consentLegalHoldToRetentionMultiplier: 1,
    consentRaceDetectionWindowMs: 1000,
    consentReauthorizeTimeoutMs: 250,
    consentReauthorizeToRaceWindowMultiplier: 4,
    consentGuardianWindowSeconds: 86400,
    consentStepUpAuthTtlSeconds: 900,
    consentGuardianWindowToStepUpMultiplier: 96,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (
    nb.consentLegalHoldMinRetentionSeconds
    < nb.consentRetentionSeconds * ri.consentLegalHoldToRetentionMultiplier
  ) {
    fail(
      `legal-hold invariant violated: legalHoldMinRetention=${nb.consentLegalHoldMinRetentionSeconds}s MUST be >= retention=${nb.consentRetentionSeconds}s × ${ri.consentLegalHoldToRetentionMultiplier}`,
    );
  } else {
    ok(`legal-hold invariant: ${nb.consentLegalHoldMinRetentionSeconds} >= ${nb.consentRetentionSeconds} × ${ri.consentLegalHoldToRetentionMultiplier}`);
  }
  if (
    nb.consentReauthorizeTimeoutMs
    > nb.consentRaceDetectionWindowMs / ri.consentReauthorizeToRaceWindowMultiplier
  ) {
    fail(
      `reauthorize invariant violated: reauthorizeTimeout=${nb.consentReauthorizeTimeoutMs}ms MUST be <= raceWindow=${nb.consentRaceDetectionWindowMs}ms / ${ri.consentReauthorizeToRaceWindowMultiplier}`,
    );
  } else {
    ok(`reauthorize invariant: ${nb.consentReauthorizeTimeoutMs}ms <= ${nb.consentRaceDetectionWindowMs}ms / ${ri.consentReauthorizeToRaceWindowMultiplier}`);
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
    REQUIRED_CONSENT_AUDIT_EVENTS,
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
  console.log("\nE10.3 DNA consent policy contract OK.");
}

main();