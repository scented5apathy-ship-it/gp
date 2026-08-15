# Backup Restore Evidence — Vault

> **Status:** E14.1 baseline stub. The actual restore drill
> log is appended by the on-call SRE after each quarterly
> drill; this file MUST exist on disk (per
> `contracts/disaster-recovery/backup-matrix-policy.yaml`
> `requiredRuntimeHelpers`).

| Field | Value |
| ----- | ----- |
| Component | `vault` |
| Cadence | `daily_snapshot` |
| Encryption | `aes_256_gcm` |
| Key custody | `vault_kv_v2_backup_self` |
| Retention | 30 daily / 12 monthly / 7 yearly (≥ 30 days) |
| RPO budget | ≤ 86 400 s |
| RTO budget | ≤ 14 400 s |
| Restore tool | `vault operator raft snapshot restore` |
| Ordering rank | 1 |

## Required log fields (appended per drill)

- `drilledAt` — RFC 3339 timestamp.
- `drilledBy` — Vault unseal role that recovered the cluster.
- `dataset` — anonymised snapshot hash.
- `rtoObservedSeconds` — measured recovery time.
- `rpoObservedSeconds` — measured data loss.
- `restoreEvidencePath` — pointer to the run log.
- `unsealQuorumWitnessed` — must be true.
- `signoff` — Security + SRE acknowledgement.

Vault is rank 1 in the restore order; failing this drill
blocks every other restore drill.