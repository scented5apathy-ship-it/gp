package com.genealogy.platform.services.operations.capacity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure orchestrator that validates capacity / HPA / benchmark
 * payloads against the E13.3 invariants. Mirrors
 * <code>contracts/reliability/performance-capacity-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>workload class is one of the closed-set
 *       <code>browse_tree / search / detail_read / write_proposal /
 *       media_upload / async_job</code>;</li>
 *   <li>HPA metric is one of the closed-set
 *       <code>cpu / memory / rps / lag / queue_depth /
 *       custom_metric</code> (manual scaling forbidden);</li>
 *   <li>connection pool sizes stay below the ceiling
 *       (postgres 75, kafka producer 200, kafka consumer 400,
 *       grpc 500, redis 600, temporal 100);</li>
 *   <li>postgres thresholds respect the cap ratios;</li>
 *   <li>Kafka partition count is within
 *       <code>1 partition / 1000 RPS</code> and never exceeds
 *       256 partitions per topic;</li>
 *   <li>Temporal worker pool respects
 *       <code>1 worker / 50 concurrent workflows</code> and
 *       the per-worker concurrent ceiling of 100;</li>
 *   <li>benchmark regression detection (&gt; 10 % p95 / &gt; 25 %
 *       errorRate / &gt; 15 % throughput / &gt; 5 % bundle)
 *       MUST trip an abort;</li>
 *   <li>synthetic dataset locale MUST be in the closed-set
 *       (vi-VN, en-US, fr-FR, ar-SA, he-IL, ja-JP, zh-CN);</li>
 *   <li>synthetic dataset MUST carry the
 *       <code>SYNTHETIC_ONLY</code> marker and never embed
 *       raw PII / DNA.</li>
 * </ul>
 */
public final class CapacityGuard {

  public static final String STATE_OK = "OK";
  public static final String STATE_OVER_LIMIT = "OVER_LIMIT";
  public static final String STATE_REGRESSION = "REGRESSION";
  public static final String STATE_INVALID = "INVALID";

  public static final String SCALE_UP = "SCALING_UP";
  public static final String SCALE_DOWN = "SCALING_DOWN";
  public static final String SCALE_STEADY = "STEADY";
  public static final String SCALE_SATURATED = "SATURATED";
  public static final String SCALE_DISABLED = "DISABLED";

  private CapacityGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validateWorkloadClass(String name) {
    if (name == null || !E13CapacityLimits.WORKLOAD_CLASSES.contains(name)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_WORKLOAD", null, name);
    }
    return new Outcome(STATE_OK, null, null, name);
  }

  public static Outcome validateHpaMetric(String metric) {
    if (metric == null || !E13CapacityLimits.HPA_METRICS.contains(metric)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_HPA_METRIC", null, metric);
    }
    return new Outcome(STATE_OK, null, null, metric);
  }

