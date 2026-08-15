package com.genealogy.platform.services.search.benchmark;

/**
 * Centralised numeric constants mirror
 * <code>contracts/search/benchmark-evolution-gate-policy.yaml</code> (E8.4).
 */
public final class BenchmarkLimits {
  private BenchmarkLimits() {}

  public static final int MAX_DATASETS = 16;
  public static final int MAX_QUERIES_PER_DATASET = 1024;
  public static final int MAX_WORKLOADS = 8;
  public static final int MAX_ROLLOUT_STAGES = 5;
  public static final int WARMUP_ITERATIONS = 50;
  public static final int MEASUREMENT_ITERATIONS = 200;
  public static final int COOLDOWN_SECONDS = 60;
  public static final int P95_BUDGET_MILLISECONDS = 1000;
  public static final int P99_BUDGET_MILLISECONDS = 2000;
  public static final int FACET_P95_BUDGET_MILLISECONDS = 250;
  public static final int CURSOR_P95_BUDGET_MILLISECONDS = 500;
  public static final int FRESHNESS_BUDGET_SECONDS = 60;
  public static final double FUZZY_RECALL_FLOOR = 0.85d;
  public static final double FUZZY_PRECISION_FLOOR = 0.90d;
  public static final int FACET_CARDINALITY_FLOOR = 16;
  public static final int SAFETY_BUDGET_VIOLATIONS = 0;
  public static final int DNA_BUCKET_LEAKS = 0;
  public static final int RUNTIME_TIMEOUT_SECONDS = 120;
  public static final int HEARTBEAT_SECONDS = 30;
  public static final int CONTRACT_HASH_LENGTH = 64;
  public static final long DATASET_MAX_ROWS = 10_000_000L;
  public static final long DATASET_MIN_ROWS = 1_000L;
  public static final int RECOMMENDATION_TTL_SECONDS = 2_592_000;
}