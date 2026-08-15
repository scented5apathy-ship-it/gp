package com.genealogy.platform.services.search.authorized;

/**
 * Centralised numeric constants mirror
 * <code>contracts/search/authorized-search-policy.yaml</code> (E8.2).
 */
public final class AuthorizedSearchLimits {
  private AuthorizedSearchLimits() {}

  public static final int MAX_PAGE_SIZE = 100;
  public static final int MIN_PAGE_SIZE = 1;
  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_CURSOR_DEPTH = 1024;
  public static final int MAX_FACET_COUNT_PER_AXIS = 256;
  public static final int MAX_FILTERS_PER_QUERY = 32;
  public static final int MAX_SAVED_SEARCH_ALERTS_PER_USER = 64;
  public static final int MAX_SAVED_SEARCH_ALERT_SUBSCRIBERS = 256;
  public static final int PERMISSION_CACHE_MAX_AGE_SECONDS = 30;
  public static final int PERMISSION_CACHE_MAX_VERSION_WINDOW = 5;
  public static final int PERMISSION_CACHE_TOMBSTONE_SECONDS = 90;
  public static final int PERMISSION_CACHE_P95_BUDGET_MILLISECONDS = 5;
  public static final int PERMISSION_CACHE_STALE_BUDGET_SECONDS = 15;
  public static final int SEARCH_P95_BUDGET_MILLISECONDS = 1000;
  public static final int SEARCH_P99_BUDGET_MILLISECONDS = 2500;
}