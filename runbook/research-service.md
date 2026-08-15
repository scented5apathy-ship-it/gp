# runbook/research-service.md — research-service operator runbook

Operator-facing runbook for the `research-service` Spring
Boot microservice. Mirrors `runbook/audit.md` + `runbook/tenant-service.md`
style.

## Source-of-truth map

| File | Purpose |
| ---- | ------- |
| `services/research-service/build.gradle.kts` | JDK 21 toolchain, Spring Boot 3.x, Flyway, gRPC starter wired (server port 9090 from `GRPC_SERVER_PORT`) |
| `services/research-service/src/main/resources/db/migration/V1__baseline_schema.sql` | Baseline Flyway (1 schema + `research_service_app` role + `current_tenant_id()` helper) |
| `services/research-service/src/main/resources/db/migration/V2__research_aggregate.sql` | 6 research aggregate tables (`repositories`, `sources`, `citations`, `research_tasks`, `hypotheses`, `conflicts`) + 5 bridge tables + RLS-FORCE + audit columns |
| `services/research-service/src/main/resources/db/migration/V3__outbox_and_workspace.sql` | Transactional outbox (`research_service.outbox`) + workspace projection (`research_service.workspace_projection`) with redaction overlay |
| `services/research-service/src/main/resources/db/migration/V4__consumer_inbox.sql` | Durable consumer inbox (`research_service.consumer_inbox`) backing idempotent `TreeVisibilityChanged` + `PersonRedacted` re-delivery handling |
| `services/research-service/src/main/java/.../domain/` | E6.1a aggregate records + invariants + provenance executor |
| `services/research-service/src/main/java/.../application/` | E6.1c REST + E6.1d command services + RLS interceptor |
| `services/research-service/src/main/java/.../grpc/` | E6.1d gRPC service adapters |
| `services/research-service/src/main/java/.../outbox/` | E6.1d framework-free relay + JdbcTemplate repository + Spring Kafka producer adapter; E6.1e relay runner + JdbcTemplate outbox repo |
| `services/research-service/src/main/java/.../workspace/` | E6.1d projection + redaction overlay service; E6.1e consumer durable inbox service + Spring `@KafkaListener` adapter |
| `services/research-service/src/main/resources/application.yml` | Datasource URL from env (no literal credentials), Hikari `min-idle: 2` / `max-pool: 10`, gRPC `server.port: ${GRPC_SERVER_PORT:9090}`, probe paths `/actuator/health/liveness` + `/actuator/health/readiness` |
| `services/research-service/OWNERS` | Ownership mirror (`config/teams.yaml`) |
| `contracts/protobuf/research/v1/*.proto` | E6.1d gRPC service surface |
| `contracts/events/research/v1/*.avsc` | E6.1d Avro schemas (`CitationCreated`, `ClaimVerified`, `ConflictDetected`) |
| `contracts/research/research-policy.yaml` | E6.1a closed-set vocabularies + state matrices + invariants |
| `platform/helm/genealogy-platform/templates/components/research-service/` | E6.1e Helm workload (Deployment, Service, ServiceAccount, NetworkPolicy, PDB, ConfigMap, ExternalSecret) |
| `platform/helm/genealogy-platform/values.yaml::services.research` | E6.1e per-env workload values (replica, probes, OpenFeature sidecar) |

## Capacity skeleton

Derived from `scale-and-slo.md` §2 + `ownership-catalog.md`
§2.1. All numbers are `DRAFT` until E13.3 wires per-service
tuning; treat as inputs to Helm defaults, not binding SLAs.

