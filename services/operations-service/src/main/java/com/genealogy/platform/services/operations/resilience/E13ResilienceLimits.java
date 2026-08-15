package com.genealogy.platform.services.operations.resilience;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E13.4 resilience /
 * chaos contract. Mirrors
 * <code>contracts/reliability/resilience-chaos-policy.yaml</code>.
 */
public final class E13ResilienceLimits {

  public static final Set<String> FAULT_CLASSES = Set.of(
      "pod_kill", "network_latency", "kafka_lag",
      "temporal_restart", "openfga_outage", "db_failover",
      "otel_collector_down", "dns_failure", "clock_skew",
      "cpu_pressure", "memory_pressure", "disk_pressure",
      "tls_rotation");

  public static final Set<String> RETRY_POLICIES = Set.of(
      "none", "linear", "exponential", "decorrelated_jitter");

  public static final Set<String> DEGRADATION_MODES = Set.of(
      "fail_closed", "fail_open", "read_only", "cached");

  public static final Set<String> CRITICAL_DEPENDENCIES = Set.of(
      "postgres", "kafka", "openfga", "temporal",
      "vault", "otel_collector", "kong", "s3");

  public static final Set<String> CANARY_ABORT_REASONS = Set.of(
      "fiveXxRatioExceeded", "p95LatencyRegression",
      "errorRateSpike", "privacyFindingDetected");

  public static final int MAX_RETRY_ATTEMPTS = 6;
  public static final int MAX_RETRY_BUDGET_SECONDS = 60;
  public static final int CIRCUIT_BREAKER_THRESHOLD = 5;
  public static final int CIRCUIT_BREAKER_OPEN_SECONDS = 30;
  public static final int CIRCUIT_BREAKER_ROLLING_WINDOW_SECONDS = 60;
  public static final int CIRCUIT_BREAKER_MINIMUM_CALLS = 10;
  public static final int HALF_OPEN_PROBE_MAX = 1;
  public static final int IDEMPOTENCY_KEY_TTL_SECONDS = 86400;
  public static final int GAME_DAY_FREQUENCY_DAYS = 90;
  public static final int RESTORE_DRILL_FREQUENCY_DAYS = 90;
  public static final double CANARY_ABORT_FIVE_XX_RATIO = 0.01;
  public static final double CANARY_ABORT_P95_LATENCY_MULTIPLIER = 2.0;
  public static final double CANARY_ABORT_ERROR_RATE_SPIKE = 0.005;
  public static final int CANARY_ABORT_FIVE_XX_FOR_SECONDS = 120;
  public static final int CANARY_ABORT_P95_FOR_SECONDS = 180;
  public static final int CANARY_ABORT_ERROR_RATE_FOR_SECONDS = 180;

  private E13ResilienceLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}