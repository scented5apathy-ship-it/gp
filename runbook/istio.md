# Runbook — Istio service mesh (E2.5)

## Source of truth

| File | Purpose |
| ---- | ------- |
| `platform/istio/mesh-config.yaml` | MeshConfig (REGISTRY_ONLY / MUTUAL_TLS / no mesh-level retry) |
| `platform/istio/peer-auth.yaml` | Per-namespace STRICT mTLS PeerAuthentication list |
| `platform/istio/authz-policies.yaml` | AuthorizationPolicy list (deny-by-default + dna/media isolation) |
| `platform/istio/telemetry.yaml` | Retry / timeout / tracing / metrics / accesslog policy |
| `platform/helm/genealogy-platform/files/istio/*.yaml` | Mirror of the four above (chart `helm template` reads from here) |
| `platform/helm/genealogy-platform/templates/components/istio/*` | Helm templates for the 4 ConfigMaps + ServiceAccount + RBAC + bootstrap Job + NetworkPolicy |
| `platform/observability/alerts/istio-rules.yaml` | 9 Prometheus alerts across 5 rule groups |

The platform deep linter is `scripts/lint-istio-config.mjs`
(`pnpm lint:istio`). The structural baseline check is
`scripts/check-platform-baseline.mjs` (E2.5 invariants).

## Recovery procedures

Each playbook requires `kubectl` + `istioctl` (Istio 1.23.x) on
PATH and a working kubecontext. The umbrella chart's
`istio-bootstrap` Job is the canonical path to apply mesh config;
operators never `kubectl apply` directly per `design.md` §13.

### 1. Control plane down — `<runbook:istio#control-plane-down>`

`IstioControlPlaneDown` fired (istiod unreachable for 2m).

1. `kubectl -n gp-platform get pods -l app=istiod` — confirm the
   Deployment is down.
2. `kubectl -n gp-platform describe deployment istiod` — check
   events (image pull errors, OOMKilled, node eviction).
3. `kubectl -n gp-platform logs -l app=istiod --tail=500` — search
   for `panic`, `error`, `FATAL`.
4. If the operator crashed during a CRD upgrade, rollback:
   `kubectl -n gp-platform rollout undo deployment/istiod`.
5. If the cert-manager CA is unavailable, the mesh cannot mint
   workload identities. Cross-check `<runbook:kubernetes#cert-manager>`.
6. Once istiod is Ready, re-run the bootstrap Job:
   `kubectl -n gp-platform create job --from=cronjob/istio-bootstrap
   istio-bootstrap-restart` (or `helm upgrade` chart).

### 2. Pilot push errors — `<runbook:istio#pilot-push-errors>`

`IstioPilotPushErrors` fired (xDS push error rate > 0.1/s for 5m).

1. `kubectl -n gp-platform logs -l app=istiod --tail=200 | grep -i
   error` — find the CRD schema mismatch.
2. The four source-of-truth ConfigMaps are rendered by the
   `istio-bootstrap` Job. Compare the rendered manifest against
   the deployed CRDs:
   `kubectl -n gp-platform get configmap genea-istio-mesh-config
   -o yaml | grep -A 200 mesh.yaml`.
3. Re-run `pnpm lint:istio && pnpm check:platform:baseline` to
   catch structural drift.
4. If a CRD schema is incompatible, pin the operator's chart
   version per `ADR-E0.5-01` (Istio 1.23.x).

### 3. mTLS handshake failures — `<runbook:istio#mtls-handshake>`

`IstioMtlsHandshakeFailures` / `IstioMtlsHandshakeFailuresHigh`
fired.

1. Check the JSON access log for the principal + namespace:
   `kubectl -n gp-platform logs -l app=istiod --tail=200 | grep -i
   handshake`.
2. The most common cause is a workload whose service-account-token
   is missing `audience: istio-ca`. Check the workload's
   `serviceAccountToken` projection:
   `kubectl -n gp-services get sa <sa> -o yaml`.
3. If the cert-manager CA is rotating, Pilot may temporarily
   reject existing workload certs. Check the
   `cert-manager/certificates` CRs in `gp-platform`.
4. For critical alerts, page security on-call — a sustained
   handshake failure spike is a potential forged-identity attempt.

### 4. AuthorizationPolicy denial spike — `<runbook:istio#authz-denial>`

`IstioAuthzDenialSpike` / `IstioAuthzDenialSpikeCritical` fired.

1. Identify the source principal + destination:
   `kubectl -n gp-platform logs -l app=istiod --tail=500 | grep
   "RBAC: denied"`.
2. Cross-check the source's SPIFFE ID against the platform
   `AuthorizationPolicy` list:
   `kubectl -n gp-<ns> get authorizationpolicy -o yaml`.
3. If the denied request is legitimate (new workload, new
   cross-namespace path), add an `ALLOW` rule to
   `platform/istio/authz-policies.yaml` and re-run `helm upgrade`.
4. If the denied request is NOT legitimate, the SPIFFE ID is
   forged. Page security on-call + freeze further CRD changes
   until the source is identified.

### 5. Upstream retry spike — `<runbook:istio#upstream-failures>`

`IstioUpstreamFailureSpike` / `IstioUpstreamFailureSpikeCritical`
fired.

1. The mesh has `retryBudget: null`; the application owns the
   retry policy. The spike implies the application retry budget
   is being consumed faster than expected.
2. Check the destination service's SLO dashboard for the
   upstream failure rate.
3. Engage the E13.4 chaos playbook if the spike is sustained
   (cascade failure risk).
4. Consider Argo Rollouts canary abort if the spike correlates
   with a recent rollout.

### 6. Bootstrap Job failed — `<runbook:istio#bootstrap-failed>`

`IstioBootstrapJobFailed` fired.

1. `kubectl -n gp-platform get jobs -l app.kubernetes.io/component=
   istio-bootstrap` — confirm the Job Pod failed.
2. `kubectl -n gp-platform logs -l app.kubernetes.io/component=
   istio-bootstrap --tail=200` — find the rendered manifest that
   failed to apply.
3. The four source-of-truth ConfigMaps must remain byte-identical
   to the chart mirror files. The linter enforces this invariant.
4. After fixing the source-of-truth, re-run `helm upgrade` — the
   chart will delete the failed Job and recreate it.

## Backup / restore

The four source-of-truth files are the mesh config. They are
versioned in git and exported via `platform/helm/genealogy-platform/files/istio/`.
The chart's `istio-bootstrap` Job is idempotent: every `helm upgrade`
re-applies the four ConfigMaps; the underlying CRDs are
server-side-apply'd so subsequent reconciles do not fight the
operator.

DR restoration is a Helm rollback: `helm history <release>` →
`helm rollback <release> <revision>`. The operator reapplies the
previous four CRDs.

## Ownership

`OWNERS` mirror — primary = `platform`, secondary =
`@genealogy/security`, on-call = `platform-primary`.
