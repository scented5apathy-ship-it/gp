/**
 * apps/web/src/lib/pwa/storage-classifier.ts
 *
 * E12.1 — Offline data classification decision table.
 *
 * The runtime consults `classifyResource` BEFORE it writes
 * anything to a Cache Storage bucket. The classifier is the
 * only path authorised to authorise or refuse a write — no
 * component may call `caches.open()` directly (enforced by
 * `scripts/lint-offline-classification.mjs`).
 *
 * Decision matrix (mirrors `cacheableResources` in
 * `contracts/pwa/offline-classification-policy.yaml`):
 *
 *   ┌──────────────────────┬───────────────────┬──────────────┐
 *   │ resource kind        │ sensitivity       │ cacheable?   │
 *   ├──────────────────────┼───────────────────┼──────────────┤
 *   │ shell-manifest       │ PUBLIC_READONLY   │ yes (no opt) │
 *   │ design-tokens        │ PUBLIC_READONLY   │ yes (no opt) │
 *   │ locale-catalogue     │ PUBLIC_READONLY   │ yes (no opt) │
 *   │ skeleton-icons       │ PUBLIC_READONLY   │ yes (no opt) │
 *   │ tree-snapshot        │ PRIVATE_PERSONAL  │ opt-in       │
 *   │ person-summary       │ PRIVATE_LIVING    │ opt-in       │
 *   │ living-person-fields │ PRIVATE_LIVING    │ FORBIDDEN    │
 *   │ dna-kit              │ PRIVATE_DNA       │ FORBIDDEN    │
 *   │ raw-dna              │ PRIVATE_DNA       │ FORBIDDEN    │
 *   │ media-original       │ PRIVATE_MEDIA     │ FORBIDDEN    │
 *   │ media-thumb          │ PRIVATE_MEDIA     │ opt-in       │
 *   │ signed-url           │ SECRET_SIGNED_URL │ FORBIDDEN    │
 *   │ oidc-token           │ SECRET_OIDC       │ FORBIDDEN    │
 *   └──────────────────────┴───────────────────┴──────────────┘
 *
 * Forbidden-resource kinds (DNA / raw media / signed URLs /
 * OIDC tokens / sensitive living fields) MUST always be
 * refused regardless of opt-in.
 */

export type SensitivityClass =
  | "PUBLIC_READONLY"
  | "PUBLIC_CACHEABLE"
  | "PRIVATE_PERSONAL"
  | "PRIVATE_LIVING"
  | "PRIVATE_DNA"
  | "PRIVATE_MEDIA"
  | "SECRET_OIDC"
  | "SECRET_SIGNED_URL";

export type CacheTier = "SHELL" | "LOCALE" | "PROJECTION" | "MEDIA_THUMB";

export type ResourceKind =
  | "shell-manifest"
  | "design-tokens"
  | "locale-catalogue"
  | "skeleton-icons"
  | "tree-snapshot"
  | "person-summary"
  | "living-person-fields"
  | "dna-kit"
  | "raw-dna"
  | "media-original"
  | "media-thumb"
  | "signed-url"
  | "oidc-token";

export type PwaFailureReason =
  | "OPT_OUT"
  | "SENSITIVITY_FORBIDDEN"
  | "QUOTA_EXCEEDED"
  | "TENANT_MISMATCH"
  | "PERMISSION_VERSION_STALE"
  | "SIGNED_URL_FORBIDDEN"
  | "DNA_FORBIDDEN"
  | "MEDIA_RAW_FORBIDDEN"
  | "LIVING_FIELD_FORBIDDEN"
  | "PRIVATE_MODE_PERSISTENT_FALSE"
  | "TIER_NOT_ALLOWED"
  | "TTL_EXPIRED"
  | "UNKNOWN_RESOURCE_KIND";

export interface CacheableResourceSpec {
  readonly kind: ResourceKind;
  readonly sensitivity: SensitivityClass;
  readonly ttlSeconds: number;
  readonly maxAgeSeconds: number;
  readonly optIn: boolean;
  readonly redactionOnRead: boolean;
  readonly tenantScoped: boolean;
  readonly tier: CacheTier;
  readonly notes?: string;
}

export interface ClassificationContext {
  readonly optInState: "OPTED_OUT" | "OPTED_IN_OPTIONAL" | "OPTED_IN_REQUIRED";
  readonly tenantId: string;
  readonly permissionVersion: string;
  readonly persistent: boolean;
  readonly allowedTiers: ReadonlyArray<CacheTier>;
}

export type ClassificationOutcome =
  | { readonly outcome: "ALLOWED"; readonly spec: CacheableResourceSpec }
  | { readonly outcome: "DENIED"; readonly reason: PwaFailureReason };

