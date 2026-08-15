package com.genealogy.platform.services.search.benchmark;

import java.util.List;
import java.util.Map;

/**
 * Pure deterministic orchestrator for the E8.4 search benchmark
 * gate.
 *
 * <p>The pipeline evaluates each {@link BenchmarkSample} against
 * the SLO budgets + the fuzzy / facet floors, and rolls the worst
 * verdict up into a single {@link BenchmarkDecision}. Any DNA
 * bucket leak short-circuits to
 * {@link BenchmarkVerdict#FAIL_SAFETY} with evolution path
 * {@link BenchmarkEvolutionPath#BLOCKED_ADR_REQUIRED}.
 *
 * <p>The orchestrator is intentionally pure: the k6/Gatling runner
 * + the dataset loader land in the worker subproject.
 */
public final class SearchBenchmarkGate {

  private static final Map<BenchmarkRolloutStage, List<BenchmarkEvolutionPath>> ROLLOUT_PATHS =
      Map.of(
          BenchmarkRolloutStage.NIGHTLY,
              List.of(
                  BenchmarkEvolutionPath.POSTGRES_HOLD,
                  BenchmarkEvolutionPath.POSTGRES_REINDEX,
                  BenchmarkEvolutionPath.POSTGRES_PARTITION,
                  BenchmarkEvolutionPath.POSTGRES_GIN_REWRITE,
                  BenchmarkEvolutionPath.POSTGRES_GIST_REWRITE,
                  BenchmarkEvolutionPath.POSTGRES_BRIN_PARTITION,
                  BenchmarkEvolutionPath.ADAPTIVE_INDEX_REQUIRED),
          BenchmarkRolloutStage.PRE_MERGE,
              List.of(
                  BenchmarkEvolutionPath.POSTGRES_HOLD,
                  BenchmarkEvolutionPath.POSTGRES_REINDEX,
                  BenchmarkEvolutionPath.POSTGRES_PARTITION,
                  BenchmarkEvolutionPath.POSTGRES_GIN_REWRITE),
          BenchmarkRolloutStage.RELEASE_CANDIDATE,
              List.of(BenchmarkEvolutionPath.POSTGRES_HOLD),
          BenchmarkRolloutStage.POST_RELEASE,
              List.of(
                  BenchmarkEvolutionPath.POSTGRES_HOLD,
                  BenchmarkEvolutionPath.POSTGRES_REINDEX),
          BenchmarkRolloutStage.AD_HOC,
              List.of(
                  BenchmarkEvolutionPath.POSTGRES_HOLD,
                  BenchmarkEvolutionPath.POSTGRES_REINDEX,
                  BenchmarkEvolutionPath.POSTGRES_PARTITION,
                  BenchmarkEvolutionPath.POSTGRES_GIN_REWRITE,
                  BenchmarkEvolutionPath.POSTGRES_GIST_REWRITE,
                  BenchmarkEvolutionPath.POSTGRES_BRIN_PARTITION,
                  BenchmarkEvolutionPath.ADAPTIVE_INDEX_REQUIRED,
                  BenchmarkEvolutionPath.OPENSEARCH_REQUIRED));

  private SearchBenchmarkGate() {}

  public static BenchmarkDecision evaluate(
      BenchmarkRolloutStage stage, List<BenchmarkSample> samples) {
    if (stage == null) {
      throw new IllegalArgumentException("stage MUST NOT be null");
    }
    if (samples == null || samples.isEmpty()) {
      return BenchmarkDecision.blocked(Map.of("reason", "no samples"));
    }
    BenchmarkVerdict worst = BenchmarkVerdict.PASS;
    BenchmarkFailureReason worstReason = null;
    String worstSample = null;
    for (BenchmarkSample sample : samples) {
      BenchmarkVerdict verdict = evaluateSample(sample);
      if (verdict.ordinal() > worst.ordinal()) {
        worst = verdict;
        worstReason = reasonFor(verdict);
        worstSample = sample.workload() + "@" + sample.datasetShape();
      }
      if (verdict == BenchmarkVerdict.FAIL_SAFETY) {
        break;
      }
    }
    BenchmarkEvolutionPath path = worst == BenchmarkVerdict.PASS
        ? BenchmarkEvolutionPath.POSTGRES_HOLD
        : pickPath(stage, worst);
    if (worst == BenchmarkVerdict.BLOCKED_ADR_REQUIRED) {
      return BenchmarkDecision.blocked(Map.of("worstSample", safe(worstSample)));
    }
    if (worst == BenchmarkVerdict.PASS) {
      return BenchmarkDecision.pass(path, Map.of("samples", Integer.toString(samples.size())));
    }
    return BenchmarkDecision.fail(
        worst,
        path,
        worstReason,
        Map.of("worstSample", safe(worstSample)));
  }

