#!/usr/bin/env node
/**
 * scripts/lint-search-projection.mjs
 *
 * E8.1 deep validator for the search projection policy contract
 * under `contracts/search/search-projection-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/search-projection-policy.yaml`.
 *
 * Mirrors the structure of `lint-albums-linking.mjs` (E7.5):
 *   - parse + structural assertions on every closed-set
 *     vocabulary (searchDocumentKinds[7], searchPrivacyClasses[5],
 *     searchProjectionStatuses[5], searchSourceDomains[4],
 *     searchEventTypes[20], searchProjectionLagPhases[5],
 *     searchReconciliationStatuses[9], searchFailureReasons[23],
 *     searchAuditEvents[18], searchBenchmarkWorkloads[8],
 *     searchBenchmarkVerdicts[8],
 *     searchProjectionEvolutionPaths[6],
 *     languageNormalizationStrategies[3],
 *     savedSearchSharingScopes[3], searchFacetAxes[7]);
 *   - sandbox egress allowlist (postgres, apicurio,
 *     vault-agent, openfga, audit-service, kafka-broker)
 *     distinct from the E7.5 albums allowlist;
 *   - projection lag state matrix validation (every
 *     status reachable from HEALTHY unless terminal;
 *     terminal states MUST have empty transition lists);
 *   - reconciliation state matrix validation (every
 *     status reachable from QUEUED unless terminal);
 *   - guard rails (postgresFullTextOnly, pgTrgmEnabled,
 *     unaccentNormalizationRequired,
 *     privacyClassificationRequired,
 *     projectionVersioningRequired,
 *     eventConsumptionIdempotent, idempotencyKeyRequired,
 *     outboxRelaySeparated, reconciliationWorkflowRequired,
 *     lagMetricsExported, lagBudgetBreachedAlerts,
 *     projectionOutOfBandForbidden,
 *     crossServiceReferencesAreOpaque,
 *     crossServiceReferencesRequirePublisherResolution,
 *     dnaBucketAccess MUST equal FORBIDDEN,
 *     languageTagIetfBcp47Required,
 *     savedSearchSharingOpaque, savedSearchQueryNoRawPii,
 *     benchmarkSuiteRequiredForRelease,
 *     adrRequiredBeforeOpenSearch);
 *   - numeric bounds (maxNameLength=256, maxAliasLength=256,
 *     maxAliasPerDocument=64, maxLanguagesPerDocument=16,
 *     maxBcp47TagLength=64, maxQueryLength=512,
 *     maxSavedSearchNameLength=128,
 *     maxSavedSearchDescriptionLength=1024,
 *     maxSavedSearchResultsPerPage=100, maxCursorDepth=1024,
 *     maxFacetCountPerAxis=256, maxProjectionBatchSize=1024,
 *     maxOutboxBatchSize=256,
 *     projectionLagP95BudgetSeconds=30,
 *     projectionLagP99BudgetSeconds=120,
 *     projectionLagBreachSeconds=300,
 *     projectionLagHeartbeatSeconds=6,
 *     backfillBatchSize=4096, backfillTimeoutSeconds=1800,
 *     backfillHeartbeatSeconds=30,
 *     backfillLookbackHours=168, backfillCadenceHours=24,
 *     reconciliationP95BudgetSeconds=600,
 *     reconciliationHeartbeatSeconds=60,
 *     benchmarkSuiteMaxDatasets=16,
 *     benchmarkSuiteMaxQueriesPerDataset=1024,
 *     benchmarkSuiteP95BudgetMilliseconds=1000,
 *     benchmarkSuiteP99BudgetMilliseconds=2500,
 *     benchmarkSuiteFreshnessBudgetSeconds=60,
 *     benchmarkSuiteFuzzyRecallFloor=0.85,
 *     benchmarkSuiteFuzzyPrecisionFloor=0.90,
 *     benchmarkSuiteFacetCardinalityFloor=16);
 *   - reconciliation invariants (projectionLagP95BudgetSeconds
 *     < multiplier × heartbeatSeconds; backfillLookbackHours
 *     ≥ multiplier × backfillCadenceHours; benchmarkSuiteP95
 *     ≤ multiplier × freshnessBudget);
 *   - audit hooks + forbidden payload patterns;
 *   - chart mirror byte-equality.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(
  ROOT,
  "contracts/search/search-projection-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/search-projection-policy.yaml",
);

const REQUIRED_SEARCH_DOCUMENT_KINDS = [
  "PERSON",
  "EVENT",
  "PLACE",
  "SOURCE",
  "CITATION",
  "MEDIA",
  "ALBUM",
];

const REQUIRED_SEARCH_PRIVACY_CLASSES = [
  "PRIVATE",
  "TREE_DEFAULT",
  "UNLISTED",
  "PUBLIC",
  "REDACTED",
];

const REQUIRED_SEARCH_PROJECTION_STATUSES = [
  "PENDING",
  "INDEXED",
  "STALE",
  "REDACTED",
  "PURGED",
];

const REQUIRED_SEARCH_SOURCE_DOMAINS = [
  "GENEALOGY",
  "RESEARCH",
  "COLLABORATION",
  "MEDIA",
];

const REQUIRED_SEARCH_EVENT_TYPES = [
  "PERSON_CREATED",
  "PERSON_UPDATED",
  "PERSON_DELETED",
  "PERSON_LIVING_STATUS_CHANGED",
  "PERSON_PRIVACY_CHANGED",
  "EVENT_CREATED",
  "EVENT_UPDATED",
  "EVENT_DELETED",
  "PLACE_CREATED",
  "PLACE_UPDATED",
  "PLACE_DELETED",
  "SOURCE_CREATED",
  "CITATION_CREATED",
  "MEDIA_ASSET_INDEXED",
  "MEDIA_ASSET_REDACTED",
  "MEDIA_ASSET_PURGED",
  "ALBUM_CREATED",
  "ALBUM_RENAMED",
  "ALBUM_VISIBILITY_CHANGED",
  "ALBUM_RECONCILIATION_PURGED",
];

const REQUIRED_SEARCH_LAG_PHASES = [
  "HEALTHY",
  "BACKFILLING",
  "DRAINING",
  "AT_RISK",
  "DEGRADED",
];

const REQUIRED_SEARCH_RECONCILIATION_STATUSES = [
  "QUEUED",
  "BACKFILL_RUNNING",
  "BACKFILL_DONE",
  "INDEX_REBUILT",
  "DRAIN_RUNNING",
  "DRAINED",
  "DECIDED",
  "FAILED",
  "RETIRED",
];

const REQUIRED_SEARCH_FAILURE_REASONS = [
  "PROJECTION_NOT_FOUND",
  "PROJECTION_VERSION_MISMATCH",
  "PROJECTION_LAG_EXCEEDED",
  "PROJECTION_EVENT_TYPE_UNKNOWN",
  "PROJECTION_SOURCE_DOMAIN_UNKNOWN",
  "PROJECTION_TENANT_MISMATCH",
  "PROJECTION_PRIVACY_CLASS_FORBIDDEN",
  "PROJECTION_PRIVACY_REDACTION_MISSING",
  "PROJECTION_DNA_BUCKET_FORBIDDEN",
  "PROJECTION_RECONCILIATION_FAILED",
  "PROJECTION_RECONCILIATION_OUTBOX_FAILED",
  "PROJECTION_BACKFILL_TIMEOUT",
  "PROJECTION_EVENT_PAYLOAD_INVALID",
  "PROJECTION_IDEMPOTENCY_KEY_MISSING",
  "PROJECTION_OUTBOX_RELAY_LOOP",
  "PROJECTION_LANGUAGE_TAG_INVALID",
  "PROJECTION_NAME_TOO_LONG",
  "PROJECTION_ALIAS_TOO_MANY",
  "PROJECTION_ALIAS_TOO_LONG",
  "PROJECTION_QUERY_TOO_LONG",
  "PROJECTION_SAVED_SEARCH_SHARE_FORBIDDEN",
  "PROJECTION_NORMALIZED_TOKEN_INVALID",
  "PROJECTION_BACKFILL_QUOTA_EXCEEDED",
];

const REQUIRED_SEARCH_AUDIT_EVENTS = [
  "SEARCH_PROJECTION_RECEIVED",
  "SEARCH_PROJECTION_INDEXED",
  "SEARCH_PROJECTION_REDACTED",
  "SEARCH_PROJECTION_PURGED",
  "SEARCH_PROJECTION_REHYDRATED",
  "SEARCH_PROJECTION_RECONCILIATION_QUEUED",
  "SEARCH_PROJECTION_RECONCILIATION_RUN",
  "SEARCH_PROJECTION_RECONCILIATION_DRAINED",
  "SEARCH_PROJECTION_RECONCILIATION_PURGED",
  "SEARCH_PROJECTION_LAG_THRESHOLD_BREACHED",
  "SEARCH_PROJECTION_LAG_RECOVERED",
  "SEARCH_PROJECTION_BACKFILL_STARTED",
  "SEARCH_PROJECTION_BACKFILL_FINISHED",
  "SEARCH_PROJECTION_DNA_BUCKET_REFUSED",
  "SEARCH_PROJECTION_PRIVACY_REDACTED",
  "SEARCH_PROJECTION_REINDEX_TRIGGERED",
  "SEARCH_PROJECTION_EVENT_DUPLICATE_DROPPED",
  "SEARCH_PROJECTION_FACET_CACHE_REBUILT",
];

const REQUIRED_SEARCH_BENCHMARK_WORKLOADS = [
  "EXACT_PERSON",
  "EXACT_ALBUM",
  "FUZZY_PERSON_TRIGRAM",
  "FUZZY_PLACE_TRIGRAM",
  "FACET_TREE_FAMILY",
  "FACET_DECADE_LIVING_STATUS",
  "CURSOR_PAGINATION",
  "SAVED_SEARCH_ALERT",
];

const REQUIRED_SEARCH_BENCHMARK_VERDICTS = [
  "PASS",
  "PASS_WITH_NOTES",
  "FAIL_P95",
  "FAIL_LAG",
  "FAIL_FRESHNESS",
  "FAIL_INDEX",
  "FAIL_SAFETY",
  "BLOCKED_ADR_REQUIRED",
];

const REQUIRED_EVOLUTION_PATHS = [
  "POSTGRES_HOLD",
  "POSTGRES_REINDEX",
  "POSTGRES_PARTITION",
  "POSTGRES_GIN_REWRITE",
  "OPENSEARCH_REQUIRED",
  "ADAPTIVE_INDEX_REQUIRED",
];

const REQUIRED_LANGUAGE_STRATEGIES = [
  "UNACCENT_LOWERCASE_NFC",
  "UNACCENT_LOWERCASE_NFKD",
  "UNACCENT_TRIGRAM_AWARE",
];

const REQUIRED_SAVED_SEARCH_SCOPES = [
  "PRIVATE",
  "TREE_COLLABORATORS",
  "TENANT_TEAM",
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

const REQUIRED_DNA_BUCKET_PREFIXES = [
  "dna/raw",
  "dna/match",
  "dna/consent",
];

const REQUIRED_AUDIT_HOOKS = [
  "SEARCH_PROJECTION_RECEIVED",
  "SEARCH_PROJECTION_INDEXED",
  "SEARCH_PROJECTION_REDACTED",
  "SEARCH_PROJECTION_PURGED",
  "SEARCH_PROJECTION_RECONCILIATION_QUEUED",
  "SEARCH_PROJECTION_RECONCILIATION_RUN",
  "SEARCH_PROJECTION_RECONCILIATION_DRAINED",
  "SEARCH_PROJECTION_RECONCILIATION_PURGED",
  "SEARCH_PROJECTION_LAG_THRESHOLD_BREACHED",
  "SEARCH_PROJECTION_LAG_RECOVERED",
  "SEARCH_PROJECTION_BACKFILL_STARTED",
  "SEARCH_PROJECTION_BACKFILL_FINISHED",
  "SEARCH_PROJECTION_DNA_BUCKET_REFUSED",
  "SEARCH_PROJECTION_PRIVACY_REDACTED",
  "SEARCH_PROJECTION_REINDEX_TRIGGERED",
  "SEARCH_PROJECTION_EVENT_DUPLICATE_DROPPED",
  "SEARCH_PROJECTION_FACET_CACHE_REBUILT",
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
  "gp.search.v1.ProjectionReconciliationPurged",
  "gp.search.v1.ProjectionLagBreached",
  "gp.search.v1.SavedSearchCreated",
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
];

const REQUIRED_BENCHMARK_QUERY_LANGUAGES = ["en", "vi", "ja", "fr", "es"];

const REQUIRED_BENCHMARK_DATASET_AXES = [
  "treeSize",
  "nameCardinality",
  "aliasCardinality",
  "placeCardinality",
  "privacyClassMix",
];

const REQUIRED_BENCHMARK_WORST_CASE = [
  "oneCharacterTrigram",
  "diacriticOnly",
  "mixedScript",
  "maxCursorDepth",
  "maxFacetCardinality",
];

const REQUIRED_BENCHMARK_ROLLOUT_GATES = [
  "PASS",
  "PASS_WITH_NOTES",
  "FAIL_P95",
  "FAIL_FRESHNESS",
  "FAIL_INDEX",
];

const REQUIRED_CAPABILITY_FORBIDDEN = [
  "Generic job-state / retry scheduler (use Temporal per ADR-E0.5-07)",
  "Distributed transaction (outbox relay is enough)",
  "Custom OpenSearch indexing (the worker consumes Avro events only)",
  "Custom PII / DNA detector (use the platform-wide redactor)",
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

function asArray(value) {
  if (value === null || value === undefined) return [];
  if (Array.isArray(value)) return value;
  if (typeof value === "object") return Object.values(value);
  return [value];
}

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
  const frontier = [initialStatus];
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

function loadYaml(text) {
  // Minimal YAML parser sufficient for this contract.
  // Handles comments, key:value pairs, nested mappings via
  // indentation, sequences with `-`, and inline `[a, b, c]` lists.
  const lines = text.split(/\r?\n/);
  const root = {};
  const stack = [{ indent: -1, container: root, isArray: false }];

  const stripComment = (line) => {
    const idx = line.indexOf("#");
    if (idx < 0) return line;
    let inString = false;
    let quote = null;
    for (let i = 0; i < idx; i += 1) {
      const c = line[i];
      if (inString) {
        if (c === "\\") {
          i += 1;
          continue;
        }
        if (c === quote) inString = false;
        continue;
      }
      if (c === '"' || c === "'") {
        inString = true;
        quote = c;
      }
    }
    if (inString) return line;
    return line.slice(0, idx);
  };

  const parseInlineList = (raw) => {
    const inner = raw.trim();
    if (!inner.startsWith("[") || !inner.endsWith("]")) return undefined;
    const body = inner.slice(1, -1).trim();
    if (!body) return [];
    return body
      .split(",")
      .map((p) => p.trim())
      .filter((p) => p.length > 0)
      .map((p) => p.replace(/^['"]|['"]$/g, ""));
  };

  const coerce = (raw) => {
    const trimmed = raw.trim();
    if (trimmed === "") return "";
    if (trimmed === "true") return true;
    if (trimmed === "false") return false;
    if (trimmed === "null" || trimmed === "~") return null;
    const inline = parseInlineList(trimmed);
    if (inline !== undefined) return inline;
    if (/^-?\d+$/.test(trimmed)) return Number.parseInt(trimmed, 10);
    if (/^-?\d+\.\d+$/.test(trimmed)) return Number.parseFloat(trimmed);
    if (
      (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
      (trimmed.startsWith("'") && trimmed.endsWith("'"))
    ) {
      return trimmed.slice(1, -1);
    }
    return trimmed;
  };

  for (let raw of lines) {
    const stripped = stripComment(raw);
    if (!stripped.trim()) continue;
    const indentMatch = /^( *)(.*)$/.exec(stripped);
    const indent = indentMatch[1].length;
    const body = indentMatch[2];

    // Detect multiline block scalar (`|`) markers: if current frame
    // is a mapping and the last key just received `|` as its raw value,
    // collect indented continuation lines as the scalar body.
    if (
      body !== "|" &&
      body !== ">" &&
      stack.length > 0
    ) {
      const top = stack[stack.length - 1];
      if (
        !Array.isArray(top.container) &&
        top.pendingBlockScalar &&
        indent >= top.pendingBlockScalar
      ) {
        top.container[top.pendingBlockScalarKey] = top.container[top.pendingBlockScalarKey]
          ? `${top.container[top.pendingBlockScalarKey]}\n${stripped.trim()}`
          : stripped.trim();
        continue;
      }
      if (top.pendingBlockScalar && indent <= top.pendingBlockScalar) {
        top.pendingBlockScalar = 0;
        top.pendingBlockScalarKey = null;
      }
    }
    while (stack.length > 1) {
      const top = stack[stack.length - 1];
      if (indent < top.indent) {
        top.pendingBlockScalar = 0;
        top.pendingBlockScalarKey = null;
        stack.pop();
      } else break;
    }
    const frame = stack[stack.length - 1];
    if (body.startsWith("- ")) {
      if (!Array.isArray(frame.container)) {
        fail(`YAML: unexpected sequence at indent ${indent}`);
        continue;
      }
      const item = body.slice(2).trim();
      if (item.length === 0) {
        const child = {};
        frame.container.push(child);
        stack.push({ indent: indent + 2, container: child, isArray: false });
        continue;
      }
      const colonIdx = item.indexOf(":");
      if (colonIdx < 0) {
        frame.container.push(coerce(item));
        continue;
      }
      const key = item.slice(0, colonIdx).trim();
      const rest = item.slice(colonIdx + 1).trim();
      const child = {};
      child[key] = rest === "" ? null : coerce(rest);
      frame.container.push(child);
      stack.push({ indent: indent + 2, container: child, isArray: false });
      continue;
    }
    const colonIdx = body.indexOf(":");
    if (colonIdx < 0) {
      fail(`YAML: invalid line '${body}'`);
      continue;
    }
    const key = body.slice(0, colonIdx).trim();
    const rest = body.slice(colonIdx + 1).trim();
    if (!frame.container || Array.isArray(frame.container)) {
      fail(`YAML: cannot add key '${key}' to non-mapping frame`);
      continue;
    }
    if (rest === "|" || rest === ">") {
      // Multiline block scalar — store as string and collect continuation.
      frame.container[key] = "";
      frame.pendingBlockScalar = indent + 2;
      frame.pendingBlockScalarKey = key;
      continue;
    }
    if (rest === "" || rest === null) {
      // Determine whether the next non-empty line is a sequence.
      const nextIdx = lines.indexOf(raw) + 1;
      let nextMeaningful = "";
      for (let i = nextIdx; i < lines.length; i += 1) {
        const cand = stripComment(lines[i]);
        if (cand.trim().length === 0) continue;
        nextMeaningful = cand;
        break;
      }
      const nextIndent = /^( *)/.exec(nextMeaningful)[1].length;
      if (nextMeaningful.trim().startsWith("- ") && nextIndent > indent) {
        const arr = [];
        frame.container[key] = arr;
        stack.push({ indent: nextIndent, container: arr, isArray: true });
      } else {
        const child = {};
        frame.container[key] = child;
        stack.push({ indent: nextIndent, container: child, isArray: false });
      }
      continue;
    }
    frame.container[key] = coerce(rest);
  }
  return root;
}

function readContract(path) {
  const raw = readFileSync(path, "utf8");
  return loadYaml(raw);
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

  // ---------- Closed-set vocabularies ----------
  assertClosedSet(
    "searchDocumentKinds",
    REQUIRED_SEARCH_DOCUMENT_KINDS,
    asArray(contract.searchDocumentKinds?.values),
  );
  assertClosedSet(
    "searchPrivacyClasses",
    REQUIRED_SEARCH_PRIVACY_CLASSES,
    asArray(contract.searchPrivacyClasses?.values),
  );
  assertClosedSet(
    "searchProjectionStatuses",
    REQUIRED_SEARCH_PROJECTION_STATUSES,
    asArray(contract.searchProjectionStatuses?.values),
  );
  assertClosedSet(
    "searchSourceDomains",
    REQUIRED_SEARCH_SOURCE_DOMAINS,
    asArray(contract.searchSourceDomains?.values),
  );
  assertClosedSet(
    "searchEventTypes",
    REQUIRED_SEARCH_EVENT_TYPES,
    asArray(contract.searchEventTypes?.values),
  );
  assertClosedSet(
    "searchProjectionLagPhases",
    REQUIRED_SEARCH_LAG_PHASES,
    asArray(contract.searchProjectionLagPhases?.values),
  );
  assertClosedSet(
    "searchReconciliationStatuses",
    REQUIRED_SEARCH_RECONCILIATION_STATUSES,
    asArray(contract.searchReconciliationStatuses?.values),
  );
  assertClosedSet(
    "searchFailureReasons",
    REQUIRED_SEARCH_FAILURE_REASONS,
    asArray(contract.searchFailureReasons?.values),
  );
  assertClosedSet(
    "searchAuditEvents",
    REQUIRED_SEARCH_AUDIT_EVENTS,
    asArray(contract.searchAuditEvents?.values),
  );
  assertClosedSet(
    "searchBenchmarkWorkloads",
    REQUIRED_SEARCH_BENCHMARK_WORKLOADS,
    asArray(contract.searchBenchmarkWorkloads?.values),
  );
  assertClosedSet(
    "searchBenchmarkVerdicts",
    REQUIRED_SEARCH_BENCHMARK_VERDICTS,
    asArray(contract.searchBenchmarkVerdicts?.values),
  );
  assertClosedSet(
    "searchProjectionEvolutionPaths",
    REQUIRED_EVOLUTION_PATHS,
    asArray(contract.searchProjectionEvolutionPaths?.values),
  );
  assertClosedSet(
    "languageNormalizationStrategies",
    REQUIRED_LANGUAGE_STRATEGIES,
    asArray(contract.languageNormalizationStrategies?.values),
  );
  assertClosedSet(
    "savedSearchSharingScopes",
    REQUIRED_SAVED_SEARCH_SCOPES,
    asArray(contract.savedSearchSharingScopes?.values),
  );
  assertClosedSet(
    "searchFacetAxes",
    REQUIRED_FACET_AXES,
    asArray(contract.searchFacetAxes?.values),
  );

  // ---------- Sandbox egress allowlist ----------
  assertClosedSet(
    "sandboxEgressAllowlist",
    REQUIRED_SANDBOX_EGRESS,
    asArray(contract.sandboxEgressAllowlist?.values),
    "sandbox egress allowlist",
  );

  // ---------- State matrices ----------
  assertStateMatrix(
    "projectionLagStateMatrix",
    contract.projectionLagStateMatrix,
    ["HEALTHY", "BACKFILLING", "DRAINING", "AT_RISK", "DEGRADED", "DECIDED"],
    "HEALTHY",
  );
  assertStateMatrix(
    "projectionReconciliationStateMatrix",
    contract.projectionReconciliationStateMatrix,
    REQUIRED_SEARCH_RECONCILIATION_STATUSES,
    "QUEUED",
  );

  // ---------- Boolean guard rails ----------
  const gr = contract.guardRails || {};
  const booleanGuards = [
    ["postgresFullTextOnly", true],
    ["pgTrgmEnabled", true],
    ["unaccentNormalizationRequired", true],
    ["privacyClassificationRequired", true],
    ["projectionVersioningRequired", true],
    ["eventConsumptionIdempotent", true],
    ["idempotencyKeyRequired", true],
    ["outboxRelaySeparated", true],
    ["reconciliationWorkflowRequired", true],
    ["lagMetricsExported", true],
    ["lagBudgetBreachedAlerts", true],
    ["projectionOutOfBandForbidden", true],
    ["crossServiceReferencesAreOpaque", true],
    ["crossServiceReferencesRequirePublisherResolution", true],
    ["languageTagIetfBcp47Required", true],
    ["savedSearchSharingOpaque", true],
    ["savedSearchQueryNoRawPii", true],
    ["benchmarkSuiteRequiredForRelease", true],
    ["adrRequiredBeforeOpenSearch", true],
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

  // ---------- Numeric bounds ----------
  const nb = contract.numericBounds || {};
  const numericGuards = {
    maxNameLength: 256,
    maxAliasLength: 256,
    maxAliasPerDocument: 64,
    maxLanguagesPerDocument: 16,
    maxBcp47TagLength: 64,
    maxQueryLength: 512,
    maxSavedSearchNameLength: 128,
    maxSavedSearchDescriptionLength: 1024,
    maxSavedSearchResultsPerPage: 100,
    maxCursorDepth: 1024,
    maxFacetCountPerAxis: 256,
    maxProjectionBatchSize: 1024,
    maxOutboxBatchSize: 256,
    projectionLagP95BudgetSeconds: 24,
    projectionLagP99BudgetSeconds: 120,
    projectionLagBreachSeconds: 300,
    projectionLagHeartbeatSeconds: 5,
    backfillBatchSize: 4096,
    backfillTimeoutSeconds: 1800,
    backfillHeartbeatSeconds: 30,
    backfillLookbackHours: 168,
    backfillCadenceHours: 24,
    reconciliationP95BudgetSeconds: 600,
    reconciliationHeartbeatSeconds: 60,
    benchmarkSuiteMaxDatasets: 16,
    benchmarkSuiteMaxQueriesPerDataset: 1024,
    benchmarkSuiteP95BudgetMilliseconds: 1000,
    benchmarkSuiteP99BudgetMilliseconds: 2500,
    benchmarkSuiteFreshnessBudgetSeconds: 60,
    benchmarkSuiteFuzzyRecallFloor: 0.85,
    benchmarkSuiteFuzzyPrecisionFloor: 0.90,
    benchmarkSuiteFacetCardinalityFloor: 16,
    projectionDocumentIdLength: 64,
    tenantScopeIdLength: 64,
    actorPseudoIdLength: 64,
    correlationIdLength: 128,
    idempotencyKeyLength: 128,
  };
  for (const [key, expected] of Object.entries(numericGuards)) {
    const actual = nb[key];
    if (actual !== expected) {
      fail(`numericBounds.${key} MUST equal ${expected} (got ${actual})`);
    } else {
      ok(`numericBounds.${key} = ${expected}`);
    }
  }

  // ---------- Reconciled invariants (numeric) ----------
  const ri = contract.reconciliationInvariants || {};
  const invariants = {
    projectionLagP95BudgetSeconds: 24,
    projectionLagHeartbeatMultiplier: 6,
    backfillCadenceMultiplier: 24,
    backfillLookbackMultiplier: 7,
    benchmarkSuiteP95BudgetMultiplier: 1,
    benchmarkSuiteFreshnessBudgetMultiplier: 60,
  };
  for (const [key, expected] of Object.entries(invariants)) {
    if (ri[key] !== expected) {
      fail(`reconciliationInvariants.${key} MUST equal ${expected} (got ${ri[key]})`);
    } else {
      ok(`reconciliationInvariants.${key} = ${expected}`);
    }
  }
  if (nb.projectionLagP95BudgetSeconds >= ri.projectionLagHeartbeatMultiplier * nb.projectionLagHeartbeatSeconds) {
    fail(
      `projection lag invariant violated: p95=${nb.projectionLagP95BudgetSeconds}s MUST be < ${ri.projectionLagHeartbeatMultiplier} × heartbeat=${nb.projectionLagHeartbeatSeconds}s`,
    );
  } else {
    ok(`projection lag invariant: p95=${nb.projectionLagP95BudgetSeconds} < ${ri.projectionLagHeartbeatMultiplier} × ${nb.projectionLagHeartbeatSeconds}`);
  }
  if (nb.backfillLookbackHours < ri.backfillLookbackMultiplier * nb.backfillCadenceHours) {
    fail(
      `backfill invariant violated: lookback=${nb.backfillLookbackHours}h MUST be >= ${ri.backfillLookbackMultiplier} × cadence=${nb.backfillCadenceHours}h`,
    );
  } else {
    ok(`backfill invariant: lookback=${nb.backfillLookbackHours} >= ${ri.backfillLookbackMultiplier} × ${nb.backfillCadenceHours}`);
  }
  if (nb.benchmarkSuiteP95BudgetMilliseconds > ri.benchmarkSuiteP95BudgetMultiplier * nb.benchmarkSuiteFreshnessBudgetSeconds * 1000) {
    fail(
      `benchmark invariant violated: p95=${nb.benchmarkSuiteP95BudgetMilliseconds}ms MUST be <= ${ri.benchmarkSuiteP95BudgetMultiplier} × freshness=${nb.benchmarkSuiteFreshnessBudgetSeconds * 1000}ms`,
    );
  } else {
    ok(`benchmark invariant: p95=${nb.benchmarkSuiteP95BudgetMilliseconds} <= ${ri.benchmarkSuiteP95BudgetMultiplier} × ${nb.benchmarkSuiteFreshnessBudgetSeconds * 1000}`);
  }

  // ---------- Outbox envelope ----------
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

  // ---------- Audit hooks ----------
  const audit = contract.auditHooks || {};
  assertClosedSet(
    "auditHooks.auditRequired",
    REQUIRED_AUDIT_HOOKS,
    asArray(audit.auditRequired),
    "auditHooks.auditRequired",
  );

  // ---------- Forbidden payload patterns ----------
  assertClosedSet(
    "forbiddenPayloadPatterns",
    REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS,
    asArray(contract.forbiddenPayloadPatterns),
    "forbidden payload patterns",
  );

  // ---------- Benchmark suite contract ----------
  const bsc = contract.benchmarkSuiteContract || {};
  assertClosedSet(
    "benchmarkSuiteContract.queryLanguages",
    REQUIRED_BENCHMARK_QUERY_LANGUAGES,
    asArray(bsc.queryLanguages),
    "benchmark queryLanguages",
  );
  assertClosedSet(
    "benchmarkSuiteContract.datasetAxes",
    REQUIRED_BENCHMARK_DATASET_AXES,
    asArray(bsc.datasetAxes),
    "benchmark datasetAxes",
  );
  assertClosedSet(
    "benchmarkSuiteContract.worstCaseQueries",
    REQUIRED_BENCHMARK_WORST_CASE,
    asArray(bsc.worstCaseQueries),
    "benchmark worstCaseQueries",
  );
  const rolloutRaw = bsc.rolloutGates;
  const rollout = Array.isArray(rolloutRaw) ? rolloutRaw : [];
  for (const key of REQUIRED_BENCHMARK_ROLLOUT_GATES) {
    const entry = rollout.find((e) => e && typeof e === "object" && key in e);
    if (!entry) {
      fail(`benchmarkSuiteContract.rolloutGates MUST declare verdict '${key}'`);
    } else {
      const arr = asArray(entry[key]);
      if (arr.length === 0) {
        fail(`benchmarkSuiteContract.rolloutGates.${key} MUST be non-empty`);
      } else {
        ok(`benchmarkSuiteContract.rolloutGates.${key} = ${JSON.stringify(arr)}`);
      }
    }
  }

  // ---------- Capability boundaries ----------
  const cb = contract.capabilityBoundaries || {};
  assertClosedSet(
    "capabilityBoundaries.forbiddenSelfBuilt",
    REQUIRED_CAPABILITY_FORBIDDEN,
    asArray(cb.forbiddenSelfBuilt),
    "capability boundaries",
  );

  // ---------- Chart mirror byte-equality ----------
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
  console.log("\nsearch projection policy contract OK.");
}

main();