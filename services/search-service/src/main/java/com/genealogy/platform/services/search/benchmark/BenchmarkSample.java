package com.genealogy.platform.services.search.benchmark;

import java.util.Map;

/**
 * Immutable benchmark observation captured for one workload against
 * one dataset.
 */
public record BenchmarkSample(
    String workload,
    String datasetShape,
    long p95Milliseconds,
    long p99Milliseconds,
    double fuzzyRecallAt10,
    double fuzzyPrecisionAt10,
    int facetCardinality,
    long freshnessSeconds,
    int safetyBudgetViolations,
    int dnaBucketLeaks) {

  public BenchmarkSample {
    if (workload == null || workload.isBlank()) {
      throw new IllegalArgumentException("workload MUST NOT be blank");
    }
    if (datasetShape == null || datasetShape.isBlank()) {
      throw new IllegalArgumentException("datasetShape MUST NOT be blank");
    }
    if (p95Milliseconds < 0 || p99Milliseconds < p95Milliseconds) {
      throw new IllegalArgumentException(
          "p95/p99 MUST be non-negative and p99 >= p95 (got p95="
              + p95Milliseconds
              + ", p99="
              + p99Milliseconds
              + ")");
    }
    if (fuzzyRecallAt10 < 0.0d || fuzzyRecallAt10 > 1.0d) {
      throw new IllegalArgumentException(
          "fuzzyRecallAt10 MUST be in [0.0, 1.0] (got "
              + fuzzyRecallAt10
              + ")");
    }
    if (fuzzyPrecisionAt10 < 0.0d || fuzzyPrecisionAt10 > 1.0d) {
      throw new IllegalArgumentException(
          "fuzzyPrecisionAt10 MUST be in [0.0, 1.0] (got "
              + fuzzyPrecisionAt10
              + ")");
    }
    if (safetyBudgetViolations < 0) {
      throw new IllegalArgumentException(
          "safetyBudgetViolations MUST be >= 0 (got "
              + safetyBudgetViolations
              + ")");
    }
    if (dnaBucketLeaks < 0) {
      throw new IllegalArgumentException(
          "dnaBucketLeaks MUST be >= 0 (got " + dnaBucketLeaks + ")");
    }
  }

  public Map<String, String> facts() {
    return Map.of(
        "workload",
        workload,
        "datasetShape",
        datasetShape,
        "p95",
        Long.toString(p95Milliseconds),
        "p99",
        Long.toString(p99Milliseconds),
        "fuzzyRecallAt10",
        Double.toString(fuzzyRecallAt10),
        "fuzzyPrecisionAt10",
        Double.toString(fuzzyPrecisionAt10),
        "facetCardinality",
        Integer.toString(facetCardinality),
        "freshnessSeconds",
        Long.toString(freshnessSeconds));
  }
}