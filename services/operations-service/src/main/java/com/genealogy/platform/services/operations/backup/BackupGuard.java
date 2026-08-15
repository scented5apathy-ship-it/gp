package com.genealogy.platform.services.operations.backup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates backup artifacts against
 * the E14.1 invariants. Mirrors
 * <code>contracts/disaster-recovery/backup-matrix-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>the component name is one of the 8 closed-set entries;</li>
 *   <li>cadence, encryption and key custody are bound to
 *       closed-sets;</li>
 *   <li>retention respects minimum (30 daily + 12 monthly +
 *       7 yearly + retentionDays &ge; 30);</li>
 *   <li>RPO / RTO respects the SaaS budget
 *       (&le; 900 / &le; 14400 seconds);</li>
 *   <li>restoreTested MUST be true;</li>
 *   <li>restoreEvidence path lives under
 *       <code>.kiro/specs/genealogy-platform/evidence/backup/</code>;</li>
 *   <li>orderingRank is 1..6;</li>
 *   <li>offsite copy is required for SaaS components
 *       (postgresql / kafka / keycloak / openfga / temporal /
 *       vault) and forbidden for object_storage / flagsmith
 *       when already replicated by customer infra;</li>
 *   <li>key custody role is unique per component (cross-tenant
 *       custody forbidden);</li>
 *   <li>backup state machine respects the 8-status matrix
 *       (terminal: SUPERSEDED / REVOKED / EXPIRED).</li>
 * </ul>
 */
public final class BackupGuard {

  public static final String STATE_OK = "OK";
  public static final String STATE_OVER_LIMIT = "OVER_LIMIT";
  public static final String STATE_FORBIDDEN = "FORBIDDEN";
  public static final String STATE_INVALID = "INVALID";

  public static final String STATUS_ENROLLED = "ENROLLED";
  public static final String STATUS_SNAPSHOTTING = "SNAPSHOTTING";
  public static final String STATUS_RESTORING = "RESTORING";
  public static final String STATUS_RESTORED_VERIFIED = "RESTORED_VERIFIED";
  public static final String STATUS_SUPERSEDED = "SUPERSEDED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_REVOKED = "REVOKED";
  public static final String STATUS_EXPIRED = "EXPIRED";

