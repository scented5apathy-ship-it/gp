package com.genealogy.platform.services.operations.resilience;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates resilience / chaos
 * payloads against the E13.4 invariants. Mirrors
 * <code>contracts/reliability/resilience-chaos-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>fault class is one of the closed-set (13 entries);</li>
 *   <li>retry policy respects
 *       <code>maxAttempts &le; 6</code> AND
 *       <code>maxWallSeconds &le; 60</code>;</li>
 *   <li>circuit breaker thresholds stay at
 *       threshold=5, openSeconds=30, halfOpenProbeMax=1;</li>
 *   <li>graceful degradation mode is one of
 *       fail_closed / fail_open / read_only / cached;</li>
 *   <li>idempotency keys MUST be UUIDv7 + baseVersion +
 *       duplicateEventId forbidden;</li>
 *   <li>canary abort reasons are wired to the closed-set
 *       (fiveXxRatioExceeded / p95LatencyRegression /
 *       errorRateSpike / privacyFindingDetected);</li>
 *   <li>scenario lifecycle is bound to the scenarioStateMatrix
 *       status set.</li>
 * </ul>
 */
public final class ResilienceGuard {

  public static final String SCENARIO_SCHEDULED = "SCHEDULED";
  public static final String SCENARIO_INJECTING = "INJECTING";
  public static final String SCENARIO_OBSERVING = "OBSERVING";
  public static final String SCENARIO_RECOVERING = "RECOVERING";
  public static final String SCENARIO_CANCELLED = "CANCELLED";
  public static final String SCENARIO_PASSED = "PASSED";
  public static final String SCENARIO_FAILED = "FAILED";
  public static final String SCENARIO_ABORTED = "ABORTED";

  public static final String STATE_OK = "OK";
  public static final String STATE_OVER_LIMIT = "OVER_LIMIT";
  public static final String STATE_FORBIDDEN = "FORBIDDEN";
  public static final String STATE_INVALID = "INVALID";

  private ResilienceGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validateFaultClass(String name) {
    if (name == null || !E13ResilienceLimits.FAULT_CLASSES.contains(name)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_FAULT", null, name);
    }
    return new Outcome(STATE_OK, null, null, name);
  }

  public static Outcome validateRetryPolicy(RetryPolicy policy) {
    if (policy == null) {
      return new Outcome(STATE_INVALID, "BLANK_POLICY", null, null);
    }
    if (!E13ResilienceLimits.RETRY_POLICIES.contains(policy.name)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_POLICY", null, policy.name);
    }
    if (policy.maxAttempts < 1
        || policy.maxAttempts > E13ResilienceLimits.MAX_RETRY_ATTEMPTS) {
      return new Outcome(STATE_OVER_LIMIT,
          "MAX_ATTEMPTS_OUT_OF_RANGE",
          Map.of("max", E13ResilienceLimits.MAX_RETRY_ATTEMPTS,
              "actual", policy.maxAttempts),
          String.valueOf(policy.maxAttempts));
    }
    if (policy.maxWallSeconds < 0
        || policy.maxWallSeconds > E13ResilienceLimits.MAX_RETRY_BUDGET_SECONDS) {
      return new Outcome(STATE_OVER_LIMIT,
          "MAX_WALL_OUT_OF_RANGE",
          Map.of("max", E13ResilienceLimits.MAX_RETRY_BUDGET_SECONDS,
              "actual", policy.maxWallSeconds),
          String.valueOf(policy.maxWallSeconds));
    }
    return new Outcome(STATE_OK, null, null, policy.name);
  }

