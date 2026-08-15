# Disaster Recovery — Operator Runbook

> **Status:** E14.2 baseline; evidence in
> `.kiro/specs/genealogy-platform/evidence/E14.2.md`.
> Mirrors `contracts/disaster-recovery/drill-policy.yaml`.

## 1. Drill kinds

Eight mandatory drill kinds:

| Kind | Cadence | Blast radius | Primary | Allowed DR |
| ---- | ------- | ------------ | ------- | ---------- |
| `cluster_loss` | 90 d | `per_cluster` | `gp-region-primary` | `gp-region-secondary-a/b` |
| `region_loss` | 90 d | `per_region` | `gp-region-primary` | `gp-region-secondary-a/b` |
| `dependency_outage` | 90 d | `per_service` | `gp-region-primary` | `gp-region-primary`, `gp-region-secondary-a` |
| `control_plane_failure` | 90 d | `per_namespace` | `gp-region-primary` | `gp-region-primary`, `gp-region-secondary-a/b` |
| `data_corruption` | 90 d | `per_aggregate` | `gp-region-primary` | `gp-region-primary`, `gp-region-secondary-a` |
| `rpo_breach` | 90 d | `per_environment` | `gp-region-primary` | `gp-region-primary` |
| `rto_breach` | 90 d | `per_environment` | `gp-region-primary` | `gp-region-primary` |
| `on_premises_failover` | 180 d | `per_site` | `onprem-customer-primary` | `onprem-customer-secondary` |

## 2. Reconcile targets

Every drill MUST publish a `reconcileTargets` list with at
least 2 entries from the closed-set:

- `outbox_relay`
- `kafka_consumer`
- `temporal_workflow`
- `search_projection`
- `public_projection`
- `audit_pipeline`
- `flagsmith_cache`

For cluster/region loss drills the full set is required.

## 3. Replay log capture

`replayLogCaptureMode` MUST equal `redacted_metrics_only`.
Anything else is **forbidden** to prevent customer-data leak
through drill artefacts. The platform refuses to admit a
drill whose capture mode diverges.

## 4. RPO / RTO budget

| Kind | RPO | RTO |
| ---- | --- | --- |
| `cluster_loss` | ≤ 900 s | ≤ 14 400 s |
| `region_loss` | ≤ 900 s | ≤ 14 400 s |
| `dependency_outage` | ≤ 3 600 s | ≤ 14 400 s |
| `control_plane_failure` | ≤ 86 400 s | ≤ 14 400 s |
| `data_corruption` | ≤ 900 s | ≤ 14 400 s |
| `rpo_breach` | ≤ 900 s | ≤ 14 400 s |
| `rto_breach` | ≤ 900 s | ≤ 14 400 s |
| `on_premises_failover` | ≤ 3 600 s | ≤ 14 400 s |

## 5. Required artefacts per drill

Every drill MUST emit all five artefacts:

- `drillLog` — chronologically ordered events (pseudonymous).
- `reconcileReport` — reconciliation delta for every
  declared `reconcileTarget`.
- `postmortem` — narrative + timeline + customer impact.
- `remediation` — owner / ticket / dueDate / severity from
  closed-set.
- `signoff` — SRE + product + privacy officer approval.

## 6. Failure handling

- Drill exceeds RPO / RTO → SEV1, freeze release.
- Drill fails `reconcileReport` coverage → SEV2, open
  remediation ticket within 24 h.
- `production_wide` blast radius without an approved
  feature flag → drill rejected before execution.
- Drill output leaks customer data → SEV1, revoke custody,
  notify DPO delegate.
- Ad-hoc region failover attempted outside the closed-set
  → SEV1, freeze release.

## 7. Evidence anchors

- `.kiro/specs/genealogy-platform/evidence/dr/cluster-loss.md`
- `.kiro/specs/genealogy-platform/evidence/dr/region-loss.md`
- `.kiro/specs/genealogy-platform/evidence/dr/dependency-outage.md`
- `.kiro/specs/genealogy-platform/evidence/dr/control-plane-failure.md`
- `.kiro/specs/genealogy-platform/evidence/dr/data-corruption.md`
- `.kiro/specs/genealogy-platform/evidence/dr/rpo-breach.md`
- `.kiro/specs/genealogy-platform/evidence/dr/rto-breach.md`
- `.kiro/specs/genealogy-platform/evidence/dr/on-premises-failover.md`