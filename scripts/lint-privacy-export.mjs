#!/usr/bin/env node
/**
 * scripts/lint-privacy-export.mjs
 *
 * E9.4 deep validator for the privacy-aware export policy contract
 * under `contracts/importexport/privacy-export-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/privacy-export-policy.yaml`.
 *
 * Closed-set vocabularies: exportScopes[6], exportFormats[7],
 * exportBundleComponents[9], exportRedactionLevels[7],
 * exportRedactionOutcomes[7], exportConsentReceipts[6],
 * exportSignLifecycleStatuses[7], exportRetentionPolicies[4],
 * exportFailureReasons[20], exportAuditEvents[15];
 * exportScopesToRedactionFloor mapping[6]; sandbox egress
 * allowlist; 1 state matrix (exportBundleStateMatrix initial=PENDING,
 * 9 statuses); 21 boolean guards; 24 numeric bounds; invariants
 * (bundle timeout ≥ 15×heartbeat, sign URL TTL ≥ 120×revocation
 * propagation, dnaDefaultOffFloor=0); outbox envelope;
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

const CONTRACT = join(ROOT, "contracts/importexport/privacy-export-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/privacy-export-policy.yaml");

const REQUIRED_SCOPES = ["FULL_TREE", "BRANCH_SUBGRAPH", "PERSON_CENTRIC", "ANCESTORS_ONLY", "DESCENDANTS_ONLY", "FAMILY_UNIT"];
const REQUIRED_FORMATS = ["GEDCOM_7", "GEDCOM_5_5_1", "CSV", "JSON", "PDF", "MEDIA_BUNDLE_ZIP", "MEDIA_BUNDLE_TAR"];
const REQUIRED_COMPONENTS = ["GEDCOM_PAYLOAD", "CSV_PAYLOAD", "JSON_PAYLOAD", "PDF_PAYLOAD", "MEDIA_PAYLOAD", "CHECKSUM_MANIFEST", "SIGNATURE_PAYLOAD", "CONSENT_RECEIPT", "REDACTION_LOG"];
const REQUIRED_REDACTION_LEVELS = ["NONE", "LIVING_ONLY", "MINOR_ONLY", "LIVING_AND_MINOR", "SENSITIVE_FULL", "DNA_DEFAULT_OFF", "CONSENT_REQUIRED"];
const REQUIRED_REDACTION_OUTCOMES = ["KEPT_AS_IS", "REDACTED_LIVING", "REDACTED_MINOR", "REDACTED_DNA", "REDACTED_CONSENT", "OMITTED_ENTIRELY", "DROPPED_PII_PAYLOAD"];
const REQUIRED_CONSENT_RECEIPTS = ["DNA_PURPOSE_OWN", "DNA_PURPOSE_FAMILY_MATCH", "DNA_PURPOSE_RESEARCH", "FULL_TREE_LIVING_MINOR", "FULL_TREE_PUBLIC_RELEASE", "MEDIA_PUBLIC_RELEASE"];
const REQUIRED_LIFECYCLE = ["PENDING", "SIGNED", "ACTIVE", "EXPIRED", "REVOKED", "DOWNLOADED", "CLEANED_UP"];
const REQUIRED_RETENTION = ["DELETE_AFTER_EXPIRY", "DELETE_AFTER_DOWNLOAD", "DELETE_AFTER_REVOCATION", "DELETE_AFTER_LEGAL_HOLD_RELEASED"];
const REQUIRED_FAILURE_REASONS = ["EXPORT_SCOPE_UNKNOWN", "EXPORT_FORMAT_UNKNOWN", "EXPORT_BUNDLE_COMPONENT_MISSING", "EXPORT_REDACTION_LEVEL_FORBIDDEN", "EXPORT_DNA_DEFAULT_OFF_VIOLATION", "EXPORT_CONSENT_RECEIPT_MISSING", "EXPORT_CONSENT_RECEIPT_EXPIRED", "EXPORT_CONSENT_RECEIPT_REVOKED", "EXPORT_SIGN_FAILED", "EXPORT_SIGN_URL_GENERATION_FAILED", "EXPORT_SIGN_URL_EXPIRED", "EXPORT_DOWNLOAD_QUOTA_EXCEEDED", "EXPORT_REDACTION_PREVIEW_DENIED", "EXPORT_BUNDLE_TOO_LARGE", "EXPORT_CHECKSUM_MISMATCH", "EXPORT_PII_LEAK_DETECTED", "EXPORT_DNA_BUCKET_FORBIDDEN", "EXPORT_TENANT_MISMATCH", "EXPORT_RETENTION_CLEANUP_FAILED", "EXPORT_USER_PROVIDED_PAYLOAD_TOO_LARGE"];
const REQUIRED_AUDIT_EVENTS = ["EXPORT_PREVIEW_QUEUED", "EXPORT_REDACTION_PREVIEW_READY", "EXPORT_DNA_DEFAULT_OFF_APPLIED", "EXPORT_CONSENT_VERIFIED", "EXPORT_BUNDLE_GENERATED", "EXPORT_BUNDLE_SIGNED", "EXPORT_SIGN_URL_ISSUED", "EXPORT_DOWNLOAD_STARTED", "EXPORT_DOWNLOAD_FINISHED", "EXPORT_DOWNLOAD_REVOKED", "EXPORT_DOWNLOAD_EXPIRED", "EXPORT_RETENTION_CLEANED_UP", "EXPORT_DNA_BUCKET_REFUSED", "EXPORT_PII_LEAK_REFUSED", "EXPORT_FAILED"];
const REQUIRED_SANDBOX_EGRESS = ["postgres", "apicurio", "vault-agent", "openfga", "audit-service", "kafka-broker", "temporal-frontend"];
const REQUIRED_DNA_BUCKET_PREFIXES = ["dna/raw", "dna/match", "dna/consent"];
const REQUIRED_OUTBOX_FIELDS = ["eventId", "eventType", "occurredAt", "tenantId", "aggregateId", "aggregateVersion", "traceId", "payload"];
const REQUIRED_OUTBOX_TYPES = ["gp.importexport.v1.ExportBundleSigned", "gp.importexport.v1.ExportDownloadRevoked", "gp.importexport.v1.ExportRetentionCleanedUp"];
const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = ["rawDnaSequence", "rawFastq", "rawBam", "rawVcf", "exifGps", "cameraSerial", "passportNumber", "socialSecurityNumber", "nameOnBirth", "rawEmail", "rawPhone", "rawAddress", "biometricTemplate", "rawFacialEmbedding", "rawLivingStatus", "rawMinorStatus", "rawConsentDocument", "rawSocialSecurityNumber", "rawPassport", "rawDriverLicense", "rawTaxId", "rawMedicalRecord", "rawPaymentInstrument", "productionPii"];
const REQUIRED_CAPABILITY_FORBIDDEN = ["Custom redactor (use the platform-wide redactor)", "Custom signing service (Vault transit is enough)", "Custom retention scheduler (Temporal schedule is enough)", "Distributed transaction (outbox relay + signed URL is enough)", "Cross-service aggregation (use Kafka events + publisher resolution)", "Custom PII / DNA detector (use the platform-wide redactor)"];
const REQUIRED_SCOPE_REDACTION_FLOOR = { FULL_TREE: "LIVING_AND_MINOR", BRANCH_SUBGRAPH: "LIVING_ONLY", PERSON_CENTRIC: "LIVING_ONLY", ANCESTORS_ONLY: "LIVING_ONLY", DESCENDANTS_ONLY: "LIVING_ONLY", FAMILY_UNIT: "LIVING_ONLY" };

const violations = [];
const ok = (m) => console.log(`OK  ${m}`);
const fail = (m) => { violations.push(m); console.error(`FAIL ${m}`); };

function main() {
  let contract;
  try { contract = loadYaml(readFileSync(CONTRACT, "utf8")); } catch (err) { fail(`could not read contract: ${err.message}`); process.exit(2); }
  if (!contract || typeof contract !== "object") { fail("contract empty"); process.exit(2); }

  assertClosedSet("exportScopes", REQUIRED_SCOPES, asArray(contract.exportScopes?.values), undefined, ok, fail);
  assertClosedSet("exportFormats", REQUIRED_FORMATS, asArray(contract.exportFormats?.values), undefined, ok, fail);
  assertClosedSet("exportBundleComponents", REQUIRED_COMPONENTS, asArray(contract.exportBundleComponents?.values), undefined, ok, fail);
  assertClosedSet("exportRedactionLevels", REQUIRED_REDACTION_LEVELS, asArray(contract.exportRedactionLevels?.values), undefined, ok, fail);
  assertClosedSet("exportRedactionOutcomes", REQUIRED_REDACTION_OUTCOMES, asArray(contract.exportRedactionOutcomes?.values), undefined, ok, fail);
  assertClosedSet("exportConsentReceipts", REQUIRED_CONSENT_RECEIPTS, asArray(contract.exportConsentReceipts?.values), undefined, ok, fail);
  assertClosedSet("exportSignLifecycleStatuses", REQUIRED_LIFECYCLE, asArray(contract.exportSignLifecycleStatuses?.values), undefined, ok, fail);
  assertClosedSet("exportRetentionPolicies", REQUIRED_RETENTION, asArray(contract.exportRetentionPolicies?.values), undefined, ok, fail);
  assertClosedSet("exportFailureReasons", REQUIRED_FAILURE_REASONS, asArray(contract.exportFailureReasons?.values), undefined, ok, fail);
  assertClosedSet("exportAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(contract.exportAuditEvents?.values), undefined, ok, fail);
  assertClosedSet("sandboxEgressAllowlist", REQUIRED_SANDBOX_EGRESS, asArray(contract.sandboxEgressAllowlist?.values), "sandbox egress allowlist", ok, fail);

  // Scope → redaction floor mapping (pairs)
  const pairs = contract.exportScopesToRedactionFloor?.pairs;
  if (!pairs || typeof pairs !== "object") {
    fail("exportScopesToRedactionFloor.pairs missing");
  } else {
    for (const [scope, level] of Object.entries(REQUIRED_SCOPE_REDACTION_FLOOR)) {
      if (pairs[scope] !== level) fail(`exportScopesToRedactionFloor.pairs.${scope} MUST equal ${level} (got ${pairs[scope]})`); else ok(`exportScopesToRedactionFloor.pairs.${scope} = ${level}`);
    }
  }

  assertStateMatrix("exportBundleStateMatrix", contract.exportBundleStateMatrix, REQUIRED_LIFECYCLE.concat(["FAILED", "BLOCKED"]), "PENDING", ok, fail);

  const gr = contract.guardRails || {};
  for (const [k, v] of [["privacyFirstByDefault", true], ["dnaDefaultOff", true], ["dnaRequiresExplicitConsent", true], ["livingRedactedByDefault", true], ["minorRedactedByDefault", true], ["redactionPreviewBeforeCommit", true], ["signedUrlShortLived", true], ["signedUrlRevocable", true], ["checksumManifestRequired", true], ["auditOnEveryDownload", true], ["retentionSchedulerRequired", true], ["outboxRelaySeparated", true], ["crossServiceReferencesAreOpaque", true], ["crossServiceReferencesRequirePublisherResolution", true], ["tenantBoundaryOnEveryRepository", true], ["secretInBundleForbidden", true], ["adrRequiredBeforeExternalRedactor", true], ["legalHoldRespected", true]]) {
    if (gr[k] !== v) fail(`guardRails.${k} MUST be ${v} (got ${gr[k]})`); else ok(`guardRails.${k} = ${v}`);
  }
  if (gr.dnaBucketAccess !== "FORBIDDEN") fail(`guardRails.dnaBucketAccess MUST equal FORBIDDEN (got ${gr.dnaBucketAccess})`); else ok("guardRails.dnaBucketAccess = FORBIDDEN");
  assertClosedSet("guardRails.dnaBucketPrefixes", REQUIRED_DNA_BUCKET_PREFIXES, asArray(gr.dnaBucketPrefixes), "DNA bucket prefixes", ok, fail);

  const nb = contract.numericBounds || {};
  const expected = { exportPreviewMaxRecords: 1000000, exportPreviewExpirySeconds: 604800, exportBundleMaxBytes: 524288000, exportBundleMediaMaxBytes: 1073741824, exportBundleMaxFiles: 100000, exportSignUrlTtlSeconds: 3600, exportSignUrlRevocationPropagationSeconds: 30, exportDownloadMaxConcurrent: 4, exportDownloadHeartbeatSeconds: 30, exportRetentionCleanupIntervalSeconds: 3600, exportRetentionHeartbeatSeconds: 60, exportBundleTimeoutSeconds: 900, exportBundleHeartbeatSeconds: 60, exportNameMaxLength: 256, exportPlaceMaxLength: 512, exportNoteMaxLength: 4096, exportIdLength: 64, exportActorPseudoIdLength: 64, exportTenantPseudoIdLength: 64, exportCorrelationIdLength: 128, exportUserPayloadMaxBytes: 52428800, exportDnaDefaultOffFloor: 0, exportConsentReceiptMaxLength: 4096 };
  for (const [k, v] of Object.entries(expected)) if (nb[k] !== v) fail(`numericBounds.${k} MUST equal ${v} (got ${nb[k]})`); else ok(`numericBounds.${k} = ${v}`);

  const ri = contract.reconciliationInvariants || {};
  const inv = { exportBundleTimeoutSeconds: 900, exportBundleHeartbeatSeconds: 60, exportBundleHeartbeatMultiplier: 15, exportRetentionHeartbeatMultiplier: 60, exportSignUrlTtlSeconds: 3600, exportSignUrlRevocationPropagationSeconds: 30, exportSignUrlTtlMultiplier: 120, exportDnaDefaultOffFloor: 0 };
  for (const [k, v] of Object.entries(inv)) if (ri[k] !== v) fail(`reconciliationInvariants.${k} MUST equal ${v} (got ${ri[k]})`); else ok(`reconciliationInvariants.${k} = ${v}`);
  if (nb.exportBundleTimeoutSeconds < ri.exportBundleHeartbeatMultiplier * nb.exportBundleHeartbeatSeconds) fail(`bundle invariant violated: timeout=${nb.exportBundleTimeoutSeconds}s MUST be >= ${ri.exportBundleHeartbeatMultiplier} × heartbeat=${nb.exportBundleHeartbeatSeconds}s`);
  else ok(`bundle invariant: timeout=${nb.exportBundleTimeoutSeconds} >= ${ri.exportBundleHeartbeatMultiplier} × ${nb.exportBundleHeartbeatSeconds}`);
  if (nb.exportSignUrlTtlSeconds < ri.exportSignUrlTtlMultiplier * nb.exportSignUrlRevocationPropagationSeconds) fail(`sign url invariant violated: ttl=${nb.exportSignUrlTtlSeconds}s MUST be >= ${ri.exportSignUrlTtlMultiplier} × propagation=${nb.exportSignUrlRevocationPropagationSeconds}s`);
  else ok(`sign url invariant: ttl=${nb.exportSignUrlTtlSeconds} >= ${ri.exportSignUrlTtlMultiplier} × ${nb.exportSignUrlRevocationPropagationSeconds}`);
  if (nb.exportDnaDefaultOffFloor !== 0) fail(`dna default off floor MUST equal 0 (got ${nb.exportDnaDefaultOffFloor})`); else ok(`dna default off floor = 0`);

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
  console.log("\nE9.4 privacy-aware export policy contract OK.");
}

main();