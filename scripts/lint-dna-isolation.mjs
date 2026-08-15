#!/usr/bin/env node
/**
 * scripts/lint-dna-isolation.mjs
 *
 * E10.2 deep validator for the DNA isolation contract under
 * `contracts/dna/dna-isolation-policy.yaml` and the platform mirror
 * under `platform/helm/genealogy-platform/files/dna/dna-isolation-policy.yaml`.
 *
 * Mirrors the E9 import/export validators (lint-temporal-transfer-framework.mjs
 * etc.):
 *   - closed-set vocabularies: dnaDatabaseRoles[4], dnaDatabaseSchemas[4],
 *     dnaBucketPrefixes[3], dnaBucketPolicies[8], dnaVaultPolicies[5],
 *     dnaKmsKeyRings[3], dnaOpenfgaNamespaces[5], dnaOpenfgaRelations[5],
 *     dnaTreeRolesNotGrantedByDefault[5], dnaNodePoolLabels[4],
 *     dnaTaskQueues[4], dnaFailureReasons[13], dnaAuditEvents[11];
 *   - sandbox egress allowlist (postgres-dna, vault-agent-dna,
 *     s3-dna, openfga-dna, audit-service, kafka-dna,
 *     temporal-frontend-dna);
 *   - 2 state matrices (dnaIsolationStateMatrix initial=GUARD_OK,
 *     dnaNodePoolStateMatrix initial=ADMITTED);
 *   - 32 boolean guard rails;
 *   - numeric bounds;
 *   - invariants: kmsRotation >= 90 days, vaultAuditToRotation
 *     multiplier, openfgaAuthTimeoutToCacheTtlMultiplier, etc;
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

const CONTRACT = join(ROOT, "contracts/dna/dna-isolation-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/dna/dna-isolation-policy.yaml",
);

const REQUIRED_DATABASE_ROLES = [
  "dna_app_ro",
  "dna_app_rw",
  "dna_admin",
  "dna_readonly_replica",
];

const REQUIRED_DATABASE_SCHEMAS = [
  "dna_kit",
  "dna_consent",
  "dna_match",
  "dna_audit",
];

const REQUIRED_BUCKET_PREFIXES = [
  "dna/raw",
  "dna/match",
  "dna/consent",
];

const REQUIRED_BUCKET_POLICIES = [
  "DENY_CROSS_ACCOUNT",
  "DENY_PUBLIC_ACCESS",
  "DENY_NON_VPC_ENDPOINT",
  "DENY_PLAINTEXT_LIST",
  "DENY_OBJECT_LOCK_DISABLED",
  "REQUIRE_OBJECT_LOCK",
  "REQUIRE_SSE_KMS_DNA",
  "REQUIRE_VERSIONING",
];

const REQUIRED_VAULT_POLICIES = [
  "dna-read-dek",
  "dna-rotate-dek",
  "dna-issue-data-key",
  "dna-revoke-data-key",
  "dna-bootstrap-envelope",
];

const REQUIRED_KMS_KEY_RINGS = [
  "dna/raw-kek",
  "dna/match-kek",
  "dna/consent-kek",
];

const REQUIRED_OPENFGA_NAMESPACES = [
  "dna.kits",
  "dna.consent",
  "dna.match",
  "dna.segment",
  "dna.research",
];

const REQUIRED_OPENFGA_RELATIONS = [
  "owner",
  "guardian",
  "delegated_admin",
  "researcher",
  "audit_reader",
];

const REQUIRED_TREE_ROLES_NOT_GRANTED = [
  "tree.viewer",
  "tree.editor",
  "tree.contributor",
  "tree.co_owner",
  "tree.public_viewer",
];

const REQUIRED_NODE_POOL_LABELS = [
  "dna-worker=true",
  "dna-tier=genetic",
  "dna-bucket-bound=true",
  "dna-vault-bound=true",
];

const REQUIRED_TASK_QUEUES = [
  "dna.upload",
  "dna.match",
  "dna.revoke",
  "dna.export",
];

const REQUIRED_FAILURE_REASONS = [
  "DNA_DATABASE_ROLE_FORBIDDEN",
  "DNA_DATABASE_SCHEMA_FORBIDDEN",
  "DNA_BUCKET_PREFIX_FORBIDDEN",
  "DNA_BUCKET_POLICY_VIOLATION",
  "DNA_VAULT_POLICY_DENIED",
  "DNA_KMS_KEY_FORBIDDEN",
  "DNA_OPENFGA_NAMESPACE_FORBIDDEN",
  "DNA_TREE_ROLE_BYPASS_DENIED",
  "DNA_NODE_POOL_LABEL_MISSING",
  "DNA_TASK_QUEUE_FORBIDDEN",
  "DNA_EGRESS_DENIED",
  "DNA_TENANT_MISMATCH",
  "DNA_FEATURE_FLAG_DISABLED",
];

const REQUIRED_AUDIT_EVENTS = [
  "DNA_ISOLATION_GUARD_OK",
  "DNA_ISOLATION_GUARD_VIOLATION",
  "DNA_BUCKET_DENIED",
  "DNA_VAULT_DENIED",
  "DNA_KMS_DENIED",
  "DNA_OPENFGA_NAMESPACE_DENIED",
  "DNA_TREE_ROLE_BYPASS_DENIED",
  "DNA_NODE_POOL_LABEL_MISSING",
  "DNA_TASK_QUEUE_REJECTED",
  "DNA_EGRESS_REJECTED",
  "DNA_FEATURE_FLAG_REJECTED",
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
  "gp.dna.v1.IsolationGuardOk",
  "gp.dna.v1.IsolationGuardViolation",
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
  "rawSocialSecurityNumber",
  "rawPassport",
  "rawDriverLicense",
  "rawTaxId",
  "nameOnBirth",
  "rawEmail",
  "rawPhone",
  "rawAddress",
  "biometricTemplate",
  "rawFacialEmbedding",
  "rawLivingStatus",
  "rawMinorStatus",
  "rawConsentDocument",
  "rawMedicalRecord",
  "rawPaymentInstrument",
  "productionPii",
  "treeViewerBypass",
];

const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom durable queue (Temporal namespace + task queue is enough)",
  "Cross-service aggregation into DNA (use Kafka events + publisher resolution)",
  "Custom policy engine for consent (use consent ledger + ABAC overlay)",
  "DNA feature default-on (must stay behind legal.dna.enabled per ADR-E0.5-15)",
  "Cross-account or public bucket policy for DNA bucket",
  "Shared KMS key with non-DNA service (envelope key isolation)",
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
    "dnaDatabaseRoles",
    REQUIRED_DATABASE_ROLES,
    asArray(contract.dnaDatabaseRoles?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaDatabaseSchemas",
    REQUIRED_DATABASE_SCHEMAS,
    asArray(contract.dnaDatabaseSchemas?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaBucketPrefixes",
    REQUIRED_BUCKET_PREFIXES,
    asArray(contract.dnaBucketPrefixes?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaBucketPolicies",
    REQUIRED_BUCKET_POLICIES,
    asArray(contract.dnaBucketPolicies?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaVaultPolicies",
    REQUIRED_VAULT_POLICIES,
    asArray(contract.dnaVaultPolicies?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaKmsKeyRings",
    REQUIRED_KMS_KEY_RINGS,
    asArray(contract.dnaKmsKeyRings?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaOpenfgaNamespaces",
    REQUIRED_OPENFGA_NAMESPACES,
    asArray(contract.dnaOpenfgaNamespaces?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaOpenfgaRelations",
    REQUIRED_OPENFGA_RELATIONS,
    asArray(contract.dnaOpenfgaRelations?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaTreeRolesNotGrantedByDefault",
    REQUIRED_TREE_ROLES_NOT_GRANTED,
    asArray(contract.dnaTreeRolesNotGrantedByDefault?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaNodePoolLabels",
    REQUIRED_NODE_POOL_LABELS,
    asArray(contract.dnaNodePoolLabels?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaTaskQueues",
    REQUIRED_TASK_QUEUES,
    asArray(contract.dnaTaskQueues?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaFailureReasons",
    REQUIRED_FAILURE_REASONS,
    asArray(contract.dnaFailureReasons?.values),
    undefined,
    ok,
    fail,
  );
  assertClosedSet(
    "dnaAuditEvents",
    REQUIRED_AUDIT_EVENTS,
    asArray(contract.dnaAuditEvents?.values),
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
    "dnaIsolationStateMatrix",
    contract.dnaIsolationStateMatrix,
    ["GUARD_OK", "GUARD_VIOLATION", "NODE_POOL_DRAINING", "REJECTED"],
    "GUARD_OK",
    ok,
    fail,
  );
  assertStateMatrix(
    "dnaNodePoolStateMatrix",
    contract.dnaNodePoolStateMatrix,
    ["ADMITTED", "DRAINING", "REJECTED"],
    "ADMITTED",
    ok,
    fail,
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["dedicatedDatabase", true],
    ["dedicatedDatabaseRole", true],
    ["dedicatedDatabaseSchema", true],
    ["dedicatedBucket", true],
    ["dedicatedBucketPrefix", true],
    ["dedicatedVaultPolicy", true],
    ["dedicatedKmsKeyRing", true],
    ["dedicatedOpenfgaNamespace", true],
    ["treeRolesDoNotGrantDna", true],
    ["dedicatedNodePool", true],
    ["dedicatedTaskQueue", true],
    ["dedicatedTemporalFrontend", true],
    ["istioEgressAllowlistOnly", true],
    ["networkPolicyDefaultDeny", true],
    ["bucketPublicAccessBlocked", true],
    ["bucketCrossAccountBlocked", true],
    ["bucketSseKmsRequired", true],
    ["bucketVersioningRequired", true],
    ["bucketObjectLockRequired", true],
    ["envelopeEncryptionRequired", true],
    ["kmsKeyRotationIntervalRequired", true],
    ["vaultPolicyAuditRequired", true],
    ["openfgaAuthorizationLocal", true],
    ["rawDnaAccessOutsideDnaServiceForbidden", true],
    ["rawDnaInLogsForbidden", true],
    ["rawDnaInTracesForbidden", true],
    ["rawDnaInMetricsForbidden", true],
    ["rawDnaInEventsForbidden", true],
    ["rawDnaInSearchForbidden", true],
    ["rawDnaInPublicApiForbidden", true],
    ["rawDnaInMediaPreviewForbidden", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["tenantBoundaryOnEveryRepository", true],
    ["featureFlagDefaultOff", true],
    ["legalJurisdictionCheckRequired", true],
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
    dnaDatabaseRoleMinimum: 4,
    dnaDatabaseSchemaMinimum: 4,
    dnaBucketPrefixMinimum: 3,
    dnaVaultPolicyMinimum: 5,
    dnaKmsKeyRingMinimum: 3,
    dnaOpenfgaNamespaceMinimum: 5,
    dnaTreeRoleNotGrantedByDefaultMinimum: 5,
    dnaNodePoolLabelMinimum: 4,
    dnaTaskQueueMinimum: 4,
    dnaSandboxEgressMinimum: 7,
    kmsKeyRotationIntervalDays: 90,
    vaultPolicyAuditIntervalSeconds: 86400,
    openfgaTupleCacheTtlSeconds: 300,
    openfgaAuthorizationTimeoutMs: 500,
    nodePoolAdmissionTimeoutSeconds: 30,
    taskQueueHeartbeatIntervalSeconds: 30,
    isolationGuardEvaluationTimeoutMs: 250,
    isolationGuardMaxEvaluationsPerRequest: 16,
    legalJurisdictionCheckTimeoutMs: 250,
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
    kmsRotationIntervalDays: 90,
    vaultPolicyAuditIntervalSeconds: 86400,
    openfgaTupleCacheTtlSeconds: 300,
    openfgaAuthTimeoutToCacheTtlMultiplier: 600,
    nodePoolAdmissionTimeoutSeconds: 30,
    taskQueueHeartbeatIntervalSeconds: 30,
    admissionToHeartbeatMultiplier: 1,
    isolationGuardTimeoutMs: 250,
    isolationGuardEvaluationsPerRequest: 16,
    isolationGuardEvaluationsToTimeoutMultiplier: 64,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (
    nb.vaultPolicyAuditIntervalSeconds
    > nb.kmsKeyRotationIntervalDays * 86400
  ) {
    fail(
      `vault invariant violated: vaultPolicyAuditIntervalSeconds=${nb.vaultPolicyAuditIntervalSeconds}s MUST be <= kmsKeyRotationIntervalDays=${nb.kmsKeyRotationIntervalDays} × 86400s (audit must happen at least as often as rotation)`,
    );
  } else {
    ok(`vault invariant: audit ${nb.vaultPolicyAuditIntervalSeconds}s <= rotation ${nb.kmsKeyRotationIntervalDays} × 86400s`);
  }
  if (
    nb.openfgaAuthorizationTimeoutMs
    > nb.openfgaTupleCacheTtlSeconds * 1000
  ) {
    fail(
      `openfga invariant violated: timeout=${nb.openfgaAuthorizationTimeoutMs}ms MUST be <= cacheTtl=${nb.openfgaTupleCacheTtlSeconds}s × 1000`,
    );
  } else {
    ok(`openfga invariant: ${nb.openfgaAuthorizationTimeoutMs}ms <= ${nb.openfgaTupleCacheTtlSeconds}s × 1000`);
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
  console.log("\nE10.2 DNA isolation policy contract OK.");
}

main();