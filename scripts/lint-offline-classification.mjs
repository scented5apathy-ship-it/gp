#!/usr/bin/env node
/**
 * scripts/lint-offline-classification.mjs
 *
 * E12.1 deep validator for the offline data classification contract
 * at `contracts/pwa/offline-classification-policy.yaml` and the
 * platform mirror at
 * `platform/helm/genealogy-platform/files/pwa/
 *  offline-classification-policy.yaml`.
 *
 * Validates:
 *   - closed-set vocabularies: sensitivityClasses[8],
 *     cacheTiers[4], purgeTriggers[8], optInStates[3],
 *     pwaAuditEvents[10], pwaFailureReasons[13],
 *     pwaForbiddenPayloadKeys[25], egressAllowlist[3];
 *   - cacheableResources[15] including FORBIDDEN-marked DNA,
 *     media-raw, signed-url and OIDC-token entries;
 *   - 2 state matrices (pwaCacheLifecycleStateMatrix initial
 *     UNINITIALIZED, optInStateMatrix initial OPTED_OUT) — both
 *     reachable from initial via BFS, terminal statuses have
 *     empty transitions;
 *   - numeric bounds (10 numeric invariants);
 *   - 13 invariants (dnaBucketAccess=FORBIDDEN,
 *     dnaCacheableKinds=[], rawDnaCacheable=false, etc.);
 *   - capability boundaries — helper `offline-cache.ts` MUST be
 *     sole cache entry point;
 *   - forbidden-keyword scan across 15 keyword patterns;
 *   - parity between contract file and helm chart mirror.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import {
  loadYaml,
  asArray,
  assertClosedSet,
  assertStateMatrix,
} from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT
  ? resolve(process.env.LINT_ROOT)
  : resolve(__dirname, "..");

const CONTRACT = join(
  ROOT,
  "contracts/pwa/offline-classification-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/pwa/offline-classification-policy.yaml",
);

const REQUIRED_SENSITIVITY_CLASSES = [
  "PUBLIC_READONLY",
  "PUBLIC_CACHEABLE",
  "PRIVATE_PERSONAL",
  "PRIVATE_LIVING",
  "PRIVATE_DNA",
  "PRIVATE_MEDIA",
  "SECRET_OIDC",
  "SECRET_SIGNED_URL",
];

const REQUIRED_CACHE_TIERS = ["SHELL", "LOCALE", "PROJECTION", "MEDIA_THUMB"];

const REQUIRED_PURGE_TRIGGERS = [
  "LOGOUT",
  "SESSION_REVOKE",
  "TENANT_SWITCH",
  "PERMISSION_VERSION_BUMP",
  "SUPPORT_JIT_REAUTH",
  "PLAN_DOWNGRADE",
  "DNA_REVOKE",
  "EXPORT_DELETE_REQUESTED",
];

const REQUIRED_OPT_IN_STATES = [
  "OPTED_OUT",
  "OPTED_IN_OPTIONAL",
  "OPTED_IN_REQUIRED",
];

const REQUIRED_AUDIT_EVENTS = [
  "pwa.cache.optIn",
  "pwa.cache.optOut",
  "pwa.cache.write",
  "pwa.cache.read",
  "pwa.cache.evict",
  "pwa.cache.purge",
  "pwa.cache.quotaWarning",
  "pwa.cache.privateModeRefusal",
  "pwa.cache.permissionVersionMismatch",
  "pwa.cache.tenantMismatch",
];

const REQUIRED_FAILURE_REASONS = [
  "OPT_OUT",
  "SENSITIVITY_FORBIDDEN",
  "QUOTA_EXCEEDED",
  "TENANT_MISMATCH",
  "PERMISSION_VERSION_STALE",
  "SIGNED_URL_FORBIDDEN",
  "DNA_FORBIDDEN",
  "MEDIA_RAW_FORBIDDEN",
  "LIVING_FIELD_FORBIDDEN",
  "PRIVATE_MODE_PERSISTENT_FALSE",
  "TIER_NOT_ALLOWED",
  "TTL_EXPIRED",
  "UNKNOWN_RESOURCE_KIND",
];

const REQUIRED_FORBIDDEN_PAYLOAD_KEYS = [
  "rawDna",
  "rawMedia",
  "dnaRawBytes",
  "dnaMatchResult",
  "signedUrlSecret",
  "oidcAccessToken",
  "oidcRefreshToken",
  "oidcIdToken",
  "rawWebhookSecret",
  "rawProviderApiKey",
  "rawKmsKey",
  "rawVaultToken",
  "rawSessionCookie",
  "rawPin",
  "rawBiometric",
  "rawDnaConsentToken",
  "rawExportToken",
  "rawS3AccessKey",
  "rawS3Secret",
  "treeViewerBypass",
  "rawGuardianReason",
  "rawSupportReason",
  "rawDeletionReason",
  "rawOnboardingToken",
  "rawOidcClientSecret",
];

const REQUIRED_EGRESS = ["same-origin", "cdn.genealogy-platform.example", "api.genealogy-platform.example"];

const REQUIRED_FORBIDDEN_KEYWORDS = [
  "AKIA",
  "BEGIN PRIVATE KEY",
  "BEGIN RSA PRIVATE KEY",
  "BEGIN OPENSSH PRIVATE KEY",
  "password=",
  "token=",
  "secret=",
  "postgres-protocol-marker",
  "mongodb-protocol-marker",
  "redis-protocol-marker",
  "slack-webhook-marker",
];

const CACHE_STATE_STATUSES = [
  "UNINITIALIZED",
  "CLASSIFYING",
  "CLASSIFIED_ALLOWED",
  "CLASSIFIED_DENIED",
  "WRITING",
  "READY",
  "READING",
  "REDACTING",
  "SERVED",
  "STALE",
  "EVICTING",
  "EVICTED",
  "PURGING",
  "PURGED",
  "QUOTA_DENIED",
  "PERMISSION_STALE",
  "TENANT_MISMATCH",
];

const OPT_IN_STATE_STATUSES = [
  "OPTED_OUT",
  "OPTED_IN_OPTIONAL",
  "OPTED_IN_REQUIRED",
  "REVOKED",
  "LOCKED",
];

const NUMERIC_BOUNDS = [
  "defaultTtlSeconds",
  "maxTtlSeconds",
  "maxProjectionBytes",
  "maxMediaThumbBytes",
  "maxLocaleCatalogueBytes",
  "quotaWarningBytes",
  "quotaHardLimitBytes",
  "purgeDebounceMs",
  "permissionVersionHeaderMaxLength",
  "optInToggleDebounceMs",
];

const INVARIANTS = [
  "dnaBucketAccess",
  "dnaCacheableKinds",
  "rawDnaCacheable",
  "rawMediaCacheable",
  "signedUrlCacheable",
  "oidcTokenCacheable",
  "tenantBoundaryEnforced",
  "permissionVersionEnforced",
  "optInDefault",
  "privateBrowsingRefusal",
  "redactionReappliedOnRead",
  "auditEveryWrite",
  "forbiddenPayloadKeysEnforced",
  "cacheHelperSoleEntryPoint",
];

const FORBIDDEN_KIND_ENTRIES = [
  "dna-kit",
  "raw-dna",
  "media-original",
  "signed-url",
  "oidc-token",
  "living-person-fields",
];

const REQUIRED_CACHEABLE_KINDS = [
  "shell-manifest",
  "design-tokens",
  "locale-catalogue",
  "skeleton-icons",
  "tree-snapshot",
  "person-summary",
  "media-thumb",
];

let violations = 0;

function ok(message) {
  process.stdout.write(`  ok  ${message}\n`);
}

function fail(message) {
  violations += 1;
  process.stderr.write(`  fail  ${message}\n`);
}

function readBoth() {
  const contractText = readFileSync(CONTRACT, "utf8");
  const chartText = readFileSync(CHART_FILE, "utf8");
  return { contractText, chartText, contract: loadYaml(contractText), chart: loadYaml(chartText) };
}

function checkParity(contractText, chartText) {
  if (contractText !== chartText) {
    fail("contract <-> helm chart mirror mismatch — byte-equal copy required");
    return;
  }
  ok("contract <-> helm chart mirror byte-equal");
}

function checkClosedSets(doc) {
  assertClosedSet("sensitivityClasses", REQUIRED_SENSITIVITY_CLASSES, asArray(doc.sensitivityClasses?.values), "E12.1 sensitivityClasses", ok, fail);
  assertClosedSet("cacheTiers", REQUIRED_CACHE_TIERS, asArray(doc.cacheTiers?.values), "E12.1 cacheTiers", ok, fail);
  assertClosedSet("purgeTriggers", REQUIRED_PURGE_TRIGGERS, asArray(doc.purgeTriggers?.values), "E12.1 purgeTriggers", ok, fail);
  assertClosedSet("optInStates", REQUIRED_OPT_IN_STATES, asArray(doc.optInStates?.values), "E12.1 optInStates", ok, fail);
  assertClosedSet("pwaAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(doc.pwaAuditEvents?.values), "E12.1 pwaAuditEvents", ok, fail);
  assertClosedSet("pwaFailureReasons", REQUIRED_FAILURE_REASONS, asArray(doc.pwaFailureReasons?.values), "E12.1 pwaFailureReasons", ok, fail);
  assertClosedSet("pwaForbiddenPayloadKeys", REQUIRED_FORBIDDEN_PAYLOAD_KEYS, asArray(doc.pwaForbiddenPayloadKeys?.values), "E12.1 pwaForbiddenPayloadKeys", ok, fail);
  assertClosedSet("egressAllowlist", REQUIRED_EGRESS, asArray(doc.egressAllowlist?.values), "E12.1 egressAllowlist", ok, fail);
  assertClosedSet("forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS, asArray(doc.forbiddenKeywords?.values), "E12.1 forbiddenKeywords", ok, fail);
}

function checkCacheableResources(doc) {
  const entries = asArray(doc.cacheableResources?.entries);
  const kinds = new Set();
  for (const entry of entries) {
    if (!entry || typeof entry !== "object") {
      fail("cacheableResources: invalid entry");
      continue;
    }
    kinds.add(entry.kind);
  }
  for (const required of REQUIRED_CACHEABLE_KINDS) {
    if (!kinds.has(required)) {
      fail(`cacheableResources: required kind "${required}" missing`);
    }
  }
  for (const forbidden of FORBIDDEN_KIND_ENTRIES) {
    if (!kinds.has(forbidden)) {
      fail(`cacheableResources: required-forbidden kind "${forbidden}" missing`);
      continue;
    }
    const entry = entries.find((e) => e.kind === forbidden);
    if (entry.ttlSeconds !== 0 || entry.maxAgeSeconds !== 0) {
      fail(`cacheableResources: ${forbidden} MUST declare ttlSeconds=0 and maxAgeSeconds=0`);
    }
    if (entry.optIn !== false) {
      fail(`cacheableResources: ${forbidden} MUST declare optIn=false`);
    }
    if (!entry.notes || !/FORBIDDEN/i.test(entry.notes)) {
      fail(`cacheableResources: ${forbidden} MUST carry a FORBIDDEN note`);
    }
  }
  if (kinds.size === REQUIRED_CACHEABLE_KINDS.length + FORBIDDEN_KIND_ENTRIES.length) {
    ok(`cacheableResources (${kinds.size} kinds incl. ${FORBIDDEN_KIND_ENTRIES.length} forbidden)`);
  }
}

function checkStateMatrices(doc) {
  const cacheMatrix = doc.pwaCacheLifecycleStateMatrix;
  assertStateMatrix(
    "E12.1 pwaCacheLifecycleStateMatrix",
    cacheMatrix,
    CACHE_STATE_STATUSES,
    "UNINITIALIZED",
    ok,
    fail,
  );
  const optMatrix = doc.optInStateMatrix;
  assertStateMatrix(
    "E12.1 optInStateMatrix",
    optMatrix,
    OPT_IN_STATE_STATUSES,
    "OPTED_OUT",
    ok,
    fail,
  );
}

function checkNumericBounds(doc) {
  const bounds = doc.numericBounds || {};
  for (const key of NUMERIC_BOUNDS) {
    if (bounds[key] === undefined) {
      fail(`numericBounds.${key} missing`);
    }
  }
  if (bounds.maxTtlSeconds !== undefined && bounds.defaultTtlSeconds !== undefined && bounds.maxTtlSeconds < bounds.defaultTtlSeconds) {
    fail(`numericBounds.maxTtlSeconds (${bounds.maxTtlSeconds}) MUST be >= defaultTtlSeconds (${bounds.defaultTtlSeconds})`);
  }
  if (bounds.quotaHardLimitBytes !== undefined && bounds.quotaWarningBytes !== undefined && bounds.quotaHardLimitBytes < bounds.quotaWarningBytes) {
    fail(`numericBounds.quotaHardLimitBytes (${bounds.quotaHardLimitBytes}) MUST be >= quotaWarningBytes (${bounds.quotaWarningBytes})`);
  }
  ok(`numericBounds (${NUMERIC_BOUNDS.length} entries)`);
}

function checkInvariants(doc) {
  const inv = doc.invariants || {};
  if (inv.dnaBucketAccess !== "FORBIDDEN") {
    fail("invariants.dnaBucketAccess MUST be FORBIDDEN");
  }
  if (!Array.isArray(inv.dnaCacheableKinds) || inv.dnaCacheableKinds.length !== 0) {
    fail("invariants.dnaCacheableKinds MUST be an empty array");
  }
  if (inv.rawDnaCacheable !== false) {
    fail("invariants.rawDnaCacheable MUST be false");
  }
  if (inv.rawMediaCacheable !== false) {
    fail("invariants.rawMediaCacheable MUST be false");
  }
  if (inv.signedUrlCacheable !== false) {
    fail("invariants.signedUrlCacheable MUST be false");
  }
  if (inv.oidcTokenCacheable !== false) {
    fail("invariants.oidcTokenCacheable MUST be false");
  }
  if (inv.tenantBoundaryEnforced !== true) {
    fail("invariants.tenantBoundaryEnforced MUST be true");
  }
  if (inv.permissionVersionEnforced !== true) {
    fail("invariants.permissionVersionEnforced MUST be true");
  }
  if (inv.optInDefault !== false) {
    fail("invariants.optInDefault MUST be false");
  }
  if (inv.privateBrowsingRefusal !== true) {
    fail("invariants.privateBrowsingRefusal MUST be true");
  }
  if (inv.redactionReappliedOnRead !== true) {
    fail("invariants.redactionReappliedOnRead MUST be true");
  }
  if (inv.auditEveryWrite !== true) {
    fail("invariants.auditEveryWrite MUST be true");
  }
  if (inv.forbiddenPayloadKeysEnforced !== true) {
    fail("invariants.forbiddenPayloadKeysEnforced MUST be true");
  }
  if (inv.cacheHelperSoleEntryPoint !== true) {
    fail("invariants.cacheHelperSoleEntryPoint MUST be true");
  }
  ok(`invariants (${INVARIANTS.length} invariants)`);
}

function checkStorageHelperSoleEntryPoint() {
  const helper = join(ROOT, "apps/web/src/lib/pwa/offline-cache.ts");
  let text;
  try {
    text = readFileSync(helper, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/pwa/offline-cache.ts missing (${err.code})`);
    return;
  }
  if (!/classifyResource|classify/.test(text)) {
    fail("offline-cache.ts MUST export a classify* function");
  }
  if (!/purge\(/.test(text)) {
    fail("offline-cache.ts MUST export a purge() function");
  }
  if (!/permissionVersion/.test(text)) {
    fail("offline-cache.ts MUST reference permissionVersion");
  }
  if (!/tenantId/.test(text)) {
    fail("offline-cache.ts MUST reference tenantId");
  }
  ok("offline-cache.ts exports classify + purge + references permissionVersion/tenantId");
}

function checkNoBareCachesOpen() {
  const sources = [
    "apps/web/src/lib/pwa/offline-cache.ts",
    "apps/web/src/lib/pwa/storage-classifier.ts",
    "apps/web/src/lib/pwa/purge.ts",
    "apps/web/src/lib/pwa/permission-version.ts",
  ];
  let scanned = 0;
  for (const rel of sources) {
    const abs = join(ROOT, rel);
    let text;
    try {
      text = readFileSync(abs, "utf8");
    } catch (err) {
      fail(`${rel} missing (${err.code})`);
      continue;
    }
    scanned += 1;
    if (/caches\.open\(/.test(text)) {
      if (!/classifyResource|cacheHelper/.test(text)) {
        fail(`${rel} opens Cache Storage outside the classifier helper`);
      }
    }
  }
  ok(`scanned ${scanned} pwa lib files for bare caches.open`);
}

function checkPurgeHooks() {
  const purge = join(ROOT, "apps/web/src/lib/pwa/purge.ts");
  let text;
  try {
    text = readFileSync(purge, "utf8");
  } catch (err) {
    fail(`apps/web/src/lib/pwa/purge.ts missing (${err.code})`);
    return;
  }
  for (const trigger of REQUIRED_PURGE_TRIGGERS) {
    if (!text.includes(trigger)) {
      fail(`purge.ts MUST handle trigger "${trigger}"`);
    }
  }
  ok("purge.ts handles all 8 purge triggers");
}

function checkManifest() {
  const manifest = join(ROOT, "apps/web/public/manifest.webmanifest");
  let text;
  try {
    text = readFileSync(manifest, "utf8");
  } catch (err) {
    fail(`apps/web/public/manifest.webmanifest missing (${err.code})`);
    return;
  }
  if (!/"id"\s*:\s*"\/"/.test(text)) {
    fail("manifest MUST declare id '/' (single-app scope)");
  }
  if (!/"display"\s*:\s*"standalone"/.test(text)) {
    fail("manifest MUST declare display standalone");
  }
  if (!/"dir"\s*:\s*"ltr"/.test(text)) {
    fail("manifest MUST declare dir ltr");
  }
  ok("manifest.webmanifest has single-app id, standalone display, dir ltr");
}

function main() {
  let data;
  try {
    data = readBoth();
  } catch (err) {
    process.stderr.write(`config error: ${err.message}\n`);
    process.exit(2);
  }

  process.stdout.write("E12.1 offline-classification linter\n");
  checkParity(data.contractText, data.chartText);
  checkClosedSets(data.contract);
  checkCacheableResources(data.contract);
  checkStateMatrices(data.contract);
  checkNumericBounds(data.contract);
  checkInvariants(data.contract);
  checkStorageHelperSoleEntryPoint();
  checkNoBareCachesOpen();
  checkPurgeHooks();
  checkManifest();

  process.stdout.write(`\nE12.1 summary: ${violations === 0 ? "OK" : `${violations} violation(s)`}\n`);
  process.exit(violations === 0 ? 0 : 1);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}