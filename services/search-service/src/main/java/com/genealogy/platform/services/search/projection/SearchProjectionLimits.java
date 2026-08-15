package com.genealogy.platform.services.search.projection;

import com.genealogy.platform.services.search.shared.SearchLimits;

/**
 * Centralised numeric constants mirror the
 * <code>contracts/search/search-projection-policy.yaml</code>
 * contract (E8.1).
 */
public final class SearchProjectionLimits {
  private SearchProjectionLimits() {}

  public static final int MAX_NAME_LENGTH = 256;
  public static final int MAX_ALIAS_LENGTH = 256;
  public static final int MAX_ALIAS_PER_DOCUMENT = 64;
  public static final int MAX_LANGUAGES_PER_DOCUMENT = 16;
  public static final int MAX_BCP47_TAG_LENGTH = 64;
  public static final int MAX_PROJECTION_BATCH_SIZE = 1024;
  public static final int MAX_OUTBOX_BATCH_SIZE = 256;
  public static final int PROJECTION_LAG_P95_BUDGET_SECONDS = 24;
  public static final int PROJECTION_LAG_P99_BUDGET_SECONDS = 120;
  public static final int PROJECTION_LAG_BREACH_SECONDS = 300;
  public static final int PROJECTION_LAG_HEARTBEAT_SECONDS = 5;
  public static final int BACKFILL_BATCH_SIZE = 4096;
  public static final int BACKFILL_TIMEOUT_SECONDS = 1800;
  public static final int BACKFILL_HEARTBEAT_SECONDS = 30;
  public static final int BACKFILL_LOOKBACK_HOURS = 168;
  public static final int BACKFILL_CADENCE_HOURS = 24;
  public static final int RECONCILIATION_P95_BUDGET_SECONDS = 600;
  public static final int RECONCILIATION_HEARTBEAT_SECONDS = 60;

  // Combined from shared module so other packages (E8.2/E8.3/E8.4) reuse
  // the same string-length caps.
  public static final int PROJECTION_DOCUMENT_ID_LENGTH = SearchLimits.PROJECTION_DOCUMENT_ID_LENGTH;
  public static final int TENANT_SCOPE_ID_LENGTH = SearchLimits.TENANT_SCOPE_ID_LENGTH;
  public static final int ACTOR_PSEUDO_ID_LENGTH = SearchLimits.ACTOR_PSEUDO_ID_LENGTH;
  public static final int CORRELATION_ID_LENGTH = SearchLimits.CORRELATION_ID_LENGTH;
  public static final int IDEMPOTENCY_KEY_LENGTH = SearchLimits.IDEMPOTENCY_KEY_LENGTH;
}