| Variable | Value (DRAFT) | Source |
| -------- | ------------- | ------ |
| Generic domain-service replicas (dev / onprem / saas) | 1 / 2 / 3 | `values.yaml::services.research.replicas` |
| Replica platform Y1 → Y3 → Y5 (capacity) | 4 → 12 → 32 | `scale-and-slo.md` §10 |
| p95 read latency budget | 300 ms | `architecture-decisions.md` §A |
| p95 write latency budget | 600 ms | `architecture-decisions.md` §A |
| RPO | ≤ 15 min SaaS / ≤ 5 min tenant-data | `architecture-decisions.md` §A |
| RTO | ≤ 4 h | `architecture-decisions.md` §A |
| SLO read availability | 99.95 % / month | `ownership-catalog.md` §2.1 |
| SLO write availability | 99.9 % / month | `ownership-catalog.md` §2.1 |
| Hikari pool (per pod) | `min-idle: 2`, `max-pool: 10` | `services/research-service/src/main/resources/application.yml` |
| Outbox poll batch size | 50 | `application.yml::research-service.outbox.relay.poll-batch-size` |
| Outbox poll interval | 5000 ms | `application.yml::research-service.outbox.relay.poll-interval-ms` |
| Outbox claim lease | 30000 ms | `application.yml::research-service.outbox.relay.claim-lease-ms` |
| Outbox max attempts | 5 | `application.yml::research-service.outbox.relay.max-attempts` |
| Outbox relay cron | `0/15 * * * * *` (15s tick) | `application.yml::platform.research.outbox.relay-cron` |

## Helm workload (E6.1e)

The umbrella chart renders the workload under
`platform/helm/genealogy-platform/templates/components/research-service/`:

- **Deployment** — one Deployment with the explicit
  replica count from `values.yaml::services.research.replicas`,
  Vault Agent Injector annotations, OpenFeature sidecar
  (opt-in via `openfeature.enabled`), `runAsNonRoot: true`
  + `readOnlyRootFilesystem: true` + drop-ALL capabilities,
  podAntiAffinity `preferred` to spread one pod per node.
- **Service** — two ports: HTTP 8080 (REST + actuator)
  and gRPC 9090 (the canonical `gp.research.v1.*` RPCs).
  A separate `actuator` port name keeps the probe contract
  distinct from the public HTTP port.
- **ServiceAccount** — the workload identity bound to the
  Vault role `research-service`. The Vault Agent Injector
  mints a short-lived AppRole token at startup.
- **PodDisruptionBudget** — `minAvailable: 1` so the
  in-flight gRPC streams + the `@Scheduled` relay runner
  never lose their claim during voluntary disruptions.
- **NetworkPolicy** — default-deny Ingress + Egress with
  an explicit allow-list for the in-mesh callers
  (gp-edge Kong, gp-bff web-bff, gp-services in-mesh
  callers, gp-observability Prometheus). Egress allows
  gp-data (Postgres + Kafka), gp-platform (Keycloak +
  OpenFGA + OpenFeature), gp-observability (OTel
  Collector), kube-system (DNS).
- **ConfigMap** — non-secret workload configuration:
  Spring datasource URL, Flyway locations, actuator
  exposure, OTel endpoint, outbox relay cron. Secrets
  NEVER live here — they are Vault-backed via
  ExternalSecret.
- **ExternalSecret** — Vault-backed `database` + `kafka`
  secret refs. The chart never inlines a literal
  credential; per-env values files override the
  `clusterSecretStore` + `*VaultPath` entries.

### Probe contract (E6.1e)

The workload exposes the Spring Boot Actuator contract
baked into the platform starter
(`libs/platform-spring-boot-starter`):

- `/actuator/health/liveness` → `livenessState` group
- `/actuator/health/readiness` → `readinessState` + `db`
  group

The umbrella's `_probes.tpl` uses `/healthz/*` for the
platform components; the research-service workload
intentionally deviates because the Spring Boot starter
owns the `/actuator/health/*` mapping. Helm chart values
override the path via
`services.research.probes.{livenessPath,readinessPath}`.

## Kafka consumer contract (E6.1e)

The two upstream genealogy events that drive the workspace
projection are:

- `gp.genealogy.v1.TreeVisibilityChanged` →
  `genealogy.tree-visibility.v1.v1` topic
