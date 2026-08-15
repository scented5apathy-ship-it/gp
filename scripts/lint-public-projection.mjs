#!/usr/bin/env node
/**
 * scripts/lint-public-projection.mjs
 *
 * E8.3 deep validator for the public projection policy contract
 * under `contracts/search/public-projection-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/public-projection-policy.yaml`.
 *
 * Mirrors the structure of `lint-search-projection.mjs` (E8.1).
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/search/public-projection-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/public-projection-policy.yaml");

const REQUIRED_PUBLIC_PROJECTION_VISIBILITY_SCOPES = ["PUBLIC", "UNLISTED"];
const REQUIRED_PUBLIC_PROJECTION_LIFECYCLE_STATUSES = [
  "PENDING",
  "REDACTED",
  "INDEXED",
  "STALE",
  "PURGED",
];
const REQUIRED_PUBLIC_PROJECTION_REDACTION_REASONS = [
  "LIVING",
  "MINOR",
  "DNA_ATTACHED",
  "CONSENT_MISSING",
  "CONSENT_REVOKED",
  "VISIBILITY_NOT_PUBLIC",
  "TENANT_REDACTION_FORBIDDEN",
  "POLICY_DENY",
  "SOFT_DELETED",
  "LEGAL_HOLD",
];
const REQUIRED_PUBLIC_PROJECTION_TOKEN_KINDS = [
  "SHARE_TOKEN",
  "COLLABORATION_TOKEN",
  "PUBLIC_LISTING_TOKEN",
];
const REQUIRED_PUBLIC_PROJECTION_PURGE_TRIGGERS = [
  "VISIBILITY_DOWNGRADE",
  "LIVING_STATUS_CHANGED",
  "MINOR_STATUS_CHANGED",
  "DNA_ATTACHMENT_ADDED",
  "CONSENT_REVOKED",
  "LEGAL_HOLD_APPLIED",
  "SOFT_DELETED",
  "POLICY_DENY",
  "TENANT_OFFBOARDED",
];
const REQUIRED_PUBLIC_PROJECTION_SITEMAP_STATUSES = [
  "BUILT",
  "SERVING",
  "STALE",
  "PURGING",
  "PURGED",
];
const REQUIRED_PUBLIC_PROJECTION_INDEX_STRATEGIES = [
  "POSTGRES_GIN",
  "POSTGRES_GIST",
  "POSTGRES_BRIN",
  "HYBRID_DISK",
];
const REQUIRED_PUBLIC_PROJECTION_FAILURE_REASONS = [
  "PUBLIC_PROJECTION_NOT_PUBLIC",
  "PUBLIC_PROJECTION_REDACTION_FAILED",
  "PUBLIC_PROJECTION_DNA_BUCKET_FORBIDDEN",
  "PUBLIC_PROJECTION_LIVING_FORBIDDEN",
  "PUBLIC_PROJECTION_MINOR_FORBIDDEN",
  "PUBLIC_PROJECTION_CONSENT_FORBIDDEN",
  "PUBLIC_PROJECTION_TOKEN_INVALID",
  "PUBLIC_PROJECTION_TOKEN_EXPIRED",
  "PUBLIC_PROJECTION_TOKEN_RATE_LIMITED",
  "PUBLIC_PROJECTION_TOKEN_HASH_MISMATCH",
  "PUBLIC_PROJECTION_TOKEN_KIND_UNKNOWN",
  "PUBLIC_PROJECTION_VISIBILITY_DOWNGRADE_FORBIDDEN",
  "PUBLIC_PROJECTION_PURGE_FAILED",
  "PUBLIC_PROJECTION_CACHE_PURGE_FAILED",
  "PUBLIC_PROJECTION_SITEMAP_PURGE_FAILED",
  "PUBLIC_PROJECTION_LIFECYCLE_FORBIDDEN",
  "PUBLIC_PROJECTION_INDEX_STRATEGY_FORBIDDEN",
  "PUBLIC_PROJECTION_SITEMAP_STALE",
  "PUBLIC_PROJECTION_ROBOTSTXT_FORBIDDEN",
  "PUBLIC_PROJECTION_CANONICAL_HOST_FORBIDDEN",
  "PUBLIC_PROJECTION_LANGUAGE_HREFLANG_FORBIDDEN",
];
const REQUIRED_PUBLIC_PROJECTION_AUDIT_EVENTS = [
  "PUBLIC_PROJECTION_RECEIVED",
  "PUBLIC_PROJECTION_REDACTED",
  "PUBLIC_PROJECTION_INDEXED",
  "PUBLIC_PROJECTION_PURGED",
  "PUBLIC_PROJECTION_VISIBILITY_CHANGED",
  "PUBLIC_PROJECTION_TOKEN_VERIFIED",
  "PUBLIC_PROJECTION_TOKEN_REJECTED",
  "PUBLIC_PROJECTION_TOKEN_EXPIRED",
  "PUBLIC_PROJECTION_TOKEN_RATE_LIMITED",
  "PUBLIC_PROJECTION_SITEMAP_BUILT",
  "PUBLIC_PROJECTION_SITEMAP_SERVING",
  "PUBLIC_PROJECTION_SITEMAP_PURGED",
  "PUBLIC_PROJECTION_CACHE_PURGED",
  "PUBLIC_PROJECTION_DNA_BUCKET_REFUSED",
  "PUBLIC_PROJECTION_LIVING_REFUSED",
  "PUBLIC_PROJECTION_MINOR_REFUSED",
  "PUBLIC_PROJECTION_CONSENT_REFUSED",
  "PUBLIC_PROJECTION_ROBOTSTXT_UPDATED",
];
const REQUIRED_SANDBOX_EGRESS = [
  "postgres",
  "apicurio",
  "vault-agent",
  "openfga",
  "audit-service",
  "kafka-broker",
];
const REQUIRED_DNA_BUCKET_PREFIXES = ["dna/raw", "dna/match", "dna/consent"];
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
  "gp.search.v1.PublicProjectionPurged",
  "gp.search.v1.PublicProjectionVisibilityChanged",
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
];
const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom OpenSearch indexing (the worker consumes Avro events only)",
  "Custom PII / DNA detector (use the platform-wide redactor)",
  "Cross-service aggregation (use Kafka events + publisher resolution)",
  "Custom rate limiter (use Kong + token hash + app validation)",
  "Custom sitemap generator (use the worker + Apicurio schemas)",
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

function assertClosedSet(name, expected, actual, label) {
  const expectedSorted = [...expected].sort().join(",");
  const actualSorted = [...actual].sort().join(",");
  if (expectedSorted !== actualSorted) {
    fail(
      `${label || name}: closed-set mismatch.\n     expected: ${expectedSorted}\n     actual:   ${actualSorted}`,
    );
    return;
  }
  if (expected.length === 0) {
    fail(`${label || name}: empty closed-set is forbidden`);
    return;
  }
  ok(`${label || name} (${actual.length} values)`);
}

function assertStateMatrix(label, matrix, expectedStatuses, initialStatus) {
  if (!matrix || typeof matrix !== "object") {
    fail(`${label}: state matrix missing`);
    return;
  }
  const statuses = asArray(matrix.statuses);
  if (statuses.length === 0) {
    fail(`${label}: state matrix is empty`);
    return;
  }
  const seen = new Set();
  const reachable = new Set([initialStatus]);
  for (const entry of statuses) {
    if (!entry || typeof entry !== "object") {
      fail(`${label}: invalid status entry ${JSON.stringify(entry)}`);
      continue;
    }
    const status = entry.status;
    if (!status) {
      fail(`${label}: status entry missing 'status' field`);
      continue;
    }
    seen.add(status);
    const transitions = asArray(entry.transitions);
    if (!Array.isArray(transitions)) {
      fail(`${label}: ${status}.transitions must be an array`);
      continue;
    }
    if (entry.terminal === true) {
      if (transitions.length !== 0) {
        fail(`${label}: terminal status ${status} MUST have empty transitions (got ${JSON.stringify(transitions)})`);
      } else {
        ok(`${label}: terminal status ${status} has empty transitions`);
      }
    } else {
      if (transitions.length === 0) {
        fail(`${label}: non-terminal status ${status} MUST declare at least one transition`);
      }
    }
    for (const t of transitions) {
      if (typeof t !== "string") {
        fail(`${label}: ${status} transition ${JSON.stringify(t)} is not a string`);
      }
    }
    if (entry.terminal !== true) {
      for (const t of transitions) reachable.add(t);
    }
  }
  if (matrix.initialStatus !== initialStatus) {
    fail(`${label}: initialStatus MUST equal ${initialStatus} (got ${matrix.initialStatus})`);
  }
  for (const s of expectedStatuses) {
    if (!seen.has(s)) {
      fail(`${label}: expected status ${s} missing`);
    }
  }
  for (const s of seen) {
    if (!expectedStatuses.includes(s)) {
      fail(`${label}: unexpected status ${s} in matrix`);
    }
  }
  for (const s of seen) {
    if (!reachable.has(s) && s !== initialStatus) {
      fail(`${label}: status ${s} is unreachable from ${initialStatus}`);
    }
  }
  ok(`${label}: ${seen.size} statuses, ${expectedStatuses.length - seen.size} missing`);
}

function readContract(path) {
  return loadYaml(readFileSync(path, "utf8"));
}

function main() {
  let contract;
  try {
    contract = readContract(CONTRACT);
  } catch (err) {
    fail(`could not read contract ${CONTRACT}: ${err.message}`);
    process.exit(2);
  }
  if (!contract || typeof contract !== "object") {
    fail(`contract ${CONTRACT} is empty or malformed`);
    process.exit(2);
  }

  assertClosedSet(
    "publicProjectionVisibilityScopes",
    REQUIRED_PUBLIC_PROJECTION_VISIBILITY_SCOPES,
    asArray(contract.publicProjectionVisibilityScopes?.values),
  );
  assertClosedSet(
    "publicProjectionLifecycleStatuses",
    REQUIRED_PUBLIC_PROJECTION_LIFECYCLE_STATUSES,
    asArray(contract.publicProjectionLifecycleStatuses?.values),
  );
  assertClosedSet(
    "publicProjectionRedactionReasons",
    REQUIRED_PUBLIC_PROJECTION_REDACTION_REASONS,
    asArray(contract.publicProjectionRedactionReasons?.values),
  );
  assertClosedSet(
    "publicProjectionTokenKinds",
    REQUIRED_PUBLIC_PROJECTION_TOKEN_KINDS,
    asArray(contract.publicProjectionTokenKinds?.values),
  );
  assertClosedSet(
    "publicProjectionPurgeTriggers",
    REQUIRED_PUBLIC_PROJECTION_PURGE_TRIGGERS,
    asArray(contract.publicProjectionPurgeTriggers?.values),
  );
  assertClosedSet(
    "publicProjectionSitemapStatuses",
    REQUIRED_PUBLIC_PROJECTION_SITEMAP_STATUSES,
    asArray(contract.publicProjectionSitemapStatuses?.values),
  );
  assertClosedSet(
    "publicProjectionIndexStrategies",
    REQUIRED_PUBLIC_PROJECTION_INDEX_STRATEGIES,
    asArray(contract.publicProjectionIndexStrategies?.values),
  );
  assertClosedSet(
    "publicProjectionFailureReasons",
    REQUIRED_PUBLIC_PROJECTION_FAILURE_REASONS,
    asArray(contract.publicProjectionFailureReasons?.values),
  );
  assertClosedSet(
    "publicProjectionAuditEvents",
    REQUIRED_PUBLIC_PROJECTION_AUDIT_EVENTS,
    asArray(contract.publicProjectionAuditEvents?.values),
  );
  assertClosedSet(
    "sandboxEgressAllowlist",
    REQUIRED_SANDBOX_EGRESS,
    asArray(contract.sandboxEgressAllowlist?.values),
    "sandbox egress allowlist",
  );

  assertStateMatrix(
    "publicProjectionLifecycleStateMatrix",
    contract.publicProjectionLifecycleStateMatrix,
    [...REQUIRED_PUBLIC_PROJECTION_LIFECYCLE_STATUSES, "DECIDED"],
    "PENDING",
  );
  assertStateMatrix(
    "sitemapStateMatrix",
    contract.sitemapStateMatrix,
    [...REQUIRED_PUBLIC_PROJECTION_SITEMAP_STATUSES, "DECIDED"],
    "BUILT",
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["publicOnlyAfterRedaction", true],
    ["livingRedactionRequired", true],
    ["minorRedactionRequired", true],
    ["consentRedactionRequired", true],
    ["consentWithdrawalInvalidatesResults", true],
    ["unlistedReturnsNoindex", true],
    ["unlistedTokenHashRequired", true],
    ["unlistedTokenExpiryRequired", true],
    ["unlistedTokenRateLimitRequired", true],
    ["unlistedTokenVerifiedAtEdge", true],
    ["unlistedTokenReverifiedInApp", true],
    ["purgePropagatesToCache", true],
    ["purgePropagatesToSitemap", true],
    ["purgePropagatesToProjection", true],
    ["robotstxtUpdatedOnVisibilityChange", true],
    ["canonicalHostEnforced", true],
    ["languageHreflangRequired", true],
    ["publicProjectionOutOfBandForbidden", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["outboxRelaySeparated", true],
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
  );

  const nb = contract.numericBounds || {};
  const numericGuards = {
    maxDocumentIdLength: 64,
    maxTenantScopeIdLength: 64,
    maxActorPseudoIdLength: 64,
    maxCorrelationIdLength: 128,
    maxUnlistedTokenLength: 256,
    maxUnlistedTokenHashLength: 128,
    unlistedTokenTtlSeconds: 2592000,
    unlistedTokenRateLimitPerMinute: 30,
    unlistedTokenRateLimitBurst: 5,
    unlistedTokenMinHashBytes: 32,
    projectionRowMaxBytes: 16384,
    projectionBatchSize: 512,
    projectionOutboxBatchSize: 256,
    redactionP95BudgetMilliseconds: 150,
    purgeP95BudgetMilliseconds: 1000,
    sitemapRebuildP95BudgetSeconds: 45,
    sitemapHeartbeatSeconds: 30,
    sitemapMaxEntries: 50000,
    robotstxtMaxBytes: 4096,
    canonicalHostMaxLength: 253,
    languageHreflangMaxEntries: 32,
  };
  for (const [key, expected] of Object.entries(numericGuards)) {
    if (nb[key] !== expected) {
      fail(`numericBounds.${key} MUST equal ${expected} (got ${nb[key]})`);
    } else {
      ok(`numericBounds.${key} = ${expected}`);
    }
  }

  const ri = contract.reconciliationInvariants || {};
  const invariants = {
    sitemapHeartbeatMultiplier: 2,
    unlistedTokenRateLimitBurstMultiplier: 6,
    projectionBatchSizeOutboxRatio: 2,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (nb.sitemapRebuildP95BudgetSeconds >= ri.sitemapHeartbeatMultiplier * nb.sitemapHeartbeatSeconds) {
    fail(
      `sitemap invariant violated: rebuildP95=${nb.sitemapRebuildP95BudgetSeconds}s MUST be < ${ri.sitemapHeartbeatMultiplier} × heartbeat=${nb.sitemapHeartbeatSeconds}s`,
    );
  } else {
    ok(`sitemap invariant: rebuildP95=${nb.sitemapRebuildP95BudgetSeconds} < ${ri.sitemapHeartbeatMultiplier} × ${nb.sitemapHeartbeatSeconds}`);
  }
  if (nb.unlistedTokenRateLimitPerMinute < ri.unlistedTokenRateLimitBurstMultiplier * nb.unlistedTokenRateLimitBurst) {
    fail(
      `token rate limit invariant violated: perMinute=${nb.unlistedTokenRateLimitPerMinute} MUST be >= ${ri.unlistedTokenRateLimitBurstMultiplier} × burst=${nb.unlistedTokenRateLimitBurst}`,
    );
  } else {
    ok(`token rate limit invariant: ${nb.unlistedTokenRateLimitPerMinute} >= ${ri.unlistedTokenRateLimitBurstMultiplier} × ${nb.unlistedTokenRateLimitBurst}`);
  }
  if (nb.projectionBatchSize !== ri.projectionBatchSizeOutboxRatio * nb.projectionOutboxBatchSize) {
    fail(
      `projection batch invariant violated: batch=${nb.projectionBatchSize} MUST equal ${ri.projectionBatchSizeOutboxRatio} × outbox=${nb.projectionOutboxBatchSize}`,
    );
  } else {
    ok(`projection batch invariant: ${nb.projectionBatchSize} = ${ri.projectionBatchSizeOutboxRatio} × ${nb.projectionOutboxBatchSize}`);
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

  assertClosedSet(
    "auditHooks.auditRequired",
    REQUIRED_PUBLIC_PROJECTION_AUDIT_EVENTS,
    asArray(contract.auditHooks?.auditRequired),
    "auditHooks.auditRequired",
  );
  assertClosedSet(
    "forbiddenPayloadPatterns",
    REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS,
    asArray(contract.forbiddenPayloadPatterns),
    "forbidden payload patterns",
  );
  assertClosedSet(
    "capabilityBoundaries.forbiddenSelfBuilt",
    REQUIRED_CAPABILITY_FORBIDDEN,
    asArray(contract.capabilityBoundaries?.forbiddenSelfBuilt),
    "capability boundaries",
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
  console.log("\npublic projection policy contract OK.");
}

main();