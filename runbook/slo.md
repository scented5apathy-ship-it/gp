# runbook/slo.md — E13.2 SLO / alert operator runbook

> Per `tasks.md` E13.2 ("Định nghĩa SLI/error budget cho edge,
> domain API, Kafka, Temporal, OpenFGA và storage. Alert
> actionable với owner, severity, dashboard và runbook link.
> Giảm cardinality; tenant ID chỉ pseudonymous và không
> dùng raw user/person IDs") + `design.md` §13 + NFR2/NFR3/NFR5.

This runbook is the single source of truth the SRE on-call
references when an SLO alert fires. Each alert rule from
`platform/observability/alerts/*.yaml` MUST link here via
the `runbook_url` annotation; the E13.2 linter fails closed
if any rule is missing the runbook URL, dashboard URL, owner,
severity, action or notify channel.

## 1. Routing

| Severity | Action  | Page roles                                     | Notify channel | Response | Runbook section |
| -------- | ------- | ---------------------------------------------- | -------------- | -------- | --------------- |
| SEV1     | PAGE    | oncall-primary + appsec-primary + dpo-delegate | `gp-sev1`      | 15 min   | §3              |
| SEV2     | PAGE    | oncall-primary                                 | `gp-sev2`      | 30 min   | §4              |
| SEV3     | TICKET  | oncall-secondary                               | `gp-sev3`      | 4 h      | §5              |
| SEV4     | SILENT  | oncall-rotation                                | `gp-noise`     | 24 h     | §6              |

SEV1 freezes release and freezes the error budget immediately.
A privacy / DNA / consent finding of severity 1 also freezes
the budget regardless of burn-rate (NFR7 + ADR-E0.5-16).

## 2. Burn-rate cookbook (Google SRE multi-window)

| Window | Factor | Action  | When                                |
| ------ | ------ | ------- | ----------------------------------- |
| 1 m    | 14.4x  | PAGE    | short burst                         |
| 5 m    | 14.4x  | PAGE    | short burst                         |
| 30 m   | 14.4x  | PAGE    | search                              |
| 1 h    | 14.4x  | PAGE    | page (default)                      |
| 2 m    | 1x     | PAGE    | synthetic probe                     |
| 6 h    | 6x     | TICKET  | ticket (default)                    |
| 10 m   | 1x     | PAGE    | PII redaction coverage              |
| 15 m   | 2x     | TICKET  | projection freshness                |
| 24 h   | 3x     | TICKET  | SLO review                          |
| 3 d    | 1x     | SILENT  | long trend                          |

## 3. SEV1 — page immediately (15 min response)

### 3.1 ApiAvailabilitySev1

`api_availability` 1h burn-rate > 14.4x or PII redaction
coverage drops below 100 %.

```bash
# 1. Confirm the error budget.
kubectl -n gp-observability exec deploy/genea-otel-collector -- \
  curl -fsS http://localhost:8888/metrics | grep http_server_requests

# 2. Identify the failing route from the recording rule.
#    Grafana dashboard: api-overview / panel "5xx rate by route".

# 3. Roll the latest canary if Argo Rollouts is in mid-flight.
kubectl -n gp-<service> argo rollouts abort <rollout>

# 4. Open incident in #gp-sev1 and assign SEV1 commander.
```

### 3.2 PiiRedactionCoverageLost

`pii_redaction_coverage` < 1.0 for 10 minutes.

This is a SEV1 even if business traffic is healthy because
the OTel Collector `redaction` processor is misconfigured.
Open the dashboard `otel-collector / redaction hits` and
verify every regex from `E13.1 telemetry-policy.yaml`
`telemetryRedactionPatterns` is present in the runtime
config. If a pattern is missing, scale the OTel Collector
back to the previous ConfigMap and audit
`telemetry.redactionRuleMissing` events in Loki.

## 4. SEV2 — page on-call (30 min response)

### 4.1 ApiReadP95Burn1h / ApiWriteP95Burn1h

`api_read_p95` or `api_write_p95` 1h burn-rate > 14.4x.

```bash
# 1. Identify the slow route.
#    Grafana dashboard: api-overview / panel "latency p95 by route".

# 2. Check upstream dependencies (Kong, BFF, Postgres, Kafka).
kubectl -n gp-edge logs -l app=genea-kong --tail=200
kubectl -n gp-data logs -l app=postgres-primary --tail=200

# 3. If Postgres is the bottleneck, fail over to the replica.
kubectl -n gp-data exec sts/genea-postgres-replica -- \
  pg_ctl promote
```

### 4.2 ConsumerLagCriticalP99

`consumer_lag_records` for audit / DNA / tree topics > 1 000
records for 5 minutes.

```bash
# 1. Inspect the consumer group.
kubectl -n gp-events exec genea-kafka-cli -- \
  kafka-consumer-groups --bootstrap-server kafka-bootstrap:9092 \
    --describe --group <consumer>

# 2. Inspect the DLQ topic.
kubectl -n gp-events exec genea-kafka-cli -- \
  kafka-console-consumer --topic <topic>.dlq \
    --bootstrap-server kafka-bootstrap:9092 --max-messages 50

# 3. Replay via E11.5 DLQ replay (snapshot + lineage hash required).
```

### 4.3 OutboxAgeP99 / WorkflowFailurePerHour

`outbox_age_seconds` > 300 s for 5 minutes OR
`workflow_failure_total` > 5 / hour.

```bash
# 1. Inspect the outbox publisher.
kubectl -n gp-<service> logs -l component=outbox-relay --tail=200

# 2. Inspect the Temporal namespace.
kubectl -n gp-workflow exec genea-temporal-cli -- \
  temporal workflow list --query "ExecutionStatus='Failed'"

# 3. Restart the outbox relay (idempotent).
kubectl -n gp-<service> rollout restart deploy/outbox-relay
```

### 4.4 SyntheticProbeFailedCore

`synthetic_availability` < 99 % for 2 minutes.

Synthetic probes cover: Kong / Keycloak / OpenFGA /
Postgres / Kafka / Temporal / Object Storage / Vault /
Flagsmith / OTel Collector. A 2-minute outage on any probe
paging on-call. Cross-reference `runbook/observability.md`
for component-specific remediation.

## 5. SEV3 — open ticket (4 h response)

### 5.1 ProjectionFreshnessP99 / SearchP95Burn30m / ApiReadP95Burn6h

Open a ticket in `gp-sre` queue, link the Grafana panel, and
assign to the relevant service owner. SEV3 does not page but
still consumes error budget; if burn-rate stays above 6x for
6 hours it auto-promotes to SEV2.

## 6. SEV4 — silent review (24 h response)

`api_read_p95` 3d burn-rate > 1x. Aggregate in the weekly SLO
review meeting; promote to SEV3 if the trend continues for 2
consecutive weeks.

## 7. Error budget freeze (NFR7 + ADR-E0.5-16)

- When 50 % of the monthly budget is consumed in the first
  week, the runtime freezes non-critical releases and the
  `Argo Rollouts` `abortOnErrorBudgetBurn` rule promotes any
  in-flight canary to abort.
- A SEV1 alert or any privacy/DNA finding freezes the budget
  immediately regardless of burn-rate.

## 8. Cardinality guards

The recording-rule layer caps label values at
`tenant_pseudo_id` ≤ 50 000 and `user_pseudo_id` ≤ 200 000.
Dimensions beyond the ceiling are dropped and emit
`telemetry.label.cardinality.exceeded`. This is required by
the Prometheus scrape config in `platform/grafana/prometheus.yaml`.

## 9. References

- `contracts/reliability/slo-alert-policy.yaml` — source of truth.
- `platform/observability/alerts/slo-alert-rules.yaml` — PrometheusRule.
- `platform/observability/alerts/burn-rate-rules.yaml` — burn-rate rule group.
- `runbook/observability.md` — Grafana / Loki / Tempo / OTel Collector.
- `services/operations-service/src/main/java/.../slo/SloGuard.java` — runtime guard.