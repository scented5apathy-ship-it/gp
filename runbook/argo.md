# runbook/argo.md — Argo CD + Argo Rollouts runbook

Operator-facing runbook for the platform-wide GitOps +
progressive delivery stack. Per `tasks.md` E2.9 +
`architecture-decisions.md` ADR-E0.5-01 (Argo CD 2.13.x,
Argo Rollouts 1.7.x) + `ownership-catalog.md` §3
(Argo CD + Rollouts owner = platform-primary, on-call
sre-primary, SLO 99.9 %, rollout decision < 60 s).

## Source-of-truth map

| File | Purpose |
| ---- | ------- |
| `platform/argo/argocd-server.yaml` | Argo CD control plane posture (server / repo-server / application-controller / dex / notifications / redis), image pins, RBAC, OIDC, TLS, drift detection |
| `platform/argo/projects.yaml` | AppProject RBAC matrix (developer / config-reviewer / release-approver / platform-admin), source-repo allowlist, destination-namespace allowlist |
| `platform/argo/applications.yaml` | Canonical Application set (Kong / Keycloak / OpenFGA / Kafka / Apicurio / Temporal / Istio / Vault / MinIO / Valkey / Flagsmith / Grafana / services / web-bff / web-public) + promotion pipeline (dev → staging → production with release-approver + MFA gate) |
| `platform/argo/rollout-strategy.yaml` | Argo Rollouts canary strategy + 4 AnalysisTemplates (error-rate / latency-p95 / success-rate / saturation) + 4 service-class templates (stateless-api / bff / domain-event-consumer / async-worker) + forbidden-strategy contract |
| `platform/argo/sync-windows.yaml` | Allow / deny windows per environment (dev / staging / production / weekend-blackout / change-freeze / legal-hold / dna-blackout) |
| `platform/argo/OWNERS` | Ownership mirror (`config/teams.yaml`) |
| `platform/argo/README.md` | Operator-facing README |

## Mirror

`platform/helm/genealogy-platform/files/argo/` mirrors the
five source-of-truth files byte-identical. The deep
linter (`scripts/lint-argo-config.mjs`) rejects drift.

## Helm templates

`platform/helm/genealogy-platform/templates/components/argo/`
ships the Argo CD Deployment (server / repo-server /
application-controller) + Argo Rollouts controller
Deployment + Argo CD redis StatefulSet + the five
ConfigMaps + the bootstrap Job + the NetworkPolicy set.

## Helm values

`components.gitOps` block in `values.yaml` carries the
canonical posture; `values-<env>.yaml` overrides per env
(replica count + image pin + resource sizing).

## Deep linter

`pnpm lint:argo` (alias: `pnpm lint:argo` /
`node scripts/lint-argo-config.mjs`) enforces:

- Image pins: `argoproj/argocd:v2.13.4` +
  `argoproj/argocd-rollouts:v1.7.2` (ADR-E0.5-01).
- RBAC strict mode (policyDefault `role:readonly`).
- Anonymous access disabled (`auth.anonymousEnabled: false`).
- TLS 1.2 minimum.
- Audit log enabled; audit fields use `actor_pseudo_id`
  (no raw email / OIDC subject).
- Drift detection enabled; resyncSeconds in [60, 1800].
- AppProject RBAC: ≥ 3 AppProjects (production / non-prod /
  platform) + four-eyes principle
  (developer / config-reviewer / release-approver /
  platform-admin) + MFA required for production promotion.
- Application set: ≥ 8 Applications + production
  promotion requires `release-approver` + MFA.
- Rollout strategy: 4 AnalysisTemplates + 4 service
  classes + canary trafficRouting istio + automatic
  rollback contract.
- Sync windows: ≥ 5 windows (dev / staging / production /
  weekend-blackout / change-freeze) + audit fields use
  `actor_pseudo_id`.
- Mirror files: byte-identity with `platform/argo/`.
- No literal secret / token / password / API key /
  private key / ssh-key in any file.

## Smoke probe

`pnpm smoke:argo` (alias: `node scripts/smoke-argo.mjs`)
runs 46 structural-only checks. With kind + kubectl +
helm on PATH, the script can additionally bring up a
minimal Argo CD + Argo Rollouts stack on a kind cluster.

## Alert playbooks

### §1. sync-failed — `ArgoCdSyncFailed`

```bash
argocd app list -o name | xargs -I{} argocd app sync {} --async
```

If the failure persists:

```bash
argocd app manifests <name> | kubectl apply --dry-run=server -f -
kubectl get events -n gp-argocd --sort-by=.metadata.creationTimestamp
```

Common causes: invalid manifest, RBAC permission denied
(missing `applications, sync` grant), webhook delivery
failure, or vault unreachable (admin password / signing
key).

### §2. drift-detected — `ArgoCdDriftDetected`