  private static BenchmarkVerdict evaluateSample(BenchmarkSample sample) {
    if (sample.dnaBucketLeaks() > BenchmarkLimits.DNA_BUCKET_LEAKS) {
      return BenchmarkVerdict.FAIL_SAFETY;
    }
    if (sample.safetyBudgetViolations() > BenchmarkLimits.SAFETY_BUDGET_VIOLATIONS) {
      return BenchmarkVerdict.FAIL_SAFETY;
    }
    if (sample.p95Milliseconds() > BenchmarkLimits.P95_BUDGET_MILLISECONDS) {
      return BenchmarkVerdict.FAIL_P95;
    }
    if (sample.p99Milliseconds() > BenchmarkLimits.P99_BUDGET_MILLISECONDS) {
      return BenchmarkVerdict.FAIL_P99;
    }
    if (sample.freshnessSeconds() > BenchmarkLimits.FRESHNESS_BUDGET_SECONDS) {
      return BenchmarkVerdict.FAIL_FRESHNESS;
    }
    if (sample.fuzzyRecallAt10() < BenchmarkLimits.FUZZY_RECALL_FLOOR) {
      return BenchmarkVerdict.FAIL_FUZZY_RECALL;
    }
    if (sample.fuzzyPrecisionAt10() < BenchmarkLimits.FUZZY_PRECISION_FLOOR) {
      return BenchmarkVerdict.FAIL_FUZZY_PRECISION;
    }
    if (sample.facetCardinality() < BenchmarkLimits.FACET_CARDINALITY_FLOOR) {
      return BenchmarkVerdict.FAIL_FACET_CARDINALITY;
    }
    return BenchmarkVerdict.PASS;
  }

  private static BenchmarkFailureReason reasonFor(BenchmarkVerdict verdict) {
    return switch (verdict) {
      case FAIL_P95 -> BenchmarkFailureReason.BENCHMARK_SLO_BUDGET_EXCEEDED;
      case FAIL_P99 -> BenchmarkFailureReason.BENCHMARK_SLO_BUDGET_EXCEEDED;
      case FAIL_LAG -> BenchmarkFailureReason.BENCHMARK_SLO_BUDGET_EXCEEDED;
      case FAIL_FRESHNESS -> BenchmarkFailureReason.BENCHMARK_FRESHNESS_BUDGET_EXCEEDED;
      case FAIL_FUZZY_RECALL -> BenchmarkFailureReason.BENCHMARK_FUZZY_RECALL_BELOW_FLOOR;
      case FAIL_FUZZY_PRECISION -> BenchmarkFailureReason.BENCHMARK_FUZZY_PRECISION_BELOW_FLOOR;
      case FAIL_FACET_CARDINALITY -> BenchmarkFailureReason.BENCHMARK_FACET_CARDINALITY_BELOW_FLOOR;
      case FAIL_INDEX -> BenchmarkFailureReason.BENCHMARK_INDEX_INVALID;
      case FAIL_SAFETY -> BenchmarkFailureReason.BENCHMARK_SAFETY_BUDGET_EXCEEDED;
      default -> null;
    };
  }

  private static BenchmarkEvolutionPath pickPath(
      BenchmarkRolloutStage stage, BenchmarkVerdict verdict) {
    List<BenchmarkEvolutionPath> allowed = ROLLOUT_PATHS.getOrDefault(stage, List.of());
    if (verdict == BenchmarkVerdict.FAIL_P95
        || verdict == BenchmarkVerdict.FAIL_P99
        || verdict == BenchmarkVerdict.FAIL_LAG) {
      return allowed.contains(BenchmarkEvolutionPath.OPENSEARCH_REQUIRED)
          ? BenchmarkEvolutionPath.OPENSEARCH_REQUIRED
          : BenchmarkEvolutionPath.ADAPTIVE_INDEX_REQUIRED;
    }
    if (verdict == BenchmarkVerdict.FAIL_FACET_CARDINALITY
        || verdict == BenchmarkVerdict.FAIL_FUZZY_RECALL
        || verdict == BenchmarkVerdict.FAIL_FUZZY_PRECISION) {
      return allowed.contains(BenchmarkEvolutionPath.POSTGRES_GIN_REWRITE)
          ? BenchmarkEvolutionPath.POSTGRES_GIN_REWRITE
          : BenchmarkEvolutionPath.POSTGRES_REINDEX;
    }
    if (verdict == BenchmarkVerdict.FAIL_FRESHNESS) {
      return allowed.contains(BenchmarkEvolutionPath.POSTGRES_PARTITION)
          ? BenchmarkEvolutionPath.POSTGRES_PARTITION
          : BenchmarkEvolutionPath.POSTGRES_REINDEX;
    }
    return BenchmarkEvolutionPath.POSTGRES_REINDEX;
  }

  private static String safe(String value) {
    return value == null ? "<unknown>" : value;
  }
}