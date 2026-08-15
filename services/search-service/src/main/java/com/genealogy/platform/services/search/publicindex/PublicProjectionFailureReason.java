package com.genealogy.platform.services.search.publicindex;

/**
 * Closed-set failure reasons emitted by the public projection.
 * Mirrors <code>contracts/search/public-projection-policy.yaml</code>
 * <code>publicProjectionFailureReasons</code>.
 */
public enum PublicProjectionFailureReason {
  PUBLIC_PROJECTION_NOT_PUBLIC,
  PUBLIC_PROJECTION_REDACTION_FAILED,
  PUBLIC_PROJECTION_DNA_BUCKET_FORBIDDEN,
  PUBLIC_PROJECTION_LIVING_FORBIDDEN,
  PUBLIC_PROJECTION_MINOR_FORBIDDEN,
  PUBLIC_PROJECTION_CONSENT_FORBIDDEN,
  PUBLIC_PROJECTION_TOKEN_INVALID,
  PUBLIC_PROJECTION_TOKEN_EXPIRED,
  PUBLIC_PROJECTION_TOKEN_RATE_LIMITED,
  PUBLIC_PROJECTION_TOKEN_HASH_MISMATCH,
  PUBLIC_PROJECTION_TOKEN_KIND_UNKNOWN,
  PUBLIC_PROJECTION_VISIBILITY_DOWNGRADE_FORBIDDEN,
  PUBLIC_PROJECTION_PURGE_FAILED,
  PUBLIC_PROJECTION_CACHE_PURGE_FAILED,
  PUBLIC_PROJECTION_SITEMAP_PURGE_FAILED,
  PUBLIC_PROJECTION_LIFECYCLE_FORBIDDEN,
  PUBLIC_PROJECTION_INDEX_STRATEGY_FORBIDDEN,
  PUBLIC_PROJECTION_SITEMAP_STALE,
  PUBLIC_PROJECTION_ROBOTSTXT_FORBIDDEN,
  PUBLIC_PROJECTION_CANONICAL_HOST_FORBIDDEN,
  PUBLIC_PROJECTION_LANGUAGE_HREFLANG_FORBIDDEN;

  public String wire() {
    return name();
  }

  public static PublicProjectionFailureReason fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException(
          "publicProjectionFailureReason MUST NOT be null");
    }
    try {
      return PublicProjectionFailureReason.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "publicProjectionFailureReason MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}