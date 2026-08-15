package com.genealogy.platform.services.search.projection;

/**
 * Closed-set projection lifecycle status per row.
 * Mirrors <code>contracts/search/search-projection-policy.yaml</code>
 * <code>searchProjectionStatuses</code>.
 */
public enum SearchProjectionStatus {
  PENDING,
  INDEXED,
  STALE,
  REDACTED,
  PURGED;

  public String wire() {
    return name();
  }

  public static SearchProjectionStatus fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchProjectionStatus MUST NOT be null");
    }
    try {
      return SearchProjectionStatus.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchProjectionStatus MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}