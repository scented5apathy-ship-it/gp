# Backup Restore Evidence — PostgreSQL

> **Status:** E14.1 baseline stub. The actual restore drill
> log is appended by the on-call SRE after each quarterly
> drill; this file MUST exist on disk (per
> `contracts/disaster-recovery/backup-matrix-policy.yaml`
> `requiredRuntimeHelpers`).

| Field | Value |
| ----- | ----- |
| Component | `postgresql` |
| Cadence | `continuous_archiving_wal` |
| Encryption | `aes_256_gcm` |
| Key custody | `vault_kv_v2_backup_pg` |
| Retention | 30 daily / 12 monthly / 7 yearly (≥ 30 days) |
| RPO budget | ≤ 900 s |
| RTO budget | ≤ 14 400 s |
| Restore tool | `pg_basebackup` + `pg_waldump` + `pg_restore` |
| Ordering rank | 2 |

## Required log fields (appended per drill)

- `drilledAt` — RFC 3339 timestamp.
- `drilledBy` — Vault role that decrypted + restored.
- `dataset` — anonymised dataset hash.
- `rtoObservedSeconds` — measured recovery time.
- `rpoObservedSeconds` — measured data loss.
- `restoreEvidencePath` — pointer to the run log.
- `signoff` — SRE + privacy officer acknowledgement.

A drill is **invalid** when any of the above fields is
blank or when `rtoObservedSeconds > 14400` /
`rpoObservedSeconds > 900`.