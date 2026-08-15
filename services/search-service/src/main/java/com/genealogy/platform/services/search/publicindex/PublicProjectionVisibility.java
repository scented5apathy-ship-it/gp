package com.genealogy.platform.services.search.publicindex;

/**
 * Closed-set visibility scopes the public projection accepts. Only
 * PUBLIC rows ever enter the index; UNLISTED rows are gated by the
 * token-verification path. Mirrors
 * <code>contracts/search/public-projection-policy.yaml</code>
 * <code>publicProjectionVisibilityScopes</code>.
 */
public enum PublicProjectionVisibility {
  PUBLIC,
  UNLISTED;

  public String wire() {
    return name();
  }

  public static PublicProjectionVisibility fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("publicProjectionVisibility MUST NOT be null");
    }
    try {
      return PublicProjectionVisibility.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "publicProjectionVisibility MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}