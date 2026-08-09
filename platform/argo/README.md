# platform/argo — Argo CD + Argo Rollouts source-of-truth

Per `tasks.md` E2.9 + `design.md` §1 + §13 +
`architecture-decisions.md` ADR-E0.5-01 (Argo CD 2.13.x,
Argo Rollouts 1.7.x) + `ownership-catalog.md` §3 this
directory declares the platform-wide GitOps + progressive
delivery contract.

## Files

| File | Purpose |
| ---- | ------- |
| `argocd-server.yaml` | Argo CD control plane posture (server / repo-server / application-controller / dex / notifications / redis), image pins, RBAC, OIDC, TLS, network policy, telemetry, audit, drift detection |
| `projects.yaml` | AppProject RBAC matrix (developer / config-reviewer / release-approver / platform-admin), source-repo allowlist, destination-namespace allowlist, sync windows per project |
| `applications.yaml` | Canonical Application set (Kong / Keycloak / OpenFGA / Kafka / Apicurio / Temporal / Istio / Vault / MinIO / Valkey / Flagsmith / Grafana / services / web-bff / web-public), promotion pipeline, promotion gates |
| `rollout-strategy.yaml` | Argo Rollouts canary strategy + 4 AnalysisTemplates (error-rate, latency-p95, success-rate, saturation) + 4 service-class templates (stateless-api / bff / domain-event-consumer / async-worker) + forbidden-strategy contract |
| `sync-windows.yaml` | Allow / deny windows per environment (dev / staging / production / weekend-blackout / change-freeze / legal-hold / dna-blackout), reconciliation defaults, notification triggers |
| `OWNERS` | Mirrors `config/teams.yaml` |

## Mirrors

The five source-of-truth files are mirrored byte-identical
into `platform/helm/genealogy-platform/files/argo/`. The
deep linter (`scripts/lint-argo-config.mjs`) rejects drift.

## Helm templates

`platform/helm/genealogy-platform/templates/components/argo/`
ships:

- `statefulset.yaml` — Argo CD server + repo-server +
  application-controller + dex + notifications + redis
  StatefulSet (the chart renders when
  `components.gitOps.driver: argocd`).
- `rollouts-controller.yaml` — Argo Rollouts controller
  Deployment (rendered when
  `components.gitOps.rolloutsEnabled: true`).
- `services.yaml` — Argo CD server (port 443) + repo-server
  (port 8081) + application-controller metrics (port 8082)
  + redis (port 6379) Services.
- `serviceaccounts.yaml` — Argo CD pod SA + Argo Rollouts
  pod SA.
- `secrets.yaml` — `argocd-admin-password` /
  `argocd-server-secret` / `argocd-signing-key` /
  `argocd-dex-signing-key` / `argocd-gpg-signing-key` /
  `argocd-redis-secret` placeholders (ESO + Vault).
- `configmap.yaml` — five ConfigMaps mirroring the
  source-of-truth files.
- `bootstrap-configmap.yaml` — idempotent bootstrap script
  (AppProject + Application + Rollout + AnalysisTemplate
  reconcile).
- `bootstrap-job.yaml` — Helm-hook Job
  (`pre-install,pre-upgrade`).
- `network-policies.yaml` — deny-by-default Ingress +
  Egress.

## Deep linter

`scripts/lint-argo-config.mjs` enforces:

- `argocd-server.yaml` pins `argoproj/argocd:v2.13.4` +
  `argoproj/argocd-rollouts:v1.7.2` (ADR-E0.5-01),
  RBAC strict mode, anonymous access disabled, TLS 1.2
  minimum, network policy present, audit log enabled,
  Prometheus telemetry enabled, drift detection enabled
  (180s resync), no literal secret.
- `projects.yaml` declares ≥ 3 AppProjects
  (production / non-prod / platform) with source-repo +
  destination-namespace allowlist; the four-eyes principle
  (developer / config-reviewer / release-approver /
  platform-admin) is enforced; per-tenant AppProject is
  FORBIDDEN.
- `applications.yaml` declares ≥ 8 canonical Applications,
  promotion pipeline (dev → staging → production) with
  release-approver + MFA gate on production.
- `rollout-strategy.yaml` declares the canary strategy +
  the 4 AnalysisTemplates + the 4 service-class templates
  + the forbidden-strategy contract (no setWeight: 100
  without analysis, no tenant-scoped canary).
- `sync-windows.yaml` declares ≥ 5 windows (dev + staging
  + production + weekend-blackout + change-freeze), no
  raw email / OIDC subject in audit fields.
- The five files are mirrored byte-identical into
  `platform/helm/genealogy-platform/files/argo/`.
- No literal secret / token / password / API key / private
  key / ssh-key in any file.

## Contract

The platform-wide Argo CD + Argo Rollouts contract lives
in `platform/helm/genealogy-platform/templates/components/contract-stubs.yaml`
under `gitops`. Domain code does NOT interact with
Argo CD directly; Argo CD reconciles Application / Rollout
state from git. The only CI surface is the AppProject RBAC
+ the OIDC group membership (developer / config-reviewer /
release-approver / platform-admin).

## Observability

`platform/observability/alerts/argo-rules.yaml` ships
alerts across 4 rule groups:

- `argo.cd-sync` — `ArgoCdSyncFailed`,
  `ArgoCdDriftDetected`, `ArgoCdHealthDegraded`.
- `argo.rollouts` — `ArgoRolloutAborted`,
  `ArgoRolloutStuck`, `ArgoRolloutAnalysisFailed`.
- `argo.controller` — `ArgoControllerDown`,
  `ArgoControllerHighErrorRate`.
- `argo.bootstrap` — `ArgoBootstrapJobFailed`.

## Runbook

`runbook/argo.md` documents source-of-truth map, alert
playbooks, manual override procedure, drift response
playbook, and backup / restore procedure (Postgres PITR
for the Argo CD server).

## Local profile

`platform/local/profile.yaml.gitops` pins the same images
(`argoproj/argocd:v2.13.4` + `argoproj/argocd-rollouts:v1.7.2`
+ `redis:7.2-alpine`); the Docker Compose stack mounts the
bootstrap script + the five source-of-truth files into the
`argocd-bootstrap` Job.
