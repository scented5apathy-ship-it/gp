# DR Drill Evidence — Region Loss

> **Status:** E14.2 baseline stub. The actual drill log,
> reconcile report, postmortem, remediation and signoff are
> appended by the on-call SRE after each quarterly drill;
> this file MUST exist on disk (per
> `contracts/disaster-recovery/drill-policy.yaml`
> `requiredRuntimeHelpers`).

| Field | Value |
| ----- | ----- |
| Drill kind | `region_loss` |
| Cadence | 90 days |
| Blast radius | `per_region` |
| Primary region | `gp-region-primary` |
| Allowed DR | `gp-region-secondary-a`, `gp-region-secondary-b` |
| Reconcile targets | `outbox_relay`, `kafka_consumer`, `temporal_workflow`, `search_projection`, `public_projection`, `audit_pipeline` |
| Replay capture | `redacted_metrics_only` |
| RPO budget | ≤ 900 s |
| RTO budget | ≤ 14 400 s |

## Required fields per drill

- `drilledAt` — RFC 3339 timestamp.
- `drilledBy` — Vault role.
- `failoverRegion` — chosen secondary region.
- `rpoObservedSeconds`, `rtoObservedSeconds`.
- `reconcileReport` — per-target reconcile delta.
- `postmortem` — narrative + customer impact.
- `remediation` — owner / ticket / dueDate / severity.
- `signoff` — SRE + product + privacy acknowledgement.

Region loss drills MUST result in at least one reconcile
report row covering `audit_pipeline` to confirm no
customer-data leak.