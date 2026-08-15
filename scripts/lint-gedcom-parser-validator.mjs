#!/usr/bin/env node
/**
 * scripts/lint-gedcom-parser-validator.mjs
 *
 * E9.2 deep validator for the GEDCOM parser / validator policy
 * contract under `contracts/importexport/gedcom-parser-validator-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/gedcom-parser-validator-policy.yaml`.
 *
 * Closed-set vocabularies: gedcomVersions[2], gedcomEncodings[4],
 * gedcomLineEndings[3], gedcomRecordKinds[8], gedcomExtensionTags[5],
 * gedcomValidationStatuses[12], gedcomDryRunSeverities[4],
 * gedcomProvenanceKinds[7], gedcomMappingOutcomes[8],
 * gedcomFailureReasons[20], gedcomAuditEvents[11],
 * gedcomRecordClasses[7]; sandbox egress allowlist (postgres,
 * apicurio, vault-agent, openfga, audit-service, kafka-broker,
 * temporal-frontend); 1 state matrix (initial=QUEUED);
 * 14 boolean guards; 24 numeric bounds; invariants
 * (validation timeout ≥ 20×heartbeat, mapping timeout ≥ 15×heartbeat);
 * outbox envelope; audit hooks; forbidden payload patterns;
 * capability boundaries; chart mirror byte-equality.
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
  "contracts/importexport/gedcom-parser-validator-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/gedcom-parser-validator-policy.yaml",
);

const REQUIRED_VERSIONS = ["5.5.1", "7.0"];
const REQUIRED_ENCODINGS = ["UTF-8", "UTF-16LE", "UTF-16BE", "ASCII"];
const REQUIRED_LINE_ENDINGS = ["LF", "CRLF", "CR"];
const REQUIRED_RECORD_KINDS = [
  "INDI",
  "FAM",
  "SOUR",
  "REPO",
  "OBJE",
  "NOTE",
  "SUBM",
  "TRLR",
];
const REQUIRED_EXTENSION_TAGS = [
  "_MEDI",
  "_DNA",
  "_CUSTOM",
  "_SHARED",
  "_PROVENANCE",
];
const REQUIRED_VALIDATION_STATUSES = [
  "VALID",
  "RECOVERABLE",
  "INVALID_STRUCTURE",
  "INVALID_ENCODING",
  "INVALID_LINE_LENGTH",
  "INVALID_DEPTH",
  "INVALID_TAG",
  "INVALID_REFERENCE",
  "INVALID_DATE",
  "INVALID_NAME",
  "INVALID_PLACE",
  "INVALID_PROVENANCE",
];
const REQUIRED_SEVERITIES = ["INFO", "WARNING", "ERROR", "CRITICAL"];
const REQUIRED_PROVENANCE_KINDS = [
  "SOURCE_CITATION",
  "TRANSCRIBER_NAME",
  "TRANSCRIPTION_DATE",
  "CONFIDENCE_LEVEL",
  "LICENSE_REFERENCE",
  "EXTENSION_AUTHOR",
  "EXTENSION_VERSION",
];
const REQUIRED_MAPPING_OUTCOMES = [
  "MAPPED_NATIVE",
  "MAPPED_TRANSFORMED",
  "MAPPED_WITH_NOTES",
  "MAPPED_WITH_PROVENANCE_PRESERVED",
  "DROPPED_UNSUPPORTED",
  "DROPPED_DNA_BUCKET",
  "DROPPED_PII_PAYLOAD",
  "FAILED_MAPPING",
];
const REQUIRED_FAILURE_REASONS = [
  "GEDCOM_PAYLOAD_TOO_LARGE",
  "GEDCOM_LINE_LENGTH_EXCEEDED",
  "GEDCOM_DEPTH_EXCEEDED",
  "GEDCOM_RECORD_COUNT_EXCEEDED",
  "GEDCOM_TAG_COUNT_EXCEEDED",
  "GEDCOM_ENCODING_INVALID",
  "GEDCOM_LINE_ENDING_INVALID",
  "GEDCOM_BOM_INVALID",
  "GEDCOM_EXTENSION_BLOCKED",
  "GEDCOM_REFERENCE_UNRESOLVED",
  "GEDCOM_DATE_INVALID",
  "GEDCOM_PLACE_INVALID",
  "GEDCOM_NAME_INVALID",
  "GEDCOM_PROVENANCE_MISSING",
  "GEDCOM_MAPPING_FAILED",
  "GEDCOM_DNA_BUCKET_FORBIDDEN",
  "GEDCOM_PII_LEAK_DETECTED",
  "GEDCOM_DRY_RUN_ONLY_OK",
  "GEDCOM_PAYLOAD_ENCRYPTED_UNSUPPORTED",
  "GEDCOM_COMPRESSION_UNSUPPORTED",
];
const REQUIRED_AUDIT_EVENTS = [
  "GEDCOM_PARSE_STARTED",
  "GEDCOM_PARSE_FINISHED",
  "GEDCOM_VALIDATION_FINISHED",
  "GEDCOM_DRY_RUN_FINISHED",
  "GEDCOM_MAPPING_FINISHED",
  "GEDCOM_PROVENANCE_PRESERVED",
  "GEDCOM_DNA_BUCKET_REFUSED",
  "GEDCOM_PII_LEAK_REFUSED",
  "GEDCOM_EXTENSION_DROPPED",
  "GEDCOM_LIMIT_BREACHED",
  "GEDCOM_REFERENCE_UNRESOLVED",
];
const REQUIRED_RECORD_CLASSES = [
  "PERSON",
  "FAMILY",
  "SOURCE",
  "REPOSITORY",
  "MEDIA",
  "SUBMITTER",
  "TRAILER",
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
  "gp.importexport.v1.GedcomParsed",
  "gp.importexport.v1.GedcomValidationFinished",
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
  "Custom GEDCOM parser (re-use the platform streaming parser)",
  "Generic streaming worker (Temporal activity is enough)",
  "Distributed transaction (outbox relay is enough)",
  "Custom XML parser (GEDCOM is line-based, not XML)",
  "External conversion service (mapping is local + versioned)",
  "Cross-service aggregation (use Kafka events + publisher resolution)",
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

  assertClosedSet("gedcomVersions", REQUIRED_VERSIONS, asArray(contract.gedcomVersions?.values), undefined, ok, fail);
  assertClosedSet("gedcomEncodings", REQUIRED_ENCODINGS, asArray(contract.gedcomEncodings?.values), undefined, ok, fail);
  assertClosedSet("gedcomLineEndings", REQUIRED_LINE_ENDINGS, asArray(contract.gedcomLineEndings?.values), undefined, ok, fail);
  assertClosedSet("gedcomRecordKinds", REQUIRED_RECORD_KINDS, asArray(contract.gedcomRecordKinds?.values), undefined, ok, fail);
  assertClosedSet("gedcomExtensionTags", REQUIRED_EXTENSION_TAGS, asArray(contract.gedcomExtensionTags?.values), undefined, ok, fail);
  assertClosedSet("gedcomValidationStatuses", REQUIRED_VALIDATION_STATUSES, asArray(contract.gedcomValidationStatuses?.values), undefined, ok, fail);
  assertClosedSet("gedcomDryRunSeverities", REQUIRED_SEVERITIES, asArray(contract.gedcomDryRunSeverities?.values), undefined, ok, fail);
  assertClosedSet("gedcomProvenanceKinds", REQUIRED_PROVENANCE_KINDS, asArray(contract.gedcomProvenanceKinds?.values), undefined, ok, fail);
  assertClosedSet("gedcomMappingOutcomes", REQUIRED_MAPPING_OUTCOMES, asArray(contract.gedcomMappingOutcomes?.values), undefined, ok, fail);
  assertClosedSet("gedcomFailureReasons", REQUIRED_FAILURE_REASONS, asArray(contract.gedcomFailureReasons?.values), undefined, ok, fail);
  assertClosedSet("gedcomAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(contract.gedcomAuditEvents?.values), undefined, ok, fail);
  assertClosedSet("gedcomRecordClasses", REQUIRED_RECORD_CLASSES, asArray(contract.gedcomRecordClasses?.values), undefined, ok, fail);
  assertClosedSet("sandboxEgressAllowlist", REQUIRED_SANDBOX_EGRESS, asArray(contract.sandboxEgressAllowlist?.values), "sandbox egress allowlist", ok, fail);

  assertStateMatrix("gedcomParseStateMatrix", contract.gedcomParseStateMatrix, [
    "QUEUED",
    "STREAMING",
    "VALIDATING",
    "MAPPING",
    "PROVENANCE_PRESERVED",
    "FINALIZED",
    "FAILED",
    "BLOCKED",
  ], "QUEUED", ok, fail);

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["streamingParserRequired", true],
    ["boundedMemoryFootprint", true],
    ["noFullFileLoad", true],
    ["userCodeExecutionForbidden", true],
    ["extensionTagSandboxEnforced", true],
    ["provenancePreservationRequired", true],
    ["mappingReproducible", true],
    ["dryRunDefaultBeforeCommit", true],
    ["domainWriteForbiddenDuringDryRun", true],
    ["outboxRelaySeparated", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["rawPayloadInEventForbidden", true],
    ["secretInPayloadForbidden", true],
    ["tenantBoundaryOnEveryRepository", true],
    ["adrRequiredBeforeExternalParser", true],
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
  assertClosedSet("guardRails.dnaBucketPrefixes", REQUIRED_DNA_BUCKET_PREFIXES, asArray(gr.dnaBucketPrefixes), "DNA bucket prefixes", ok, fail);

  const nb = contract.numericBounds || {};
  const numericGuards = {
    gedcomMaxPayloadBytes: 52428800,
    gedcomMaxLineLength: 4096,
    gedcomMaxDepth: 32,
    gedcomMaxRecordCount: 5000000,
    gedcomMaxTagCount: 25000000,
    gedcomMaxExtensionTagBytes: 1024,
    gedcomMaxReferenceCount: 1000000,
    gedcomStreamingChunkBytes: 65536,
    gedcomValidationTimeoutSeconds: 600,
    gedcomValidationHeartbeatSeconds: 30,
    gedcomMappingTimeoutSeconds: 900,
    gedcomMappingHeartbeatSeconds: 60,
    gedcomBatchInsertSize: 1000,
    gedcomBatchInsertTimeoutSeconds: 300,
    gedcomMaxProvenanceLinesPerRecord: 256,
    gedcomNameMaxLength: 256,
    gedcomPlaceMaxLength: 512,
    gedcomDateMaxLength: 128,
    gedcomNoteMaxLength: 4096,
    gedcomSourceCitationMaxLength: 1024,
    gedcomProvenanceAuthorMaxLength: 256,
    gedcomProvenanceVersionMaxLength: 64,
    gedcomActorPseudoIdLength: 64,
    gedcomTenantPseudoIdLength: 64,
    gedcomCorrelationIdLength: 128,
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
    gedcomValidationTimeoutSeconds: 600,
    gedcomValidationHeartbeatSeconds: 30,
    gedcomValidationHeartbeatMultiplier: 20,
    gedcomMappingTimeoutSeconds: 900,
    gedcomMappingHeartbeatSeconds: 60,
    gedcomMappingHeartbeatMultiplier: 15,
    gedcomBatchInsertTimeoutSeconds: 300,
    gedcomBatchInsertSize: 1000,
    gedcomBatchInsertPerRecordBudgetMultiplier: 0,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (nb.gedcomValidationTimeoutSeconds < ri.gedcomValidationHeartbeatMultiplier * nb.gedcomValidationHeartbeatSeconds) {
    fail(`validation invariant violated: timeout=${nb.gedcomValidationTimeoutSeconds}s MUST be >= ${ri.gedcomValidationHeartbeatMultiplier} × heartbeat=${nb.gedcomValidationHeartbeatSeconds}s`);
  } else {
    ok(`validation invariant: timeout=${nb.gedcomValidationTimeoutSeconds} >= ${ri.gedcomValidationHeartbeatMultiplier} × ${nb.gedcomValidationHeartbeatSeconds}`);
  }
  if (nb.gedcomMappingTimeoutSeconds < ri.gedcomMappingHeartbeatMultiplier * nb.gedcomMappingHeartbeatSeconds) {
    fail(`mapping invariant violated: timeout=${nb.gedcomMappingTimeoutSeconds}s MUST be >= ${ri.gedcomMappingHeartbeatMultiplier} × heartbeat=${nb.gedcomMappingHeartbeatSeconds}s`);
  } else {
    ok(`mapping invariant: timeout=${nb.gedcomMappingTimeoutSeconds} >= ${ri.gedcomMappingHeartbeatMultiplier} × ${nb.gedcomMappingHeartbeatSeconds}`);
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
  assertClosedSet("auditHooks.auditRequired", REQUIRED_AUDIT_EVENTS, asArray(audit.auditRequired), "auditHooks.auditRequired", ok, fail);
  assertClosedSet("forbiddenPayloadPatterns", REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS, asArray(contract.forbiddenPayloadPatterns), "forbidden payload patterns", ok, fail);

  const cb = contract.capabilityBoundaries || {};
  assertClosedSet("capabilityBoundaries.forbiddenSelfBuilt", REQUIRED_CAPABILITY_FORBIDDEN, asArray(cb.forbiddenSelfBuilt), "capability boundaries", ok, fail);

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
  console.log("\nE9.2 GEDCOM parser + validator policy contract OK.");
}

main();