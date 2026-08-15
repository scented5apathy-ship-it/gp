package com.genealogy.platform.services.search.benchmark;

/**
 * Closed-set verdicts emitted by the search benchmark gate.
 * Mirrors <code>contracts/search/benchmark-evolution-gate-policy.yaml</code>
 * <code>benchmarkVerdicts</code>.
 */
public enum BenchmarkVerdict {
  PASS,
  PASS_WITH_NOTES,
  FAIL_P95,
  FAIL_P99,
  FAIL_LAG,
  FAIL_FRESHNESS,
  FAIL_FUZZY_RECALL,
  FAIL_FUZZY_PRECISION,
  FAIL_FACET_CARDINALITY,
  FAIL_INDEX,
  FAIL_SAFETY,
  BLOCKED_ADR_REQUIRED;

  public String wire() {
    return name();
  }

  public static BenchmarkVerdict fromWire(String wire) {
    if (wire == null) {
      throw new IllegalArgumentException("benchmarkVerdict MUST NOT be null");
    }
    try {
      return BenchmarkVerdict.valueOf(wire);
    } catch (IllegalArgumentException unknown) {
      throw new IllegalArgumentException(
          "benchmarkVerdict MUST be one of "
              + java.util.Arrays.toString(values())
              + " (got "
              + wire
              + ")");
    }
  }
}