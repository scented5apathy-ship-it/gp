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

Owner: platform-primary (`config/teams.yaml`). Reviews: PR template
`/.github/PULL_REQUEST_TEMPLATE/helm.md` (added in E2.9).
