# platform/grafana — E2.10 Grafana OSS stack
#
# Source-of-truth for the platform observability stack:
#
# | File                     | Contract                                                |
# | ------------------------ | ------------------------------------------------------- |
# | `otel-collector.yaml`    | OTel Collector pipeline (receivers / processors / exporters) |
# | `prometheus.yaml`        | Prometheus scrape + recording rules + SLO alerts        |
# | `loki.yaml`              | Loki schema + retention + compactor                     |
# | `tempo.yaml`             | Tempo OTLP receiver + ingester + storage                |
# | `dashboards.yaml`        | 9 mandatory dashboards + template variables + audit     |
# | `grafana.yaml`           | Grafana server config (Keycloak OIDC SSO + 2FA + audit) |
#
# Per `tasks.md` E2.10 ("OTel Collector, Prometheus, Loki,
# Tempo và Grafana với retention đã chốt; Dashboard cho Kong,
# Kafka, Temporal, OpenFGA, Istio, Vault, database và
# workloads; Áp log redaction và tenant pseudonymous labels
# để tránh cardinality/PII leak") + `design.md` §13 +
# `requirements.md` NFR1/NFR3/NFR4, the source-of-truth is
# mirrored byte-identical into
# `platform/helm/genealogy-platform/files/grafana/` so the
# umbrella chart can render the workloads via Helm.
#
# ## Reuse the platform; do not rebuild
#
# - **OTel Collector** = upstream `otel/opentelemetry-collector-contrib`
#   (ADR-E0.5-01 baseline, version `0.110.0`).
# - **Prometheus** = upstream `prometheus/prometheus` (version
#   `v2.55.0`).
# - **Loki** = upstream `grafana/loki` (version `3.4.0`).
# - **Tempo** = upstream `grafana/tempo` (version `2.7.0`).
# - **Grafana** = upstream `grafana/grafana` (version `11.3.0`).
#
# We do NOT build a custom metric backend, custom log
# aggregator, custom trace storage, or custom dashboard UI.
# The contract enforces the upstream configuration.
#
# ## Contract enforcer
#
# `scripts/lint-grafana-config.mjs` parses every YAML, asserts
# the documented contract, and fails CI on any drift.
# `scripts/smoke-grafana.mjs` runs a structural probe (15 PASS
# expected). Both are wired into `pnpm lint:grafana` and
# `pnpm smoke:grafana`.
#
# ## Tenant isolation + PII
#
# Every metric / log / trace / audit resource attribute uses
# `tenant_pseudo_id` (HMAC-SHA256 keyed by Vault-managed
# pepper) — never raw `tenant_id`. Raw PII / DNA / OIDC
# subjects are scrubbed by the OTel Collector `redaction`
# processor before any exporter writes. The deep linter
# rejects the forbidden labels (`tenant_id` / `user_id` /
# `email` / `oidc_subject` / `raw_dna` / `raw_pii`) at the
# source.
#
# ## Audit
#
# Every Grafana dashboard view + every Prometheus / Loki /
# Tempo query publishes a `dashboard_viewed` / `query_executed`
# event to the OTel Collector audit pipeline. The audit
# pipeline scrubs `email` / `oidc_subject` and emits
# `actor_pseudo_id` only.
#
# ## Retention
#
# - Prometheus: dev 7d / on-prem 30d / saas 90d
# - Loki: dev 7d / on-prem 30d / saas 90d
# - Tempo: dev 3d / on-prem 14d / saas 30d
#
# Retention is enforced by the Loki compactor (`compactor.
# retention_enabled: true`) + Tempo compactor
# (`block_retention: 720h`) + Prometheus `--storage.tsdb.
# retention.time` argument rendered by the umbrella chart.
#
# ## Backup / restore
#
# The Loki / Tempo / Prometheus data PVCs are snapshotted by
# the platform baseline (E2.1 backup CronJob; the bucket
# `genea-tempo-blocks` is replicated cross-region for SaaS).
# Per `runbook/observability.md` §5 restore procedure.