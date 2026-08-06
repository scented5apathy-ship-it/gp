# platform/observability

Observability assets that ship alongside the runtime: Grafana
dashboards, alert rules, OTel Collector pipelines and SLO definitions.

Per `design.md` §13 ("OpenTelemetry SDK/agent → OTel Collector →
Prometheus, Tempo, Loki and Grafana") and `scale-and-slo.md`, every
service exposes RED metrics, Kong latency/status, Temporal workflow
failure, OpenFGA latency, consumer lag, outbox age, DLQ size, media
job state and projection freshness — all labelled with
`tenant_pseudo_id` / `user_pseudo_id` only (no raw PII/DNA).

Contents (added in later epics):

- `grafana/dashboards/` — per-service and per-platform dashboards
  matching the SLO slices in `scale-and-slo.md`.
- `grafana/alert-rules/` — PrometheusRule CRDs consumed by the
  kube-prometheus-stack chart in `platform/helm/`.
- `otel/collector/` — pipelines (receivers/processors/exporters)
  including the redaction processor (`processors/redact`).
- `tempo/` and `loki/` — retention, label policy and exemplars.
- `slo/` — generated SLO manifests (burn-rate alerts, error-budget
  panels) keyed by service SLO class in
  `ownership-catalog.md` §2/§3.

Owner: platform-secondary (Grafana OSS + OTel). Reviewers: SRE,
Security (redaction rules), Privacy (label taxonomy).
