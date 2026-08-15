package com.genealogy.platform.services.operations.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.backup.BackupGuard.Component;
import com.genealogy.platform.services.operations.backup.BackupGuard.Outcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackupGuardTest {

  private Component canonical(String name) {
    String keyCustody;
    switch (name) {
      case "postgresql": keyCustody = "vault_kv_v2_backup_pg"; break;
      case "kafka": keyCustody = "vault_kv_v2_backup_kafka"; break;
      case "object_storage": keyCustody = "vault_kv_v2_backup_s3"; break;
      case "keycloak": keyCustody = "vault_kv_v2_backup_keycloak"; break;
      case "openfga": keyCustody = "vault_kv_v2_backup_openfga"; break;
      case "temporal": keyCustody = "vault_kv_v2_backup_temporal"; break;
      case "vault": keyCustody = "vault_kv_v2_backup_self"; break;
      case "flagsmith": keyCustody = "vault_kv_v2_backup_flagsmith"; break;
      default: throw new IllegalArgumentException(name);
    }
    return new Component(name,
        "daily_snapshot",
        "aes_256_gcm",
        keyCustody,
        365, 30, 12, 7,
        !name.equals("object_storage") && !name.equals("flagsmith"),
        true,
        ".kiro/specs/genealogy-platform/evidence/backup/" + name + "-restore.md",
        900, 14400, 2);
  }

  @Test
  void allEightComponentsAreAccepted() {
    for (String name : E14BackupLimits.COMPONENTS) {
      Outcome out = BackupGuard.validateComponent(canonical(name));
      assertEquals(BackupGuard.STATE_OK, out.state,
          () -> "component " + name + " was " + out.violationCode);
    }
  }

  @Test
  void unknownComponentIsRejected() {
    Component c = new Component("mongodb", "daily_snapshot",
        "aes_256_gcm", "vault_kv_v2_backup_pg",
        365, 30, 12, 7, true, true,
        ".kiro/specs/genealogy-platform/evidence/backup/mongodb-restore.md",
        900, 14400, 2);
    Outcome out = BackupGuard.validateComponent(c);
    assertEquals(BackupGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("UNKNOWN_COMPONENT"));
  }

  @Test
  void restoreNotTestedIsForbidden() {
    Component c = new Component("postgresql", "daily_snapshot",
        "aes_256_gcm", "vault_kv_v2_backup_pg",
        365, 30, 12, 7, true, false,
        ".kiro/specs/genealogy-platform/evidence/backup/postgresql-restore.md",
        900, 14400, 2);
    Outcome out = BackupGuard.validateComponent(c);
    assertEquals(BackupGuard.STATE_FORBIDDEN, out.state);
  }

  @Test
  void restoreEvidenceOutsideEvidenceDirIsRejected() {
    Component c = new Component("postgresql", "daily_snapshot",
        "aes_256_gcm", "vault_kv_v2_backup_pg",
        365, 30, 12, 7, true, true,
        "evidence/postgresql-restore.md",
        900, 14400, 2);
    Outcome out = BackupGuard.validateComponent(c);
    assertEquals(BackupGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("RESTORE_EVIDENCE_PATH_INVALID"));
  }

  @Test
  void retentionBelowDailyLimitIsRejected() {
    Component c = new Component("postgresql", "daily_snapshot",
        "aes_256_gcm", "vault_kv_v2_backup_pg",
        365, 5, 12, 7, true, true,
        ".kiro/specs/genealogy-platform/evidence/backup/postgresql-restore.md",
        900, 14400, 2);
    Outcome out = BackupGuard.validateComponent(c);
    assertEquals(BackupGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("RETENTION_DAILY_BELOW_LIMIT"));
  }

  @Test
  void rpoOverBudgetIsRejected() {
    Component c = new Component("postgresql", "daily_snapshot",
        "aes_256_gcm", "vault_kv_v2_backup_pg",
        365, 30, 12, 7, true, true,
        ".kiro/specs/genealogy-platform/evidence/backup/postgresql-restore.md",
        3600, 14400, 2);
    Outcome out = BackupGuard.validateComponent(c);
    assertEquals(BackupGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("RPO_OUT_OF_BUDGET"));
  }

  @Test
  void plainTextEncryptionIsForbidden() {
    Component c = new Component("postgresql", "daily_snapshot",
        "plain_text", "vault_kv_v2_backup_pg",
        365, 30, 12, 7, true, true,
        ".kiro/specs/genealogy-platform/evidence/backup/postgresql-restore.md",
        900, 14400, 2);
    Outcome out = BackupGuard.validateComponent(c);
    assertEquals(BackupGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("PLAIN_TEXT_BACKUP_FORBIDDEN"));
  }

  @Test
  void restoreOrderTopologicalIsAccepted() {
    Outcome out = BackupGuard.validateRestoreOrder(List.of(1, 2, 3, 4, 5, 6));
    assertEquals(BackupGuard.STATE_OK, out.state);
  }

  @Test
  void restoreOrderNonTopologicalIsRejected() {
    Outcome out = BackupGuard.validateRestoreOrder(List.of(2, 1, 3));
    assertEquals(BackupGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("RESTORE_ORDER_NOT_TOPOLOGICAL"));
  }

  @Test
  void keyCustodyUniquenessAcrossComponentsIsAccepted() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("postgresql", "vault_kv_v2_backup_pg");
    map.put("kafka", "vault_kv_v2_backup_kafka");
    map.put("vault", "vault_kv_v2_backup_self");
    Outcome out = BackupGuard.validateKeyCustodyUniqueness(map);
    assertEquals(BackupGuard.STATE_OK, out.state);
  }

  @Test
  void sharedCustodyAcrossComponentsIsForbidden() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("postgresql", "vault_kv_v2_backup_pg");
    map.put("kafka", "vault_kv_v2_backup_pg");
    Outcome out = BackupGuard.validateKeyCustodyUniqueness(map);
    assertEquals(BackupGuard.STATE_FORBIDDEN, out.state);
    assertTrue(out.violationCode.contains("CROSS_COMPONENT_KEY_CUSTODY"));
  }

  @Test
  void backupTransitionEnrolledToSnapshotttingIsAccepted() {
    Outcome out = BackupGuard.validateBackupTransition(
        BackupGuard.STATUS_ENROLLED, BackupGuard.STATUS_SNAPSHOTTING);
    assertEquals(BackupGuard.STATE_OK, out.state);
  }

  @Test
  void backupTransitionSupersededIsTerminal() {
    Outcome out = BackupGuard.validateBackupTransition(
        BackupGuard.STATUS_RESTORED_VERIFIED, BackupGuard.STATUS_SUPERSEDED);
    assertEquals(BackupGuard.STATE_OK, out.state);
    Outcome bad = BackupGuard.validateBackupTransition(
        BackupGuard.STATUS_SUPERSEDED, BackupGuard.STATUS_ENROLLED);
    assertFalse(bad.state.equals(BackupGuard.STATE_OK));
  }
}