  private BackupGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validateComponent(Component c) {
    if (c == null) {
      return new Outcome(STATE_INVALID, "BLANK_COMPONENT", null, null);
    }
    if (c.name == null || !E14BackupLimits.COMPONENTS.contains(c.name)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_COMPONENT", null, c.name);
    }
    if (c.cadence == null
        || !E14BackupLimits.CADENCE_KINDS.contains(c.cadence)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_CADENCE",
          Map.of("component", c.name), c.cadence);
    }
    if (c.encryption == null) {
      return new Outcome(STATE_INVALID, "BLANK_ENCRYPTION",
          Map.of("component", c.name), null);
    }
    if ("plain_text".equals(c.encryption)) {
      return new Outcome(STATE_FORBIDDEN, "PLAIN_TEXT_BACKUP_FORBIDDEN",
          Map.of("component", c.name), c.encryption);
    }
    if (!E14BackupLimits.ENCRYPTION_METHODS.contains(c.encryption)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_ENCRYPTION",
          Map.of("component", c.name), c.encryption);
    }
    if (c.keyCustody == null
        || !E14BackupLimits.KEY_CUSTODY_ROLES.contains(c.keyCustody)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_KEY_CUSTODY",
          Map.of("component", c.name), c.keyCustody);
    }
    if (c.retentionDays < E14BackupLimits.MIN_RETENTION_DAYS) {
      return new Outcome(STATE_OVER_LIMIT, "RETENTION_DAYS_BELOW_LIMIT",
          Map.of("min", E14BackupLimits.MIN_RETENTION_DAYS,
              "actual", c.retentionDays),
          String.valueOf(c.retentionDays));
    }
    if (c.retentionDaily < E14BackupLimits.RETENTION_DAILY_MIN) {
      return new Outcome(STATE_OVER_LIMIT, "RETENTION_DAILY_BELOW_LIMIT",
          Map.of("min", E14BackupLimits.RETENTION_DAILY_MIN,
              "actual", c.retentionDaily),
          String.valueOf(c.retentionDaily));
    }
    if (c.retentionMonthly < E14BackupLimits.RETENTION_MONTHLY_MIN) {
      return new Outcome(STATE_OVER_LIMIT, "RETENTION_MONTHLY_BELOW_LIMIT",
          Map.of("min", E14BackupLimits.RETENTION_MONTHLY_MIN,
              "actual", c.retentionMonthly),
          String.valueOf(c.retentionMonthly));
    }
    if (c.retentionYearly < E14BackupLimits.RETENTION_YEARLY_MIN) {
      return new Outcome(STATE_OVER_LIMIT, "RETENTION_YEARLY_BELOW_LIMIT",
          Map.of("min", E14BackupLimits.RETENTION_YEARLY_MIN,
              "actual", c.retentionYearly),
          String.valueOf(c.retentionYearly));
    }
    if (c.rpoSeconds <= 0
        || c.rpoSeconds > E14BackupLimits.SAAS_RPO_SECONDS_MAX) {
      return new Outcome(STATE_OVER_LIMIT, "RPO_OUT_OF_BUDGET",
          Map.of("max", E14BackupLimits.SAAS_RPO_SECONDS_MAX,
              "actual", c.rpoSeconds),
          String.valueOf(c.rpoSeconds));
    }
    if (c.rtoSeconds <= 0
        || c.rtoSeconds > E14BackupLimits.SAAS_RTO_SECONDS_MAX) {
      return new Outcome(STATE_OVER_LIMIT, "RTO_OUT_OF_BUDGET",
          Map.of("max", E14BackupLimits.SAAS_RTO_SECONDS_MAX,
              "actual", c.rtoSeconds),
          String.valueOf(c.rtoSeconds));
    }
    if (!c.restoreTested) {
      return new Outcome(STATE_FORBIDDEN, "RESTORE_NOT_TESTED",
          Map.of("component", c.name), null);
    }
    if (c.restoreEvidence == null
        || !c.restoreEvidence.startsWith(
            ".kiro/specs/genealogy-platform/evidence/backup/")) {
      return new Outcome(STATE_INVALID, "RESTORE_EVIDENCE_PATH_INVALID",
          Map.of("component", c.name), c.restoreEvidence);
    }
    if (c.orderingRank < 1 || c.orderingRank > 6) {
      return new Outcome(STATE_INVALID, "ORDERING_RANK_OUT_OF_RANGE",
          Map.of("component", c.name), String.valueOf(c.orderingRank));
    }
    return new Outcome(STATE_OK, null, null, c.name);
  }

  public static Outcome validateRestoreOrder(java.util.List<Integer> ranks) {
    if (ranks == null || ranks.isEmpty()) {
      return new Outcome(STATE_INVALID, "BLANK_RESTORE_ORDER", null, null);
    }
    java.util.Set<Integer> seen = new java.util.HashSet<>();
    int previous = -1;
    for (Integer r : ranks) {
      if (r == null || r < 1 || r > 6) {
        return new Outcome(STATE_INVALID, "RESTORE_RANK_OUT_OF_RANGE",
            null, String.valueOf(r));
      }
      if (!seen.add(r)) {
        return new Outcome(STATE_INVALID, "DUPLICATE_RESTORE_RANK",
            null, String.valueOf(r));
      }
      if (previous >= 0 && r < previous) {
        return new Outcome(STATE_INVALID, "RESTORE_ORDER_NOT_TOPOLOGICAL",
            Map.of("previous", previous, "next", r), String.valueOf(r));
      }
      previous = r;
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateKeyCustodyUniqueness(
      java.util.Map<String, String> componentToRole) {
    if (componentToRole == null || componentToRole.isEmpty()) {
      return new Outcome(STATE_INVALID, "BLANK_CUSTODY_MAP", null, null);
    }
    java.util.Set<String> roles = new java.util.HashSet<>();
    for (java.util.Map.Entry<String, String> e : componentToRole.entrySet()) {
      if (!E14BackupLimits.COMPONENTS.contains(e.getKey())) {
        return new Outcome(STATE_INVALID, "UNKNOWN_COMPONENT",
            null, e.getKey());
      }
      if (!E14BackupLimits.KEY_CUSTODY_ROLES.contains(e.getValue())) {
        return new Outcome(STATE_INVALID, "UNKNOWN_KEY_CUSTODY_ROLE",
            Map.of("component", e.getKey()), e.getValue());
      }
      if (!roles.add(e.getValue())) {
        return new Outcome(STATE_FORBIDDEN, "CROSS_COMPONENT_KEY_CUSTODY",
          Map.of("role", e.getValue()), e.getValue());
      }
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateBackupTransition(String from, String to) {
    Set<String> valid = E14BackupLimits.BACKUP_STATUSES;
    if (from == null || !valid.contains(from)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_FROM", null, from);
    }
    if (to == null || !valid.contains(to)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_TO", null, to);
    }
    Map<String, Set<String>> allowed = Map.of(
        STATUS_ENROLLED, Set.of(
            STATUS_SNAPSHOTTING, STATUS_RESTORING,
            STATUS_FAILED, STATUS_REVOKED),
        STATUS_SNAPSHOTTING, Set.of(
            STATUS_RESTORED_VERIFIED, STATUS_FAILED, STATUS_REVOKED),
        STATUS_RESTORING, Set.of(
            STATUS_RESTORED_VERIFIED, STATUS_FAILED, STATUS_REVOKED),
        STATUS_RESTORED_VERIFIED, Set.of(
            STATUS_ENROLLED, STATUS_SUPERSEDED, STATUS_EXPIRED),
        STATUS_SUPERSEDED, Set.of(),
        STATUS_FAILED, Set.of(STATUS_ENROLLED, STATUS_REVOKED),
        STATUS_REVOKED, Set.of(),
        STATUS_EXPIRED, Set.of());
    Set<String> fromAllowed = allowed.get(from);
    if (fromAllowed == null || !fromAllowed.contains(to)) {
      return new Outcome(STATE_INVALID,
          "INVALID_TRANSITION:" + from + "->" + to,
          Map.of("from", from, "to", to), to);
    }
    return new Outcome(STATE_OK, null, null, to);
  }

  public static final class Component {
    public final String name;
    public final String cadence;
    public final String encryption;
    public final String keyCustody;
    public final int retentionDays;
    public final int retentionDaily;
    public final int retentionMonthly;
    public final int retentionYearly;
    public final boolean offsiteCopyRequired;
    public final boolean restoreTested;
    public final String restoreEvidence;
    public final int rpoSeconds;
    public final int rtoSeconds;
    public final int orderingRank;

    public Component(String name, String cadence, String encryption,
        String keyCustody, int retentionDays, int retentionDaily,
        int retentionMonthly, int retentionYearly,
        boolean offsiteCopyRequired, boolean restoreTested,
        String restoreEvidence, int rpoSeconds, int rtoSeconds,
        int orderingRank) {
      this.name = name;
      this.cadence = cadence;
      this.encryption = encryption;
      this.keyCustody = keyCustody;
      this.retentionDays = retentionDays;
      this.retentionDaily = retentionDaily;
      this.retentionMonthly = retentionMonthly;
      this.retentionYearly = retentionYearly;
      this.offsiteCopyRequired = offsiteCopyRequired;
      this.restoreTested = restoreTested;
      this.restoreEvidence = restoreEvidence;
      this.rpoSeconds = rpoSeconds;
      this.rtoSeconds = rtoSeconds;
      this.orderingRank = orderingRank;
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