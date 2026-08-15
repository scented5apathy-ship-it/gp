package com.genealogy.platform.services.search.projection;

/**
 * Closed-set kind values for the search projection contract
 * (<code>contracts/search/search-projection-policy.yaml</code>,
 * E8.1). Wire values are pinned by the lint script
 * <code>scripts/lint-search-projection.mjs</code>.
 */
public enum SearchDocumentKind {
  PERSON,
  EVENT,
  PLACE,
  SOURCE,
  CITATION,
  MEDIA,
  ALBUM;

  public String wire() {
    return name();
  }

  public static SearchDocumentKind fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchDocumentKind MUST NOT be null");
    }
    try {
      return SearchDocumentKind.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchDocumentKind MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}