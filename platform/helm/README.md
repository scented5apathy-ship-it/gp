# platform/helm

Helm chart sources for deploying the genealogy platform to Kubernetes.

Per `design.md` §13 (GitOps) and `architecture-decisions.md`, every
service, BFF, public-api and shared platform component is shipped as a
versioned Helm chart. Argo CD reconciles these charts against the
target cluster; Argo Rollouts performs canary promotion using SLI
signals from the OTel/Prometheus stack.

Contents (added in later epics):

- `Charts.yaml` — umbrella chart referencing all 11 domain services
  plus the edge (Kong), BFFs, public-api, web-app, Keycloak, OpenFGA,
  Strimzi Kafka, Apicurio Registry, Temporal, Istio addons, Vault,
  S3/MinIO, Valkey, Flagsmith, Grafana/OTel collector.
- `values-<env>.yaml` per environment (`saas`, `onprem`, `staging`,
  `dev`) controlling replica counts, resource budgets, image tags,
  storage classes and feature-flag defaults.
- `templates/<service>/` per workload with PodSpec, Service, HPA,
  PDB, NetworkPolicy, ServiceAccount (Istio workload identity),
  OTel sidecar/agent wiring and sealed-secret placeholders.

## E2.1 — Cluster baseline (added in E2.1)

The umbrella chart `genealogy-platform/` ships the E2.1 cluster
baseline so every environment gets the same security and isolation
posture:

- **Namespaces + quotas** — 8 namespaces (`gp-platform`,
  `gp-edge`, `gp-bff`, `gp-services`, `gp-workers`, `gp-data`,
  `gp-observability`, `gp-argocd`) with Pod Security admission
  labels (`restricted` everywhere except `gp-edge` / `gp-data`),
  per-namespace `ResourceQuota` and a `gp-tenant-shared: "true"`
  label on the namespaces that host multi-tenant workloads.
- **Default-deny NetworkPolicy** — every namespace gets a
  `default-deny` policy (Ingress + Egress) with explicit
  CoreDNS allow rules. Workload charts opt-in by layering allow
  rules; the preflight script (`scripts/check-platform-baseline.mjs`)
  refuses a chart that drops a namespace or removes the policy.
- **PodDisruptionBudget** — default `minAvailable: 1` for every
  platform namespace plus `maxUnavailable: 0` for the
  single-replica control-plane workloads (`kong`, `vault`,
  `keycloak`) so voluntary disruption can never split-brain them.
- **Probes** — `_probes.tpl` helper emits startup/liveness/readiness
  probes pointing at `/healthz/{startup,live,ready}` — the contract
  enforced by `libs/platform-spring-boot-starter`. Workload charts
  are not allowed to override the path.
- **Storage** — three StorageClasses (`gp-data-ssd`,
  `gp-data-hdd`, `gp-data-nvme`) all marked `encrypted: "true"`,
  `reclaimPolicy: Retain`, `volumeBindingMode: WaitForFirstConsumer`.
- **Component contract stubs** — `templates/components/contract-stubs.yaml`
  ships a labelled ConfigMap per shared component (Kong, Vault,
  observability) that encodes the in-umbrella contract:
  Kong is forbidden from domain authorization, Vault is forbidden
  from raw DNA / PII / unencrypted secret storage, observability
  only carries the `tenant_pseudo_id` / `user_pseudo_id` label
  allowlist. Detailed component manifests land in E2.2–E2.10.

Preflight: `pnpm check:platform:baseline` runs
`scripts/check-platform-baseline.mjs` and exits non-zero if the
baseline drifts (missing namespace, missing policy, missing
per-env values, literal secret in values, encrypted StorageClass
missing, Kong runtime invariants — see E2.2 below). Unit tests live
at `scripts/__tests__/check-platform-baseline.test.mjs`.

## E2.2 — Kong Gateway runtime (added in E2.2)

The umbrella chart installs Kong OSS 3.8 (per ADR-E0.5-01 / ADR-E0.5-04)
in DB-less mode into `gp-edge`. The full route + plugin stack lives
in `platform/kong/kong.yml` (mounted via the
`templates/components/kong/declarative-configmap.yaml` ConfigMap) so
every environment — SaaS, on-prem, dev — shares the same route
diff. Per `design.md` §4.1 / `tasks.md` E2.2 Kong handles **only**:

- TLS termination (fronted by the cloud CDN/WAF).
- JWT / OIDC validation slot (key resolver wired in E3.1).
- CORS, request-size, rate limit, correlation ID.
- Routing for the four route classes (`public`, `authenticated`,
  `partner`, `admin`) — each tagged with `route-class:<name>` so a
  drift is grep-able.

Domain authorization (OpenFGA + ABAC, living/DNA/consent) MUST live
in the destination service. Kong's `KONG_PLUGINS` allow-list is
pinned at startup; `scripts/lint-kong-config.mjs` and
`scripts/check-platform-baseline.mjs` reject any plugin that could
carry domain authorization.

The Kong declarative config lives in `files/kong.yml` (so Helm can
mount it via `.Files.Get`). `platform/kong/kong.yml` is a symlink to
that file so the local docker-compose can mount the same source of
truth at `/etc/kong/kong.yml`.

Workload manifests:

- `templates/components/kong/declarative-configmap.yaml` — the
  DB-less config (`kong.yml` mounted from `files/`).
- `templates/components/kong/deployment.yaml` — Kong 3.8 Deployment
  (security context, probes, secrets via External Secrets).
- `templates/components/kong/service.yaml` — Service + ServiceAccount.
- `templates/components/kong/network-policy.yaml` — explicit ingress
  from the Istio ingress gateway + bastion; egress to `gp-bff` /
  `gp-platform` / `gp-observability`.
- `templates/components/contract-stubs.yaml` — runtime contract
  ConfigMap encoding the responsibility / forbidden list.

Validation:

- `pnpm lint:kong` (deep YAML validation).
- `pnpm check:platform:baseline` (static Kong invariants).
- `pnpm smoke:kong` (live `/status`, route match, plugin
  behaviour; runs against a local Kong container).
- `node --test scripts/__tests__/lint-kong-config.test.mjs`
  (4 unit tests).

Owner: platform-primary (`config/teams.yaml`). Reviews: PR template
`/.github/PULL_REQUEST_TEMPLATE/helm.md` (added in E2.9).
