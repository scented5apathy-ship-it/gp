package com.genealogy.platform.services.notification.shared;

/**
 * Numeric limits shared by every E11.x orchestrator.
 * Mirrors the numeric bounds declared in the E11.1 — E11.5 contracts.
 * Every limit MUST stay below or equal to the value defined in the
 * matching contract; this class is the single source of truth for
 * Java-side guards (the YAML contract is validated by the
 * <code>scripts/lint-notification*.mjs</code>,
 * <code>scripts/lint-reporting.mjs</code> and
 * <code>scripts/lint-operations.mjs</code> linters).
 */
public final class E11Limits {

  public static final int TEMPLATE_MAX_BYTES = 65_536;
  public static final int DIGEST_MAX_RECIPIENTS_PER_RUN = 1_000;
  public static final int INAPP_INBOX_MAX_PER_USER = 500;
  public static final int PREFERENCE_UPDATE_MAX_PER_REQUEST = 16;
  public static final int RENDER_TIMEOUT_MS = 250;
  public static final int PROVIDER_SEND_TIMEOUT_SECONDS = 30;
  public static final int RETRY_POLICY_MAX_ATTEMPTS = 8;
  public static final int RETRY_POLICY_INITIAL_BACKOFF_SECONDS = 30;
  public static final int RETRY_POLICY_MAX_BACKOFF_SECONDS = 1_800;
  public static final int RATE_LIMIT_PER_USER_PER_MINUTE = 60;
  public static final int RATE_LIMIT_PER_TENANT_PER_MINUTE = 10_000;
  public static final int QUIET_HOURS_DEFAULT_MINUTES = 480;
  public static final int LOCALE_TEMPLATE_MAX_VERSIONS_RETAINED = 50;
  public static final int INBOX_ACKNOWLEDGEMENT_SLA_HOURS = 168;
  public static final int DELIVERY_WEBHOOK_SIGNATURE_MAX_CLOCK_SKEW_SECONDS = 300;

  public static final int DEEP_LINK_TOKEN_TTL_SECONDS = 3_600;
  public static final int UNSUBSCRIBE_CLICK_TOKEN_TTL_SECONDS = 86_400;
  public static final int BOUNCE_RETENTION_DAYS = 30;
  public static final int COMPLAINT_RETENTION_DAYS = 365;
  public static final int SUPPRESSION_RETENTION_DAYS = 365;
  public static final int BRANDING_REFRESH_INTERVAL_SECONDS = 3_600;
  public static final int ABAC_RECHECK_TIMEOUT_MS = 250;
  public static final int GENERIC_TEXT_MAX_BYTES = 4_096;
  public static final int DEEP_LINK_TOKEN_MAX_BYTES = 512;
  public static final int ABAC_DECISION_CACHE_TTL_SECONDS = 60;
  public static final int PRIVACY_PAYLOAD_REDACTION_TIMEOUT_MS = 100;
  public static final int OUTBOUND_BODY_MAX_BYTES = 65_536;

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

  public static final int PLAN_CHANGE_PROPAGATION_TIMEOUT_SECONDS = 30;
  public static final int QUOTA_WARNING_EMISSION_INTERVAL_SECONDS = 300;
  public static final int BILLING_WEBHOOK_REPLAY_WINDOW_SECONDS = 300;
  public static final int USAGE_EVENT_MAX_BYTES = 4_096;
  public static final int LICENSE_MAX_BYTES = 65_536;
  public static final int LICENSE_CLOCK_SKEW_SECONDS = 300;
  public static final int QUOTA_RESET_CADENCE_DAYS = 30;
  public static final int FREE_TIER_TREE_LIMIT = 3;
  public static final int FREE_TIER_PERSON_LIMIT = 250;
  public static final long FREE_TIER_MEDIA_BYTES = 524_288_000L;
  public static final int PRO_TIER_TREE_LIMIT = 50;
  public static final int PRO_TIER_PERSON_LIMIT = 25_000;
  public static final long PRO_TIER_MEDIA_BYTES = 107_374_182_400L;
  public static final int ENTERPRISE_TIER_TREE_LIMIT = 10_000;
  public static final int ENTERPRISE_TIER_PERSON_LIMIT = 10_000_000;
  public static final long ENTERPRISE_TIER_MEDIA_BYTES = 10_995_116_277_760L;
  public static final int WEBHOOK_DELIVERY_DAILY_LIMIT = 1_000_000;

  public static final int JIT_MAX_DURATION_MINUTES = 240;
  public static final int JIT_MAX_SCOPE_MINUTES = 240;
  public static final int JIT_APPROVAL_TIMEOUT_SECONDS = 900;
  public static final int IMPERSONATION_MAX_DURATION_SECONDS = 0;
  public static final int DLQ_REPLAY_MAX_EVENTS_PER_RUN = 10_000;
  public static final int DLQ_REPLAY_MAX_WINDOW_HOURS = 168;
  public static final int FEATURE_FLAG_OVERRIDE_MAX_TTL_SECONDS = 86_400;
  public static final int PLAN_OVERRIDE_MAX_TTL_SECONDS = 2_592_000;
  public static final int QUOTA_OVERRIDE_MAX_TTL_SECONDS = 604_800;
  public static final int AUDIT_EXPORT_SIGNED_URL_TTL_SECONDS = 3_600;
  public static final int AUDIT_EXPORT_MAX_BYTES = 524_288_000;
  public static final int TENANT_SWITCH_REAUTH_MAX_CLOCK_SKEW_SECONDS = 60;

  private E11Limits() {
    throw new UnsupportedOperationException("constants holder");
  }
}