  public static Outcome validateCircuitBreaker(CircuitBreaker cb) {
    if (cb == null) {
      return new Outcome(STATE_INVALID, "BLANK_CB", null, null);
    }
    if (cb.threshold != E13ResilienceLimits.CIRCUIT_BREAKER_THRESHOLD) {
      return new Outcome(STATE_OVER_LIMIT,
          "CB_THRESHOLD_MISMATCH",
          Map.of("expected",
              E13ResilienceLimits.CIRCUIT_BREAKER_THRESHOLD,
              "actual", cb.threshold),
          String.valueOf(cb.threshold));
    }
    if (cb.openSeconds != E13ResilienceLimits.CIRCUIT_BREAKER_OPEN_SECONDS) {
      return new Outcome(STATE_OVER_LIMIT,
          "CB_OPEN_SECONDS_MISMATCH",
          Map.of("expected",
              E13ResilienceLimits.CIRCUIT_BREAKER_OPEN_SECONDS,
              "actual", cb.openSeconds),
          String.valueOf(cb.openSeconds));
    }
    if (cb.halfOpenProbeMax != E13ResilienceLimits.HALF_OPEN_PROBE_MAX) {
      return new Outcome(STATE_OVER_LIMIT,
          "CB_HALF_OPEN_PROBE_MISMATCH",
          Map.of("expected",
              E13ResilienceLimits.HALF_OPEN_PROBE_MAX,
              "actual", cb.halfOpenProbeMax),
          String.valueOf(cb.halfOpenProbeMax));
    }
    if (cb.minimumCalls < E13ResilienceLimits.CIRCUIT_BREAKER_MINIMUM_CALLS) {
      return new Outcome(STATE_OVER_LIMIT,
          "CB_MIN_CALLS_BELOW_LIMIT",
          Map.of("expected",
              E13ResilienceLimits.CIRCUIT_BREAKER_MINIMUM_CALLS,
              "actual", cb.minimumCalls),
          String.valueOf(cb.minimumCalls));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateIdempotencyKey(IdempotencyKey key) {
    if (key == null) {
      return new Outcome(STATE_INVALID, "BLANK_KEY", null, null);
    }
    if (!"UUIDv7".equals(key.format)) {
      return new Outcome(STATE_INVALID, "KEY_FORMAT_NOT_UUIDV7",
          null, key.format);
    }
    if (!key.baseVersionPresent) {
      return new Outcome(STATE_INVALID, "BASE_VERSION_MISSING", null, null);
    }
    if (key.seenTwice) {
      return new Outcome(STATE_FORBIDDEN,
          "DUPLICATE_SIDE_EFFECT", null, key.eventId);
    }
    if (key.duplicateEventId) {
      return new Outcome(STATE_FORBIDDEN,
          "DUPLICATE_EVENT_ID", null, key.eventId);
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateCanaryAbort(String reason) {
    if (reason == null
        || !E13ResilienceLimits.CANARY_ABORT_REASONS.contains(reason)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_ABORT_REASON",
          null, reason);
    }
    return new Outcome(STATE_OK, null, null, reason);
  }

  public static Outcome validateDegradationMode(String dependency,
      String mode) {
    if (dependency == null
        || !E13ResilienceLimits.CRITICAL_DEPENDENCIES.contains(dependency)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_DEPENDENCY",
          null, dependency);
    }
    if (mode == null
        || !E13ResilienceLimits.DEGRADATION_MODES.contains(mode)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_MODE", null, mode);
    }
    return new Outcome(STATE_OK, null, null, dependency);
  }

  public static Outcome validateScenarioTransition(String from, String to) {
    Set<String> validStatuses = Set.of(
        SCENARIO_SCHEDULED, SCENARIO_INJECTING, SCENARIO_OBSERVING,
        SCENARIO_RECOVERING, SCENARIO_CANCELLED, SCENARIO_PASSED,
        SCENARIO_FAILED, SCENARIO_ABORTED);
    if (from == null || !validStatuses.contains(from)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_FROM", null, from);
    }
    if (to == null || !validStatuses.contains(to)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_TO", null, to);
    }
    Map<String, Set<String>> allowed = Map.of(
        SCENARIO_SCHEDULED,
            Set.of(SCENARIO_INJECTING, SCENARIO_CANCELLED, SCENARIO_ABORTED),
        SCENARIO_INJECTING,
            Set.of(SCENARIO_OBSERVING, SCENARIO_FAILED, SCENARIO_ABORTED),
        SCENARIO_OBSERVING,
            Set.of(SCENARIO_RECOVERING, SCENARIO_FAILED, SCENARIO_ABORTED),
        SCENARIO_RECOVERING,
            Set.of(SCENARIO_PASSED, SCENARIO_FAILED, SCENARIO_ABORTED),
        SCENARIO_CANCELLED, Set.of(),
        SCENARIO_PASSED, Set.of(),
        SCENARIO_FAILED, Set.of(),
        SCENARIO_ABORTED, Set.of());
    Set<String> fromAllowed = allowed.get(from);
    if (fromAllowed == null || !fromAllowed.contains(to)) {
      return new Outcome(STATE_INVALID,
          "INVALID_TRANSITION:" + from + "->" + to,
          Map.of("from", from, "to", to),
          to);
    }
    return new Outcome(STATE_OK, null, null, to);
  }

  public static final class RetryPolicy {
    public final String name;
    public final int maxAttempts;
    public final int maxWallSeconds;
    public final boolean jitter;

    public RetryPolicy(String name, int maxAttempts, int maxWallSeconds,
        boolean jitter) {
      this.name = name;
      this.maxAttempts = maxAttempts;
      this.maxWallSeconds = maxWallSeconds;
      this.jitter = jitter;
    }
  }

  public static final class CircuitBreaker {
    public final int threshold;
    public final int openSeconds;
    public final int rollingWindowSeconds;
    public final int halfOpenProbeMax;
    public final int minimumCalls;

    public CircuitBreaker(int threshold, int openSeconds,
        int rollingWindowSeconds, int halfOpenProbeMax,
        int minimumCalls) {
      this.threshold = threshold;
      this.openSeconds = openSeconds;
      this.rollingWindowSeconds = rollingWindowSeconds;
      this.halfOpenProbeMax = halfOpenProbeMax;
      this.minimumCalls = minimumCalls;
    }
  }

  public static final class IdempotencyKey {
    public final String format;
    public final boolean baseVersionPresent;
    public final boolean seenTwice;
    public final boolean duplicateEventId;
    public final String eventId;

    public IdempotencyKey(String format, boolean baseVersionPresent,
        boolean seenTwice, boolean duplicateEventId, String eventId) {
      this.format = format;
      this.baseVersionPresent = baseVersionPresent;
      this.seenTwice = seenTwice;
      this.duplicateEventId = duplicateEventId;
      this.eventId = eventId;
    }
  }

  public static final class Outcome {
    public final String state;
    public final String violationCode;
    public final Map<String, ?> context;
    public final String offendingValue;

    public Outcome(String state, String violationCode,
        Map<String, ?> context, String offendingValue) {
      this.state = state;
      this.violationCode = violationCode;
      this.context = context == null ? new LinkedHashMap<>() : context;
      this.offendingValue = offendingValue;
    }
  }
}