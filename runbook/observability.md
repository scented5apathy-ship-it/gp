# Observability runbook — Grafana OSS stack

> Per `tasks.md` E2.10 ("OTel Collector, Prometheus, Loki,
> Tempo và Grafana với retention đã chốt; Dashboard cho
> Kong, Kafka, Temporal, OpenFGA, Istio, Vault, database
> và workloads; Áp log redaction và tenant pseudonymous
> labels") + `design.md` §13 + NFR1/NFR3/NFR4.

This runbook covers the operator-level remediation
procedures for the Grafana OSS stack (OTel Collector +
Prometheus + Loki + Tempo + Grafana). It assumes the on-call
SRE has read access to the platform namespace
`gp-observability` + the Keycloak realm
`genealogy`.

## 1. Routing

| Symptom                                            | On-call                | Reference                          |
| -------------------------------------------------- | ---------------------- | ---------------------------------- |
| OTel Collector down / memory / queue full          | `sre-primary`          | §2 otel                            |
| Prometheus scrape failing                          | `sre-primary`          | §3 prometheus                      |
| API availability burn > 0.1% for 5m                | `sre-primary`          | §3.2 api-availability              |
| PII redaction coverage lost (10m)                  | `sre-primary` + `appsec-primary` (page both) | §4 pii-redaction |
| Loki write path down                               | `sre-primary`          | §5 loki                            |
| Tempo write path down                               | `sre-primary`          | §6 tempo                          |
| Grafana down                                       | `sre-primary`          | §7 grafana                        |

## 2. otel-collector

### 2.1 otel-memory — `OtelCollectorMemoryPressure`

The OTel Collector heap allocation has exceeded 8 GiB for
10m. Most likely cause: the redaction regex is
back-tracking on long match strings, or a tail-based
sampler is creating more spans than expected.

```bash
# 1. Check current heap allocation.
kubectl -n gp-observability exec deploy/genea-otel-collector -- \
  curl -fsS http://localhost:8888/metrics | grep process_runtime_go_memstats_heap_alloc_bytes

# 2. Inspect redaction matches.
kubectl -n gp-observability exec deploy/genea-otel-collector -- \
  curl -fsS http://localhost:8888/metrics | grep otelcol_redaction_matches_total

# 3. Restart the collector (rolls the pod, releases heap).
kubectl -n gp-observability rollout restart deploy/genea-otel-collector
```

### 2.2 otel-queue — `OtelCollectorQueueNearFull`

The OTel Collector exporter queue is > 90 % capacity for
10m. Most likely cause: a downstream exporter (Tempo / Loki)
is unreachable. Cross-check §5 + §6.

### 2.3 dropped-spans — `OtelCollectorDroppedSpans`

Spans are being dropped at the `filter/logs` processor.
Inspect the dropped bodies for `raw_dna` / `raw_pii` /
`consent_dropped` substrings:

```bash
kubectl -n gp-observability exec deploy/genea-otel-collector -- \
  curl -fsS http://localhost:8888/metrics | grep otelcol_processor_dropped_spans
```

If the drop rate is > 0, the offending service is emitting
a label that the filter rejects. The service must remove
the label at the source (not bypass the filter).

## 3. prometheus

### 3.1 scrape-failing — `PrometheusScrapeFailing`

Prometheus has been unable to scrape a target for 10m.

```bash
# 1. Identify the failing target.
kubectl -n gp-observability exec sts/genea-prometheus-0 -- \
  wget -qO- http://localhost:9090/api/v1/targets | \
  jq '.data.activeTargets[] | select(.health != "up") | {labels, health, lastError}'

# 2. Verify the NetworkPolicy allows scrape traffic.
kubectl -n gp-observability get networkpolicy genea-prometheus-deny -o yaml
```

The NetworkPolicy allows ingress from `gp-edge`, `gp-bff`,
`gp-services`, `gp-workers`, `gp-platform`. If a new
namespace is added, update `network-policies.yaml`.

### 3.2 api-availability — `GrafanaApiAvailabilityBurn`

API availability < 99.9% over 5m (NFR3 SLO). The 5xx error
rate is the leading indicator. Cross-reference the
service-specific runbook (kong / kong-bff / service).

### 3.3 tsdb-compaction — `PrometheusTSDBCompactionFailing`

TSDB compaction has failed. Retention enforcement may be at
risk. Increase the PVC size or the compactor resources.

## 4. pii-redaction — `GrafanaPiiRedactionCoverageLost`

The OTel Collector `redaction` processor has stopped
emitting matches. This means the redaction pipeline is
broken — page the on-call SRE + AppSec immediately.

```bash
# 1. Verify the processor is wired.
kubectl -n gp-observability exec deploy/genea-otel-collector -- \
  curl -fsS http://localhost:8888/metrics | grep -E 'otelcol_processor_(accepted|dropped|rejected)_spans_total'

# 2. Verify the redaction rules are intact.
kubectl -n gp-observability get cm genea-otel-collector-config -o jsonpath='{.data.config\.yaml}' | grep -A 1 'name: raw-dna-marker'

# 3. If the rules are missing, the deep linter has been
#    bypassed. Roll back the chart + open a Sev-1 incident.
```

## 5. loki

### 5.1 write-path-down — `LokiWritePathDown`

Loki is unreachable from the OTel Collector for 5m.

```bash
kubectl -n gp-observability rollout status sts/genea-loki
kubectl -n gp-observability get pods -l app.kubernetes.io/component=loki
```

If the StatefulSet is healthy, verify the NetworkPolicy:
`genea-loki-deny` allows ingress from `gp-observability` +
`gp-edge` + `gp-bff` + `gp-services` + `gp-workers`.

### 5.2 denied-label — `LokiDeniedLabelSpike`

Loki returned > 100 HTTP 400 in the last 10m. Most likely
cause: a service is emitting `email` / `oidc_subject` /
`raw_dna` / `raw_pii` (forbidden label).

```bash
# 1. Identify the offending service via Loki query.
# (run inside Grafana Explore)
{service_name=~".+"} | json | email=~".+"

# 2. Once identified, remove the label at the source —
#    NEVER bypass the redaction filter.
```

## 6. tempo

### 6.1 write-path-down — `TempoWritePathDown`

Tempo is unreachable from the OTel Collector for 5m.
Same remediation as §5.1.

### 6.2 block-retention — `TempoBlockRetentionExpiring`

The compactor is lagging. Increase compactor resources or
shorten `block_retention` in `platform/grafana/tempo.yaml`.

### 6.3 wal-growth — `TempoWALGrowthHigh`

WAL trace creation > 1000/s for 15m. The trace sampler is
creating more traces than expected. Cross-check the
sampling config in the OTel Collector.

## 7. grafana

### 7.1 grafana-down — `GrafanaDown`

Grafana is unreachable for 5m.

```bash
kubectl -n gp-observability rollout status deploy/genea-grafana
kubectl -n gp-observability get pods -l app.kubernetes.io/component=grafana
```

### 7.2 audit-zero — `GrafanaDashboardAuditVolumeZero`

No dashboard audit events have been emitted for 30m. The
audit pipeline is broken.

```bash
# 1. Verify the audit pipeline is wired.
kubectl -n gp-observability get cm genea-otel-collector-config -o jsonpath='{.data.config\.yaml}' | grep -A 2 'audit:'

# 2. Verify the audit receiver is listening.
kubectl -n gp-observability exec deploy/genea-otel-collector -- \
  curl -fsS http://localhost:8888/metrics | grep otcol_exporter_sent_spans
```

## 8. Restore / Backup

Per `platform/helm/genealogy-platform/files/grafana/`
contract, the observability data is backed up via the
platform baseline (E2.1):

- Prometheus: snapshot via `promtool admin dump` to the
  `genea-observability-snapshots` S3 bucket.
- Loki: chunk store snapshot via `mc mirror` to the same
  bucket.
- Tempo: S3 backend already replicates via the AWS S3
  cross-region replication (SaaS) or customer Vault-managed
  replication (on-prem).

Restore procedure: see
`docs/platform-setup.md` §7 Backup / Restore.