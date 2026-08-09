# Runbook — Valkey (Redis-compatible) cache (E2.7)

## Source of truth

| File                                                       | Purpose                                                                                  |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `platform/storage/valkey-config.yaml`                      | Valkey server posture (image pin, region, TLS, ACL, persistence, TTL ceilings, required users) |
| `platform/helm/genealogy-platform/files/storage/valkey-config.yaml` | Mirror of the above (chart `helm template` reads from here)                              |
| `platform/helm/genealogy-platform/templates/components/cache/*`   | Helm templates for the Valkey StatefulSet + Services + SA + Secrets + ACL ConfigMap + NetworkPolicy |
| `platform/observability/alerts/valkey-rules.yaml`          | 9 Prometheus alerts across 4 rule groups                                                 |

The platform deep linter is `scripts/lint-s3-config.mjs`
(`pnpm lint:s3`). The structural baseline check is
`scripts/check-platform-baseline.mjs` (E2.7 invariants).

## Recovery procedures

Each playbook requires `kubectl` + `valkey-cli` on
PATH and a working kubecontext. The umbrella chart
ships the `valkey-exporter` sidecar so the SLI
metrics are scrapeable. Operators never edit the
ACL by hand; the chart re-renders the
`genea-cache-valkey-acl` ConfigMap on every
`helm upgrade`.

### 1. Server down — `<runbook:valkey#server-down>`

`ValkeyServerDown` fired (exporter unreachable for 1m).

1. `kubectl -n gp-data get pods -l app.kubernetes.io/component=cache`
   — confirm the StatefulSet is down.
2. `kubectl -n gp-data describe statefulset valkey`
   — check events (image pull errors, OOMKilled,
   node eviction).
3. `kubectl -n gp-data logs -l app.kubernetes.io/component=cache
   --tail=500` — search for `panic`, `error`, `FATAL`.
4. If the StatefulSet crashed during a chart upgrade,
   rollback: `kubectl -n gp-data rollout undo
   statefulset/valkey`.
5. If the underlying `gp-data-ssd` PVC is full, expand
   it: `kubectl -n gp-data edit pvc data-valkey-0`.
6. Cache data is fail-safe; domain services rebuild
   cache state from PostgreSQL on Valkey restart.

### 2. Sentinel no master — `<runbook:valkey#no-master>`

`ValkeySentinelNoMaster` fired.

1. `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD sentinel masters`
   — show the master's status.
2. If no master is reported, the cluster is in a
   no-master state. Run:
   `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD sentinel failover`.
3. If failover does not elect a master, the
   replication chain is broken. Check
   `valkey-cli -a $VALKEY_PASSWORD cluster nodes` —
   a missing replica indicates network or storage
   failure.
4. After failover, re-run the bootstrap Job:
   `kubectl -n gp-data create job --from=...`.

### 3. GET latency high — `<runbook:valkey#get-latency>`

`ValkeyGetLatencyHigh` (5ms p95) /
`ValkeyGetLatencyCritical` (20ms p95) fired.

1. `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD slowlog get 50`
   — show the last 50 slow queries.
2. `valkey-cli -a $VALKEY_PASSWORD info clients`
   — check the connected client count.
3. `valkey-cli -a $VALKEY_PASSWORD info memory`
   — check the used memory + fragmentation ratio.
4. A high fragmentation ratio (> 1.5) means the
   `activedefrag` is needed:
   `valkey-cli -a $VALKEY_PASSWORD config set
   activedefrag yes`.
5. If the slow log shows a `KEYS` call, the
   workload is misconfigured (the linter rejects
   `KEYS *` from service users).

### 4. Memory high / evictions — `<runbook:valkey#memory-high>` + `<runbook:valkey#evictions>`

`ValkeyMemoryHigh` (80%) / `ValkeyMemoryCritical`
(95%) / `ValkeyEvictionsHigh` (100 keys/s) fired.

1. `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD info keyspace`
   — show the per-DB key count + TTL.
2. The per-class TTL is pinned in
   `platform/storage/valkey-config.yaml.ttl`. A
   class with no TTL is a bug — fix the application
   code.
3. The OpenFGA / ABAC decision cache must be
   invalidated on tuple write / policy change (E3.4).
   A leaking cache is a canonical cause of memory
   pressure.
4. Scale the StatefulSet (Valkey is sharded by
   key prefix `gp:{tenant_pseudo_id}:`). The
   `maxmemory` per replica is pinned in
   `values.yaml`; on SaaS, the cluster operator
   scales the cluster.

### 5. Hit ratio low — `<runbook:valkey#hit-ratio>`

`ValkeyHitRatioLow` (< 85%) /
`ValkeyHitRatioCritical` (< 50%) fired.

1. `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD info stats` —
   show the keyspace hits / misses.
2. A high miss rate means the cache key is changing
   (e.g. the application code includes a timestamp
   in the key). Inspect a few `KEYS` from the
   observability user (read-only):
   `valkey-cli -a $VALKEY_PASSWORD --user observability
   keys "gp:*:openfga:*" | head -20`.
3. The OpenFGA / ABAC cache must be invalidated
   on tuple write / policy change. Verify the
   Kafka invalidation topic is healthy.

### 6. ACL authentication failures — `<runbook:valkey#acl-failures>`

`ValkeyAclAuthFailures` fired.

1. `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD acl whoami` —
   identify the failing user.
2. `valkey-cli -a $VALKEY_PASSWORD acl list`
   — show the current ACL.
3. The ACL is loaded from the
   `genea-cache-valkey-acl` ConfigMap. The linter
   rejects `@admin` for service users. A
   misconfigured workload may be using a stale
   password — the password is sourced from the
   `valkey-credentials` Secret.
4. After a password rotation, restart the
   affected workload pods.

### 7. Connection pool exhaustion — `<runbook:valkey#connections>`

`ValkeyConnectionsHigh` (5000 connections) fired.

1. `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD info clients`
   — show the per-IP client count.
2. The platform's `valkey-client` library enforces
   a per-pod pool of 16 + 8 idle. A misconfigured
   workload may not return connections.
3. A leak in a downstream service is the canonical
   cause. Identify the workload from the client
   IP and roll back the deployment.

## Backup + restore

Valkey is a cache, NOT a source of truth. Backup
is optional and uses `SAVE` / `BGSAVE`. The
CronJob (`backups.valkey` in the umbrella chart)
uploads the RDB file to the S3 `import-export`
bucket. Restore is a controlled process:

1. Stop the workload that owns the cache class
   (the application code rebuilds from PostgreSQL).
2. `kubectl -n gp-data exec -it valkey-0 --
   valkey-cli -a $VALKEY_PASSWORD shutdown
   nosave` — flush in-memory state.
3. Re-deploy the StatefulSet — Valkey boots
   empty; the application code rebuilds the
   cache.

The platform does NOT treat Valkey as a
source-of-truth. Per `design.md` §3.1, the cache
is fail-safe only; domain services must rebuild
cache state from PostgreSQL on Valkey restart.
