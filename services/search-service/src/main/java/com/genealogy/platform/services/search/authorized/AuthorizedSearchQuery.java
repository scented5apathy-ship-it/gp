package com.genealogy.platform.services.search.authorized;

import com.genealogy.platform.services.search.shared.SearchLimits;
import java.util.List;

/**
 * Input payload for the authorized-search orchestrator.
 * Mirrors <code>contracts/search/authorized-search-policy.yaml</code>
 * closed-set + numeric invariants.
 */
public record AuthorizedSearchQuery(
    String tenantScopeId,
    String actorPseudoId,
    String correlationId,
    String permissionVersion,
    String queryText,
    String resourceKind,
    List<String> filterValues,
    int pageSize,
    int cursorDepth) {

  public AuthorizedSearchQuery {
    requireNonBlank(tenantScopeId, "tenantScopeId", SearchLimits.TENANT_SCOPE_ID_LENGTH);
    requireNonBlank(actorPseudoId, "actorPseudoId", SearchLimits.ACTOR_PSEUDO_ID_LENGTH);
    requireNonBlank(correlationId, "correlationId", SearchLimits.CORRELATION_ID_LENGTH);
    if (permissionVersion == null || permissionVersion.isBlank()) {
      throw new IllegalArgumentException("permissionVersion MUST NOT be blank");
    }
    if (queryText == null || queryText.isBlank()) {
      throw new IllegalArgumentException("queryText MUST NOT be blank");
    }
    if (queryText.length() > SearchLimits.MAX_QUERY_LENGTH) {
      throw new IllegalArgumentException(
          "queryText length MUST be <= "
              + SearchLimits.MAX_QUERY_LENGTH
              + " (got "
              + queryText.length()
              + ")");
    }
    if (resourceKind == null || resourceKind.isBlank()) {
      throw new IllegalArgumentException("resourceKind MUST NOT be blank");
    }
    if (filterValues == null) {
      throw new IllegalArgumentException("filterValues MUST NOT be null");
    }
    if (filterValues.size() > AuthorizedSearchLimits.MAX_FILTERS_PER_QUERY) {
      throw new IllegalArgumentException(
          "filterValues.size() MUST be <= "
              + AuthorizedSearchLimits.MAX_FILTERS_PER_QUERY
              + " (got "
              + filterValues.size()
              + ")");
    }
    if (pageSize < AuthorizedSearchLimits.MIN_PAGE_SIZE
        || pageSize > AuthorizedSearchLimits.MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "pageSize MUST be in ["
              + AuthorizedSearchLimits.MIN_PAGE_SIZE
              + ", "
              + AuthorizedSearchLimits.MAX_PAGE_SIZE
              + "] (got "
              + pageSize
              + ")");
    }
    if (cursorDepth < 0 || cursorDepth > AuthorizedSearchLimits.MAX_CURSOR_DEPTH) {
      throw new IllegalArgumentException(
          "cursorDepth MUST be in [0, "
              + AuthorizedSearchLimits.MAX_CURSOR_DEPTH
              + "] (got "
              + cursorDepth
              + ")");
    }
  }

  private static void requireNonBlank(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " MUST NOT be blank");
    }
    if (value.length() > maxLength) {
      throw new IllegalArgumentException(
          field
              + " length MUST be <= "
              + maxLength
              + " (got "
              + value.length()
              + ")");
    }
  }
}