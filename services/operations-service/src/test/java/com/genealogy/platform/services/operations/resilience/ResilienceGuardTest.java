package com.genealogy.platform.services.operations.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.resilience.ResilienceGuard.CircuitBreaker;
import com.genealogy.platform.services.operations.resilience.ResilienceGuard.IdempotencyKey;
import com.genealogy.platform.services.operations.resilience.ResilienceGuard.Outcome;
import com.genealogy.platform.services.operations.resilience.ResilienceGuard.RetryPolicy;
import org.junit.jupiter.api.Test;

class ResilienceGuardTest {

  @Test
  void unknownFaultClassIsRejected() {
    Outcome out = ResilienceGuard.validateFaultClass("made_up");
    assertEquals(ResilienceGuard.STATE_INVALID, out.state);
  }

  @Test
  void knownFaultClassIsAccepted() {
    Outcome out = ResilienceGuard.validateFaultClass("pod_kill");
    assertEquals(ResilienceGuard.STATE_OK, out.state);
  }

  @Test
  void retryPolicyRespectingBudgetIsAccepted() {
    Outcome out = ResilienceGuard.validateRetryPolicy(
        new RetryPolicy("exponential", 5, 30, false));
    assertEquals(ResilienceGuard.STATE_OK, out.state);
  }

  @Test
  void retryPolicyExceedingBudgetIsRejected() {
    Outcome out = ResilienceGuard.validateRetryPolicy(
        new RetryPolicy("exponential", 10, 120, false));
    assertEquals(ResilienceGuard.STATE_OVER_LIMIT, out.state);
  }

  @Test
  void manualRetryPolicyIsRejected() {
    Outcome out = ResilienceGuard.validateRetryPolicy(
        new RetryPolicy("manual", 3, 30, false));
    assertEquals(ResilienceGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_POLICY"));
  }

  @Test
  void circuitBreakerDefaultsAreEnforced() {
    Outcome out = ResilienceGuard.validateCircuitBreaker(
        new CircuitBreaker(5, 30, 60, 1, 10));
    assertEquals(ResilienceGuard.STATE_OK, out.state);
  }

  @Test
  void circuitBreakerWrongThresholdIsRejected() {
    Outcome out = ResilienceGuard.validateCircuitBreaker(
        new CircuitBreaker(3, 30, 60, 1, 10));
    assertEquals(ResilienceGuard.STATE_OVER_LIMIT, out.state);
  }

  @Test
  void circuitBreakerWrongOpenSecondsIsRejected() {
    Outcome out = ResilienceGuard.validateCircuitBreaker(
        new CircuitBreaker(5, 60, 60, 1, 10));
    assertEquals(ResilienceGuard.STATE_OVER_LIMIT, out.state);
  }

  @Test
  void idempotencyKeyUuidV7WithBaseVersionIsAccepted() {
    Outcome out = ResilienceGuard.validateIdempotencyKey(
        new IdempotencyKey("UUIDv7", true, false, false, "evt-1"));
    assertEquals(ResilienceGuard.STATE_OK, out.state);
  }

  @Test
  void idempotencyKeyUuidV4IsRejected() {
    Outcome out = ResilienceGuard.validateIdempotencyKey(
        new IdempotencyKey("UUIDv4", true, false, false, "evt-1"));
    assertEquals(ResilienceGuard.STATE_INVALID, out.state);
  }

  @Test
  void idempotencyKeyMissingBaseVersionIsRejected() {
    Outcome out = ResilienceGuard.validateIdempotencyKey(
        new IdempotencyKey("UUIDv7", false, false, false, "evt-1"));
    assertEquals(ResilienceGuard.STATE_INVALID, out.state);
  }

  @Test
  void duplicateSideEffectIsForbidden() {
    Outcome out = ResilienceGuard.validateIdempotencyKey(
        new IdempotencyKey("UUIDv7", true, true, false, "evt-1"));
    assertEquals(ResilienceGuard.STATE_FORBIDDEN, out.state);
  }

  @Test
  void duplicateEventIdIsForbidden() {
    Outcome out = ResilienceGuard.validateIdempotencyKey(
        new IdempotencyKey("UUIDv7", true, false, true, "evt-1"));
    assertEquals(ResilienceGuard.STATE_FORBIDDEN, out.state);
  }

  @Test
  void knownCanaryAbortReasonIsAccepted() {
    Outcome out = ResilienceGuard.validateCanaryAbort(
        "fiveXxRatioExceeded");
    assertEquals(ResilienceGuard.STATE_OK, out.state);
  }

  @Test
  void unknownCanaryAbortReasonIsRejected() {
    Outcome out = ResilienceGuard.validateCanaryAbort(
        "made_up_reason");
    assertEquals(ResilienceGuard.STATE_INVALID, out.state);
  }

  @Test
  void knownDegradationCellIsAccepted() {
    Outcome out = ResilienceGuard.validateDegradationMode("postgres",
        "read_only");
    assertEquals(ResilienceGuard.STATE_OK, out.state);
  }

  @Test
  void unknownDegradationModeIsRejected() {
    Outcome out = ResilienceGuard.validateDegradationMode("postgres",
        "panic");
    assertEquals(ResilienceGuard.STATE_INVALID, out.state);
  }

  @Test
  void validScenarioTransitionIsAccepted() {
    Outcome out = ResilienceGuard.validateScenarioTransition(
        ResilienceGuard.SCENARIO_SCHEDULED,
        ResilienceGuard.SCENARIO_INJECTING);
    assertEquals(ResilienceGuard.STATE_OK, out.state);
  }

  @Test
  void invalidScenarioTransitionIsRejected() {
    Outcome out = ResilienceGuard.validateScenarioTransition(
        ResilienceGuard.SCENARIO_PASSED,
        ResilienceGuard.SCENARIO_INJECTING);
    assertEquals(ResilienceGuard.STATE_INVALID, out.state);
    assertFalse(out.violationCode.contains("UNKNOWN"));
    assertTrue(out.violationCode.contains("INVALID_TRANSITION"));
  }
}