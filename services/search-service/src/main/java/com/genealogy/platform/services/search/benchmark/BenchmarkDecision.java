package com.genealogy.platform.services.search.benchmark;

import java.util.Map;

/**
 * Output of {@link SearchBenchmarkGate#evaluate}. Compact constructor
 * pins the invariant shape:
 * <ul>
 *   <li><code>PASS</code> / <code>PASS_WITH_NOTES</code> MUST
 *       carry a non-null evolution path.</li>
 *   <li><code>FAIL_*</code> MUST carry a non-null evolution path
 *       and may carry a failure reason.</li>
 *   <li><code>BLOCKED_ADR_REQUIRED</code> MUST carry
 *       {@link BenchmarkFailureReason#BENCHMARK_ADR_REQUIRED}.</li>
 * </ul>
 */
public record BenchmarkDecision(
    BenchmarkVerdict verdict,
    BenchmarkEvolutionPath evolutionPath,
    BenchmarkFailureReason failureReason,
    Map<String, String> facts) {

  public BenchmarkDecision {
    if (verdict == null) {
      throw new IllegalArgumentException("verdict MUST NOT be null");
    }
    if (facts == null) {
      facts = Map.of();
    } else {
      facts = Map.copyOf(facts);
    }
    switch (verdict) {
      case PASS, PASS_WITH_NOTES -> {
        if (evolutionPath == null) {
          throw new IllegalArgumentException(
              verdict + " decision MUST carry an evolutionPath");
        }
      }
      case FAIL_P95, FAIL_P99, FAIL_LAG, FAIL_FRESHNESS,
          FAIL_FUZZY_RECALL, FAIL_FUZZY_PRECISION, FAIL_FACET_CARDINALITY,
          FAIL_INDEX, FAIL_SAFETY -> {
        if (evolutionPath == null) {
          throw new IllegalArgumentException(
              verdict + " decision MUST carry an evolutionPath");
        }
      }
      case BLOCKED_ADR_REQUIRED -> {
        if (failureReason != BenchmarkFailureReason.BENCHMARK_ADR_REQUIRED) {
          throw new IllegalArgumentException(
              "BLOCKED_ADR_REQUIRED MUST carry BENCHMARK_ADR_REQUIRED failureReason");
        }
      }
      default -> {
        // exhaustive switch over the closed-set BenchmarkVerdict.
      }
    }
  }

  public static BenchmarkDecision pass(BenchmarkEvolutionPath path, Map<String, String> facts) {
    return new BenchmarkDecision(BenchmarkVerdict.PASS, path, null, facts);
  }

  public static BenchmarkDecision fail(
      BenchmarkVerdict verdict,
      BenchmarkEvolutionPath path,
      BenchmarkFailureReason reason,
      Map<String, String> facts) {
    return new BenchmarkDecision(verdict, path, reason, facts);
  }

  public static BenchmarkDecision blocked(Map<String, String> facts) {
    return new BenchmarkDecision(
        BenchmarkVerdict.BLOCKED_ADR_REQUIRED,
        null,
        BenchmarkFailureReason.BENCHMARK_ADR_REQUIRED,
        facts);
  }
}