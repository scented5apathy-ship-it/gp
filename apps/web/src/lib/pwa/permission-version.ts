/**
 * apps/web/src/lib/pwa/permission-version.ts
 *
 * E12.1 — Permission version gate.
 *
 * Every cache entry MUST carry the permissionVersion header
 * value observed at write-time. The runtime MUST refuse any
 * read whose cached permissionVersion does not match the
 * active value (R6.4). When the version bumps the runtime
 * MUST discard the entry on the next access — bumping the
 * version triggers a full PROJECTION + MEDIA_THUMB purge
 * via `purge.ts`.
 */

const MAX_LENGTH = 128;

export interface PermissionVersionState {
  readonly current: string;
  readonly observedAt: number;
}

export function isValidPermissionVersion(value: string): boolean {
  return value.length > 0 && value.length <= MAX_LENGTH;
}

export function checkPermissionVersion(
  cached: string | undefined,
  active: string,
): { outcome: "OK" } | { outcome: "STALE"; cached?: string } {
  if (!cached) {
    return { outcome: "STALE" };
  }
  if (cached !== active) {
    return { outcome: "STALE", cached };
  }
  return { outcome: "OK" };
}

/**
 * Compute the cache key for a projection read. The key binds
 * tenant + resource kind + permissionVersion + entityId so a
 * version bump automatically produces a new key (the stale key
 * is left to TTL eviction).
 */
export function projectionCacheKey(
  tenantId: string,
  kind: string,
  permissionVersion: string,
  entityId: string,
): string {
  return `projection:${tenantId}:${kind}:${permissionVersion}:${entityId}`;
}