- `gp.genealogy.v1.PersonRedacted` →
  `genealogy.person-redacted.v1.v1` topic

The Spring `@KafkaListener` adapter lives at
`ResearchConsumerInboxListener` and:

- uses `MANUAL_IMMEDIATE` ack mode so the offset commits
  only after the database transaction commits;
- calls `ResearchConsumerInboxService.apply(...)` for
  every delivery; the service runs `INSERT ... ON
  CONFLICT DO NOTHING` against
  `research_service.consumer_inbox` so a re-delivery
  skips the projection mutation;
- rebinds the trusted tenant context via
  `ResearchTenantContextBinder` for the duration of the
  projection mutation;
- flips the inbox row to `Outcome.PROCESSED` on success
  or `Outcome.FAILED` on error; a failed delivery throws
  so the Spring container routes the offset to the DLT.

Per `design.md` §7.3 + ADR-E0.5-08 the consumer is
`enable.auto.commit=false`; the offset commits only after
the inbox + projection row commit. The durable inbox makes
re-delivery safe even under concurrent processing.

## Observability

### Dashboards (DEFERRED — E2.10 / E13.1)

`grafana/dashboards/research-service.json` is the canonical
service dashboard path. The file does NOT exist yet — it
ships in E2.10 (Grafana OSS config-as-code) / E13.1 (SLO
dashboard wiring). When E13.1 lands the panel list MUST
include:

- **Request rate** by route + status class (counter
  `http_server_requests_seconds_count`) for both the HTTP
  port (8080) and the gRPC port (9090).
- **p95 latency** read / write split (histogram
  `http_server_requests_seconds_bucket`) — same split
  for gRPC `grpc_server_handled_total`.
- **Error budget burn-rate** computed against the 99.95 %
  read / 99.9 % write SLO per `ownership-catalog.md` §2.1.
- **Postgres connection pool** active / idle / pending
  (Hikari metrics; target `max-pool: 10` per
  `application.yml`).
- **Outbox lag** by event type — counters
  `platform.outbox.published_total` /
  `platform.outbox.dead_lettered_total` /
  `platform.outbox.retried_total` (publish to
  `genealogy.research.v1.v1`).
- **Outbox publish latency** histogram
  `platform.outbox.publish_duration_seconds` (the relay
  uses `Duration pollInterval` as the floor).
- **Consumer inbox lag** by outcome — counters
  `platform.consumer_inbox.processed_total` /
  `platform.consumer_inbox.skipped_duplicate_total` /
  `platform.consumer_inbox.failed_total` (one per source
  topic).
- **Redaction overlay coverage** — counter
  `platform.workspace.redacted_rows_total` /
  `platform.workspace.rebroadcast_rows_total`.
- **Vault Agent Injector status** — metric
  `vault_agent_inject_status` (per the platform E2.6
  contract); a stuck injector must page the on-call.
- **OpenFeature provider health** — metric
  `openfeature_evaluation_total{outcome=...}` so a
  Flagsmith outage surfaces as a dashboard alert.

All metrics MUST be labelled with `tenant_pseudo_id` +
`service=research-service` + `version`; raw `tenant_id`
+ `user_id` are forbidden (per `runbook/observability.md`).

### Alert rules (DEFERRED — E13.2)

`platform/observability/alerts/research-service.yaml` is
the canonical alert rule path. The file does not exist
in E6.1e; the prometheus-rules linter for service-scoped
rules lands in E13.2. When E13.2 ships the rule skeleton
MUST be:

- **High error rate** —
  `sum(rate(http_server_requests_seconds_count{
    service="research-service",status=~"5..",
    uri=~"/api/v1/.*"}[5m]))
   /
  sum(rate(http_server_requests_seconds_count{
    service="research-service",uri=~"/api/v1/.*"}[5m]))
   > 0.01`
- **Outbox DLQ growth** —
  `increase(platform_outbox_dead_lettered_total{
    service="research-service"}[1h]) > 5`
