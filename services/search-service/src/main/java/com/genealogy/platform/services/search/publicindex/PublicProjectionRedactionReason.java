package com.genealogy.platform.services.search.publicindex;

/**
 * Closed-set reasons the redactor may cite when stripping a row
 * before it lands in the public index. Mirrors
 * <code>contracts/search/public-projection-policy.yaml</code>
 * <code>publicProjectionRedactionReasons</code>.
 */
public enum PublicProjectionRedactionReason {
  LIVING,
  MINOR,
  DNA_ATTACHED,
  CONSENT_MISSING,
  CONSENT_REVOKED,
  VISIBILITY_NOT_PUBLIC,
  TENANT_REDACTION_FORBIDDEN,
  POLICY_DENY,
  SOFT_DELETED,
  LEGAL_HOLD;

  public String wire() {
    return name();
  }

  public static PublicProjectionRedactionReason fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException(
          "publicProjectionRedactionReason MUST NOT be null");
    }
    try {
      return PublicProjectionRedactionReason.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "publicProjectionRedactionReason MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}