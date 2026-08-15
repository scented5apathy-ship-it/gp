#!/usr/bin/env node
/**
 * scripts/lint-albums-linking.mjs
 *
 * E7.5 deep validator for the media albums + linking policy
 * contract under
 * `contracts/media/albums-linking-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/albums-linking-policy.yaml`.
 *
 * Mirrors the structure of
 * `lint-media-protected-delivery.mjs` (E7.4):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (albumVisibilities, albumLifecycleStates,
 *     albumMemberKinds, albumMemberSources,
 *     albumSortOrders, albumReferenceKinds,
 *     albumReferenceOutcomes,
 *     albumReferenceActionModes,
 *     reconciliationOutcomes, albumFailureReasons,
 *     albumAuditEvents, dnaBucketPrefixes,
 *     albumLinkableAssetStatuses,
 *     crossServiceReferenceKinds,
 *     tagNormalizationRules);
 *   - sandbox egress allowlist (apicurio, vault-agent,
 *     openfga, audit-service, kafka-broker,
 *     search-service) — distinct from the E7.4
 *     processing allowlist;
 *   - album authorization state matrix validation (every
 *     status reachable from PENDING unless terminal;
 *     terminal states MUST have empty transition lists);
 *   - reconciliation state matrix validation (every
 *     status reachable from QUEUED unless terminal);
 *   - guard rails (sandboxOnly, sandboxReadOnlyFilesystem,
 *     sandboxNonRoot, egressDeniedToPublicInternet,
 *     onlyDerivedReadyIsLinkable, dnaBucketAccess,
 *     crossServiceReferencesAreOpaque,
 *     crossServiceReferencesRequirePublisherResolution,
 *     softDeleteRetentionRequired,
 *     objectLockComplianceRequiredForLegalHold,
 *     captionLanguageIetfBcp47Required,
 *     placeReferenceFormat, dateReferenceFormat,
 *     tagNormalizationRule, albumVersioningRequired,
 *     albumEtagRequired,
 *     albumReferencesCheckedBeforeCommit,
 *     reconciliationWorkflowRequired,
 *     reconciliationOutboxEventRequired,
 *     albumOpsOutOfBandForbidden, audit toggles);
 *   - numeric bounds (maxItemsPerAlbum, maxAlbumsPerTenant,
 *     maxAlbumsPerUser, maxReferencesPerItem,
 *     maxCaptionLength, maxTagLength, maxTagsPerItem,
 *     softDeleteRetentionDays, objectLockComplianceDays,
 *     reconciliationCadenceHours, reconciliationBatchSize,
 *     reconciliationLookbackHours,
 *     reconciliationP95BudgetSeconds,
 *     reconciliationOutboxBatchSize, activity heartbeats,
 *     string length caps);
 *   - reconciliation invariants (p95 budget < multiplier ×
 *     heartbeat, lookback ≥ cadence × 2, batch sizes
 *     bounded by `reconciliationOutboxBatchSize`);
 *   - audit hooks + invariant reason codes;
 *   - forbidden-literal / forbidden-payload scan;
 *   - chart mirror byte-equality.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration
 * error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(
  ROOT,
  "contracts/media/albums-linking-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/albums-linking-policy.yaml",
);

const REQUIRED_ALBUM_VISIBILITIES = [
  "PRIVATE",
  "UNLISTED",
  "PUBLIC",
  "INTERNAL_TENANT",
  "LEGAL_HOLD",
];

const REQUIRED_ALBUM_LIFECYCLE_STATES = [
  "ACTIVE",
  "SOFT_DELETED",
  "LEGAL_HOLD",
  "PURGED",
  "FAILED",
];

const REQUIRED_ALBUM_MEMBER_KINDS = [
  "ASSET",
  "VARIANT",
  "FOLDER",
  "COLLECTION",
];

const REQUIRED_ALBUM_MEMBER_SOURCES = [
  "USER_UPLOAD",
  "DERIVATIVE",
  "OCR",
  "THUMBNAIL",
  "PREVIEW",
  "TRANSCODE",
];

const REQUIRED_ALBUM_SORT_ORDERS = [
  "MANUAL_PIN",
  "CAPTURED_AT_ASC",
  "CAPTURED_AT_DESC",
  "TITLE_ASC",
  "ADDED_AT_ASC",
  "ADDED_AT_DESC",
];

const REQUIRED_ALBUM_REFERENCE_KINDS = [
  "PERSON",
  "EVENT",
  "SOURCE",
  "CITATION",
  "PLACE",
  "DATE",
];

const REQUIRED_ALBUM_REFERENCE_OUTCOMES = [
  "RESOLVED",
  "DANGLING",
  "REVOKED",
  "PUBLISHER_MISSING",
];

const REQUIRED_ALBUM_REFERENCE_ACTION_MODES = [
  "DIRECT_EDIT",
  "APPROVAL_REQUIRED",
];

const REQUIRED_RECONCILIATION_OUTCOMES = [
  "HEALTHY",
  "DANGLING_REFERENCES",
  "REVOKED_REFERENCES",
  "ORPHAN_ASSETS",
  "QUOTA_EXCEEDED",
  "PURGED",
];

const REQUIRED_ALBUM_FAILURE_REASONS = [
  "ALBUM_NOT_FOUND",
  "ALBUM_VERSION_MISMATCH",
  "ALBUM_QUOTA_EXCEEDED",
  "ALBUM_VISIBILITY_FORBIDDEN",
  "ALBUM_ITEM_NOT_FOUND",
  "ALBUM_REFERENCE_INVALID",
  "ALBUM_REFERENCE_DANGLING",
  "ALBUM_REFERENCE_REVOKED",
  "ALBUM_REFERENCE_KIND_UNKNOWN",
  "ALBUM_LIFECYCLE_FORBIDDEN",
  "ALBUM_CAPTION_LANGUAGE_MISSING",
  "ALBUM_TAG_TOO_LONG",
  "ALBUM_CAPTION_TOO_LONG",
  "ALBUM_TAG_TOO_MANY",
  "ALBUM_REFERENCES_TOO_MANY",
  "ALBUM_ITEMS_TOO_MANY",
  "ALBUM_OBJECT_KEY_TOO_LONG",
  "ALBUM_ACTOR_PSEUDO_ID_TOO_LONG",
  "ALBUM_CORRELATION_ID_TOO_LONG",
  "ALBUM_REFERENCE_PSEUDO_ID_TOO_LONG",
  "ALBUM_DERIVED_OBJECT_KEY_NOT_READY",
  "ALBUM_DNA_BUCKET_FORBIDDEN",
  "ALBUM_RECONCILIATION_FAILED",
];

const REQUIRED_ALBUM_AUDIT_EVENTS = [
  "ALBUM_CREATED",
  "ALBUM_RENAMED",
  "ALBUM_ITEM_ADDED",
  "ALBUM_ITEM_REMOVED",
  "ALBUM_ITEM_REORDERED",
  "ALBUM_TAGS_SET",
  "ALBUM_CAPTION_SET",
  "ALBUM_REFERENCE_ADDED",
  "ALBUM_REFERENCE_REMOVED",
  "ALBUM_REFERENCE_RESOLVED",
  "ALBUM_REFERENCE_DANGLING",
  "ALBUM_REFERENCE_REVOKED",
  "ALBUM_RECONCILIATION_RUN",
  "ALBUM_RECONCILIATION_PURGED",
  "ALBUM_SOFT_DELETED",
  "ALBUM_PURGED",
  "ALBUM_VISIBILITY_CHANGED",
  "ALBUM_DNA_BUCKET_REFUSED",
];

const REQUIRED_DNA_BUCKET_PREFIXES = [
  "dna/raw",
  "dna/match",
  "dna/consent",
];

const REQUIRED_ALBUM_LINKABLE_ASSET_STATUSES = ["DERIVED_READY"];

const REQUIRED_CROSS_SERVICE_REFERENCE_KINDS = [
  "PERSON",
  "EVENT",
  "SOURCE",
  "CITATION",
  "PLACE",
  "DATE",
];

const REQUIRED_TAG_NORMALIZATION_RULES = ["LOWERCASE_TRIM_DASH"];

const REQUIRED_SANDBOX_EGRESS_ALLOWLIST = [
  "apicurio",
  "vault-agent",
  "openfga",
  "audit-service",
  "kafka-broker",
  "search-service",
];

const REQUIRED_INVARIANTS = [
  "ALBUM_BLANK_ACTOR",
  "ALBUM_BLANK_TENANT",
  "ALBUM_BLANK_CORRELATION",
  "ALBUM_BLANK_ALBUM_ID",
  "ALBUM_BLANK_TITLE",
  "ALBUM_TITLE_TOO_LONG",
  "ALBUM_DESCRIPTION_TOO_LONG",
  "ALBUM_VISIBILITY_FORBIDDEN",
  "ALBUM_LIFECYCLE_FORBIDDEN",
  "ALBUM_QUOTA_EXCEEDED",
  "ALBUM_ITEMS_TOO_MANY",
  "ALBUM_VERSION_MISMATCH",
  "ALBUM_NOT_FOUND",
  "ALBUM_ITEM_NOT_FOUND",
  "ALBUM_OBJECT_KEY_TOO_LONG",
  "ALBUM_ACTOR_PSEUDO_ID_TOO_LONG",
  "ALBUM_CORRELATION_ID_TOO_LONG",
  "ALBUM_DERIVED_OBJECT_KEY_NOT_READY",
  "ALBUM_DNA_BUCKET_FORBIDDEN",
  "ALBUM_REFERENCE_PSEUDO_ID_TOO_LONG",
  "ALBUM_REFERENCE_KIND_UNKNOWN",
  "ALBUM_REFERENCE_DANGLING",
  "ALBUM_REFERENCE_REVOKED",
  "ALBUM_REFERENCES_TOO_MANY",
  "ALBUM_CAPTION_TOO_LONG",
  "ALBUM_CAPTION_LANGUAGE_MISSING",
  "ALBUM_TAG_TOO_LONG",
  "ALBUM_TAG_TOO_MANY",
  "ALBUM_BCP47_TAG_TOO_LONG",
  "ALBUM_RECONCILIATION_FAILED",
  "ALBUM_RECONCILIATION_QUOTA_EXCEEDED",
  "ALBUM_CROSS_SERVICE_RAW_LEAK",
  "ALBUM_AUDIT_KEY_FORBIDDEN",
];

const REQUIRED_AUDIT_KEYS = [
  "actorPseudoId",
  "correlationId",
  "albumId",
];

const FORBIDDEN_LITERALS = [
  "credential",
  "api[_-]?key",
  "private[_-]?key",
  "authorization",
  "bearer",
  "raw[_-]?dna",
  "raw[_-]?ssn",
  "raw[_-]?passport",
  "fastq",
  "dna[_-]?raw",
  "exif[_-]?gps",
  "camera[_-]?serial",
  "name[_-]?on[_-]?birth",
  "social[_-]?security",
];

let violations = 0;

function fail(msg) {
  violations++;
  console.error(`[albums-linking] ${msg}`);
}

function requireField(obj, path, fileName) {
  const parts = path.split(".");
  let cur = obj;
  for (const p of parts) {
    if (cur == null || typeof cur !== "object") {
      fail(`${fileName}: missing required field '${path}'`);
      return undefined;
    }
    cur = cur[p];
  }
  if (cur === undefined) {
    fail(`${fileName}: missing required field '${path}'`);
    return undefined;
  }
  return cur;
}

function assertIncludes(set, required, label, fileName) {
  for (const v of required) {
    if (!set.has(v)) {
      fail(`${fileName}: ${label} missing required value '${v}'`);
    }
  }
}

function assertString(value, expected, label, fileName) {
  if (value !== expected) {
    fail(`${fileName}: ${label} must equal '${expected}', got '${value}'`);
  }
}

function scanForbiddenLiterals(raw, fileName) {
  const lines = raw.split(/\r?\n/);
  let inForbiddenList = false;
  let forbiddenIndent = -1;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.trimStart().startsWith("#")) continue;
    if (line.trim() === "") continue;
    const indent = line.match(/^ */)[0].length;
    const trimmed = line.slice(indent);
    if (/^forbiddenPayloadPatterns\s*:\s*$/.test(trimmed)) {
      inForbiddenList = true;
      forbiddenIndent = indent;
      continue;
    }
    if (/^forbiddenPayloadPatterns\s*:\s*\[/.test(trimmed)) {
      inForbiddenList = true;
      forbiddenIndent = indent;
      continue;
    }
    if (inForbiddenList) {
      if (indent <= forbiddenIndent && !/^- /.test(trimmed)) {
        inForbiddenList = false;
      } else if (/^- /.test(trimmed)) {
        continue;
      }
    }
    const stripped = line.replace(/"[^"]*"/g, "");
    for (const pat of FORBIDDEN_LITERALS) {
      const re = new RegExp(`\\b${pat}\\b`, "i");
      if (re.test(stripped)) {
        fail(
          `${fileName}:${i + 1}: forbidden literal '${pat}' outside the forbiddenPayloadPatterns list`,
        );
      }
    }
  }
}

