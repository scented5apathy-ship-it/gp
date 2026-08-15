package com.genealogy.platform.services.search.benchmark;

/**
 * Closed-set rollout stages the benchmark run may attach to.
 * Mirrors <code>contracts/search/benchmark-evolution-gate-policy.yaml</code>
 * <code>benchmarkRolloutStages</code>.
 */
public enum BenchmarkRolloutStage {
  NIGHTLY,
  PRE_MERGE,
  RELEASE_CANDIDATE,
  POST_RELEASE,
  AD_HOC;

  public String wire() {
    return name();
  }

  public static BenchmarkRolloutStage fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("benchmarkRolloutStage MUST NOT be null");
    }
    try {
      return BenchmarkRolloutStage.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "benchmarkRolloutStage MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}