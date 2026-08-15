# runbook/resilience.md — E13.4 Resilience / chaos operator runbook

> Per `tasks.md` E13.4 ("Kiểm pod kill, network latency,
> Kafka lag, Temporal restart, OpenFGA outage và database
> failover. Xác minh retry budget, circuit breaker, graceful
> degradation và no-duplicate side effects. Argo Rollouts
> phải abort canary trên synthetic regression") +
> `design.md` §13 + NFR3 / NFR4 / NFR5 / NFR7.

This runbook covers the operator-level procedures for the
resilience / chaos engineering surface. Each fault class
ships a Litmus / chaos-mesh scenario under
`tools/chaos/scenarios/`. The `E13.4 ResilienceGuard`
validates the runtime invariants before a chaos run starts.

## 1. Routing

| Symptom                                              | On-call       | Reference |
| ---------------------------------------------------- | ------------- | --------- |
| pod_kill scenario fails                              | sre-primary   | §2.1      |
| network_latency regression detected                  | sre-primary   | §2.2      |
| kafka_lag exceeds ceiling                            | sre-primary   | §2.3      |
| temporal_restart detected                            | sre-primary   | §2.4      |
| openfga_outage cascades to fail_closed               | sre-primary   | §2.5      |
| db_failover triggered                                | sre-primary   | §2.6      |
| otel_collector_down triggers ring buffer             | sre-primary   | §2.7      |
| dns_failure cascades to client retry                 | sre-primary   | §2.8      |
| clock_skew tolerance exceeded                        | sre-primary   | §2.9      |
| cpu / memory / disk pressure                        | sre-primary   | §2.10     |
| tls_rotation not re-issued                          | sre-primary   | §2.11     |
| Argo Rollouts canary ABORT triggered                | sre-primary   | §3        |
| Game day postmortem due                              | sre-primary   | §4        |
| Restore drill due                                    | sre-primary   | §5        |

## 2. Fault class procedures

### 2.1 pod_kill

`tools/chaos/scenarios/pod-kill.yaml`. Verifies PDB +
HPA. Run with `kubectl apply -f tools/chaos/scenarios/pod-kill.yaml`.

```bash
# 1. Confirm HPA recovers.
kubectl -n gp-services get hpa genea-api-default-hpa

# 2. Inspect readiness probes.
kubectl -n gp-services exec deploy/genea-api -- \
  curl -fsS http://localhost:8080/actuator/health/readiness
```

### 2.2 network_latency

`tools/chaos/scenarios/network-latency.yaml`. Verifies
Istio fault filter + circuit breaker on downstream calls.

```bash
# 1. Apply Istio fault filter.
kubectl -n gp-services apply -f tools/istio/fault-delay.yaml

# 2. Verify the circuit breaker tripped.
kubectl -n gp-services logs -l app=genea-api --tail=200 | grep CB
```

### 2.3 kafka_lag

`tools/chaos/scenarios/kafka-lag.yaml`. Pause consumer,
verify DLQ + replay path (E11.5).

### 2.4 temporal_restart

`tools/chaos/scenarios/temporal-restart.yaml`. Verify
workflow continues after restart.

### 2.5 openfga_outage

`tools/chaos/scenarios/openfga-outage.yaml`. Block egress
to OpenFGA, verify fail_closed on every dependent service.

### 2.6 db_failover

`tools/chaos/scenarios/db-failover.yaml`. Promote replica,
verify connection pool re-resolves within 60 seconds.

### 2.7 otel_collector_down

`tools/chaos/scenarios/otel-collector-down.yaml`. Verify
circuit breaker + ring buffer keeps business code alive.

### 2.8 dns_failure

`tools/chaos/scenarios/dns-failure.yaml`. Verify client
retry on SERVFAIL.

### 2.9 clock_skew

`tools/chaos/scenarios/clock-skew.yaml`. Verify client
clock tolerance (`traceparent.version == 00` accepted,
`version >= 1` rejected).

### 2.10 cpu / memory / disk pressure

`tools/chaos/scenarios/{cpu,memory,disk}-pressure.yaml`.
Verify HPA scale-up + pod reschedule.

### 2.11 tls_rotation

`tools/chaos/scenarios/tls-rotation.yaml`. Verify
Vault-managed cert re-issue + Kong mTLS refresh.

## 3. Argo Rollouts canary abort

The `platform/argocd/canary/abort-rules.yaml` Rollout
aborts the canary when:

- `five-xx-ratio > 0.01` for 2 minutes (4 × 30s intervals)
- `p95-latency-regression > 2× baseline` for 3 minutes
- `error-rate-spike > 0.005` for 3 minutes
- `privacy-finding >= 1` (immediate abort + budget freeze)

```bash
# 1. Inspect the canary status.
kubectl -n gp-services argo rollouts get rollout genea-api-canary

# 2. Abort manually if the auto-abort is not triggered.
kubectl -n gp-services argo rollouts abort genea-api-canary
```

## 4. Game day (every 90 days)

Mandatory scenarios: pod_kill, db_failover, kafka_lag,
openfga_outage, tls_rotation. Required artefacts: injects,
observers, rollback, postmortem. Postmortem MUST be filed
under `.kiro/specs/genealogy-platform/evidence/game-day/`.

## 5. Restore drill (every 90 days)

Mandatory components: postgres, kafka, s3, keycloak,
openfga, temporal, vault. Required artefacts: dataset,
RTO, RPO, restoreTooling, restoreEvidence. Restore evidence
MUST be filed under
`.kiro/specs/genealogy-platform/evidence/restore-drill/`.

## 6. References

- `contracts/reliability/resilience-chaos-policy.yaml` — source of truth.
- `tools/chaos/scenarios/*.yaml` — Litmus / chaos-mesh scenarios.
- `platform/argocd/canary/abort-rules.yaml` — Argo Rollouts abort.
- `services/operations-service/src/main/java/.../resilience/ResilienceGuard.java` — runtime guard.