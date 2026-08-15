# Capacity envelope — E13.3

> Per `tasks.md` E13.3 ("Xác định HPA, connection pool, Kafka
> partition, Temporal worker và database thresholds. Ghi capacity
> envelope và scale procedure cho SaaS/on-premise") +
> `scale-and-slo.md` §4 + NFR2 / NFR3 / NFR4.

This document is the operator-readable summary of the E13.3
performance / capacity contract. The runtime guard
(`services/operations-service/.../capacity/CapacityGuard.java`)
and the benchmark driver (`tools/k6/bench-suite.mjs`)
enforce the numbers below.

## 1. Per-environment headroom

| Environment | CPU headroom | Memory headroom | Burst multiplier |
| ----------- | ------------ | --------------- | ---------------- |
| dev         | 1×           | 1×              | 1                |
| saas        | 3×           | 3×              | 3                |
| on_premise  | 1,5×         | 1,5×            | 2                |

## 2. Connection pool ceilings

| Dependency        | Maximum per process |
| ----------------- | ------------------- |
| PostgreSQL        | 75                  |
| Kafka producer    | 200                 |
| Kafka consumer    | 400                 |
| gRPC client       | 500                 |
| Redis client      | 600                 |
| Temporal worker   | 100                 |

Exceeding the ceiling trips a SEV3 alert (`CapacityGuard`).

## 3. Database thresholds

| Metric                          | Threshold     |
| ------------------------------- | ------------- |
| pg_max_connections utilization   | ≤ 0,75        |
| pg_replication_lag_seconds       | ≤ 30 s        |
| pg_longest_query_seconds         | ≤ 60 s        |
| pg_dead_tuples_ratio             | ≤ 0,2         |

## 4. Kafka sizing rule

| Constraint                       | Value      |
| -------------------------------- | ---------- |
| Partitions per 1 000 sustained RPS | 1         |
| Max partitions per topic         | 256        |
| Min in-sync replicas             | 2          |
| Consumer lag ceiling (critical)  | 30 s       |
| Consumer lag ceiling (async)     | 300 s      |

## 5. Temporal sizing rule

| Constraint                                | Value   |
| ----------------------------------------- | ------- |
| Workers per 50 concurrent workflows       | 1       |
| Max concurrent workflows per worker       | 100     |
| Activity heartbeat                        | 30 s    |
| Workflow task timeout                     | 60 s    |

## 6. Core Web Vitals budget

| Metric | Target   | Unit  |
| ------ | -------- | ----- |
| LCP    | ≤ 2500   | ms    |
| CLS    | ≤ 0,1    | ratio |
| INP    | ≤ 200    | ms    |
| TTFB   | ≤ 800    | ms    |
| TTI    | ≤ 2500   | ms    |
| TBT    | ≤ 200    | ms    |

## 7. Regression gates

A 10 % regression in p95 latency OR a 25 % regression in error
rate OR a 15 % regression in throughput OR a 5 % regression in
bundle size blocks the canary via `Argo Rollouts`
`abortOnErrorBudgetBurn`.

## 8. Benchmark scenarios

| Workload class  | Dataset        | Duration | Target VUs | p95 ms | Error rate |
| --------------- | -------------- | -------- | ---------- | ------ | ---------- |
| browse_tree     | 100k_person    | 5m       | 200        | 300    | 0,01       |
| search          | 1m_person      | 5m       | 100        | 1000   | 0,01       |
| detail_read     | 100k_person    | 5m       | 80         | 300    | 0,01       |
| write_proposal  | 100k_person    | 5m       | 50         | 600    | 0,005      |
| media_upload    | 100k_person    | 5m       | 30         | 2000   | 0,005      |
| async_job       | 1m_person      | 10m      | 80         | 15000  | 0,001      |

## 9. References

- `contracts/reliability/performance-capacity-policy.yaml` — contract source of truth.
- `platform/helm/genealogy-platform/files/capacity/hpa-policy.yaml` — HPA + PDB.
- `tools/k6/bench-suite.mjs` — k6 driver gate.
- `apps/web/bench/perf-budget.test.ts` — Core Web Vitals gate.
- `services/operations-service/src/main/java/.../capacity/CapacityGuard.java` — runtime guard.