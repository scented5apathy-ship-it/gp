package com.genealogy.platform.services.operations.recovery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pure orchestrator that validates upgrade / rollback
 * payloads against the E14.4 invariants. Mirrors
 * <code>contracts/disaster-recovery/recovery-rollback-policy.yaml</code>.
 *
 * <p>The guard enforces:
 * <ul>
 *   <li>sourceVersion + targetVersion are bound to the
 *       closed-set of supported previous versions;</li>
 *   <li>migrationKind is one of the 6 Flyway expand-contract
 *       kinds; destructive operations are forbidden in
 *       release window;</li>
 *   <li>compatibilityKind is one of the 4 closed-set
 *       kinds (BACKWARD / BACKWARD_TRANSITIVE / FULL /
 *       NONE_BREAKING_SUPERSEDED_BY_ADR);</li>
 *   <li>preUpgradeChecks + postUpgradeChecks each cover
 *       the entire closed-set;</li>
 *   <li>abortRuleKind comes from the 4 closed-set entries
 *       (mirror of E13.4 canary abort);</li>
 *   <li>rollback target is one of the supported previous
 *       versions + approval ticket + tenant-scoped
 *       (max 1 rollback per tenant; cross-tenant
 *       forbidden);</li>
 *   <li>upgrade state transitions respect the 10-status
 *       matrix (terminal: CANCELLED / SUCCEEDED → SUPERSEDED
 *       / ROLLED_BACK).</li>
 * </ul>
 */
public final class RecoveryGuard {

  public static final String STATE_OK = "OK";
  public static final String STATE_OVER_LIMIT = "OVER_LIMIT";
  public static final String STATE_FORBIDDEN = "FORBIDDEN";
  public static final String STATE_INVALID = "INVALID";

  public static final String STATUS_PLANNED = "PLANNED";
  public static final String STATUS_PRECHECK_RUNNING = "PRECHECK_RUNNING";
  public static final String STATUS_APPLYING = "APPLYING";
  public static final String STATUS_POSTCHECK_RUNNING = "POSTCHECK_RUNNING";
  public static final String STATUS_CANCELLED = "CANCELLED";
  public static final String STATUS_SUCCEEDED = "SUCCEEDED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_ROLLING_BACK = "ROLLING_BACK";
  public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";
  public static final String STATUS_SUPERSEDED = "SUPERSEDED";

  private RecoveryGuard() {
    throw new UnsupportedOperationException("pure utility");
  }

