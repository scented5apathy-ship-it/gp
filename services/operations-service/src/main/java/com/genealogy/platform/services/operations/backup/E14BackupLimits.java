package com.genealogy.platform.services.operations.backup;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E14.1 backup matrix
 * contract. Mirrors
 * <code>contracts/disaster-recovery/backup-matrix-policy.yaml</code>.
 */
public final class E14BackupLimits {

  public static final Set<String> COMPONENTS = Set.of(
      "postgresql", "kafka", "object_storage", "keycloak",
      "openfga", "temporal", "vault", "flagsmith");

  public static final Set<String> CADENCE_KINDS = Set.of(
      "continuous_archiving_wal",
      "continuous_cross_region_replication",
      "daily_snapshot",
      "weekly_snapshot",
      "monthly_snapshot");

  public static final Set<String> ENCRYPTION_METHODS = Set.of(
      "aes_256_gcm", "cloud_kms_managed_envelope", "vault_transit_rewrap");

  public static final Set<String> KEY_CUSTODY_ROLES = Set.of(
      "vault_kv_v2_backup_pg",
      "vault_kv_v2_backup_kafka",
      "vault_kv_v2_backup_s3",
      "vault_kv_v2_backup_keycloak",
      "vault_kv_v2_backup_openfga",
      "vault_kv_v2_backup_temporal",
      "vault_kv_v2_backup_self",
      "vault_kv_v2_backup_flagsmith");

  public static final Set<String> BACKUP_STATUSES = Set.of(
      "ENROLLED", "SNAPSHOTTING", "RESTORING",
      "RESTORED_VERIFIED", "SUPERSEDED", "FAILED",
      "REVOKED", "EXPIRED");

  public static final int MIN_RETENTION_DAYS = 30;
  public static final int RETENTION_DAILY_MIN = 30;
  public static final int RETENTION_MONTHLY_MIN = 12;
  public static final int RETENTION_YEARLY_MIN = 7;
  public static final int SAAS_RPO_SECONDS_MAX = 900;
  public static final int SAAS_RTO_SECONDS_MAX = 14400;
  public static final int RESTORE_DRILL_CADENCE_DAYS = 90;
  public static final int OFFSITE_COPY_MIN_REGIONS = 1;
  public static final int KEY_ROTATION_DAYS = 90;
  public static final int ENCRYPTION_MIN_KEY_BITS = 256;
  public static final int MAX_RESTORE_WINDOW_MINUTES = 240;
  public static final int BACKUP_ARTIFACT_INTEGRITY_CHECK_HOURS = 24;

  private E14BackupLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}