function assertStateMatrix(parsed, path, fileName, initialState) {
  const matrix = requireField(parsed, path, fileName);
  if (!matrix || typeof matrix !== "object") return;
  const statuses = Object.keys(matrix);
  if (statuses.length === 0) {
    fail(`${fileName}: ${path} MUST be a non-empty object`);
    return;
  }
  if (!statuses.includes(initialState)) {
    fail(`${fileName}: ${path} missing initial state '${initialState}'`);
    return;
  }
  for (const status of statuses) {
    const nexts = matrix[status];
    if (nexts === undefined) continue;
    if (!Array.isArray(nexts)) {
      fail(`${fileName}: ${path}['${status}'] must be an array`);
    }
  }
  const reachable = new Set([initialState]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const status of Array.from(reachable)) {
      const nexts = matrix[status];
      if (Array.isArray(nexts)) {
        for (const n of nexts) {
          if (!reachable.has(n)) {
            reachable.add(n);
            changed = true;
          }
        }
      }
    }
  }
  for (const status of statuses) {
    if (status === initialState) continue;
    if (!reachable.has(status)) {
      fail(
        `${fileName}: ${path} status '${status}' is unreachable from '${initialState}'`,
      );
    }
  }
  for (const status of statuses) {
    const nexts = matrix[status];
    if (nexts === undefined) {
      fail(
        `${fileName}: ${path}['${status}'] is declared but missing transitions (terminal markers MUST be an explicit empty array [])`,
      );
    }
  }
}