  public static Outcome validateVersionPair(String from, String to) {
    if (from == null || !E14RecoveryLimits.SUPPORTED_PREVIOUS_VERSIONS
        .contains(from)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_SOURCE_VERSION",
          null, from);
    }
    if (to == null || !E14RecoveryLimits.SUPPORTED_PREVIOUS_VERSIONS
        .contains(to)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_TARGET_VERSION",
          null, to);
    }
    if (from.equals(to)) {
      return new Outcome(STATE_INVALID, "SAME_VERSION_UPGRADE",
          Map.of("from", from, "to", to), to);
    }
    return new Outcome(STATE_OK, null, null, to);
  }

  public static Outcome validateMigration(String kind) {
    if (kind == null) {
      return new Outcome(STATE_INVALID, "BLANK_MIGRATION_KIND", null, null);
    }
    String normalised = kind.toLowerCase();
    if ("destructive_migration".equals(normalised)
        || normalised.contains("drop")
        || normalised.contains("shrink")
        || normalised.contains("truncate")) {
      return new Outcome(STATE_FORBIDDEN,
          "DESTRUCTIVE_MIGRATION_FORBIDDEN", null, kind);
    }
    if (!E14RecoveryLimits.MIGRATION_KINDS.contains(kind)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_MIGRATION_KIND",
          null, kind);
    }
    return new Outcome(STATE_OK, null, null, kind);
  }

  public static Outcome validateCompatibility(String kind) {
    if (kind == null
        || !E14RecoveryLimits.COMPAT_KINDS.contains(kind)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_COMPAT_KIND",
          null, kind);
    }
    return new Outcome(STATE_OK, null, null, kind);
  }

  public static Outcome validatePreChecks(java.util.Set<String> checks) {
    if (checks == null
        || !checks.containsAll(E14RecoveryLimits.PRE_CHECKS)) {
      java.util.Set<String> missing = new java.util.LinkedHashSet<>(
          E14RecoveryLimits.PRE_CHECKS);
      if (checks != null) missing.removeAll(checks);
      return new Outcome(STATE_INVALID, "PRE_CHECKS_MISSING",
          Map.of("missing", new java.util.ArrayList<>(missing)),
          String.join(",", missing));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validatePostChecks(java.util.Set<String> checks) {
    if (checks == null
        || !checks.containsAll(E14RecoveryLimits.POST_CHECKS)) {
      java.util.Set<String> missing = new java.util.LinkedHashSet<>(
          E14RecoveryLimits.POST_CHECKS);
      if (checks != null) missing.removeAll(checks);
      return new Outcome(STATE_INVALID, "POST_CHECKS_MISSING",
          Map.of("missing", new java.util.ArrayList<>(missing)),
          String.join(",", missing));
    }
    return new Outcome(STATE_OK, null, null, null);
  }

  public static Outcome validateAbortRuleKind(String kind) {
    if (kind == null
        || !E14RecoveryLimits.ABORT_RULE_KINDS.contains(kind)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_ABORT_KIND", null, kind);
    }
    return new Outcome(STATE_OK, null, null, kind);
  }

  public static Outcome validateRollback(Rollback rollback) {
    if (rollback == null) {
      return new Outcome(STATE_INVALID, "BLANK_ROLLBACK", null, null);
    }
    if (rollback.targetVersion == null
        || !E14RecoveryLimits.SUPPORTED_PREVIOUS_VERSIONS
            .contains(rollback.targetVersion)) {
      return new Outcome(STATE_INVALID, "ROLLBACK_TARGET_UNSUPPORTED",
          null, rollback.targetVersion);
    }
    if (rollback.approvalTicket == null
        || rollback.approvalTicket.isBlank()) {
      return new Outcome(STATE_INVALID, "ROLLBACK_TICKET_BLANK",
          null, null);
    }
    if (rollback.tenantId == null || rollback.tenantId.isBlank()) {
      return new Outcome(STATE_INVALID, "ROLLBACK_TENANT_BLANK",
          null, null);
    }
    if (rollback.crossTenant) {
      return new Outcome(STATE_FORBIDDEN, "CROSS_TENANT_ROLLBACK_FORBIDDEN",
          null, rollback.tenantId);
    }
    if (rollback.activeRollbacksForTenant
        >= E14RecoveryLimits.MAX_ACTIVE_ROLLBACKS_PER_TENANT) {
      return new Outcome(STATE_OVER_LIMIT,
          "MAX_ROLLBACKS_PER_TENANT_EXCEEDED",
          Map.of("max",
              E14RecoveryLimits.MAX_ACTIVE_ROLLBACKS_PER_TENANT,
              "actual",
              rollback.activeRollbacksForTenant),
          rollback.tenantId);
    }
    if (!rollback.featureFlagKillSwitch) {
      return new Outcome(STATE_FORBIDDEN,
          "ROLLBACK_REQUIRES_FEATURE_FLAG_KILL_SWITCH",
          null, rollback.tenantId);
    }
    return new Outcome(STATE_OK, null, null, rollback.targetVersion);
  }

  public static Outcome validateTransition(String from, String to) {
    Set<String> valid = E14RecoveryLimits.UPGRADE_STATUSES;
    if (from == null || !valid.contains(from)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_FROM", null, from);
    }
    if (to == null || !valid.contains(to)) {
      return new Outcome(STATE_INVALID, "UNKNOWN_TO", null, to);
    }
    Map<String, Set<String>> allowed = Map.of(
        STATUS_PLANNED, Set.of(STATUS_PRECHECK_RUNNING, STATUS_CANCELLED),
        STATUS_PRECHECK_RUNNING, Set.of(
            STATUS_APPLYING, STATUS_FAILED, STATUS_CANCELLED),
        STATUS_APPLYING, Set.of(STATUS_POSTCHECK_RUNNING, STATUS_FAILED),
        STATUS_POSTCHECK_RUNNING, Set.of(
            STATUS_SUCCEEDED, STATUS_ROLLING_BACK, STATUS_FAILED),
        STATUS_CANCELLED, Set.of(),
        STATUS_SUCCEEDED, Set.of(STATUS_SUPERSEDED),
        STATUS_FAILED, Set.of(STATUS_ROLLING_BACK, STATUS_CANCELLED),
        STATUS_ROLLING_BACK, Set.of(STATUS_ROLLED_BACK, STATUS_FAILED),
        STATUS_ROLLED_BACK, Set.of(),
        STATUS_SUPERSEDED, Set.of());
    Set<String> fromAllowed = allowed.get(from);
    if (fromAllowed == null || !fromAllowed.contains(to)) {
      return new Outcome(STATE_INVALID,
          "INVALID_TRANSITION:" + from + "->" + to,
          Map.of("from", from, "to", to), to);
    }
    return new Outcome(STATE_OK, null, null, to);
  }

  public static final class Rollback {
    public final String targetVersion;
    public final String approvalTicket;
    public final String tenantId;
    public final boolean crossTenant;
    public final int activeRollbacksForTenant;
    public final boolean featureFlagKillSwitch;

    public Rollback(String targetVersion, String approvalTicket,
        String tenantId, boolean crossTenant,
        int activeRollbacksForTenant,
        boolean featureFlagKillSwitch) {
      this.targetVersion = targetVersion;
      this.approvalTicket = approvalTicket;
      this.tenantId = tenantId;
      this.crossTenant = crossTenant;
      this.activeRollbacksForTenant = activeRollbacksForTenant;
      this.featureFlagKillSwitch = featureFlagKillSwitch;
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