```bash
argocd app diff <name>
argocd app history <name>
argocd app rollback <name>
```

Verify the live state against the source-of-truth in
`platform/argo/applications.yaml`. If a manual `kubectl
apply` was run out-of-band, sync the Application with
`--force` to overwrite.

### §3. health-degraded — `ArgoCdHealthDegraded`

```bash
argocd app get <name> --refresh hard
kubectl get pods -n <namespace> -l app.kubernetes.io/part-of=genealogy-platform
```

Verify the canary analysis gate (Prometheus metrics +
AnalysisTemplate thresholds) + the rollout strategy
(`rollout-strategy.yaml`).

### §4. rollout-aborted — `ArgoRolloutAborted`

```bash
kubectl get rollout -A
kubectl describe rollout <name> -n <namespace>
kubectl get analysistemplate -A
```

Common causes: canary error rate > 5 %, latency p95 >
2000 ms, success rate < 98 %, CPU saturation > 92 %.
Verify the AnalysisTemplate thresholds in
`platform/argo/rollout-strategy.yaml`.

### §5. rollout-stuck — `ArgoRolloutStuck`

```bash
argocd app get <name> --refresh hard
kubectl get rollout <name> -n <namespace> -o yaml
```

Common causes: AnalysisTemplate running for > 30 minutes
(verify Prometheus metrics), or release-approver has not
promoted (verify Keycloak OIDC group membership).

### §6. analysis-failed — `ArgoRolloutAnalysisFailed`

```bash
kubectl get analysistemplate -A
kubectl describe analysistemplate <name> -n <namespace>
```

Common causes: Prometheus query syntax error, missing
metric label, or success/failure condition threshold
misconfigured.

### §7. controller-down — `ArgoControllerDown`

```bash
kubectl get pods -n gp-argocd -l app.kubernetes.io/part-of=genealogy-platform
kubectl logs -n gp-argocd -l app.kubernetes.io/name=argocd-server --tail=100
```

Common causes: redis unreachable, vault unreachable (admin
password / signing key), or keycloak OIDC unreachable.

### §8. controller-errors — `ArgoControllerHighErrorRate`

```bash
kubectl logs -n gp-argocd -l app.kubernetes.io/name=argocd-application-controller --tail=200
kubectl get events -n gp-argocd --sort-by=.metadata.creationTimestamp
```

Common causes: downstream API server error, network policy
misconfiguration, vault / keycloak unreachable.

### §9. bootstrap-failed — `ArgoBootstrapJobFailed`

```bash
kubectl describe job argocd-bootstrap -n gp-argocd
kubectl logs -n gp-argocd -l app.kubernetes.io/name=argocd-bootstrap --tail=100
```

Common causes: drift between `platform/argo/*.yaml` and
the live AppProject / Application / Rollout /
AnalysisTemplate set. Re-run `pnpm lint:argo` to verify
the source-of-truth is consistent with the helm template
config.

## Manual override procedure

Every blackout window (`sync-windows.yaml.windows[id:kind:
deny]`) can be overridden by a release-approver human
action with an audit trail:

1. Verify the ticket ID (e.g. `CHG-123456` for
   `change-freeze-q4`).
2. Run `argocd app sync <name> --async` with a `--reason`
   flag carrying the ticket ID.
3. Verify the audit log entry via OTel Collector
   (`actor_pseudo_id` + `window_id` + `reason` + `ticket`).
4. Sync windows for `dna-blackout` require an additional
   release-approver + MFA human action (per E10.2).

## Backup / restore

Argo CD state lives in two places:

1. **Source-of-truth in git.** The canonical Application /
   Rollout / AppProject / AnalysisTemplate set is
   reconstructed from `platform/argo/*.yaml`. A git revert
   + `helm upgrade` is the canonical restore.
2. **Live state in etcd.** The Argo CD server stores
   Application status + secret data + cluster credentials
   in `gp-argocd/argocd-redis` (single-replica StatefulSet)
   + the secrets in `gp-argocd`. Postgres PITR is NOT
   applicable (redis is ephemeral cache). Application
   reconciliation rebuilds the live state from the
   source-of-truth every 180 s.

For DR drill:

```bash
# 1. Verify source-of-truth integrity
pnpm lint:argo

# 2. Verify mirror files
cmp platform/argo/*.yaml platform/helm/genealogy-platform/files/argo/*.yaml

# 3. Re-apply AppProject + Application + Rollout
kubectl apply -f platform/helm/genealogy-platform/templates/components/argo/bootstrap-job.yaml
```

## On-call escalation

| Tier | Team | Contact |
| ---- | ---- | ------- |
| 1 | sre-primary | `#oncall-sre` |
| 2 | platform-primary | `#platform-platform` |
| 3 | AppSec partner | `#appsec` |