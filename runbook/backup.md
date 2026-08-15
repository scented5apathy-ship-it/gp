# Backup — Operator Runbook

> **Status:** E14.1 baseline; evidence in
> `.kiro/specs/genealogy-platform/evidence/E14.1.md`.
> Mirrors `contracts/disaster-recovery/backup-matrix-policy.yaml`.

## 1. Scope

Eight mandatory components: `postgresql`, `kafka`,
`object_storage`, `keycloak`, `openfga`, `temporal`, `vault`,
`flagsmith`. Every component MUST be backed up, encrypted
(`aes_256_gcm` / `cloud_kms_managed_envelope` /
`vault_transit_rewrap`), and restored at least once per
quarter. A snapshot that has not been restored is **NOT** a
backup per E14.1.

## 2. Restore order

| Rank | Component | Dependency |
| ---- | --------- | ---------- |
| 1 | `vault` | Self-contained (key custody for every other restore). |
| 2 | `postgresql` | Tenant data, Keycloak + OpenFGA sit on top. |
| 3 | `keycloak` | Realm + clients + flows. |
| 4 | `kafka` / `openfga` / `temporal` (parallel) | Authorization / events / workflows. |
| 5 | `object_storage` | Media + DNA raw envelope. |
| 6 | `flagsmith` | Feature flags last (safe fallback defaults). |

## 3. Cadence + retention

| Component | Cadence | Daily | Monthly | Yearly | RPO | RTO |
| --------- | ------- | ----- | ------- | ------ | --- | --- |
| `postgresql` | continuous WAL | 30 | 12 | 7 | 15 min | 4 h |
| `kafka` | daily snapshot | 30 | 12 | 7 | 60 min | 4 h |
| `object_storage` | cross-region replication | 30 | 12 | 7 | 15 min | 4 h |
| `keycloak` | daily snapshot | 30 | 12 | 7 | 24 h | 4 h |
| `openfga` | daily snapshot | 30 | 12 | 7 | 24 h | 4 h |
| `temporal` | daily snapshot | 30 | 12 | 7 | 24 h | 4 h |
| `vault` | daily Raft snapshot | 30 | 12 | 7 | 24 h | 4 h |
| `flagsmith` | daily snapshot | 30 | 12 | 7 | 24 h | 4 h |

## 4. Key custody

- Each component has its own Vault KVv2 path:
  - `vault_kv_v2_backup_pg` — PostgreSQL artefacts.
  - `vault_kv_v2_backup_kafka` — Kafka snapshot key.
  - `vault_kv_v2_backup_s3` — Object storage replication key.
  - `vault_kv_v2_backup_keycloak` — Keycloak export key.
  - `vault_kv_v2_backup_openfga` — OpenFGA model + tuple key.
  - `vault_kv_v2_backup_temporal` — Temporal namespace export key.
  - `vault_kv_v2_backup_self` — Vault Raft snapshot unseal key.
  - `vault_kv_v2_backup_flagsmith` — Flagsmith API token.
- **Cross-tenant custody is forbidden**; the on-call SRE
  tier-1 / tier-2 roles only ever read the keys for their
  own environment.
- Rotation cadence: every **90 days** (`KEY_ROTATION_DAYS`).

## 5. Restore drill cadence

- Restore drill **once per quarter** (`restoreDrillCadenceDays=90`).
- Each drill MUST produce an artefact under
  `.kiro/specs/genealogy-platform/evidence/backup/` declaring
  dataset, RTO, RPO, restore tool and a `restoreEvidence`
  pointer to the run log.
- BackupGuard refuses to admit a component as "backed up"
  unless the restore evidence is on disk and within the
  quarter window.

## 6. Failure handling

- Snapshot failure → alert `BackupSnapshotFailed` (SEV3) and
  fire `BackupRpoBreach` if not recovered within `RPO * 2`.
- Restore drill failure → SEV2, freeze release until
  remediation lands.
- Cross-tenant custody attempt → SEV1, audit + page on-call.
- Plain-text backup artefact detected → SEV1, freeze
  release + revoke custody role.

## 7. Evidence anchors

- `.kiro/specs/genealogy-platform/evidence/backup/postgresql-restore.md`
- `.kiro/specs/genealogy-platform/evidence/backup/kafka-restore.md`
- `.kiro/specs/genealogy-platform/evidence/backup/object-storage-restore.md`
- `.kiro/specs/genealogy-platform/evidence/backup/keycloak-restore.md`
- `.kiro/specs/genealogy-platform/evidence/backup/openfga-restore.md`
- `.kiro/specs/genealogy-platform/evidence/backup/temporal-restore.md`
- `.kiro/specs/genealogy-platform/evidence/backup/vault-restore.md`
- `.kiro/specs/genealogy-platform/evidence/backup/flagsmith-restore.md`