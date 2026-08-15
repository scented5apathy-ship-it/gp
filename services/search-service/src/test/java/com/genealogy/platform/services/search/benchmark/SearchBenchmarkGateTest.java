package com.genealogy.platform.services.search.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchBenchmarkGateTest {

  private BenchmarkSample passingSample(String workload) {
    return new BenchmarkSample(
        workload,
        "SMALL_HOT_TREE",
        800,
        1600,
        0.95d,
        0.95d,
        64,
        30,
        0,
        0);
  }

  @Test
  void allSamplesPassingProducesPassVerdict() {
    BenchmarkDecision decision =
        SearchBenchmarkGate.evaluate(
            BenchmarkRolloutStage.RELEASE_CANDIDATE,
            List.of(passingSample("EXACT_PERSON"), passingSample("FUZZY_PERSON_TRIGRAM")));
    assertEquals(BenchmarkVerdict.PASS, decision.verdict());
    assertEquals(BenchmarkEvolutionPath.POSTGRES_HOLD, decision.evolutionPath());
    assertNull(decision.failureReason());
  }

  @Test
  void p95BreachProducesFailP95() {
    BenchmarkSample breach = new BenchmarkSample(
        "EXACT_PERSON", "SMALL_HOT_TREE", 1500, 1600, 0.95d, 0.95d, 64, 30, 0, 0);
    BenchmarkDecision decision =
        SearchBenchmarkGate.evaluate(
            BenchmarkRolloutStage.RELEASE_CANDIDATE, List.of(breach));
    assertEquals(BenchmarkVerdict.FAIL_P95, decision.verdict());
    assertEquals(BenchmarkFailureReason.BENCHMARK_SLO_BUDGET_EXCEEDED, decision.failureReason());
  }

  @Test
  void fuzzyRecallBreachProducesFailFuzzyRecall() {
    BenchmarkSample recallBreach = new BenchmarkSample(
        "FUZZY_PERSON_TRIGRAM", "SMALL_HOT_TREE", 800, 1600, 0.50d, 0.95d, 64, 30, 0, 0);
    BenchmarkDecision decision =
        SearchBenchmarkGate.evaluate(
            BenchmarkRolloutStage.PRE_MERGE, List.of(recallBreach));
    assertEquals(BenchmarkVerdict.FAIL_FUZZY_RECALL, decision.verdict());
    assertEquals(
        BenchmarkFailureReason.BENCHMARK_FUZZY_RECALL_BELOW_FLOOR, decision.failureReason());
  }

  @Test
  void dnaBucketLeakProducesFailSafety() {
    BenchmarkSample dnaLeak = new BenchmarkSample(
        "EXACT_PERSON", "SMALL_HOT_TREE", 800, 1600, 0.95d, 0.95d, 64, 30, 0, 1);
    BenchmarkDecision decision =
        SearchBenchmarkGate.evaluate(BenchmarkRolloutStage.NIGHTLY, List.of(dnaLeak));
    assertEquals(BenchmarkVerdict.FAIL_SAFETY, decision.verdict());
    assertEquals(
        BenchmarkFailureReason.BENCHMARK_SAFETY_BUDGET_EXCEEDED, decision.failureReason());
  }

  @Test
  void emptySamplesProduceBlocked() {
    BenchmarkDecision decision =
        SearchBenchmarkGate.evaluate(BenchmarkRolloutStage.NIGHTLY, List.of());
    assertEquals(BenchmarkVerdict.BLOCKED_ADR_REQUIRED, decision.verdict());
    assertEquals(BenchmarkFailureReason.BENCHMARK_ADR_REQUIRED, decision.failureReason());
  }

  @Test
  void freshnessBreachProducesFailFreshness() {
    BenchmarkSample freshnessBreach = new BenchmarkSample(
        "EXACT_PERSON", "SMALL_HOT_TREE", 800, 1600, 0.95d, 0.95d, 64, 120, 0, 0);
    BenchmarkDecision decision =
        SearchBenchmarkGate.evaluate(
            BenchmarkRolloutStage.PRE_MERGE, List.of(freshnessBreach));
    assertEquals(BenchmarkVerdict.FAIL_FRESHNESS, decision.verdict());
    assertEquals(
        BenchmarkFailureReason.BENCHMARK_FRESHNESS_BUDGET_EXCEEDED, decision.failureReason());
  }

  @Test
  void facetCardinalityBelowFloorProducesFailFacetCardinality() {
    BenchmarkSample lowFacet = new BenchmarkSample(
        "FACET_TREE_FAMILY", "SMALL_HOT_TREE", 800, 1600, 0.95d, 0.95d, 4, 30, 0, 0);
    BenchmarkDecision decision =
        SearchBenchmarkGate.evaluate(BenchmarkRolloutStage.NIGHTLY, List.of(lowFacet));
    assertEquals(BenchmarkVerdict.FAIL_FACET_CARDINALITY, decision.verdict());
    assertEquals(
        BenchmarkFailureReason.BENCHMARK_FACET_CARDINALITY_BELOW_FLOOR,
        decision.failureReason());
  }

  @Test
  void evaluateRequiresNonNullStage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SearchBenchmarkGate.evaluate(null, List.of(passingSample("EXACT_PERSON"))));
  }
}