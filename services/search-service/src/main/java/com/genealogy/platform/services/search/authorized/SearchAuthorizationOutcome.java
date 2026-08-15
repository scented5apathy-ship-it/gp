package com.genealogy.platform.services.search.authorized;

/**
 * Closed-set verdict emitted by the authorized-search pipeline.
 * Mirrors <code>contracts/search/authorized-search-policy.yaml</code>
 * <code>searchAuthorizationOutcomes</code>.
 */
public enum SearchAuthorizationOutcome {
  ALLOWED,
  TENANT_MISMATCH,
  OPENFGA_DENY,
  ABAC_LIVING_FORBIDDEN,
  ABAC_MINOR_FORBIDDEN,
  ABAC_DNA_FORBIDDEN,
  ABAC_CONSENT_REQUIRED,
  ABAC_CONTEXTUAL_DENY,
  PERMISSION_VERSION_STALE,
  REJECTED;

  public String wire() {
    return name();
  }

  public static SearchAuthorizationOutcome fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchAuthorizationOutcome MUST NOT be null");
    }
    try {
      return SearchAuthorizationOutcome.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchAuthorizationOutcome MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}