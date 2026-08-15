package com.genealogy.platform.services.search.projection;

/**
 * Closed-set audit hooks emitted by the search projection worker.
 * Mirrors <code>contracts/search/search-projection-policy.yaml</code>
 * <code>searchAuditEvents</code>.
 */
public enum SearchAuditEvent {
  SEARCH_PROJECTION_RECEIVED,
  SEARCH_PROJECTION_INDEXED,
  SEARCH_PROJECTION_REDACTED,
  SEARCH_PROJECTION_PURGED,
  SEARCH_PROJECTION_REHYDRATED,
  SEARCH_PROJECTION_RECONCILIATION_QUEUED,
  SEARCH_PROJECTION_RECONCILIATION_RUN,
  SEARCH_PROJECTION_RECONCILIATION_DRAINED,
  SEARCH_PROJECTION_RECONCILIATION_PURGED,
  SEARCH_PROJECTION_LAG_THRESHOLD_BREACHED,
  SEARCH_PROJECTION_LAG_RECOVERED,
  SEARCH_PROJECTION_BACKFILL_STARTED,
  SEARCH_PROJECTION_BACKFILL_FINISHED,
  SEARCH_PROJECTION_DNA_BUCKET_REFUSED,
  SEARCH_PROJECTION_PRIVACY_REDACTED,
  SEARCH_PROJECTION_REINDEX_TRIGGERED,
  SEARCH_PROJECTION_EVENT_DUPLICATE_DROPPED,
  SEARCH_PROJECTION_FACET_CACHE_REBUILT;

  public String wire() {
    return name();
  }

  public static SearchAuditEvent fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("searchAuditEvent MUST NOT be null");
    }
    try {
      return SearchAuditEvent.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "searchAuditEvent MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}