- **Consumer inbox FAILED growth** —
  `increase(platform_consumer_inbox_failed_total{
    service="research-service"}[15m]) > 3`
- **p95 write latency** —
  `histogram_quantile(0.95,
    sum(rate(http_server_requests_seconds_bucket{
      service="research-service",uri=~"/api/v1/.*"}[5m]))
    by (le, uri)) > 0.6`
- **Vault Agent stuck** —
  `vault_agent_inject_status{state="error"} == 1`
- **Redaction overlay saturation** —
  `platform_workspace_redacted_rows_total{
    service="research-service"} > 1000`

The on-call routing rule for `research-service` is
`sre-primary` per `services/research-service/OWNERS`.

## Failure playbook

| Symptom | Likely cause | Action |
| ------- | ------------ | ------ |
| `http_server_requests_seconds_count{uri="/actuator/health/readiness",status="503"}` rising | Postgres connection refused | Check Hikari metrics; restart pod if `db` health group fails; page DBA on-call if persistent |
| Outbox rows stuck in `PENDING` | `@Scheduled` driver crashed | Restart pod; check `ResearchOutboxRelayRunner` logs; verify `platform.research.outbox.relay-cron` env var |
| `platform.outbox.dead_lettered_total` rising | Producer broker unreachable or payload forbidden-field detected | Inspect DLQ rows in `research_service.outbox WHERE status='DEAD_LETTERED'`; check `last_error` + `dlq_reason` |
| Consumer inbox row stuck in `IN_FLIGHT` | Transaction rolled back but the row was never flipped | Run `UPDATE ... SET outcome='FAILED' WHERE outcome='IN_FLIGHT' AND received_at < now() - interval '10 minutes'` (the recovery script is the E13.4 backlog item) |
| Vault Agent Injector `state=error` | Vault server unreachable or Vault policy revoked | Verify Vault server health; check Vault audit log for `research-service` AppRole denials |
| Cross-tenant SELECT returning non-empty | RLS policy bypass | Page DBA on-call immediately — this is a critical security incident per `design.md` §5 |

## Migration + schema history

The schema is owned by the research-service Flyway
migrations under
`services/research-service/src/main/resources/db/migration/`:

- `V1__baseline_schema.sql` — baseline (1 schema + 1 role + 1 helper)
- `V2__research_aggregate.sql` — 6 aggregate tables + 5 bridge tables
- `V3__outbox_and_workspace.sql` — outbox + workspace projection
- `V4__consumer_inbox.sql` — durable consumer inbox

Every table carries the canonical audit columns:
`created_at`, `updated_at`, `archived_at`, `version`,
`created_by_actor_pseudo_id`, `correlation_id`. Every
table is `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL
SECURITY` with a `tenant_isolation` policy that matches
`tenant_id = research_service.current_tenant_id()`.

The runtime role `research_service_app` is `NOLOGIN`
(production posture); the workload's connection pool
issues `SET LOCAL ROLE research_service_app` +
`SET LOCAL app.tenant_id = ?` per transaction via
`ResearchRlsTxInterceptor.bind()`.

## References

- `.kiro/specs/genealogy-platform/requirements.md` — R8,
  R10, R14, NFR1, NFR4
- `.kiro/specs/genealogy-platform/design.md` — §5.5
  (research aggregate), §7.3 (transactional outbox +
  inbox), §12 (secrets), §13 (probes + retry)
- `.kiro/specs/genealogy-platform/ownership-catalog.md`
  — §2.1 (research-service SLOs)
- `.kiro/specs/genealogy-platform/architecture-decisions.md`
  — ADR-E0.5-01 (toolchain), ADR-E0.5-02 (schema-per-service),
  ADR-E0.5-08 (Avro + Apicurio + BACKWARD evolution)
- `evidence/E6.1.md` + `evidence/E6.1a.md` … `evidence/E6.1d.md`
  + `evidence/E6.1e.md` — per-subtask completion evidence
