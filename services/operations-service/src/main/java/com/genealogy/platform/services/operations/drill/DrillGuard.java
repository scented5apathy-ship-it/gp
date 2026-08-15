package com.genealogy.platform.services.operations.drill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates DR drill payloads against
 * the E14.2 invariants. Mirrors
 * <code>contracts/disaster-recovery/drill-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>drillKind is one of 8 closed-set entries;</li>
 *   <li>primaryRegion + allowedDrRegions are bound to the
 *       closed-set of 5 regions;</li>
 *   <li>blastRadius is one of the 8 closed-set entries and
 *       production_wide is forbidden without an explicit
 *       feature flag;</li>
 *   <li>reconcileTargets MUST have at least 2 entries from
 *       the closed-set;</li>
 *   <li>replayLogCaptureMode MUST equal
 *       <code>redacted_metrics_only</code> — any other mode
 *       is forbidden to prevent customer-data leak;</li>
 *   <li>requiredArtifacts MUST cover every mandatory field
 *       (drillLog, reconcileReport, postmortem, remediation,
 *       signoff);</li>
 *   <li>RPO / RTO respect per-kind budget caps;</li>
 *   <li>cadenceDays equals 90 (SaaS) or 180 (on-premise);</li>
 *   <li>regionLoss drill MUST include a non-primary
 *       secondary region;</li>
 *   <li>onPremFailover drill MUST use an on-prem region;</li>
 *   <li>drill state transition respects the 9-status matrix
 *       (terminal: CANCELLED / FAILED / SUPERSEDED).</li>
 * </ul>
 */
public final class DrillGuard {

  public static final String STATE_OK = "OK";
  public static final String STATE_OVER_LIMIT = "OVER_LIMIT";
  public static final String STATE_FORBIDDEN = "FORBIDDEN";
  public static final String STATE_INVALID = "INVALID";

  public static final String STATUS_PLANNED = "PLANNED";
  public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
  public static final String STATUS_RECONCILING = "RECONCILING";
  public static final String STATUS_CANCELLED = "CANCELLED";
  public static final String STATUS_PASSED = "PASSED";
  public static final String STATUS_REMEDIATION_PENDING = "REMEDIATION_PENDING";
  public static final String STATUS_REMEDIATION_DONE = "REMEDIATION_DONE";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_SUPERSEDED = "SUPERSEDED";

