package com.genealogy.platform.services.operations.shared;

/**
 * Numeric limits shared by the E11.4 + E11.5 operations pipelines.
 * Mirrors the numeric bounds declared in
 * <code>contracts/operations/entitlement-quota-billing-policy.yaml</code>
 * and
 * <code>contracts/operations/admin-support-operations-policy.yaml</code>.
 */
public final class E11Limits {

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
  public static final int PROJECTION_REBUILD_MAX_ROWS = 5_000_000;
  public static final int PROJECTION_REBUILD_TIMEOUT_SECONDS = 1_800;
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