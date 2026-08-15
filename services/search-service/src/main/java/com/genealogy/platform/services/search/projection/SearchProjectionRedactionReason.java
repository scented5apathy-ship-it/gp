package com.genealogy.platform.services.search.projection;

/**
 * Closed-set redaction reasons surfaced when the projection
 * downgrades visibility before indexing.
 * Mirrors <code>contracts/search/search-projection-policy.yaml</code>
 * <code>searchAuditEvents</code> (subset) — these are the public
 * redaction reasons carried in the projection decision.
 */
public enum SearchProjectionRedactionReason {
  LIVING,
  MINOR,
  DNA_ATTACHED,
  CONSENT_MISSING,
  CONSENT_REVOKED,
  TENANT_REDACTION_FORBIDDEN,
  POLICY_DENY;

  public String wire() {
    return name();
  }

  public static SearchProjectionRedactionReason fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException(
          "searchProjectionRedactionReason MUST NOT be null");
    }
    try {
      return SearchProjectionRedactionReason.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchProjectionRedactionReason MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}