package com.genealogy.platform.services.operations.slo;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E13.2 SLO / alert /
 * runbook contract. Mirrors
 * <code>contracts/reliability/slo-alert-policy.yaml</code>.
 */
public final class E13SloLimits {

  public static final Set<String> SLI_NAMES = Set.of(
      "api_read_p95", "api_write_p95", "search_p95",
      "tree_view_initial_tti_p75",
      "consumer_lag_critical_p99", "outbox_age_p99",
      "workflow_failure_rate_per_hour",
      "projection_freshness_p99",
      "synthetic_availability", "api_availability",
      "pii_redaction_coverage");

  public static final Set<String> SEVERITIES = Set.of(
      "SEV1", "SEV2", "SEV3", "SEV4");

  public static final Set<String> ACTIONS = Set.of("PAGE", "TICKET", "SILENT");

  public static final Set<String> BURN_RATE_WINDOWS = Set.of(
      "1m", "5m", "30m", "1h", "2m", "6h",
      "10m", "15m", "24h", "3d");

  public static final Set<String> SYNTHETIC_PROBES = Set.of(
      "kong_health", "keycloak_realm", "openfga_store",
      "postgres_primary", "kafka_broker", "temporal_namespace",
      "object_storage", "vault_seal_status", "flagsmith_health",
      "otel_collector");

  public static final Set<String> REQUIRED_RUNBOOK_FIELDS = Set.of(
      "runbookRef", "dashboardRef", "owner", "severity",
      "action", "summary", "notifyChannel");

  public static final long API_READ_P95_TARGET_MS = 300L;
  public static final long API_WRITE_P95_TARGET_MS = 600L;
  public static final long SEARCH_P95_TARGET_MS = 1000L;
  public static final long TREE_VIEW_INITIAL_TTI_P75_TARGET_MS = 2500L;
  public static final long CONSUMER_LAG_CRITICAL_P99_RECORDS = 1000L;
  public static final long OUTBOX_AGE_P99_SECONDS = 300L;
  public static final long WORKFLOW_FAILURE_PER_HOUR = 5L;
  public static final long PROJECTION_FRESHNESS_P99_SECONDS = 900L;
  public static final double SYNTHETIC_AVAILABILITY_TARGET_RATIO = 0.99;
  public static final double API_AVAILABILITY_TARGET_RATIO = 0.999;
  public static final double PII_REDACTION_COVERAGE_TARGET_RATIO = 1.0;
  public static final double SHORT_BURST_FACTOR = 14.4;
  public static final double TICKET_FACTOR = 6.0;
  public static final double REVIEW_FACTOR = 3.0;
  public static final int SEV1_RESPONSE_MINUTES = 15;
  public static final int SEV2_RESPONSE_MINUTES = 30;
  public static final int SEV3_RESPONSE_MINUTES = 240;
  public static final int SEV4_RESPONSE_MINUTES = 1440;
  public static final double BUDGET_FREEZE_WEEK1_RATIO = 0.5;
  public static final long CARDINALITY_TENANT_CEILING = 50000L;
  public static final long CARDINALITY_USER_CEILING = 200000L;

  private E13SloLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}