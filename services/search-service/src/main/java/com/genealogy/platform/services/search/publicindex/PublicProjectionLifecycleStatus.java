package com.genealogy.platform.services.search.publicindex;

/**
 * Lifecycle status for each row in the public projection.
 * Mirrors <code>contracts/search/public-projection-policy.yaml</code>
 * <code>publicProjectionLifecycleStatuses</code>.
 */
public enum PublicProjectionLifecycleStatus {
  PENDING,
  REDACTED,
  INDEXED,
  STALE,
  PURGED;

  public String wire() {
    return name();
  }

  public static PublicProjectionLifecycleStatus fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException(
          "publicProjectionLifecycleStatus MUST NOT be null");
    }
    try {
      return PublicProjectionLifecycleStatus.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "publicProjectionLifecycleStatus MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}