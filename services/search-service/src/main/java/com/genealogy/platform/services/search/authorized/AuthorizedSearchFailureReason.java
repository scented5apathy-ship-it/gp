package com.genealogy.platform.services.search.authorized;

/**
 * Closed-set failure reasons used by the authorized query pipeline.
 * Mirrors <code>contracts/search/authorized-search-policy.yaml</code>
 * <code>searchFailureReasons</code>.
 */
public enum AuthorizedSearchFailureReason {
  SEARCH_QUERY_TOO_LONG,
  SEARCH_QUERY_EMPTY,
  SEARCH_CURSOR_INVALID,
  SEARCH_CURSOR_DEPTH_EXCEEDED,
  SEARCH_RESOURCE_KIND_UNKNOWN,
  SEARCH_FILTER_MODE_UNKNOWN,
  SEARCH_ORDER_MODE_UNKNOWN,
  SEARCH_TENANT_MISMATCH,
  SEARCH_OPENFGA_DENY,
  SEARCH_ABAC_LIVING_FORBIDDEN,
  SEARCH_ABAC_MINOR_FORBIDDEN,
  SEARCH_ABAC_DNA_FORBIDDEN,
  SEARCH_ABAC_CONSENT_REQUIRED,
  SEARCH_ABAC_CONTEXTUAL_DENY,
  SEARCH_PERMISSION_VERSION_STALE,
  SEARCH_SAVED_SEARCH_NOT_FOUND,
  SEARCH_SAVED_SEARCH_SHARE_FORBIDDEN,
  SEARCH_SAVED_SEARCH_QUERY_NO_PII,
  SEARCH_ALERT_CADENCE_FORBIDDEN,
  SEARCH_ALERT_CHANNEL_FORBIDDEN,
  SEARCH_FACET_AXIS_UNKNOWN,
  SEARCH_PERMISSION_CACHE_MISS,
  SEARCH_DNA_BUCKET_FORBIDDEN,
  SEARCH_PERMISSION_TOKEN_INVALID;

  public String wire() {
    return name();
  }

  public static AuthorizedSearchFailureReason fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("authorizedSearchFailureReason MUST NOT be null");
    }
    try {
      return AuthorizedSearchFailureReason.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "authorizedSearchFailureReason MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}