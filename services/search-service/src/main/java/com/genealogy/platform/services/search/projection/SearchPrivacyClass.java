package com.genealogy.platform.services.search.projection;

/**
 * Closed-set privacy classes the search projection stores per row.
 * Mirrors <code>contracts/search/search-projection-policy.yaml</code>
 * <code>searchPrivacyClasses</code>.
 */
public enum SearchPrivacyClass {
  PRIVATE,
  TREE_DEFAULT,
  UNLISTED,
  PUBLIC,
  REDACTED;

  public String wire() {
    return name();
  }

  public static SearchPrivacyClass fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchPrivacyClass MUST NOT be null");
    }
    try {
      return SearchPrivacyClass.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchPrivacyClass MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}