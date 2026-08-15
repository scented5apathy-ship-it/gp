package com.genealogy.platform.services.search.projection;

/**
 * Closed-set event types accepted by the search projection worker.
 * Mirrors <code>contracts/search/search-projection-policy.yaml</code>
 * <code>searchEventTypes</code>.
 */
public enum SearchEventType {
  PERSON_CREATED,
  PERSON_UPDATED,
  PERSON_DELETED,
  PERSON_LIVING_STATUS_CHANGED,
  PERSON_PRIVACY_CHANGED,
  EVENT_CREATED,
  EVENT_UPDATED,
  EVENT_DELETED,
  PLACE_CREATED,
  PLACE_UPDATED,
  PLACE_DELETED,
  SOURCE_CREATED,
  CITATION_CREATED,
  MEDIA_ASSET_INDEXED,
  MEDIA_ASSET_REDACTED,
  MEDIA_ASSET_PURGED,
  ALBUM_CREATED,
  ALBUM_RENAMED,
  ALBUM_VISIBILITY_CHANGED,
  ALBUM_RECONCILIATION_PURGED;

  public String wire() {
    return name();
  }

  public static SearchEventType fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchEventType MUST NOT be null");
    }
    try {
      return SearchEventType.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchEventType MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}