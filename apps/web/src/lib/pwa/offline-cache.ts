/**
 * apps/web/src/lib/pwa/offline-cache.ts
 *
 * E12.1 — Sole cache entry point.
 *
 * The runtime NEVER calls `caches.open()` directly. Every cache
 * read / write / evict / purge goes through one of the helpers
 * exported by this module. The functions are intentionally
 * side-effect free so they can be unit-tested with a fake
 * `CacheStorage` implementation.
 */
import {
  classifyResource,
  type CacheTier,
  type ClassificationContext,
  type ResourceKind,
} from "./storage-classifier";
import {
  checkPermissionVersion,
  projectionCacheKey,
} from "./permission-version";
import { planForTrigger, type PurgeTrigger } from "./purge";

const FORBIDDEN_KEYS: ReadonlyArray<string> = [
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

export interface CacheEntry<T> {
  readonly tenantId: string;
  readonly permissionVersion: string;
  readonly tier: CacheTier;
  readonly kind: ResourceKind;
  readonly entityId: string;
  readonly value: T;
  readonly createdAt: number;
  readonly ttlSeconds: number;
}

export interface CacheReadResult<T> {
  readonly outcome: "HIT" | "STALE" | "DENIED" | "MISS";
  readonly entry?: CacheEntry<T>;
  readonly reason?: string;
}

function firstViolation(payload: Readonly<Record<string, unknown>>): string | undefined {
  for (const key of Object.keys(payload)) {
    if (key.length === 0) {
      return "blank-key";
    }
    if (FORBIDDEN_KEYS.includes(key)) {
      return key;
    }
  }
  return undefined;
}

export function isForbiddenPayloadKey(key: string): boolean {
  return FORBIDDEN_KEYS.includes(key);
}

/**
 * Validate a payload against the forbidden-key list. The
 * runtime MUST call this before `write` to refuse any cache
 * entry whose payload carries a forbidden key.
 */
export function validatePayload(payload: Readonly<Record<string, unknown>>): string | undefined {
  return firstViolation(payload);
}

/**
 * Decide whether a cache tier may be written to in the
 * active context.
 */
export function classify(
  kind: ResourceKind,
  context: ClassificationContext,
) {
  return classifyResource(kind, context);
}

export interface WriteRequest<T> {
  readonly kind: ResourceKind;
  readonly entityId: string;
  readonly tenantId: string;
  readonly permissionVersion: string;
  readonly value: T;
  readonly allowedTiers: ReadonlyArray<CacheTier>;
  readonly optInState: ClassificationContext["optInState"];
  readonly persistent: boolean;
}

export type WriteOutcome =
  | { readonly outcome: "WRITTEN"; readonly entry: CacheEntry<unknown> }
  | { readonly outcome: "DENIED"; readonly reason: string };

/**
 * Authorise a cache write. The helper runs the classifier +
 * payload guard + tenant/version guard. The actual `caches.open`
 * is left to the caller.
 */
export function authoriseWrite<T>(req: WriteRequest<T>, now: number): WriteOutcome {
  const ctx: ClassificationContext = {
    optInState: req.optInState,
    tenantId: req.tenantId,
    permissionVersion: req.permissionVersion,
    persistent: req.persistent,
    allowedTiers: req.allowedTiers,
  };
  const classification = classifyResource(req.kind, ctx);
  if (classification.outcome === "DENIED") {
    return { outcome: "DENIED", reason: classification.reason };
  }
  const payloadKey = firstViolation(req.value as unknown as Record<string, unknown>);
  if (payloadKey) {
    return { outcome: "DENIED", reason: `forbidden-payload-key:${payloadKey}` };
  }
  const entry: CacheEntry<unknown> = {
    tenantId: req.tenantId,
    permissionVersion: req.permissionVersion,
    tier: classification.spec.tier,
    kind: req.kind,
    entityId: req.entityId,
    value: req.value as unknown,
    createdAt: now,
    ttlSeconds: classification.spec.ttlSeconds,
  };
  return { outcome: "WRITTEN", entry };
}

/**
 * Decide whether a cache read may proceed. The helper enforces
 * tenant boundary + permission version + TTL.
 */
export function authoriseRead<T>(
  entry: CacheEntry<T>,
  ctx: { readonly tenantId: string; readonly permissionVersion: string; readonly now: number },
): CacheReadResult<T> {
  if (entry.tenantId !== ctx.tenantId) {
    return { outcome: "DENIED", reason: "TENANT_MISMATCH" };
  }
  const versionCheck = checkPermissionVersion(entry.permissionVersion, ctx.permissionVersion);
  if (versionCheck.outcome === "STALE") {
    return { outcome: "STALE", entry, reason: "PERMISSION_VERSION_STALE" };
  }
  const ageSeconds = Math.max(0, Math.floor((ctx.now - entry.createdAt) / 1000));
  if (ageSeconds > entry.ttlSeconds) {
    return { outcome: "STALE", entry, reason: "TTL_EXPIRED" };
  }
  return { outcome: "HIT", entry };
}

export { projectionCacheKey };

export interface PurgeRequest {
  readonly trigger: PurgeTrigger;
}

export function purge(req: PurgeRequest) {
  return planForTrigger(req.trigger);
}