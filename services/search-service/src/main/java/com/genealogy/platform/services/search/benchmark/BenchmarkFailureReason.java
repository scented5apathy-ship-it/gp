package com.genealogy.platform.services.search.benchmark;

/**
 * Closed-set failure reasons emitted by the benchmark gate.
 * Mirrors <code>contracts/search/benchmark-evolution-gate-policy.yaml</code>
 * <code>benchmarkFailureReasons</code>.
 */
public enum BenchmarkFailureReason {
  BENCHMARK_WORKLOAD_UNKNOWN,
  BENCHMARK_DATASET_SHAPE_UNKNOWN,
  BENCHMARK_SLO_METRIC_UNKNOWN,
  BENCHMARK_QUERY_LANGUAGE_UNKNOWN,
  BENCHMARK_EVOLUTION_PATH_UNKNOWN,
  BENCHMARK_VERDICT_UNKNOWN,
  BENCHMARK_ROLLOUT_STAGE_UNKNOWN,
  BENCHMARK_DATASET_MISSING,
  BENCHMARK_DATASET_TOO_SMALL,
  BENCHMARK_DATASET_TOO_LARGE,
  BENCHMARK_SLO_BUDGET_EXCEEDED,
  BENCHMARK_FUZZY_RECALL_BELOW_FLOOR,
  BENCHMARK_FUZZY_PRECISION_BELOW_FLOOR,
  BENCHMARK_FACET_CARDINALITY_BELOW_FLOOR,
  BENCHMARK_FRESHNESS_BUDGET_EXCEEDED,
  BENCHMARK_INDEX_INVALID,
  BENCHMARK_SAFETY_BUDGET_EXCEEDED,
  BENCHMARK_DNA_BUCKET_LEAK,
  BENCHMARK_CONTRACT_HASH_DRIFT,
  BENCHMARK_RUNTIME_TIMEOUT,
  BENCHMARK_DETERMINISTIC_FAIL,
  BENCHMARK_ADR_REQUIRED;

  public String wire() {
    return name();
  }

  public static BenchmarkFailureReason fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("benchmarkFailureReason MUST NOT be null");
    }
    try {
      return BenchmarkFailureReason.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "benchmarkFailureReason MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}