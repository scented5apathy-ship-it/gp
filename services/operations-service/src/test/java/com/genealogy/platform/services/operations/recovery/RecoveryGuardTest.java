package com.genealogy.platform.services.operations.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.recovery.RecoveryGuard.Outcome;
import com.genealogy.platform.services.operations.recovery.RecoveryGuard.Rollback;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecoveryGuardTest {

  @Test
  void versionPairForwardIsAccepted() {
    Outcome out = RecoveryGuard.validateVersionPair("2026.02", "2026.06");
    assertEquals(RecoveryGuard.STATE_OK, out.state);
  }

  @Test
  void sameVersionUpgradeIsRejected() {
    Outcome out = RecoveryGuard.validateVersionPair("2026.06", "2026.06");
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("SAME_VERSION_UPGRADE"));
  }

  @Test
  void unsupportedSourceVersionIsRejected() {
    Outcome out = RecoveryGuard.validateVersionPair(
        "2024.01", "2026.06");
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_SOURCE_VERSION"));
  }

  @Test
  void expandColumnAddMigrationIsAccepted() {
    Outcome out = RecoveryGuard.validateMigration("expand_column_add");
    assertEquals(RecoveryGuard.STATE_OK, out.state);
  }

  @Test
  void destructiveMigrationIsForbidden() {
    Outcome out = RecoveryGuard.validateMigration("drop_column_immediate");
    assertEquals(RecoveryGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("DESTRUCTIVE_MIGRATION_FORBIDDEN"));
  }

  @Test
  void destructiveMigrationIsForbiddenWhenShrinkImmediate() {
    Outcome out = RecoveryGuard.validateMigration("shrink_column_immediate");
    assertEquals(RecoveryGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("DESTRUCTIVE_MIGRATION_FORBIDDEN"));
  }

  @Test
  void unknownMigrationKindIsRejected() {
    Outcome out = RecoveryGuard.validateMigration("create_index");
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_MIGRATION_KIND"));
  }

  @Test
  void backwardCompatibilityIsAccepted() {
    Outcome out = RecoveryGuard.validateCompatibility("BACKWARD");
    assertEquals(RecoveryGuard.STATE_OK, out.state);
  }

  @Test
  void unknownCompatKindIsRejected() {
    Outcome out = RecoveryGuard.validateCompatibility("FULL_BREAKING");
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_COMPAT_KIND"));
  }

  @Test
  void allPreChecksPresentIsAccepted() {
    Outcome out = RecoveryGuard.validatePreChecks(
        E14RecoveryLimits.PRE_CHECKS);
    assertEquals(RecoveryGuard.STATE_OK, out.state);
  }

  @Test
  void missingFeatureFlagKillSwitchIsRejected() {
    Set<String> checks = new LinkedHashSet<>(
        E14RecoveryLimits.PRE_CHECKS);
    checks.remove("feature_flag_kill_switch_attached");
    Outcome out = RecoveryGuard.validatePreChecks(checks);
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("PRE_CHECKS_MISSING"));
  }

  @Test
  void allPostChecksPresentIsAccepted() {
    Outcome out = RecoveryGuard.validatePostChecks(
        E14RecoveryLimits.POST_CHECKS);
    assertEquals(RecoveryGuard.STATE_OK, out.state);
  }

  @Test
  void missingSignoffAttachedIsRejected() {
    Set<String> checks = new LinkedHashSet<>(
        E14RecoveryLimits.POST_CHECKS);
    checks.remove("signoff_attached");
    Outcome out = RecoveryGuard.validatePostChecks(checks);
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
  }

  @Test
  void allAbortRuleKindsAreAccepted() {
    for (String k : E14RecoveryLimits.ABORT_RULE_KINDS) {
      Outcome out = RecoveryGuard.validateAbortRuleKind(k);
      assertEquals(RecoveryGuard.STATE_OK, out.state,
          () -> k + " was " + out.violationCode);
    }
  }

  @Test
  void canonicalRollbackIsAccepted() {
    Rollback r = new Rollback("2026.04", "SUP-1234",
        "tenant-acme", false, 0, true);
    Outcome out = RecoveryGuard.validateRollback(r);
    assertEquals(RecoveryGuard.STATE_OK, out.state);
  }

  @Test
  void crossTenantRollbackIsForbidden() {
    Rollback r = new Rollback("2026.04", "SUP-1234",
        "tenant-acme", true, 0, true);
    Outcome out = RecoveryGuard.validateRollback(r);
    assertEquals(RecoveryGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("CROSS_TENANT_ROLLBACK_FORBIDDEN"));
  }

  @Test
  void rollbackWithoutKillSwitchIsForbidden() {
    Rollback r = new Rollback("2026.04", "SUP-1234",
        "tenant-acme", false, 0, false);
    Outcome out = RecoveryGuard.validateRollback(r);
    assertEquals(RecoveryGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains(
        "ROLLBACK_REQUIRES_FEATURE_FLAG_KILL_SWITCH"));
  }

  @Test
  void secondRollbackForSameTenantIsRejected() {
    Rollback r = new Rollback("2026.04", "SUP-1234",
        "tenant-acme", false,
        E14RecoveryLimits.MAX_ACTIVE_ROLLBACKS_PER_TENANT, true);
    Outcome out = RecoveryGuard.validateRollback(r);
    assertEquals(RecoveryGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("MAX_ROLLBACKS_PER_TENANT_EXCEEDED"));
  }

  @Test
  void rollbackToUnsupportedVersionIsRejected() {
    Rollback r = new Rollback("2024.01", "SUP-1234",
        "tenant-acme", false, 0, true);
    Outcome out = RecoveryGuard.validateRollback(r);
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("ROLLBACK_TARGET_UNSUPPORTED"));
  }

  @Test
  void rollbackWithoutApprovalTicketIsRejected() {
    Rollback r = new Rollback("2026.04", "",
        "tenant-acme", false, 0, true);
    Outcome out = RecoveryGuard.validateRollback(r);
    assertEquals(RecoveryGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("ROLLBACK_TICKET_BLANK"));
  }

  @Test
  void transitionPlannedToPrecheckIsAccepted() {
    Outcome out = RecoveryGuard.validateTransition(
        RecoveryGuard.STATUS_PLANNED, RecoveryGuard.STATUS_PRECHECK_RUNNING);
    assertEquals(RecoveryGuard.STATE_OK, out.state);
  }

  @Test
  void transitionRolledBackIsTerminal() {
    Outcome ok = RecoveryGuard.validateTransition(
        RecoveryGuard.STATUS_ROLLING_BACK,
        RecoveryGuard.STATUS_ROLLED_BACK);
    assertEquals(RecoveryGuard.STATE_OK, ok.state);
    Outcome bad = RecoveryGuard.validateTransition(
        RecoveryGuard.STATUS_ROLLED_BACK, RecoveryGuard.STATUS_PLANNED);
    assertFalse(bad.state.equals(RecoveryGuard.STATE_OK));
  }
}