const TABLE: Readonly<Record<ResourceKind, CacheableResourceSpec>> = {
  "shell-manifest": {
    kind: "shell-manifest",
    sensitivity: "PUBLIC_READONLY",
    ttlSeconds: 604800,
    maxAgeSeconds: 2592000,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: false,
    tier: "SHELL",
  },
  "design-tokens": {
    kind: "design-tokens",
    sensitivity: "PUBLIC_READONLY",
    ttlSeconds: 604800,
    maxAgeSeconds: 2592000,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: false,
    tier: "SHELL",
  },
  "locale-catalogue": {
    kind: "locale-catalogue",
    sensitivity: "PUBLIC_READONLY",
    ttlSeconds: 86400,
    maxAgeSeconds: 2592000,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: false,
    tier: "LOCALE",
  },
  "skeleton-icons": {
    kind: "skeleton-icons",
    sensitivity: "PUBLIC_READONLY",
    ttlSeconds: 604800,
    maxAgeSeconds: 2592000,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: false,
    tier: "SHELL",
  },
  "tree-snapshot": {
    kind: "tree-snapshot",
    sensitivity: "PRIVATE_PERSONAL",
    ttlSeconds: 900,
    maxAgeSeconds: 3600,
    optIn: true,
    redactionOnRead: true,
    tenantScoped: true,
    tier: "PROJECTION",
  },
  "person-summary": {
    kind: "person-summary",
    sensitivity: "PRIVATE_LIVING",
    ttlSeconds: 900,
    maxAgeSeconds: 3600,
    optIn: true,
    redactionOnRead: true,
    tenantScoped: true,
    tier: "PROJECTION",
  },
  "living-person-fields": {
    kind: "living-person-fields",
    sensitivity: "PRIVATE_LIVING",
    ttlSeconds: 0,
    maxAgeSeconds: 0,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: true,
    tier: "PROJECTION",
    notes: "FORBIDDEN — sensitive living fields NEVER cached.",
  },
  "dna-kit": {
    kind: "dna-kit",
    sensitivity: "PRIVATE_DNA",
    ttlSeconds: 0,
    maxAgeSeconds: 0,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: true,
    tier: "PROJECTION",
    notes: "FORBIDDEN — DNA never cached.",
  },
  "raw-dna": {
    kind: "raw-dna",
    sensitivity: "PRIVATE_DNA",
    ttlSeconds: 0,
    maxAgeSeconds: 0,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: true,
    tier: "PROJECTION",
    notes: "FORBIDDEN — raw DNA bytes never cached.",
  },
  "media-original": {
    kind: "media-original",
    sensitivity: "PRIVATE_MEDIA",
    ttlSeconds: 0,
    maxAgeSeconds: 0,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: true,
    tier: "MEDIA_THUMB",
    notes: "FORBIDDEN — original media bytes never cached.",
  },
  "media-thumb": {
    kind: "media-thumb",
    sensitivity: "PRIVATE_MEDIA",
    ttlSeconds: 3600,
    maxAgeSeconds: 86400,
    optIn: true,
    redactionOnRead: false,
    tenantScoped: true,
    tier: "MEDIA_THUMB",
  },
  "signed-url": {
    kind: "signed-url",
    sensitivity: "SECRET_SIGNED_URL",
    ttlSeconds: 0,
    maxAgeSeconds: 0,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: false,
    tier: "MEDIA_THUMB",
    notes: "FORBIDDEN — signed URLs never cached.",
  },
  "oidc-token": {
    kind: "oidc-token",
    sensitivity: "SECRET_OIDC",
    ttlSeconds: 0,
    maxAgeSeconds: 0,
    optIn: false,
    redactionOnRead: false,
    tenantScoped: false,
    tier: "SHELL",
    notes: "FORBIDDEN — tokens never cached.",
  },
};

export const FORBIDDEN_KINDS: ReadonlyArray<ResourceKind> = [
  "living-person-fields",
  "dna-kit",
  "raw-dna",
  "media-original",
  "signed-url",
  "oidc-token",
];

export function isForbiddenKind(kind: ResourceKind): boolean {
  return FORBIDDEN_KINDS.includes(kind);
}

/**
 * Classify a resource kind against the active context. The
 * runtime MUST call this helper before opening a Cache Storage
 * bucket.
 */
export function classifyResource(
  kind: ResourceKind,
  context: ClassificationContext,
): ClassificationOutcome {
  const spec = TABLE[kind];
  if (!spec) {
    return { outcome: "DENIED", reason: "UNKNOWN_RESOURCE_KIND" };
  }
  if (!context.tenantId) {
    return { outcome: "DENIED", reason: "TENANT_MISMATCH" };
  }
  if (!context.persistent) {
    return { outcome: "DENIED", reason: "PRIVATE_MODE_PERSISTENT_FALSE" };
  }
  if (!context.allowedTiers.includes(spec.tier)) {
    return { outcome: "DENIED", reason: "TIER_NOT_ALLOWED" };
  }
  if (spec.ttlSeconds === 0) {
    if (kind === "dna-kit" || kind === "raw-dna") {
      return { outcome: "DENIED", reason: "DNA_FORBIDDEN" };
    }
    if (kind === "media-original") {
      return { outcome: "DENIED", reason: "MEDIA_RAW_FORBIDDEN" };
    }
    if (kind === "living-person-fields") {
      return { outcome: "DENIED", reason: "LIVING_FIELD_FORBIDDEN" };
    }
    if (kind === "signed-url") {
      return { outcome: "DENIED", reason: "SIGNED_URL_FORBIDDEN" };
    }
    return { outcome: "DENIED", reason: "SENSITIVITY_FORBIDDEN" };
  }
  if (spec.optIn && context.optInState === "OPTED_OUT") {
    return { outcome: "DENIED", reason: "OPT_OUT" };
  }
  return { outcome: "ALLOWED", spec };
}