# platform/featureflags — Flagsmith + OpenFeature source-of-truth

Per `tasks.md` E2.8 + `design.md` §1 + §13 + `privacy-and-legal-gate.md`
§12 this directory declares the platform-wide Flagsmith + OpenFeature
contract.

## Files

| File | Purpose |
| ---- | ------- |
| `flagsmith-server.yaml` | Server image pin, backing store, CORS allowlist, audit, telemetry, rate limit, cache, replicas |
| `environments.yaml` | 5 environments (`development`, `staging`, `production`, `onprem`, `audit`), 1 organisation per cluster, 4 RBAC roles |
| `flag-taxonomy.yaml` | Canonical flag list (8 legal-gate + 4 rollout + 2 segment overrides), required columns, forbidden patterns |
| `safe-defaults.yaml` | OpenFeature SDK safe-default rules (typed defaults, per-env provider, evaluation context, audit event contract) |
| `sdk-config.yaml` | Bootstrap Job + SDK wiring (Spring Boot + Next.js), Kong edge route, circuit breaker / retry / bulkhead |
| `OWNERS` | Mirrors `config/teams.yaml` |

## Mirrors

The five source-of-truth files are mirrored byte-identical into
`platform/helm/genealogy-platform/files/featureflags/`. The deep
linter (`scripts/lint-flagsmith-config.mjs`) rejects drift.

## Helm templates

`platform/helm/genealogy-platform/templates/components/featureflags/`
ships:

- `statefulset.yaml` — Flagsmith server (front-end + admin UI on the
  same node).
- `services.yaml` — API Service (port 8000) + headless internal
  Service.
- `serviceaccounts.yaml` — Flagsmith pod SA + bootstrap SA.
- `secrets.yaml` — `flagsmith-server-tls` (cert-manager placeholder)
  + per-environment `flagsmith-api-token-*` placeholders.
- `configmap.yaml` — five ConfigMaps mirroring the source-of-truth
  files.
- `bootstrap-configmap.yaml` — idempotent bootstrap script
  (creates environments + RBAC roles + flags).
- `bootstrap-job.yaml` — Helm-hook Job
  (`pre-install,pre-upgrade`).
- `network-policies.yaml` — deny-by-default Ingress + Egress.

## Deep linter

`scripts/lint-flagsmith-config.mjs` enforces:

- `flagsmith-server.yaml` pin (`flagsmith/flagsmith:2.139.4`),
  backing store = postgresql, anonymous access = false, CORS
  allowlist (no wildcard), TLS 1.2 minimum, audit log enabled,
  Prometheus telemetry enabled, no literal secret.
- `environments.yaml` declares 5 environments + 4 RBAC roles; no
  per-tenant environment; `audit` environment is read-only.
- `flag-taxonomy.yaml` declares 8 legal-gate flags (one per row in
  `privacy-and-legal-gate.md` §12) + ≥ 4 rollout flags + the 11
  required columns per flag; the 12 forbidden key patterns are
  enforced (regex).
- `safe-defaults.yaml` declares per-type fallback, per-env
  evaluation timeout, evaluation context contract (tenant_pseudo_id
  required; tenant_id forbidden), audit event contract.
- `sdk-config.yaml` declares the bootstrap Job + Spring Boot
  properties + Next.js env + Kong route + circuit breaker / retry
  / bulkhead.
- The five files are mirrored byte-identical into
  `platform/helm/genealogy-platform/files/featureflags/`.
- No literal secret / token / password in any file.

## Contract

The platform-wide Flagsmith + OpenFeature contract lives in
`platform/helm/genealogy-platform/templates/components/contract-stubs.yaml`
under `featureFlags`. Domain code binds to:

- `com.genealogy.platform.featureflags.FeatureFlagClient` (Java)
- `@openfeature/js-sdk` (Node.js / Next.js PWA)

…which delegates to OpenFeature SDK + Flagsmith provider when
reachable, and to the typed safe default when not. The
`safe-defaults.yaml` rules guarantee the consumer never observes
`null`.

## Observability

`platform/observability/alerts/flagsmith-rules.yaml` ships 9 alerts
across 4 rule groups:

- `flagsmith.availability` — `FlagsmithServerDown`,
  `FlagsmithApiLatencyHigh`, `FlagsmithApiLatencyCritical`.
- `flagsmith.eval` — `FlagsmithEvalErrorRateHigh`,
  `FlagsmithDefaultUsedRateHigh` (signals broken SDK wiring or
  unreachable provider).
- `flagsmith.bootstrap` — `FlagsmithBootstrapJobFailed`,
  `FlagsmithDriftDetected`.
- `flagsmith.audit` — `FlagsmithFlagChangeWithoutAudit`.

## Runbook

`runbook/flagsmith.md` documents source-of-truth map, 9 alert
playbooks, backup / restore procedure (Postgres PITR + flagsmith
CLI re-import), and the SDK safe-default drill.

## Local profile

`platform/local/profile.yaml.flagsmith` pins the same image
(`flagsmith/flagsmith:2.139.4`); the Docker Compose stack mounts
the bootstrap script + the five source-of-truth files into the
`featureflags-bootstrap` Job.