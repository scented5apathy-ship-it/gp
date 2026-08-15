package com.genealogy.platform.services.search.benchmark;

/**
 * Closed-set evolution paths the gate may recommend. Mirrors
 * <code>contracts/search/benchmark-evolution-gate-policy.yaml</code>
 * <code>benchmarkEvolutionPaths</code>.
 */
public enum BenchmarkEvolutionPath {
  POSTGRES_HOLD,
  POSTGRES_REINDEX,
  POSTGRES_PARTITION,
  POSTGRES_GIN_REWRITE,
  POSTGRES_GIST_REWRITE,
  POSTGRES_BRIN_PARTITION,
  ADAPTIVE_INDEX_REQUIRED,
  OPENSEARCH_REQUIRED;

  public String wire() {
    return name();
  }

  public static BenchmarkEvolutionPath fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("benchmarkEvolutionPath MUST NOT be null");
    }
    try {
      return BenchmarkEvolutionPath.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "benchmarkEvolutionPath MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}