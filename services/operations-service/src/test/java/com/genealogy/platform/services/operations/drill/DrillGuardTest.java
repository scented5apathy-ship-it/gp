package com.genealogy.platform.services.operations.drill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.drill.DrillGuard.Drill;
import com.genealogy.platform.services.operations.drill.DrillGuard.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class DrillGuardTest {

  private Drill canonical(String kind) {
    List<String> allowed = kind.equals("region_loss")
        ? List.of("gp-region-secondary-a", "gp-region-secondary-b")
        : kind.equals("on_premises_failover")
            ? List.of("onprem-customer-secondary")
            : List.of("gp-region-primary", "gp-region-secondary-a");
    int cadence = kind.equals("on_premises_failover") ? 180 : 90;
    return new Drill(kind, cadence, "per_region", false,
        "gp-region-primary", allowed,
        List.of("outbox_relay", "temporal_workflow", "audit_pipeline"),
        "redacted_metrics_only",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 900, 14400);
  }

  @Test
  void allEightKindsAreAccepted() {
    for (String kind : E14DrillLimits.DRILL_KINDS) {
      Outcome out = DrillGuard.validateDrill(canonical(kind));
      assertEquals(DrillGuard.STATE_OK, out.state,
          () -> "kind " + kind + " was " + out.violationCode);
    }
  }

  @Test
  void unknownKindIsRejected() {
    Outcome out = DrillGuard.validateDrill(canonical("made_up"));
    assertEquals(DrillGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_DRILL_KIND"));
  }

  @Test
  void replayLogCaptureModeFullPayloadIsForbidden() {
    Drill d = new Drill("cluster_loss", 90, "per_region", false,
        "gp-region-primary",
        List.of("gp-region-secondary-a"),
        List.of("outbox_relay", "temporal_workflow"),
        "full_payload",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 900, 14400);
    Outcome out = DrillGuard.validateDrill(d);
    assertEquals(DrillGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("REPLAY_LOG_CAPTURE_MODE_FORBIDDEN"));
  }

  @Test
  void regionLossWithoutSecondaryIsRejected() {
    Drill d = new Drill("region_loss", 90, "per_region", false,
        "gp-region-primary",
        List.of("gp-region-primary"),
        List.of("outbox_relay", "temporal_workflow"),
        "redacted_metrics_only",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 900, 14400);
    Outcome out = DrillGuard.validateDrill(d);
    assertEquals(DrillGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("REGION_LOSS_DRILL_HAS_NO_SECONDARY"));
  }

  @Test
  void onPremFailoverWithoutOnPremRegionIsRejected() {
    Drill d = new Drill("on_premises_failover", 180, "per_site", false,
        "gp-region-primary",
        List.of("gp-region-secondary-a"),
        List.of("outbox_relay", "temporal_workflow"),
        "redacted_metrics_only",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 3600, 14400);
    Outcome out = DrillGuard.validateDrill(d);
    assertEquals(DrillGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("ONPREM_FAILOVER_HAS_NO_ONPREM_REGION"));
  }

  @Test
  void productionWideWithoutFlagIsForbidden() {
    Drill d = new Drill("cluster_loss", 90, "production_wide", false,
        "gp-region-primary",
        List.of("gp-region-secondary-a"),
        List.of("outbox_relay", "temporal_workflow"),
        "redacted_metrics_only",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 900, 14400);
    Outcome out = DrillGuard.validateDrill(d);
    assertEquals(DrillGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("PRODUCTION_WIDE_DRILL_REQUIRES_FLAG"));
  }

  @Test
  void productionWideWithFlagIsAccepted() {
    Drill d = new Drill("cluster_loss", 90, "production_wide", true,
        "gp-region-primary",
        List.of("gp-region-secondary-a"),
        List.of("outbox_relay", "temporal_workflow"),
        "redacted_metrics_only",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 900, 14400);
    Outcome out = DrillGuard.validateDrill(d);
    assertEquals(DrillGuard.STATE_OK, out.state);
  }

  @Test
  void reconcileTargetsBelowMinimumIsRejected() {
    Drill d = new Drill("cluster_loss", 90, "per_cluster", false,
        "gp-region-primary",
        List.of("gp-region-secondary-a"),
        List.of("outbox_relay"),
        "redacted_metrics_only",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 900, 14400);
    Outcome out = DrillGuard.validateDrill(d);
    assertEquals(DrillGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("RECONCILE_TARGETS_BELOW_MINIMUM"));
  }

  @Test
  void cadenceOutsideBudgetIsRejected() {
    Drill d = new Drill("cluster_loss", 30, "per_cluster", false,
        "gp-region-primary",
        List.of("gp-region-secondary-a"),
        List.of("outbox_relay", "temporal_workflow"),
        "redacted_metrics_only",
        List.of("drillLog", "reconcileReport", "postmortem",
            "remediation", "signoff"),
        "SEV2", 900, 14400);
    Outcome out = DrillGuard.validateDrill(d);
    assertEquals(DrillGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("CADENCE_OUT_OF_RANGE"));
  }

  @Test
  void clusterLossRpoOverBudgetIsRejected() {
    Drill d = canonical("cluster_loss");
    Drill over = new Drill("cluster_loss", d.cadenceDays, d.blastRadius,
        d.productionWideFlagApproved, d.primaryRegion,
        d.allowedDrRegions, d.reconcileTargets,
        d.replayLogCaptureMode, d.requiredArtifacts, d.severity,
        7200, 14400);
    Outcome out = DrillGuard.validateBudget(over);
    assertEquals(DrillGuard.STATE_OVER_LIMIT, out.state);
  }

  @Test
  void drillTransitionPlannedToInProgressIsAccepted() {
    Outcome out = DrillGuard.validateDrillTransition(
        DrillGuard.STATUS_PLANNED, DrillGuard.STATUS_IN_PROGRESS);
    assertEquals(DrillGuard.STATE_OK, out.state);
  }

  @Test
  void drillTransitionFailedIsTerminal() {
    Outcome ok = DrillGuard.validateDrillTransition(
        DrillGuard.STATUS_IN_PROGRESS, DrillGuard.STATUS_FAILED);
    assertEquals(DrillGuard.STATE_OK, ok.state);
    Outcome bad = DrillGuard.validateDrillTransition(
        DrillGuard.STATUS_FAILED, DrillGuard.STATUS_PLANNED);
    assertFalse(bad.state.equals(DrillGuard.STATE_OK));
  }
}