  private DrillGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validateDrill(Drill drill) {
    if (drill == null) {
      return new Outcome(STATE_INVALID, "BLANK_DRILL", null, null);
    }
    if (drill.drillKind == null
        || !E14DrillLimits.DRILL_KINDS.contains(drill.drillKind)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_DRILL_KIND",
          null, drill.drillKind);
    }
    if (drill.primaryRegion == null
        || !E14DrillLimits.REGIONS.contains(drill.primaryRegion)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_PRIMARY_REGION",
          Map.of("drillKind", drill.drillKind), drill.primaryRegion);
    }
    if (drill.allowedDrRegions == null || drill.allowedDrRegions.isEmpty()) {
      return new Outcome(STATE_INVALID, "BLANK_ALLOWED_REGIONS",
          Map.of("drillKind", drill.drillKind), null);
    }
    for (String r : drill.allowedDrRegions) {
      if (!E14DrillLimits.REGIONS.contains(r)) {
        return new Outcome(STATE_INVALID, "UNKNOWN_REGION",
            Map.of("drillKind", drill.drillKind), r);
      }
    }
    if (drill.blastRadius == null) {
      return new Outcome(STATE_INVALID, "BLANK_BLAST_RADIUS",
          Map.of("drillKind", drill.drillKind), null);
    }
    if ("production_wide".equals(drill.blastRadius)) {
      if (!drill.productionWideFlagApproved) {
        return new Outcome(STATE_FORBIDDEN,
            "PRODUCTION_WIDE_DRILL_REQUIRES_FLAG",
            Map.of("drillKind", drill.drillKind), null);
      }
      // accepted below after reconcile checks; flag is required + approved
    } else if (!E14DrillLimits.BLAST_RADII.contains(drill.blastRadius)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_BLAST_RADIUS",
          Map.of("drillKind", drill.drillKind), drill.blastRadius);
    }
    if (drill.reconcileTargets == null
        || drill.reconcileTargets.size()
            < E14DrillLimits.RECONCILE_TARGETS_PER_DRILL_MIN) {
      return new Outcome(STATE_OVER_LIMIT,
          "RECONCILE_TARGETS_BELOW_MINIMUM",
          Map.of("min", E14DrillLimits.RECONCILE_TARGETS_PER_DRILL_MIN,
              "actual",
              drill.reconcileTargets == null ? 0
                  : drill.reconcileTargets.size()),
          drill.drillKind);
    }
    for (String t : drill.reconcileTargets) {
      if (!E14DrillLimits.RECONCILE_TARGETS.contains(t)) {
        return new Outcome(STATE_INVALID, "UNKNOWN_RECONCILE_TARGET",
            Map.of("drillKind", drill.drillKind), t);
      }
    }
    if (!"redacted_metrics_only".equals(drill.replayLogCaptureMode)) {
      return new Outcome(STATE_FORBIDDEN,
          "REPLAY_LOG_CAPTURE_MODE_FORBIDDEN",
          Map.of("drillKind", drill.drillKind),
          drill.replayLogCaptureMode);
    }
    if (drill.requiredArtifacts == null
        || !drill.requiredArtifacts.containsAll(
            E14DrillLimits.REQUIRED_ARTIFACT_FIELDS)) {
      return new Outcome(STATE_INVALID, "REQUIRED_ARTIFACTS_MISSING",
          Map.of("drillKind", drill.drillKind),
          drill.requiredArtifacts == null ? ""
              : String.join(",", drill.requiredArtifacts));
    }
    if (drill.severity == null
        || !E14DrillLimits.SEVERITIES.contains(drill.severity)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_SEVERITY",
          Map.of("drillKind", drill.drillKind), drill.severity);
    }
    if (drill.cadenceDays != E14DrillLimits.SAAS_DRILL_CADENCE_DAYS
        && drill.cadenceDays
            != E14DrillLimits.ONPREM_DRILL_CADENCE_DAYS_MAX) {
      return new Outcome(STATE_INVALID, "CADENCE_OUT_OF_RANGE",
          Map.of("drillKind", drill.drillKind),
          String.valueOf(drill.cadenceDays));
    }
    if ("region_loss".equals(drill.drillKind)) {
      boolean hasSecondary = drill.allowedDrRegions.stream()
          .anyMatch(r -> r.startsWith("gp-region-secondary"));
      if (!hasSecondary) {
        return new Outcome(STATE_INVALID,
            "REGION_LOSS_DRILL_HAS_NO_SECONDARY",
            Map.of("drillKind", drill.drillKind), null);
      }
    }
    if ("on_premises_failover".equals(drill.drillKind)) {
      boolean hasOnPrem = drill.allowedDrRegions.stream()
          .anyMatch(r -> r.startsWith("onprem-customer"));
      if (!hasOnPrem) {
        return new Outcome(STATE_INVALID,
            "ONPREM_FAILOVER_HAS_NO_ONPREM_REGION",
            Map.of("drillKind", drill.drillKind), null);
      }
    }
    return new Outcome(STATE_OK, null, null, drill.drillKind);
  }

  public static Outcome validateBudget(Drill drill) {
    if (drill == null || drill.drillKind == null) {
      return new Outcome(STATE_INVALID, "BLANK_DRILL", null, null);
    }
    int rpo = drill.rpoSeconds;
    int rto = drill.rtoSeconds;
    if (rpo <= 0 || rto <= 0) {
      return new Outcome(STATE_INVALID, "RPO_RTO_NOT_POSITIVE",
          Map.of("drillKind", drill.drillKind), null);
    }
    switch (drill.drillKind) {
      case "cluster_loss":
        if (rpo > E14DrillLimits.CLUSTER_LOSS_RPO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RPO_OVER_BUDGET",
              Map.of("max", E14DrillLimits.CLUSTER_LOSS_RPO_SECONDS_MAX,
                  "actual", rpo),
              drill.drillKind);
        }
        break;
      case "region_loss":
        if (rto > E14DrillLimits.REGION_LOSS_RTO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RTO_OVER_BUDGET",
              Map.of("max", E14DrillLimits.REGION_LOSS_RTO_SECONDS_MAX,
                  "actual", rto),
              drill.drillKind);
        }
        break;
      case "dependency_outage":
        if (rto > E14DrillLimits.DEPENDENCY_OUTAGE_RTO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RTO_OVER_BUDGET",
              Map.of("max",
                  E14DrillLimits.DEPENDENCY_OUTAGE_RTO_SECONDS_MAX,
                  "actual", rto),
              drill.drillKind);
        }
        break;
      case "control_plane_failure":
        if (rpo > E14DrillLimits.CONTROL_PLANE_FAILURE_RPO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RPO_OVER_BUDGET",
              Map.of("max",
                  E14DrillLimits.CONTROL_PLANE_FAILURE_RPO_SECONDS_MAX,
                  "actual", rpo),
              drill.drillKind);
        }
        break;
      case "data_corruption":
        if (rto > E14DrillLimits.DATA_CORRUPTION_RTO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RTO_OVER_BUDGET",
              Map.of("max",
                  E14DrillLimits.DATA_CORRUPTION_RTO_SECONDS_MAX,
                  "actual", rto),
              drill.drillKind);
        }
        break;
      case "rpo_breach":
        if (rto > E14DrillLimits.RPO_BREACH_RTO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RTO_OVER_BUDGET",
              Map.of("max", E14DrillLimits.RPO_BREACH_RTO_SECONDS_MAX,
                  "actual", rto),
              drill.drillKind);
        }
        break;
      case "rto_breach":
        if (rto > E14DrillLimits.RPO_BREACH_RTO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RTO_OVER_BUDGET",
              Map.of("max", E14DrillLimits.RPO_BREACH_RTO_SECONDS_MAX,
                  "actual", rto),
              drill.drillKind);
        }
        break;
      case "on_premises_failover":
        if (rpo > E14DrillLimits.ONPREM_FAILOVER_RPO_SECONDS_MAX) {
          return new Outcome(STATE_OVER_LIMIT, "RPO_OVER_BUDGET",
              Map.of("max",
                  E14DrillLimits.ONPREM_FAILOVER_RPO_SECONDS_MAX,
                  "actual", rpo),
              drill.drillKind);
        }
        break;
      default:
        return new Outcome(STATE_INVALID, "UNKNOWN_DRILL_KIND",
            null, drill.drillKind);
    }
    return new Outcome(STATE_OK, null, null, drill.drillKind);
  }

  public static Outcome validateDrillTransition(String from, String to) {
    Set<String> valid = E14DrillLimits.DRILL_STATUSES;
    if (from == null || !valid.contains(from)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_FROM", null, from);
    }
    if (to == null || !valid.contains(to)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_TO", null, to);
    }
    Map<String, Set<String>> allowed = Map.of(
        STATUS_PLANNED, Set.of(STATUS_IN_PROGRESS, STATUS_CANCELLED),
        STATUS_IN_PROGRESS, Set.of(STATUS_RECONCILING, STATUS_FAILED),
        STATUS_RECONCILING, Set.of(STATUS_PASSED, STATUS_FAILED),
        STATUS_CANCELLED, Set.of(),
        STATUS_PASSED, Set.of(STATUS_REMEDIATION_PENDING,
            STATUS_SUPERSEDED),
        STATUS_REMEDIATION_PENDING, Set.of(
            STATUS_REMEDIATION_DONE, STATUS_FAILED),
        STATUS_REMEDIATION_DONE, Set.of(STATUS_SUPERSEDED),
        STATUS_FAILED, Set.of(),
        STATUS_SUPERSEDED, Set.of());
    Set<String> fromAllowed = allowed.get(from);
    if (fromAllowed == null || !fromAllowed.contains(to)) {
      return new Outcome(STATE_INVALID,
          "INVALID_TRANSITION:" + from + "->" + to,
          Map.of("from", from, "to", to), to);
    }
    return new Outcome(STATE_OK, null, null, to);
  }

  public static final class Drill {
    public final String drillKind;
    public final int cadenceDays;
    public final String blastRadius;
    public final boolean productionWideFlagApproved;
    public final String primaryRegion;
    public final List<String> allowedDrRegions;
    public final List<String> reconcileTargets;
    public final String replayLogCaptureMode;
    public final List<String> requiredArtifacts;
    public final String severity;
    public final int rpoSeconds;
    public final int rtoSeconds;

    public Drill(String drillKind, int cadenceDays, String blastRadius,
        boolean productionWideFlagApproved, String primaryRegion,
        List<String> allowedDrRegions, List<String> reconcileTargets,
        String replayLogCaptureMode, List<String> requiredArtifacts,
        String severity, int rpoSeconds, int rtoSeconds) {
      this.drillKind = drillKind;
      this.cadenceDays = cadenceDays;
      this.blastRadius = blastRadius;
      this.productionWideFlagApproved = productionWideFlagApproved;
      this.primaryRegion = primaryRegion;
      this.allowedDrRegions = allowedDrRegions;
      this.reconcileTargets = reconcileTargets;
      this.replayLogCaptureMode = replayLogCaptureMode;
      this.requiredArtifacts = requiredArtifacts;
      this.severity = severity;
      this.rpoSeconds = rpoSeconds;
      this.rtoSeconds = rtoSeconds;
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