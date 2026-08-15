#!/usr/bin/env node
/**
 * scripts/lint-authorized-search.mjs
 *
 * E8.2 deep validator for the authorized search policy contract
 * under `contracts/search/authorized-search-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/authorized-search-policy.yaml`.
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

const CONTRACT = join(ROOT, "contracts/search/authorized-search-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/authorized-search-policy.yaml");

const REQUIRED_SEARCH_FILTER_MODES = ["ANY_OF", "ALL_OF", "NONE_OF"];
const REQUIRED_SEARCH_ORDER_MODES = [
  "RELEVANCE",
  "CAPTURED_AT_ASC",
  "CAPTURED_AT_DESC",
  "UPDATED_AT_DESC",
  "NAME_ASC",
  "NAME_DESC",
];
const REQUIRED_SEARCH_RESOURCE_KINDS = [
  "PERSON",
  "EVENT",
  "PLACE",
  "SOURCE",
  "CITATION",
  "MEDIA",
  "ALBUM",
];
const REQUIRED_SEARCH_AUTHORIZATION_OUTCOMES = [
  "ALLOWED",
  "TENANT_MISMATCH",
  "OPENFGA_DENY",
  "ABAC_LIVING_FORBIDDEN",
  "ABAC_MINOR_FORBIDDEN",
  "ABAC_DNA_FORBIDDEN",
  "ABAC_CONSENT_REQUIRED",
  "ABAC_CONTEXTUAL_DENY",
  "PERMISSION_VERSION_STALE",
  "REJECTED",
];
const REQUIRED_SEARCH_FACET_SORTS = ["COUNT_DESC", "LABEL_ASC", "RECENT_FIRST"];
const REQUIRED_SEARCH_CURSOR_ENCODINGS = ["BASE64URL_OPAQUE", "KEY_PACKED"];
const REQUIRED_SEARCH_SAVED_SEARCH_ALERT_CADENCES = [
  "REALTIME",
  "HOURLY",
  "DAILY",
  "WEEKLY",
  "DISABLED",
];
const REQUIRED_SEARCH_SAVED_SEARCH_ALERT_CHANNELS = [
  "IN_APP",
  "EMAIL",
  "PUSH",
  "WEBHOOK",
];
const REQUIRED_SEARCH_SAVED_SEARCH_SCOPES = [
  "PRIVATE",
  "TREE_COLLABORATORS",
  "TENANT_TEAM",
];
const REQUIRED_SEARCH_PERMISSION_CACHE_STATUS = ["VALID", "STALE", "PURGING", "PURGED"];
const REQUIRED_SEARCH_FAILURE_REASONS = [
  "SEARCH_QUERY_TOO_LONG",
  "SEARCH_QUERY_EMPTY",
  "SEARCH_CURSOR_INVALID",
  "SEARCH_CURSOR_DEPTH_EXCEEDED",
  "SEARCH_RESOURCE_KIND_UNKNOWN",
  "SEARCH_FILTER_MODE_UNKNOWN",
  "SEARCH_ORDER_MODE_UNKNOWN",
  "SEARCH_TENANT_MISMATCH",
  "SEARCH_OPENFGA_DENY",
  "SEARCH_ABAC_LIVING_FORBIDDEN",
  "SEARCH_ABAC_MINOR_FORBIDDEN",
  "SEARCH_ABAC_DNA_FORBIDDEN",
  "SEARCH_ABAC_CONSENT_REQUIRED",
  "SEARCH_ABAC_CONTEXTUAL_DENY",
  "SEARCH_PERMISSION_VERSION_STALE",
  "SEARCH_SAVED_SEARCH_NOT_FOUND",
  "SEARCH_SAVED_SEARCH_SHARE_FORBIDDEN",
  "SEARCH_SAVED_SEARCH_QUERY_NO_PII",
  "SEARCH_ALERT_CADENCE_FORBIDDEN",
  "SEARCH_ALERT_CHANNEL_FORBIDDEN",
  "SEARCH_FACET_AXIS_UNKNOWN",
  "SEARCH_PERMISSION_CACHE_MISS",
  "SEARCH_DNA_BUCKET_FORBIDDEN",
  "SEARCH_PERMISSION_TOKEN_INVALID",
];
const REQUIRED_SEARCH_AUDIT_EVENTS = [
  "SEARCH_QUERY_RECEIVED",
  "SEARCH_QUERY_ALLOWED",
  "SEARCH_QUERY_REJECTED",
  "SEARCH_FACETS_RETURNED",
  "SEARCH_CURSOR_ADVANCED",
  "SEARCH_PERMISSION_CACHE_HIT",
  "SEARCH_PERMISSION_CACHE_MISS",
  "SEARCH_PERMISSION_CACHE_INVALIDATED",
  "SEARCH_PERMISSION_CACHE_PURGED",
  "SEARCH_SAVED_SEARCH_CREATED",
  "SEARCH_SAVED_SEARCH_UPDATED",
  "SEARCH_SAVED_SEARCH_DELETED",
  "SEARCH_SAVED_SEARCH_SHARED",
  "SEARCH_SAVED_SEARCH_ALERT_FIRED",
  "SEARCH_DNA_BUCKET_REFUSED",
  "SEARCH_LIVING_RULE_APPLIED",
  "SEARCH_MINOR_RULE_APPLIED",
  "SEARCH_CONSENT_RULE_APPLIED",
];
const REQUIRED_FACET_AXES = [
  "TREE",
  "FAMILY",
  "DECADE",
  "LIVING_STATUS",
  "PLACE",
  "RESOURCE_KIND",
  "PRIVACY_CLASS",
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
const REQUIRED_AUDIT_HOOKS = REQUIRED_SEARCH_AUDIT_EVENTS;
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
  "gp.search.v1.SavedSearchCreated",
  "gp.search.v1.SavedSearchAlertFired",
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
];
const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom OpenSearch indexing (the worker consumes Avro events only)",
  "Custom PII / DNA detector (use the platform-wide redactor)",
  "Cross-service aggregation (use Kafka events + publisher resolution)",
  "Custom permission cache (use Flagsmith + cache-aside on top)",
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

  assertClosedSet("searchFilterModes", REQUIRED_SEARCH_FILTER_MODES, asArray(contract.searchFilterModes?.values));
  assertClosedSet("searchOrderModes", REQUIRED_SEARCH_ORDER_MODES, asArray(contract.searchOrderModes?.values));
  assertClosedSet("searchResourceKinds", REQUIRED_SEARCH_RESOURCE_KINDS, asArray(contract.searchResourceKinds?.values));
  assertClosedSet(
    "searchAuthorizationOutcomes",
    REQUIRED_SEARCH_AUTHORIZATION_OUTCOMES,
    asArray(contract.searchAuthorizationOutcomes?.values),
  );
  assertClosedSet("searchFacetSorts", REQUIRED_SEARCH_FACET_SORTS, asArray(contract.searchFacetSorts?.values));
  assertClosedSet(
    "searchCursorEncodings",
    REQUIRED_SEARCH_CURSOR_ENCODINGS,
    asArray(contract.searchCursorEncodings?.values),
  );
  assertClosedSet(
    "searchSavedSearchAlertCadences",
    REQUIRED_SEARCH_SAVED_SEARCH_ALERT_CADENCES,
    asArray(contract.searchSavedSearchAlertCadences?.values),
  );
  assertClosedSet(
    "searchSavedSearchAlertChannels",
    REQUIRED_SEARCH_SAVED_SEARCH_ALERT_CHANNELS,
    asArray(contract.searchSavedSearchAlertChannels?.values),
  );
  assertClosedSet(
    "searchSavedSearchScopes",
    REQUIRED_SEARCH_SAVED_SEARCH_SCOPES,
    asArray(contract.searchSavedSearchScopes?.values),
  );
  assertClosedSet(
    "searchPermissionCacheStatus",
    REQUIRED_SEARCH_PERMISSION_CACHE_STATUS,
    asArray(contract.searchPermissionCacheStatus?.values),
  );
  assertClosedSet("searchFailureReasons", REQUIRED_SEARCH_FAILURE_REASONS, asArray(contract.searchFailureReasons?.values));
  assertClosedSet("searchAuditEvents", REQUIRED_SEARCH_AUDIT_EVENTS, asArray(contract.searchAuditEvents?.values));
  assertClosedSet("searchFacetAxes", REQUIRED_FACET_AXES, asArray(contract.searchFacetAxes?.values));
  assertClosedSet(
    "sandboxEgressAllowlist",
    REQUIRED_SANDBOX_EGRESS,
    asArray(contract.sandboxEgressAllowlist?.values),
    "sandbox egress allowlist",
  );

  assertStateMatrix(
    "permissionCacheStateMatrix",
    contract.permissionCacheStateMatrix,
    [...REQUIRED_SEARCH_PERMISSION_CACHE_STATUS, "DECIDED"],
    "VALID",
  );

  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["tenantFilterEnforced", true],
    ["openfgaCheckEnforced", true],
    ["abacOverlayEnforced", true],
    ["permissionVersionBindingEnforced", true],
    ["permissionCacheMaxVersionWindow", true],
    ["cursorOpaque", true],
    ["cursorDepthBounded", true],
    ["facetCardinalityBounded", true],
    ["savedSearchSharingOpaque", true],
    ["savedSearchQueryNoRawPii", true],
    ["alertChannelTenantConstrained", true],
    ["searchOutOfBandForbidden", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["livingStatusRedactionApplied", true],
    ["minorStatusRedactionApplied", true],
    ["dnaConsentRedactionApplied", true],
    ["consentWithdrawalInvalidatesResults", true],
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
    maxQueryLength: 512,
    maxCursorDepth: 1024,
    maxFacetCountPerAxis: 256,
    maxPageSize: 100,
    minPageSize: 1,
    defaultPageSize: 20,
    maxFiltersPerQuery: 32,
    maxSavedSearchNameLength: 128,
    maxSavedSearchDescriptionLength: 1024,
    maxSavedSearchFiltersPerQuery: 32,
    maxSavedSearchResultsPerPage: 100,
    maxSavedSearchAlertsPerUser: 64,
    maxSavedSearchAlertSubscribers: 256,
    permissionCacheMaxAgeSeconds: 30,
    permissionCacheMaxVersionWindow: 5,
    permissionCacheTombstoneSeconds: 90,
    permissionCacheP95BudgetMilliseconds: 5,
    permissionCacheStaleBudgetSeconds: 15,
    alertWorkerHeartbeatSeconds: 30,
    alertWorkerP95BudgetSeconds: 60,
    alertCadenceRealtimeP95BudgetMilliseconds: 250,
    searchP95BudgetMilliseconds: 1000,
    searchP99BudgetMilliseconds: 2500,
    savedSearchIdLength: 64,
    alertIdLength: 64,
    tenantScopeIdLength: 64,
    actorPseudoIdLength: 64,
    correlationIdLength: 128,
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
    permissionCacheTombstoneMultiplier: 3,
    permissionCacheMaxAgeMultiplier: 1,
    alertCadenceRealtimeP95Multiplier: 1,
    searchP95BudgetMultiplier: 1,
    pageSizeDefaultToMaxRatio: 5,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (nb.permissionCacheTombstoneSeconds < ri.permissionCacheTombstoneMultiplier * nb.permissionCacheMaxAgeSeconds) {
    fail(
      `permission cache tombstone invariant violated: tombstone=${nb.permissionCacheTombstoneSeconds}s MUST be >= ${ri.permissionCacheTombstoneMultiplier} × maxAge=${nb.permissionCacheMaxAgeSeconds}s`,
    );
  } else {
    ok(`permission cache tombstone invariant: ${nb.permissionCacheTombstoneSeconds} >= ${ri.permissionCacheTombstoneMultiplier} × ${nb.permissionCacheMaxAgeSeconds}`);
  }
  if (nb.maxPageSize !== ri.pageSizeDefaultToMaxRatio * nb.defaultPageSize) {
    fail(
      `page size invariant violated: maxPageSize=${nb.maxPageSize} MUST equal ${ri.pageSizeDefaultToMaxRatio} × defaultPageSize=${nb.defaultPageSize} (got ${nb.defaultPageSize})`,
    );
  } else {
    ok(`page size invariant: maxPageSize=${nb.maxPageSize} = ${ri.pageSizeDefaultToMaxRatio} × ${nb.defaultPageSize}`);
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
    REQUIRED_AUDIT_HOOKS,
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
  console.log("\nauthorized search policy contract OK.");
}

main();