function checkContract() {
  let raw;
  try {
    raw = readFileSync(CONTRACT, "utf8");
  } catch (err) {
    fail(`cannot read ${relative(ROOT, CONTRACT)}: ${err.message}`);
    return;
  }

  const fileName = relative(ROOT, CONTRACT);

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (_) {
    parsed = parseSimpleYaml(raw);
  }

  const spec = requireField(parsed, "spec", fileName);
  if (!spec) return;

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "media-albums-linking-policy/v1",
    "spec.policyId",
    fileName,
  );

  const closedSetChecks = [
    ["spec.albumVisibilities", REQUIRED_ALBUM_VISIBILITIES],
    ["spec.albumLifecycleStates", REQUIRED_ALBUM_LIFECYCLE_STATES],
    ["spec.albumMemberKinds", REQUIRED_ALBUM_MEMBER_KINDS],
    ["spec.albumMemberSources", REQUIRED_ALBUM_MEMBER_SOURCES],
    ["spec.albumSortOrders", REQUIRED_ALBUM_SORT_ORDERS],
    ["spec.albumReferenceKinds", REQUIRED_ALBUM_REFERENCE_KINDS],
    ["spec.albumReferenceOutcomes", REQUIRED_ALBUM_REFERENCE_OUTCOMES],
    [
      "spec.albumReferenceActionModes",
      REQUIRED_ALBUM_REFERENCE_ACTION_MODES,
    ],
    ["spec.reconciliationOutcomes", REQUIRED_RECONCILIATION_OUTCOMES],
    ["spec.albumFailureReasons", REQUIRED_ALBUM_FAILURE_REASONS],
    ["spec.albumAuditEvents", REQUIRED_ALBUM_AUDIT_EVENTS],
    ["spec.dnaBucketPrefixes", REQUIRED_DNA_BUCKET_PREFIXES],
    [
      "spec.albumLinkableAssetStatuses",
      REQUIRED_ALBUM_LINKABLE_ASSET_STATUSES,
    ],
    [
      "spec.crossServiceReferenceKinds",
      REQUIRED_CROSS_SERVICE_REFERENCE_KINDS,
    ],
    ["spec.tagNormalizationRules", REQUIRED_TAG_NORMALIZATION_RULES],
  ];

  for (const [path, required] of closedSetChecks) {
    const value = requireField(parsed, path, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${path} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, path, fileName);
  }

  const linkable = requireField(
    parsed,
    "spec.albumLinkableAssetStatuses",
    fileName,
  );
  if (Array.isArray(linkable)) {
    if (
      linkable.length !== 1 ||
      linkable[0] !== "DERIVED_READY"
    ) {
      fail(
        `${fileName}: spec.albumLinkableAssetStatuses MUST equal ['DERIVED_READY'] (E7.3 carry-through)`,
      );
    }
  }

  const sandboxAllow = requireField(
    parsed,
    "spec.sandboxEgressAllowlist",
    fileName,
  );
  if (!Array.isArray(sandboxAllow)) {
    fail(`${fileName}: spec.sandboxEgressAllowlist must be an array`);
  } else {
    const set = new Set(sandboxAllow);
    for (const must of REQUIRED_SANDBOX_EGRESS_ALLOWLIST) {
      if (!set.has(must)) {
        fail(
          `${fileName}: spec.sandboxEgressAllowlist missing required entry '${must}'`,
        );
      }
    }
  }

  assertStateMatrix(
    parsed,
    "spec.albumAuthorizationMatrix",
    fileName,
    "PENDING",
  );
  assertStateMatrix(
    parsed,
    "spec.reconciliationStateMatrix",
    fileName,
    "QUEUED",
  );

  const guardRails = [
    "spec.sandboxOnly",
    "spec.sandboxReadOnlyFilesystem",
    "spec.sandboxNonRoot",
    "spec.egressDeniedToPublicInternet",
    "spec.onlyDerivedReadyIsLinkable",
    "spec.crossServiceReferencesAreOpaque",
    "spec.crossServiceReferencesRequirePublisherResolution",
    "spec.softDeleteRetentionRequired",
    "spec.objectLockComplianceRequiredForLegalHold",
    "spec.captionLanguageIetfBcp47Required",
    "spec.albumVersioningRequired",
    "spec.albumEtagRequired",
    "spec.albumReferencesCheckedBeforeCommit",
    "spec.reconciliationWorkflowRequired",
    "spec.reconciliationOutboxEventRequired",
    "spec.albumOpsOutOfBandForbidden",
    "spec.auditRequiredOnAlbumCreated",
    "spec.auditRequiredOnAlbumRenamed",
    "spec.auditRequiredOnAlbumItemAdded",
    "spec.auditRequiredOnAlbumItemRemoved",
    "spec.auditRequiredOnAlbumItemReordered",
    "spec.auditRequiredOnAlbumTagsSet",
    "spec.auditRequiredOnAlbumCaptionSet",
    "spec.auditRequiredOnAlbumReferenceAdded",
    "spec.auditRequiredOnAlbumReferenceRemoved",
    "spec.auditRequiredOnAlbumReferenceResolved",
    "spec.auditRequiredOnAlbumReferenceDangling",
    "spec.auditRequiredOnAlbumReferenceRevoked",
    "spec.auditRequiredOnAlbumReconciliationRun",
    "spec.auditRequiredOnAlbumReconciliationPurged",
    "spec.auditRequiredOnAlbumSoftDeleted",
    "spec.auditRequiredOnAlbumPurged",
    "spec.auditRequiredOnAlbumVisibilityChanged",
    "spec.auditRequiredOnAlbumDnaBucketRefused",
  ];
  for (const g of guardRails) {
    const v = requireField(parsed, g, fileName);
    if (v !== true) {
      fail(`${fileName}: ${g} must be true`);
    }
  }

  const dnaAccess = requireField(parsed, "spec.dnaBucketAccess", fileName);
  if (dnaAccess !== "FORBIDDEN") {
    fail(
      `${fileName}: spec.dnaBucketAccess MUST equal 'FORBIDDEN', got '${dnaAccess}'`,
    );
  }

  const placeRefFormat = requireField(
    parsed,
    "spec.placeReferenceFormat",
    fileName,
  );
  if (placeRefFormat !== "PLACE_PSEUDO_ID") {
    fail(
      `${fileName}: spec.placeReferenceFormat MUST equal 'PLACE_PSEUDO_ID', got '${placeRefFormat}'`,
    );
  }
  const dateRefFormat = requireField(
    parsed,
    "spec.dateReferenceFormat",
    fileName,
  );
  if (dateRefFormat !== "DATE_PSEUDO_ID") {
    fail(
      `${fileName}: spec.dateReferenceFormat MUST equal 'DATE_PSEUDO_ID', got '${dateRefFormat}'`,
    );
  }
  const tagNorm = requireField(
    parsed,
    "spec.tagNormalizationRule",
    fileName,
  );
  if (tagNorm !== "LOWERCASE_TRIM_DASH") {
    fail(
      `${fileName}: spec.tagNormalizationRule MUST equal 'LOWERCASE_TRIM_DASH', got '${tagNorm}'`,
    );
  }

  const numericBounds = [
    ["spec.maxItemsPerAlbum", 4096],
    ["spec.maxAlbumsPerTenant", 8192],
    ["spec.maxAlbumsPerUser", 512],
    ["spec.maxReferencesPerItem", 64],
    ["spec.maxCaptionLength", 4096],
    ["spec.maxTagLength", 64],
    ["spec.maxTagsPerItem", 64],
    ["spec.maxAlbumTitleLength", 256],
    ["spec.maxAlbumDescriptionLength", 4096],
    ["spec.softDeleteRetentionDays", 365],
    ["spec.objectLockComplianceDays", 30],
    ["spec.reconciliationCadenceHours", 24],
    ["spec.reconciliationBatchSize", 1024],
    ["spec.reconciliationLookbackHours", 168],
    ["spec.reconciliationP95BudgetSeconds", 150],
    ["spec.reconciliationOutboxBatchSize", 256],
    ["spec.albumVersionFloor", 1],
    ["spec.albumIdLength", 64],
    ["spec.albumItemIdLength", 64],
    ["spec.albumReferencePseudoIdLength", 64],
    ["spec.albumActorPseudoIdLength", 64],
    ["spec.albumCorrelationIdLength", 128],
    ["spec.albumObjectKeyLength", 1024],
    ["spec.albumBcp47TagLength", 64],
    ["spec.albumEtagLength", 128],
    ["spec.activityHeartbeatSeconds", 30],
    ["spec.activityHeartbeatMultiplier", 6],
  ];
  for (const [path, expected] of numericBounds) {
    const v = requireField(parsed, path, fileName);
    if (typeof v !== "number") {
      fail(`${fileName}: ${path} must be a number`);
      continue;
    }
    if (v !== expected) {
      fail(`${fileName}: ${path} must equal ${expected}, got ${v}`);
    }
  }

  const heartbeat = requireField(
    parsed,
    "spec.activityHeartbeatSeconds",
    fileName,
  );
  const multiplier = requireField(
    parsed,
    "spec.activityHeartbeatMultiplier",
    fileName,
  );
  if (typeof heartbeat === "number" && typeof multiplier === "number") {
    const p95 = requireField(
      parsed,
      "spec.reconciliationP95BudgetSeconds",
      fileName,
    );
    if (typeof p95 === "number" && p95 >= multiplier * heartbeat) {
      fail(
        `${fileName}: reconciliationP95BudgetSeconds (${p95}s) must be < multiplier × heartbeat (${multiplier * heartbeat}s)`,
      );
    }
    const lookback = requireField(
      parsed,
      "spec.reconciliationLookbackHours",
      fileName,
    );
    const cadence = requireField(
      parsed,
      "spec.reconciliationCadenceHours",
      fileName,
    );
    if (
      typeof lookback === "number" &&
      typeof cadence === "number" &&
      lookback < cadence * 2
    ) {
      fail(
        `${fileName}: reconciliationLookbackHours (${lookback}) must be >= 2 × reconciliationCadenceHours (${cadence})`,
      );
    }
    const batch = requireField(
      parsed,
      "spec.reconciliationBatchSize",
      fileName,
    );
    const outboxBatch = requireField(
      parsed,
      "spec.reconciliationOutboxBatchSize",
      fileName,
    );
    if (typeof batch === "number" && typeof outboxBatch === "number") {
      if (outboxBatch > batch) {
        fail(
          `${fileName}: reconciliationOutboxBatchSize (${outboxBatch}) MUST NOT exceed reconciliationBatchSize (${batch})`,
        );
      }
    }
  }

  assertString(
    requireField(parsed, "spec.auditActorKey", fileName),
    "actorPseudoId",
    "spec.auditActorKey",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditCorrelationKey", fileName),
    "correlationId",
    "spec.auditCorrelationKey",
    fileName,
  );

  const auditKeys = requireField(parsed, "spec.auditRequiredKeys", fileName);
  if (!Array.isArray(auditKeys)) {
    fail(`${fileName}: spec.auditRequiredKeys must be an array`);
  } else {
    assertIncludes(
      new Set(auditKeys),
      REQUIRED_AUDIT_KEYS,
      "spec.auditRequiredKeys",
      fileName,
    );
  }

  const auditPairs = [
    ["spec.auditClassOnAlbum", "media"],
    ["spec.auditActionOnAlbumCreated", "media.album.created"],
    ["spec.auditActionOnAlbumRenamed", "media.album.renamed"],
    ["spec.auditActionOnAlbumItemAdded", "media.album.item.added"],
    ["spec.auditActionOnAlbumItemRemoved", "media.album.item.removed"],
    ["spec.auditActionOnAlbumItemReordered", "media.album.item.reordered"],
    ["spec.auditActionOnAlbumTagsSet", "media.album.tags.set"],
    ["spec.auditActionOnAlbumCaptionSet", "media.album.caption.set"],
    ["spec.auditActionOnAlbumReferenceAdded", "media.album.reference.added"],
    ["spec.auditActionOnAlbumReferenceRemoved", "media.album.reference.removed"],
    [
      "spec.auditActionOnAlbumReferenceResolved",
      "media.album.reference.resolved",
    ],
    [
      "spec.auditActionOnAlbumReferenceDangling",
      "media.album.reference.dangling",
    ],
    [
      "spec.auditActionOnAlbumReferenceRevoked",
      "media.album.reference.revoked",
    ],
    [
      "spec.auditActionOnAlbumReconciliationRun",
      "media.album.reconciliation.run",
    ],
    [
      "spec.auditActionOnAlbumReconciliationPurged",
      "media.album.reconciliation.purged",
    ],
    ["spec.auditActionOnAlbumSoftDeleted", "media.album.soft_deleted"],
    ["spec.auditActionOnAlbumPurged", "media.album.purged"],
    [
      "spec.auditActionOnAlbumVisibilityChanged",
      "media.album.visibility.changed",
    ],
    [
      "spec.auditActionOnAlbumDnaBucketRefused",
      "media.album.dna_bucket.refused",
    ],
  ];
  for (const [field, expected] of auditPairs) {
    assertString(requireField(parsed, field, fileName), expected, field, fileName);
  }

  const invariants = requireField(parsed, "spec.invariants", fileName);
  if (!Array.isArray(invariants)) {
    fail(`${fileName}: spec.invariants must be an array`);
  } else {
    assertIncludes(
      new Set(invariants),
      REQUIRED_INVARIANTS,
      "spec.invariants",
      fileName,
    );
  }

  const forbiddenPayload = requireField(
    parsed,
    "spec.forbiddenPayloadPatterns",
    fileName,
  );
  if (!Array.isArray(forbiddenPayload) || forbiddenPayload.length === 0) {
    fail(`${fileName}: spec.forbiddenPayloadPatterns must be a non-empty array`);
  }

  const bcp47 = requireField(
    parsed,
    "spec.bcp47LanguageTagPattern",
    fileName,
  );
  if (typeof bcp47 !== "string" || bcp47.length === 0) {
    fail(`${fileName}: spec.bcp47LanguageTagPattern must be a non-empty string`);
  } else {
    try {
      new RegExp(bcp47);
    } catch (e) {
      fail(`${fileName}: spec.bcp47LanguageTagPattern is not a valid regex: ${e.message}`);
    }
  }

  const outboxEnvelope = requireField(
    parsed,
    "spec.outboxRequiredFields",
    fileName,
  );
  if (!Array.isArray(outboxEnvelope)) {
    fail(`${fileName}: spec.outboxRequiredFields must be an array`);
  }
  const requiredEnvelope = [
    "eventId",
    "eventType",
    "occurredAt",
    "tenantId",
    "aggregateId",
    "aggregateVersion",
    "traceId",
  ];
  assertIncludes(
    new Set(Array.isArray(outboxEnvelope) ? outboxEnvelope : []),
    requiredEnvelope,
    "spec.outboxRequiredFields",
    fileName,
  );

  scanForbiddenLiterals(raw, fileName);
}