  public static Outcome validateConnectionPool(ConnectionPoolSnapshot snap) {
    if (snap == null) {
      return new Outcome(STATE_INVALID, "BLANK_POOL", null, null);
    }
    Map<String, Long> violations = new LinkedHashMap<>();
    if (snap.postgres > E13CapacityLimits.POSTGRES_MAX) {
      violations.put("postgres", snap.postgres);
    }
    if (snap.kafkaProducer > E13CapacityLimits.KAFKA_PRODUCER_MAX) {
      violations.put("kafkaProducer", snap.kafkaProducer);
    }
    if (snap.kafkaConsumer > E13CapacityLimits.KAFKA_CONSUMER_MAX) {
      violations.put("kafkaConsumer", snap.kafkaConsumer);
    }
    if (snap.grpcClient > E13CapacityLimits.GRPC_CLIENT_MAX) {
      violations.put("grpcClient", snap.grpcClient);
    }
    if (snap.redisClient > E13CapacityLimits.REDIS_CLIENT_MAX) {
      violations.put("redisClient", snap.redisClient);
    }
    if (snap.temporalWorker > E13CapacityLimits.TEMPORAL_WORKER_MAX) {
      violations.put("temporalWorker", snap.temporalWorker);
    }
    if (!violations.isEmpty()) {
      return new Outcome(STATE_OVER_LIMIT,
          "POOL_OVER_LIMIT:" + violations.keySet(),
          violations, null);
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateKafkaPartitions(long partitionCount,
      long sustainedRps) {
    long required = (sustainedRps + E13CapacityLimits.KAFKA_PARTITION_PER_RPS
        - 1) / E13CapacityLimits.KAFKA_PARTITION_PER_RPS;
    if (partitionCount > E13CapacityLimits.KAFKA_MAX_PARTITIONS_PER_TOPIC) {
      return new Outcome(STATE_OVER_LIMIT,
          "KAFKA_PARTITIONS_OVER_CAP",
          Map.of("partitions", partitionCount, "cap",
              E13CapacityLimits.KAFKA_MAX_PARTITIONS_PER_TOPIC),
          String.valueOf(partitionCount));
    }
    if (partitionCount < required) {
      return new Outcome(STATE_OVER_LIMIT,
          "KAFKA_PARTITIONS_UNDER_REQUIRED",
          Map.of("required", required, "actual", partitionCount),
          String.valueOf(partitionCount));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateTemporalWorkers(long workerCount,
      long concurrentWorkflows) {
    long required = (concurrentWorkflows + E13CapacityLimits.TEMPORAL_WORKER_PER_WORKFLOW
        - 1) / E13CapacityLimits.TEMPORAL_WORKER_PER_WORKFLOW;
    if (workerCount < required) {
      return new Outcome(STATE_OVER_LIMIT,
          "TEMPORAL_WORKERS_UNDER_REQUIRED",
          Map.of("required", required, "actual", workerCount),
          String.valueOf(workerCount));
    }
    if (workerCount > E13CapacityLimits.TEMPORAL_WORKER_MAX) {
      return new Outcome(STATE_OVER_LIMIT,
          "TEMPORAL_WORKER_OVER_MAX",
          Map.of("workerMax", E13CapacityLimits.TEMPORAL_WORKER_MAX,
              "actual", workerCount),
          String.valueOf(workerCount));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static boolean blocksCanary(RegressionSnapshot snap) {
    if (snap == null) {
      return false;
    }
    return snap.p95RegressionPercent
        > E13CapacityLimits.MAX_P95_REGRESSION_PERCENT
        || snap.errorRateRegressionPercent
            > E13CapacityLimits.MAX_ERROR_RATE_REGRESSION_PERCENT
        || snap.throughputRegressionPercent
            > E13CapacityLimits.MAX_THROUGHPUT_REGRESSION_PERCENT
        || snap.bundleSizeRegressionPercent
            > E13CapacityLimits.MAX_BUNDLE_SIZE_REGRESSION_PERCENT;
  }

  public static Outcome validateSyntheticDataset(
      String locale, boolean syntheticMarkerPresent,
      boolean containsRealPii, boolean containsRealDna) {
    if (locale == null
        || !E13CapacityLimits.SYNTHETIC_DATASET_LOCALES.contains(locale)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_LOCALE", null, locale);
    }
    if (!syntheticMarkerPresent) {
      return new Outcome(STATE_INVALID,
          "MISSING_SYNTHETIC_ONLY_MARKER", null, locale);
    }
    if (containsRealPii) {
      return new Outcome(STATE_INVALID, "CONTAINS_REAL_PII", null, locale);
    }
    if (containsRealDna) {
      return new Outcome(STATE_INVALID, "CONTAINS_REAL_DNA", null, locale);
    }
    return new Outcome(STATE_OK, null, null, locale);
  }

  public static Outcome validateCwv(CwvSnapshot snap) {
    if (snap == null) {
      return new Outcome(STATE_INVALID, "BLANK_CWV", null, null);
    }
    Map<String, Double> violations = new LinkedHashMap<>();
    if (snap.lcpMs > E13CapacityLimits.CWV_LCP_MS) {
      violations.put("lcp", (double) snap.lcpMs);
    }
    if (snap.cls > E13CapacityLimits.CWV_CLS) {
      violations.put("cls", snap.cls);
    }
    if (snap.inpMs > E13CapacityLimits.CWV_INP_MS) {
      violations.put("inp", (double) snap.inpMs);
    }
    if (snap.ttfbMs > E13CapacityLimits.CWV_TTFB_MS) {
      violations.put("ttfb", (double) snap.ttfbMs);
    }
    if (snap.ttiMs > E13CapacityLimits.CWV_TTI_MS) {
      violations.put("tti", (double) snap.ttiMs);
    }
    if (snap.tbtMs > E13CapacityLimits.CWV_TBT_MS) {
      violations.put("tbt", (double) snap.tbtMs);
    }
    if (!violations.isEmpty()) {
      return new Outcome(STATE_REGRESSION,
          "CWV_OVER_BUDGET:" + violations.keySet(),
          violations, null);
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static final class ConnectionPoolSnapshot {
    public final long postgres;
    public final long kafkaProducer;
    public final long kafkaConsumer;
    public final long grpcClient;
    public final long redisClient;
    public final long temporalWorker;

    public ConnectionPoolSnapshot(long postgres, long kafkaProducer,
        long kafkaConsumer, long grpcClient, long redisClient,
        long temporalWorker) {
      this.postgres = postgres;
      this.kafkaProducer = kafkaProducer;
      this.kafkaConsumer = kafkaConsumer;
      this.grpcClient = grpcClient;
      this.redisClient = redisClient;
      this.temporalWorker = temporalWorker;
    }
  }

  public static final class RegressionSnapshot {
    public final double p95RegressionPercent;
    public final double errorRateRegressionPercent;
    public final double throughputRegressionPercent;
    public final double bundleSizeRegressionPercent;

    public RegressionSnapshot(double p95RegressionPercent,
        double errorRateRegressionPercent, double throughputRegressionPercent,
        double bundleSizeRegressionPercent) {
      this.p95RegressionPercent = p95RegressionPercent;
      this.errorRateRegressionPercent = errorRateRegressionPercent;
      this.throughputRegressionPercent = throughputRegressionPercent;
      this.bundleSizeRegressionPercent = bundleSizeRegressionPercent;
    }
  }

  public static final class CwvSnapshot {
    public final long lcpMs;
    public final double cls;
    public final long inpMs;
    public final long ttfbMs;
    public final long ttiMs;
    public final long tbtMs;

    public CwvSnapshot(long lcpMs, double cls, long inpMs,
        long ttfbMs, long ttiMs, long tbtMs) {
      this.lcpMs = lcpMs;
      this.cls = cls;
      this.inpMs = inpMs;
      this.ttfbMs = ttfbMs;
      this.ttiMs = ttiMs;
      this.tbtMs = tbtMs;
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