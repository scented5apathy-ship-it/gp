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
missing). Unit tests live at
`scripts/__tests__/check-platform-baseline.test.mjs`.

Owner: platform-primary (`config/teams.yaml`). Reviews: PR template
`/.github/PULL_REQUEST_TEMPLATE/helm.md` (added in E2.9).