function parseSimpleYaml(raw) {
  const root = {};
  const stack = [{ indent: -1, obj: root }];
  const lines = raw.split(/\r?\n/);
  const filtered = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.trim() === "" || line.trimStart().startsWith("#")) continue;
    filtered.push({ idx: i, line });
  }
  for (let i = 0; i < filtered.length; i++) {
    const entry = filtered[i];
    const line = entry.line;
    const indent = line.match(/^ */)[0].length;
    const trimmed = line.slice(indent);
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }
    const frame = stack[stack.length - 1];
    const parent = frame.obj;
    if (trimmed.startsWith("- ")) {
      const value = parseScalar(trimmed.slice(2).trim());
      if (Array.isArray(parent)) {
        parent.push(value);
      }
      continue;
    }
    const m = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$/);
    if (!m) continue;
    const key = m[1];
    const rest = m[2].trim();
    if (rest === "") {
      const next = filtered[i + 1];
      if (next && /^- /.test(next.line.slice(next.line.match(/^ */)[0].length))) {
        const nextIndent = next.line.match(/^ */)[0].length;
        if (nextIndent > indent) {
          const arr = [];
          parent[key] = arr;
          stack.push({ indent: nextIndent - 2, obj: arr });
          continue;
        }
      }
      const child = {};
      parent[key] = child;
      stack.push({ indent, obj: child });
    } else {
      parent[key] = parseScalar(rest);
    }
  }
  return root;
}

