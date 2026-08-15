package com.genealogy.platform.services.importexport.shared;

/**
 * Numeric limits shared by every E9.x orchestrator.
 * Mirrors the numeric bounds declared in the E9.1 — E9.6 contracts.
 * Every limit MUST stay below or equal to the value defined in the
 * matching contract; this class is the single source of truth for
 * Java-side guards (the YAML contract is validated by the
 * <code>scripts/lint-*.mjs</code> linters).
 */
public final class ImportExportLimits {

  public static final int WORKFLOW_MAX_SIGNALS_PER_SECOND = 10;
  public static final int WORKFLOW_MAX_QUERIES_PER_SECOND = 50;
  public static final int WORKFLOW_MAX_ACTIVITY_ATTEMPTS = 8;
  public static final int WORKFLOW_MAX_CONCURRENT_ACTIVITIES = 16;
  public static final int WORKFLOW_MAX_HISTORY_LENGTH_EVENTS = 50_000;
  public static final int WORKFLOW_MAX_EXECUTION_DURATION_SECONDS = 86_400;
  public static final int ACTIVITY_START_TO_CLOSE_TIMEOUT_SECONDS = 310;
  public static final int ACTIVITY_HEARTBEAT_INTERVAL_SECONDS = 30;
  public static final int ACTIVITY_RETRY_MAX_ATTEMPTS = 8;

  public static final int GEDCOM_MAX_PAYLOAD_BYTES = 52_428_800;
  public static final int GEDCOM_MAX_LINE_LENGTH = 4_096;
  public static final int GEDCOM_MAX_DEPTH = 32;
  public static final int GEDCOM_MAX_RECORD_COUNT = 5_000_000;
  public static final int GEDCOM_STREAMING_CHUNK_BYTES = 65_536;

  public static final int IMPORT_CHUNK_MAX_RECORDS = 1_000;
  public static final int IMPORT_CHUNK_MAX_BYTES = 10_485_760;
  public static final int IMPORT_CHUNK_MAX_CONCURRENT_COMMITS = 8;
  public static final int IMPORT_CHUNK_COMMIT_TIMEOUT_SECONDS = 300;

  public static final int EXPORT_BUNDLE_MAX_BYTES = 524_288_000;
  public static final int EXPORT_SIGN_URL_TTL_SECONDS = 3_600;
  public static final int EXPORT_REVOCATION_PROPAGATION_SECONDS = 30;

  public static final int PUBLIC_API_PER_IP_PER_MINUTE = 60;
  public static final int PUBLIC_API_PER_IP_PER_HOUR = 1_000;
  public static final int PUBLIC_API_PER_CLIENT_PER_MINUTE = 120;
  public static final int PUBLIC_API_PER_CLIENT_PER_HOUR = 5_000;
  public static final int PUBLIC_API_IDEMPOTENCY_KEY_TTL_SECONDS = 86_400;

  public static final int WEBHOOK_MAX_DELIVERY_ATTEMPTS = 8;
  public static final int WEBHOOK_INITIAL_BACKOFF_SECONDS = 5;
  public static final int WEBHOOK_MAX_BACKOFF_SECONDS = 3_600;
  public static final int WEBHOOK_TARGET_RESPONSE_TIMEOUT_SECONDS = 30;
  public static final int WEBHOOK_DISPATCHER_TIMEOUT_SECONDS = 600;

  public static final int DNA_BUCKET_ACCESS_FORBIDDEN = 1;
  public static final int SECRET_MAX_BYTES = 0;

  private ImportExportLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}