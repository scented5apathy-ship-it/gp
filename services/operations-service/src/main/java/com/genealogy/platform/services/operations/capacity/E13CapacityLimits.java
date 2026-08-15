package com.genealogy.platform.services.operations.capacity;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E13.3 performance /
 * capacity contract. Mirrors
 * <code>contracts/reliability/performance-capacity-policy.yaml</code>.
 */
public final class E13CapacityLimits {

  public static final Set<String> WORKLOAD_CLASSES = Set.of(
      "browse_tree", "search", "detail_read",
      "write_proposal", "media_upload", "async_job");

  public static final Set<String> HPA_METRICS = Set.of(
      "cpu", "memory", "rps", "lag", "queue_depth", "custom_metric");

  public static final long POSTGRES_MAX = 75L;
  public static final long KAFKA_PRODUCER_MAX = 200L;
  public static final long KAFKA_CONSUMER_MAX = 400L;
  public static final long GRPC_CLIENT_MAX = 500L;
  public static final long REDIS_CLIENT_MAX = 600L;
  public static final long TEMPORAL_WORKER_MAX = 100L;

  public static final double PG_MAX_CONNECTIONS_RATIO = 0.75;
  public static final long PG_REPLICATION_LAG_SECONDS = 30L;
  public static final long PG_LONGEST_QUERY_SECONDS = 60L;
  public static final double PG_DEAD_TUPLES_RATIO = 0.2;

  public static final long KAFKA_MAX_PARTITIONS_PER_TOPIC = 256L;
  public static final long KAFKA_PARTITION_PER_RPS = 1000L;
  public static final long KAFKA_MIN_IN_SYNC_REPLICAS = 2L;
  public static final long KAFKA_LAG_CRITICAL_SECONDS = 30L;
  public static final long KAFKA_LAG_ASYNC_SECONDS = 300L;

  public static final long TEMPORAL_WORKER_PER_WORKFLOW = 50L;
  public static final long TEMPORAL_MAX_CONCURRENT_PER_WORKER = 100L;
  public static final long TEMPORAL_HEARTBEAT_SECONDS = 30L;
  public static final long TEMPORAL_TASK_TIMEOUT_SECONDS = 60L;

  public static final double MAX_P95_REGRESSION_PERCENT = 10.0;
  public static final double MAX_ERROR_RATE_REGRESSION_PERCENT = 25.0;
  public static final double MAX_THROUGHPUT_REGRESSION_PERCENT = 15.0;
  public static final double MAX_BUNDLE_SIZE_REGRESSION_PERCENT = 5.0;
  public static final double SAAS_BURST_MULTIPLIER = 3.0;
  public static final double ON_PREMISE_BURST_MULTIPLIER = 2.0;

  public static final long CWV_LCP_MS = 2500L;
  public static final double CWV_CLS = 0.1;
  public static final long CWV_INP_MS = 200L;
  public static final long CWV_TTFB_MS = 800L;
  public static final long CWV_TTI_MS = 2500L;
  public static final long CWV_TBT_MS = 200L;

  public static final Set<String> SYNTHETIC_DATASET_LOCALES = Set.of(
      "vi-VN", "en-US", "fr-FR", "ar-SA",
      "he-IL", "ja-JP", "zh-CN");

  private E13CapacityLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}