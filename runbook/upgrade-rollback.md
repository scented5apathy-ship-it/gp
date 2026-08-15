# Upgrade / Rollback — Operator Runbook

> **Status:** E14.4 baseline; evidence in
> `.kiro/specs/genealogy-platform/evidence/E14.4.md`.
> Mirrors
> `contracts/disaster-recovery/recovery-rollback-policy.yaml`.

## 1. Supported previous versions

The upgrade script MUST support the last 5 platform
versions: `2025.10`, `2025.12`, `2026.02`, `2026.04`,
`2026.06`. Older versions are out of support; the upgrade
script refuses to operate on them.

## 2. Migration contract

Flyway migrations MUST follow the expand-contract pattern:

| Kind | Allowed |
| ---- | ------- |
| `expand_column_add` | yes |
| `expand_table_create` | yes |
| `expand_index_create` | yes |
| `expand_backfill` | yes |
| `expand_switch` | yes |
| `deprecated_drop_followup` | yes (next release) |

Destructive operations (`drop_column_immediate`,
`shrink_column_immediate`, etc.) are **forbidden** in the
release window. Follow-up drops must be a separate release.

## 3. Compatibility

API / event / schema compatibility MUST be:

- `BACKWARD` (default — schema evolution is additive only)
- `BACKWARD_TRANSITIVE`
- `FULL`
- `NONE_BREAKING_SUPERSEDED_BY_ADR` (requires ADR
  supersession)

`FULL_BREAKING` is rejected without an ADR supersession.

## 4. Pre-upgrade checks

The upgrade MUST clear all 6 pre-checks:

1. `flyway_no_destructive`
2. `schema_compatibility_checked`
3. `event_compatibility_checked`
4. `rollback_plan_attached`
5. `feature_flag_kill_switch_attached`
6. `preflight_passed`

## 5. Post-upgrade checks

The upgrade MUST complete all 7 post-checks:

1. `flyway_migration_applied`
2. `red_metrics_under_budget`
3. `workflow_completion_under_budget`
4. `search_projection_fresh`
5. `audit_pipeline_receiving`
6. `reconcile_targets_stable`
7. `signoff_attached`

## 6. Rollback constraints

- `maxOneRollbackPerTenant` — only 1 active rollback per
  tenant; concurrent rollbacks forbidden.
- `noCrossTenantRollback` — never roll back another
  tenant's data.
- `rollbackToSupportedPreviousVersionOnly` — rollback to
  any of the 5 supported previous versions.
- `rollbackRequiresApprovalTicket` — every rollback
  references a support ticket.
- `rollbackEvacuatesActiveMutations` — drain in-flight
  mutations before swapping image / version.
- `rollbackRunsFeatureFlagKillSwitch` — flip the relevant
  Flagsmith flag BEFORE swapping image.

## 7. Argo Rollouts abort

The upgrade is wired to the same 4 canary abort rules as
E13.4 (`five_xx_ratio_exceeded`, `p95_latency_regression`,
`error_rate_spike`, `privacy_finding_detected`). Any rule
firing for the configured `forSeconds` ⇒ automatic abort.

## 8. Upgrade test coverage

Every upgrade is exercised against the last
`upgradeTestCoverageRequiredVersions=3` supported
versions. Production-like dataset + dry-run must succeed.

## 9. Failure handling

- Pre-check fails → upgrade aborted before APPLYING.
- Post-check fails → automatic rollback.
- Abort rule fires during canary → automatic rollback to
  previous version.
- Rollback without approval ticket → refused.
- Cross-tenant rollback attempt → SEV1, page on-call.

## 10. Evidence anchors

- `.kiro/specs/genealogy-platform/evidence/E14.4.md` — DoD.
- `tools/upgrade/simulate-upgrade.sh` — simulation runner.
- `platform/argocd/canary/abort-rules.yaml` — E13.4 abort
  rules wired into the upgrade.