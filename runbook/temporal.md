# Runbook — Temporal cluster (E2.4)

## Scope

Self-hosted Temporal cluster that backs every workflow / saga in
the genealogy platform. Owned by `@genealogy/platform`; on-call =
`platform-primary`. Secondary = `@genealogy/data`, on-call =
`data-primary`.

| Field                  | Value                                                             |
| ---------------------- | ----------------------------------------------------------------- |
| Cluster name           | `genea-temporal`                                                  |
| Namespace              | `gp-data`                                                         |
| Helm chart             | `platform/helm/genealogy-platform/templates/components/temporal/` |
| Source-of-truth files  | `platform/temporal/*.yaml`                                        |
| Image                  | `temporalio/temporal:1.26.2`                                      |
| Admin-tools image      | `temporalio/admin-tools:1.26.2`                                   |
| UI image               | `temporalio/ui:2.5.0`                                             |
| Persistence            | PostgreSQL `genea_temporal` database on `postgres.gp-data`        |
| SLO class              | 99.9% / month; workflow start p95 < 400 ms                        |
| Sync dep budget (E2.4) | `n_sync ≤ 1` (start) per request                                  |

## Architecture

```
Browser / PWA
    │
    ▼
Kong → web-bff (REST)
    │              │
    │              ▼
    │       domain-service (gRPC client)
    │              │
    ▼              ▼
Istio mTLS  ──►  Temporal front-end (7233)
                     │
                     ▼
                 History / Matching / Worker
                     │
                     ▼
                 PostgreSQL (gp-data)
```

The four upstream Temporal services (front-end, history, matching,
worker) run inside one `temporal` StatefulSet on distinct ports
(7233, 7234, 7235, 7239). The chart renders the same all-in-one
layout in SaaS / on-prem / dev per ADR-E0.5-07. Horizontal scaling
to a per-service split is tracked separately (out of scope for E2.4).

## Source of truth

| File                                      | Purpose                                         |
| ----------------------------------------- | ----------------------------------------------- |
| `platform/temporal/namespace-config.yaml` | 8 platform namespaces + retention + visibility  |
| `platform/temporal/search-attrs.yaml`     | 9 visibility attributes + 13 forbidden names    |
| `platform/temporal/dynamic-config.yaml`   | workflow + activity defaults + visibility block |
| `platform/temporal/task-queues.yaml`      | declarative task-queue list + worker identity   |
| `platform/temporal/schemas/README.md`     | workflow / activity contract schemas (Protobuf) |

The same files are mirrored into
`platform/helm/genealogy-platform/files/temporal/`. The deep linter
(`scripts/lint-temporal-config.mjs`) enforces the mirror.

## Reconciliation

Two Helm-hook Jobs (`temporal-namespace-init`,
`temporal-task-queue-init`) re-emit `temporal operator namespace` /
`temporal task-queue update` for every entry in the source-of-truth
files on every `helm upgrade`. They run before the StatefulSet
rolls and delete themselves on success (`hook-delete-policy:
before-hook-creation,hook-succeeded`).

## Common operations

### Start a workflow

```sh
temporal workflow start \
  --address temporal.gp-data.svc.cluster.local:7233 \
  --task-queue genea-genealogy-main \
  --type TreeMergeWorkflow \
  --workflow-id tree-merge-2026-08-09-001 \
  --search-attribute TenantId=tenant-abc123
```

### List workflows by tenant

```sh
temporal workflow list \
  --address temporal.gp-data.svc.cluster.local:7233 \
  --query 'TenantId="tenant-abc123"'
```

### Inspect a workflow

```sh
temporal workflow describe \
  --address temporal.gp-data.svc.cluster.local:7233 \
  --workflow-id tree-merge-2026-08-09-001
```

### Cancel a workflow

```sh
temporal workflow cancel \
  --address temporal.gp-data.svc.cluster.local:7233 \
  --workflow-id tree-merge-2026-08-09-001
```

### Diagnose an activity failure

```sh
temporal activity list \
  --address temporal.gp-data.svc.cluster.local:7233 \
  --workflow-id tree-merge-2026-08-09-001
temporal activity describe \
  --address temporal.gp-data.svc.cluster.local:7233 \
  --activity-id ...
```

## Incident playbooks

### `TemporalServerDown`

Prometheus `up{job="temporal"} == 0` for 2m.

