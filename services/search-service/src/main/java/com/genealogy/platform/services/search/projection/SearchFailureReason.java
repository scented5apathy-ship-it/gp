package com.genealogy.platform.services.search.projection;

/**
 * Closed-set failure reasons used by the search projection worker +
 * reconciliation workflow.
 * Mirrors <code>contracts/search/search-projection-policy.yaml</code>
 * <code>searchFailureReasons</code>.
 */
public enum SearchFailureReason {
  PROJECTION_NOT_FOUND,
  PROJECTION_VERSION_MISMATCH,
  PROJECTION_LAG_EXCEEDED,
  PROJECTION_EVENT_TYPE_UNKNOWN,
  PROJECTION_SOURCE_DOMAIN_UNKNOWN,
  PROJECTION_TENANT_MISMATCH,
  PROJECTION_PRIVACY_CLASS_FORBIDDEN,
  PROJECTION_PRIVACY_REDACTION_MISSING,
  PROJECTION_DNA_BUCKET_FORBIDDEN,
  PROJECTION_RECONCILIATION_FAILED,
  PROJECTION_RECONCILIATION_OUTBOX_FAILED,
  PROJECTION_BACKFILL_TIMEOUT,
  PROJECTION_EVENT_PAYLOAD_INVALID,
  PROJECTION_IDEMPOTENCY_KEY_MISSING,
  PROJECTION_OUTBOX_RELAY_LOOP,
  PROJECTION_LANGUAGE_TAG_INVALID,
  PROJECTION_NAME_TOO_LONG,
  PROJECTION_ALIAS_TOO_MANY,
  PROJECTION_ALIAS_TOO_LONG,
  PROJECTION_QUERY_TOO_LONG,
  PROJECTION_SAVED_SEARCH_SHARE_FORBIDDEN,
  PROJECTION_NORMALIZED_TOKEN_INVALID,
  PROJECTION_BACKFILL_QUOTA_EXCEEDED;

  public String wire() {
    return name();
  }

  public static SearchFailureReason fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchFailureReason MUST NOT be null");
    }
    try {
      return SearchFailureReason.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchFailureReason MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}