function parseScalar(value) {
  if (value === "true") return true;
  if (value === "false") return false;
  if (value === "null" || value === "~") return null;
  if (/^-?\d+$/.test(value)) return parseInt(value, 10);
  if (/^-?\d+\.\d+$/.test(value)) return parseFloat(value);
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  if (value.startsWith("[") && value.endsWith("]")) {
    const inner = value.slice(1, -1).trim();
    if (inner === "") return [];
    return inner
      .split(",")
      .map((s) => parseScalar(s.trim()))
      .filter((v) => v !== undefined);
  }
  return value;
}

function checkChartMirror() {
  let srcRaw, destRaw;
  try {
    srcRaw = readFileSync(CONTRACT, "utf8");
  } catch (err) {
    fail(`cannot read source ${relative(ROOT, CONTRACT)}: ${err.message}`);
    return;
  }
  try {
    destRaw = readFileSync(CHART_FILE, "utf8");
  } catch (err) {
    fail(
      `chart mirror missing at ${relative(ROOT, CHART_FILE)}: ${err.message}`,
    );
    return;
  }
  if (srcRaw !== destRaw) {
    fail(
      `chart mirror ${relative(ROOT, CHART_FILE)} is NOT byte-identical to ${relative(ROOT, CONTRACT)}`,
    );
  }
}

function main() {
  checkContract();
  checkChartMirror();
  if (violations === 0) {
    console.log("[albums-linking] OK");
    process.exit(0);
  } else {
    console.error(
      `[albums-linking] ${violations} violation(s)`,
    );
    process.exit(1);
  }
}

main();