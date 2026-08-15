package com.genealogy.platform.services.operations.recovery;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E14.4 upgrade /
 * rollback contract. Mirrors
 * <code>contracts/disaster-recovery/recovery-rollback-policy.yaml</code>.
 */
public final class E14RecoveryLimits {

  public static final Set<String> SUPPORTED_PREVIOUS_VERSIONS = Set.of(
      "2025.10", "2025.12", "2026.02", "2026.04", "2026.06");

  public static final Set<String> MIGRATION_KINDS = Set.of(
      "expand_column_add", "expand_table_create",
      "expand_index_create", "expand_backfill", "expand_switch",
      "deprecated_drop_followup");

  public static final Set<String> COMPAT_KINDS = Set.of(
      "BACKWARD", "BACKWARD_TRANSITIVE", "FULL",
      "NONE_BREAKING_SUPERSEDED_BY_ADR");

  public static final Set<String> ABORT_RULE_KINDS = Set.of(
      "five_xx_ratio_exceeded", "p95_latency_regression",
      "error_rate_spike", "privacy_finding_detected");

  public static final Set<String> PRE_CHECKS = Set.of(
      "flyway_no_destructive", "schema_compatibility_checked",
      "event_compatibility_checked", "rollback_plan_attached",
      "feature_flag_kill_switch_attached", "preflight_passed");

  public static final Set<String> POST_CHECKS = Set.of(
      "flyway_migration_applied", "red_metrics_under_budget",
      "workflow_completion_under_budget",
      "search_projection_fresh", "audit_pipeline_receiving",
      "reconcile_targets_stable", "signoff_attached");

  public static final Set<String> ROLLBACK_CONSTRAINTS = Set.of(
      "maxOneRollbackPerTenant", "noCrossTenantRollback",
      "rollbackToSupportedPreviousVersionOnly",
      "rollbackRequiresApprovalTicket",
      "rollbackEvacuatesActiveMutations",
      "rollbackRunsFeatureFlagKillSwitch");

  public static final Set<String> UPGRADE_STATUSES = Set.of(
      "PLANNED", "PRECHECK_RUNNING", "APPLYING",
      "POSTCHECK_RUNNING", "CANCELLED", "SUCCEEDED",
      "FAILED", "ROLLING_BACK", "ROLLED_BACK", "SUPERSEDED");

  public static final int MAX_SUPPORTED_PREVIOUS_VERSIONS = 5;
  public static final int MIN_SUPPORTED_PREVIOUS_VERSIONS = 3;
  public static final int PRECHECK_TIMEOUT_SECONDS = 1800;
  public static final int APPLY_TIMEOUT_SECONDS = 7200;
  public static final int POSTCHECK_TIMEOUT_SECONDS = 3600;
  public static final int ROLLBACK_TIMEOUT_SECONDS = 3600;
  public static final int MAX_ACTIVE_ROLLBACKS_PER_TENANT = 1;
  public static final int UPGRADE_TEST_COVERAGE_REQUIRED_VERSIONS = 3;
  public static final int MAX_BACKWARDS_COMPAT_WINDOW_RELEASES = 6;
  public static final int MIN_FEATURE_FLAG_KILL_SWITCH_LATENCY_SECONDS = 60;
  public static final int DESTRUCTIVE_MIGRATION_WINDOW_RELEASES = 0;

  private E14RecoveryLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}