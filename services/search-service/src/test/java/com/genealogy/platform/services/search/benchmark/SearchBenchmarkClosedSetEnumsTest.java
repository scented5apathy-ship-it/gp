package com.genealogy.platform.services.search.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchBenchmarkClosedSetEnumsTest {

  @Test
  void benchmarkVerdictFromWireCoversAllValues() {
    for (BenchmarkVerdict verdict : BenchmarkVerdict.values()) {
      assertEquals(verdict, BenchmarkVerdict.fromWire(verdict.wire()));
    }
    assertEquals(12, BenchmarkVerdict.values().length);
    assertThrows(IllegalArgumentException.class, () -> BenchmarkVerdict.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> BenchmarkVerdict.fromWire("BOGUS"));
  }

  @Test
  void benchmarkEvolutionPathFromWireCoversAllValues() {
    for (BenchmarkEvolutionPath path : BenchmarkEvolutionPath.values()) {
      assertEquals(path, BenchmarkEvolutionPath.fromWire(path.wire()));
    }
    assertEquals(8, BenchmarkEvolutionPath.values().length);
    assertThrows(IllegalArgumentException.class, () -> BenchmarkEvolutionPath.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> BenchmarkEvolutionPath.fromWire("BOGUS"));
  }

  @Test
  void benchmarkRolloutStageFromWireCoversAllValues() {
    for (BenchmarkRolloutStage stage : BenchmarkRolloutStage.values()) {
      assertEquals(stage, BenchmarkRolloutStage.fromWire(stage.wire()));
    }
    assertEquals(5, BenchmarkRolloutStage.values().length);
    assertThrows(IllegalArgumentException.class, () -> BenchmarkRolloutStage.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> BenchmarkRolloutStage.fromWire("BOGUS"));
  }

  @Test
  void benchmarkFailureReasonFromWireCoversAllValues() {
    for (BenchmarkFailureReason reason : BenchmarkFailureReason.values()) {
      assertEquals(reason, BenchmarkFailureReason.fromWire(reason.wire()));
    }
    assertEquals(22, BenchmarkFailureReason.values().length);
    assertThrows(IllegalArgumentException.class, () -> BenchmarkFailureReason.fromWire(null));
    assertThrows(IllegalArgumentException.class, () -> BenchmarkFailureReason.fromWire("BOGUS"));
  }

  @Test
  void numericLimitsPinContractValues() {
    assertEquals(16, BenchmarkLimits.MAX_DATASETS);
    assertEquals(1024, BenchmarkLimits.MAX_QUERIES_PER_DATASET);
    assertEquals(8, BenchmarkLimits.MAX_WORKLOADS);
    assertEquals(5, BenchmarkLimits.MAX_ROLLOUT_STAGES);
    assertEquals(50, BenchmarkLimits.WARMUP_ITERATIONS);
    assertEquals(200, BenchmarkLimits.MEASUREMENT_ITERATIONS);
    assertEquals(60, BenchmarkLimits.COOLDOWN_SECONDS);
    assertEquals(1000, BenchmarkLimits.P95_BUDGET_MILLISECONDS);
    assertEquals(2000, BenchmarkLimits.P99_BUDGET_MILLISECONDS);
    assertEquals(250, BenchmarkLimits.FACET_P95_BUDGET_MILLISECONDS);
    assertEquals(500, BenchmarkLimits.CURSOR_P95_BUDGET_MILLISECONDS);
    assertEquals(60, BenchmarkLimits.FRESHNESS_BUDGET_SECONDS);
    assertEquals(0.85d, BenchmarkLimits.FUZZY_RECALL_FLOOR);
    assertEquals(0.90d, BenchmarkLimits.FUZZY_PRECISION_FLOOR);
    assertEquals(16, BenchmarkLimits.FACET_CARDINALITY_FLOOR);
    assertEquals(0, BenchmarkLimits.SAFETY_BUDGET_VIOLATIONS);
    assertEquals(0, BenchmarkLimits.DNA_BUCKET_LEAKS);
    assertEquals(120, BenchmarkLimits.RUNTIME_TIMEOUT_SECONDS);
    assertEquals(30, BenchmarkLimits.HEARTBEAT_SECONDS);
    assertEquals(64, BenchmarkLimits.CONTRACT_HASH_LENGTH);
    assertEquals(10_000_000L, BenchmarkLimits.DATASET_MAX_ROWS);
    assertEquals(1_000L, BenchmarkLimits.DATASET_MIN_ROWS);
    assertEquals(2_592_000, BenchmarkLimits.RECOMMENDATION_TTL_SECONDS);
  }

  @Test
  void decisionConstructorRejectsPassWithoutPath() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BenchmarkDecision(
                BenchmarkVerdict.PASS, null, null, null));
  }

  @Test
  void decisionConstructorRejectsBlockedWithoutAdrReason() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BenchmarkDecision(
                BenchmarkVerdict.BLOCKED_ADR_REQUIRED,
                null,
                BenchmarkFailureReason.BENCHMARK_SLO_BUDGET_EXCEEDED,
                null));
  }

  @Test
  void sampleRejectsInvalidPercentages() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BenchmarkSample(
                "EXACT_PERSON",
                "SMALL_HOT_TREE",
                100,
                200,
                -0.1d,
                0.95d,
                16,
                30,
                0,
                0));
  }

  @Test
  void sampleRejectsNegativeP95() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BenchmarkSample(
                "EXACT_PERSON",
                "SMALL_HOT_TREE",
                -1,
                0,
                0.95d,
                0.95d,
                16,
                30,
                0,
                0));
  }
}