1. `kubectl -n gp-data describe statefulset temporal` — confirm
   the StatefulSet is `Ready`.
2. `kubectl -n gp-data logs temporal-0 --tail=200` — surface the
   crash cause. Common root causes:
   - PostgreSQL unreachable: `DB_POSTGRES_HOST` secret mount
     invalid; check `kubectl -n gp-data get secret
temporal-postgres-credentials -o yaml`.
   - Storage class mismatch: the StatefulSet's `volumeClaimTemplate`
     cannot provision on `gp-data-ssd` (or `standard` on dev).
     Override `components.temporal.persistence.cacheStorageClass`.
   - Dynamic-config parse error: re-render the chart after fixing
     `platform/temporal/dynamic-config.yaml`. The server reads the
     file on `SIGHUP`; the chart's rollout bounces the StatefulSet.
3. If the StatefulSet is healthy but gRPC is unreachable, check
   the NetworkPolicy: `kubectl -n gp-data get networkpolicy`.
   Confirm `temporal-allow` is present and allows ingress from
   `gp-services`, `gp-workers`, `gp-platform`, `gp-observability`.
4. Failover: there is no cross-region Temporal replica by default
   (ADR-E0.5-07 forbids cross-region transfer). The historical
   state lives in PostgreSQL; rerun the affected workflows from
   the originating service after the server recovers.

### `TemporalWorkflowFailureRateHigh`

Failure rate > 5% over 5m.

1. `temporal workflow list --query 'ExecutionStatus="Failed"'` —
   narrow by namespace + workflow type.
2. Inspect the failing activity with
   `temporal activity describe` (see "Diagnose an activity
   failure").
3. Check the downstream service: the failure is almost always a
   dependency outage (Kafka, PostgreSQL, S3, Keycloak).
4. If the failure is in the activity retry policy, bump
   `system.activity.maxAttempts` in `dynamic-config.yaml` and
   chart-roll.

### `TemporalActivityFailureRateHigh`

Same playbook as above; the alert names the activity class via
labels.

### `TemporalTaskQueueDepthHigh`

Pending tasks > 1000 for 10m.

1. `temporal task-queue describe --task-queue <name>` — surface
   the worker identity + poll latency.
2. If the worker pool is starved, scale the worker Deployment
   (or the `genealogy-service` / `media-worker` / `dna-worker`
   replicas — the task queues are pinned to specific service
   identities in `task-queues.yaml`).
3. If the activity is too slow, check the activity timeout in
   `dynamic-config.yaml` (`system.activity.scheduleToCloseTimeout`
   = 60s default).

### `TemporalReconciliationFailed`

The Helm-hook Job (`temporal-namespace-init` or
`temporal-task-queue-init`) has been failing for 5m.

1. `kubectl -n gp-data describe job temporal-namespace-init` —
   surface the failure cause.
2. `kubectl -n gp-data logs job/temporal-namespace-init` — the
   admin-tools container runs `temporal-init.sh`. Look for
   "unrecognised policy file" or "policy file ... not readable".
3. The most common root cause is a YAML parse error in
   `platform/temporal/namespace-config.yaml` /
   `task-queues.yaml`. Run `node scripts/lint-temporal-config.mjs`
   locally; fix the violation; commit; push; `helm upgrade`.

## Backup / restore

PostgreSQL PITR covers workflow state. The Helm-hook Jobs are
idempotent so re-running `helm upgrade` after a restore regenerates
the namespaces + task queues from the source-of-truth files.

## Security

- Search attribute whitelist (`search-attrs.yaml`) rejects any
  raw PII, DNA, token, file content, or signed URL. The worker
  SDK MUST pseudonymize via the platform `tenant-pseudo-id`
  library before `UpsertWorkflowExecutionOptions`.
- The Temporal UI is restricted to `gp-platform` + `gp-observability`
  by NetworkPolicy. Operators reach the UI via the platform
  SSO-protected BFF route (E3.5); nothing is public.
- Worker identities are Kubernetes service-account names. Any
  unknown identity is rejected at `RegisterTaskQueue`.

## ADR references

- ADR-E0.5-07 — Temporal distribution (self-host mandatory; SaaS
  only with in-region + on-prem parity).
- ADR-E0.5-01 — pinned baseline (Temporal 1.26.x).
