package com.genealogy.platform.services.reporting.shared;

/**
 * Numeric limits shared by the E11.3 reporting pipeline. Mirrors the
 * numeric bounds declared in
 * <code>contracts/reporting/reporting-policy.yaml</code>. Kept
 * package-local because cross-service sharing is forbidden by the
 * monorepo boundary rules (E1.1).
 */
public final class E11Limits {

  public static final int REPORT_MAX_INPUT_ROWS = 1_000_000;
  public static final int REPORT_MAX_OUTPUT_BYTES = 524_288_000;
  public static final int REPORT_PREVIEW_MAX_BYTES = 52_428_800;
  public static final int GOTENBERG_RENDER_TIMEOUT_SECONDS = 300;
  public static final int PROJECTION_REBUILD_MAX_ROWS = 5_000_000;
  public static final int PROJECTION_REBUILD_TIMEOUT_SECONDS = 1_800;
  public static final int JOB_SUBMISSION_TIMEOUT_MS = 500;
  public static final int SIGNED_URL_TTL_SECONDS = 3_600;
  public static final int DETERMINISTIC_DEFINITION_HASH_BYTES = 32;
  public static final int REDACTION_TOKEN_MAX_BYTES = 256;
  public static final int ANALYTICS_BATCH_MAX_EVENTS = 1_000;
  public static final int ANALYTICS_PRODUCT_METRICS_SCRUB_INTERVAL_SECONDS = 60;

  private E11Limits() {
    throw new UnsupportedOperationException("constants holder");
  }
}