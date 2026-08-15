package com.genealogy.platform.services.operations.drill;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E14.2 DR drill
 * contract. Mirrors
 * <code>contracts/disaster-recovery/drill-policy.yaml</code>.
 */
public final class E14DrillLimits {

  public static final Set<String> DRILL_KINDS = Set.of(
      "cluster_loss", "region_loss", "dependency_outage",
      "control_plane_failure", "data_corruption", "rpo_breach",
      "rto_breach", "on_premises_failover");

  public static final Set<String> REGIONS = Set.of(
      "gp-region-primary", "gp-region-secondary-a",
      "gp-region-secondary-b", "onprem-customer-primary",
      "onprem-customer-secondary");

  public static final Set<String> RECONCILE_TARGETS = Set.of(
      "outbox_relay", "kafka_consumer", "temporal_workflow",
      "search_projection", "public_projection", "audit_pipeline",
      "flagsmith_cache");

  public static final Set<String> BLAST_RADII = Set.of(
      "per_pod", "per_service", "per_namespace", "per_cluster",
      "per_region", "per_site", "per_environment", "per_aggregate");

  public static final Set<String> REPLAY_LOG_CAPTURE_MODES = Set.of(
      "redacted_metrics_only");

  public static final Set<String> SEVERITIES = Set.of(
      "SEV1", "SEV2", "SEV3", "SEV4");

  public static final Set<String> DRILL_STATUSES = Set.of(
      "PLANNED", "IN_PROGRESS", "RECONCILING", "CANCELLED",
      "PASSED", "REMEDIATION_PENDING", "REMEDIATION_DONE",
      "FAILED", "SUPERSEDED");

  public static final Set<String> REQUIRED_ARTIFACT_FIELDS = Set.of(
      "drillLog", "reconcileReport", "postmortem",
      "remediation", "signoff");

  public static final int SAAS_DRILL_CADENCE_DAYS = 90;
  public static final int ONPREM_DRILL_CADENCE_DAYS_MAX = 180;
  public static final int CLUSTER_LOSS_RPO_SECONDS_MAX = 900;
  public static final int REGION_LOSS_RTO_SECONDS_MAX = 14400;
  public static final int DEPENDENCY_OUTAGE_RTO_SECONDS_MAX = 14400;
  public static final int CONTROL_PLANE_FAILURE_RPO_SECONDS_MAX = 86400;
  public static final int DATA_CORRUPTION_RTO_SECONDS_MAX = 14400;
  public static final int RPO_BREACH_RTO_SECONDS_MAX = 14400;
  public static final int ONPREM_FAILOVER_RPO_SECONDS_MAX = 3600;
  public static final int DRILL_EVIDENCE_RETENTION_DAYS = 1095;
  public static final int RECONCILE_TARGETS_PER_DRILL_MIN = 2;
  public static final int MAX_PRODUCTION_WIDE_DRILLS_PER_QUARTER = 0;

  private E14DrillLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}