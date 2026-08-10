# runbook/tenant-service.md — tenant-service operator runbook

Operator-facing runbook for the `tenant-service` Spring Boot
microservice. Per `tasks.md` E3.2 (split into E3.2a–E3.2e) +
`ownership-catalog.md` §2.1 (domain owner = Identity & Tenant team,
on-call = `sre-primary`, SLO read 99.95 % / month, write 99.9 %,
p95 read 300 ms / write 600 ms, sync dep budget `n_sync ≤ 2`) +
`scale-and-slo.md` §2 (Y1 1 000 / Y3 5 000 / Y5 20 000 active
tenants) + `architecture-decisions.md` §A (numeric thresholds
`DRAFT` until E0.6 sign-off).

## Source-of-truth map

| File | Purpose |
| ---- | ------- |
| `services/tenant-service/build.gradle.kts`            | JDK 21 toolchain, Spring Boot 3.x, jOOQ, Flyway, gRPC starter wired (server port 9090 from `GRPC_SERVER_PORT`) |
| `services/tenant-service/src/main/resources/db/migration/V2__tenant_aggregate.sql` | E3.2a schema (`tenant_service.tenants`, `memberships`, `invitations`, `entitlements`, `outbox_events`) with opaque PK + `tenant_id` + audit columns |
| `services/tenant-service/src/main/java/.../domain/`   | E3.2b aggregates (`Tenant`, `Membership`, `Invitation`, `Entitlement`) — Java records, framework-free |
| `services/tenant-service/src/main/java/.../application/` | E3.2c command services + E3.2d query services; `KeycloakSubjectMirror` (in-memory impl now, real Keycloak admin API in E3.5) |
| `services/tenant-service/src/main/java/.../web/`     | E3.2d REST controllers (`TenantController`, `MembershipController`) + RFC 9457 exception handler + idempotency cache |
| `services/tenant-service/src/main/java/.../grpc/TenantGrpcService.java` | E3.2e Spring `@Component` stub (logs on startup); real `BindableService` lands in E4.x once the protobuf plugin is wired |
| `services/tenant-service/src/main/resources/application.yml` | Datasource URL from env (no literal credentials), Hikari `minimum-idle: 2`, `maximum-pool-size: 10`, gRPC `server.port: ${GRPC_SERVER_PORT:9090}` |
| `services/tenant-service/OWNERS`                     | Ownership mirror (`config/teams.yaml`) — primary `identity-primary`, secondary `@genealogy/platform`, on-call `sre-primary` |
| `contracts/protobuf/tenant/v1/tenant_service.proto`   | E1.3 contract — schema present (package `com.genealogy.platform.tenant.v1`, single `TenantService` with tenant + membership RPCs), Java stubs not generated yet (E4.x fixes the sibling-enum collisions: `TenantStatus.ACTIVE` vs `MembershipStatus.ACTIVE`, etc.) |
| `contracts/openapi/public-api/v1/tenant.yaml`        | E1.3 OpenAPI contract — REST surface (7 routes) stable since E3.2d |

## Helm templates (DEFERRED)

The tenant-service Helm workload (Deployment, Service,
ServiceAccount, NetworkPolicy, PDB, HPA, ExternalSecret,
ConfigMap, probes) is **NOT YET RENDERED** in
`platform/helm/genealogy-platform/`. Until those templates land,
the runbook cannot quote replica counts, HPA thresholds, PDB
minimum, or probe paths; capacity numbers below are DRAFT
planning inputs only. The Helm chart owner is `platform-primary`;
the work item belongs to E2.1 (cluster baseline) + the E2.1
component rollout.

## Capacity skeleton

Derived from `scale-and-slo.md` §2.1 + §2.5 + §10. All numbers
are **`DRAFT`** until E0.6 sign-off; treat as inputs to Helm
defaults, not binding SLAs. The machine-readable
`scale-and-slo.yml::tenant` file is deferred to E13.3.

| Variable | Value (DRAFT) | Source |
| -------- | ------------- | ------ |
| Active tenant (Y1 / Y3 / Y5)     | 1 000 / 5 000 / 20 000 | `scale-and-slo.md` §2.1 |
| Generic domain-service replicas  | 4 / 12 / 32 (Y1/Y3/Y5) | `scale-and-slo.md` §10 |
| Read RPS budget (platform Y1)    | 5 000 (per-service share TBD E13.3) | `scale-and-slo.md` §2.5 |
| Write RPS budget (platform Y1)   | 300 (per-service share TBD E13.3) | `scale-and-slo.md` §2.5 |
| Burst multiplier                 | 3.0× SaaS / 2.0× on-prem | `architecture-decisions.md` §A |
| p95 read latency budget          | 300 ms | `architecture-decisions.md` §A |
| p95 write latency budget         | 600 ms | `architecture-decisions.md` §A |
| RPO                              | ≤ 15 min SaaS / ≤ 5 min tenant-data | `architecture-decisions.md` §A + `scale-and-slo.md` §5.3 (`DRAFT`) |
| RTO                              | ≤ 4 h | `architecture-decisions.md` §A (`DRAFT`) |
| SLO read availability            | 99.95 % / month | `ownership-catalog.md` §2.1 |
| SLO write availability           | 99.9 % / month | `ownership-catalog.md` §2.1 |
| Hikari pool (per pod)            | `min-idle: 2`, `max-pool: 10` | `services/tenant-service/src/main/resources/application.yml` |

> **Per-service RPS share, members-per-tenant p99, and tenants-
> per-node p95 are NOT yet defined** — they live in the
> `scale-and-slo.yml::tenant` machine-readable form that E13.3
> ships. Operators MUST NOT pick their own numbers from this
> runbook today.

## Dependency map

The diagram below mixes **current runtime** (E3.2) and
**target production** (post E3.5 / E4.7) — see the "Current
vs target" callouts.

```
                ┌──────────────┐
                │  Kong edge   │
                └──────┬───────┘
                       │ JWT (Keycloak) + X-Tenant-Id
                       ▼
                ┌──────────────┐
                │ tenant-svc   │
                └──┬──────┬────┘
                   │      │
       current:    │      │  target (E3.5):
       in-memory   │      │  Keycloak
       mirror      │      │  /admin/users
                   ▼      ▼
        ┌────────────┐  ┌──────────────┐
        │ Postgres   │  │ Keycloak     │
        │ primary    │  │ /admin/users │  (E3.5 target)
        └────┬───────┘  └──────────────┘
             │
             │ outbox_events (transactional, E3.2c)
             ▼
        ┌──────────────────┐
        │ Relay (E4.7)     │  ← deferred; today outbox is
        │ publishes to     │     visible only in
        │ genealogy.*      │     `tenant_service.outbox_events`
        └──────────────────┘
```

- **PostgreSQL primary** — synchronous (≤ 50 ms p95 target,
  DRAFT). Flyway-managed `tenant_service` schema, RLS enforced
  via `app.tenant_id` session var (E3.2a). This counts as 1 of
  the `n_sync ≤ 2` budget slots per `ownership-catalog.md` §2.1.
- **Keycloak `/admin/realms/{realm}/users`** — **target**
  synchronous dependency (≤ 200 ms p95, ≤ 1 hop) used by the
  production `KeycloakSubjectMirror`. The current E3.2c
  implementation is in-memory; the real adapter lands in E3.5.
  When wired, this will be the 2nd (and last) sync slot.
- **Outbox + audit topic** — E3.2c writes tenant domain events
  (`TenantCreated`, `MembershipInvited`, `MembershipActivated`,
  `MembershipRevoked`, `EntitlementChanged`) into the
  `tenant_service.outbox_events` table inside the same
  transaction as the aggregate mutation. The Kafka relay that
  publishes them is **E4.7**; until E4.7 ships the rows are
  only visible via SQL. The canonical audit topic is
  `genealogy.audit.v1.v1` (365-day retention, owner
  `audit-service`) — tenant-service does not publish directly
  to that topic in E3.2.

## On-call rotation

| Tier   | Coverage   | Rota source |
| ------ | ---------- | ----------- |
| Tier-1 | Business hours (08:00–18:00 UTC+7, Mon–Fri) | `sre-primary` weekly rotation, published in `docs/ownership/team-map.yaml` |
| Tier-2 | 24×7       | `sre-primary` paging → escalation to `@genealogy/platform` after 30 min unack → `@genealogy/security` |

Per `ownership-catalog.md` §C #3 the rotation roster lives in
`docs/ownership/team-map.yaml` and is renewed quarterly by the
Identity & Tenant team lead. The roster is a YAML map keyed by
team slug; operators MUST consult the file before paging.

## Common operations

### Tenant stuck in `SUSPENDED`

1. `kubectl -n gp-services logs -l app.kubernetes.io/component=
   tenant-service --tail=500 | grep "tenant.suspend"` — find the
   actor / reason in the structured log field.
2. `kubectl -n gp-services exec postgres-primary-0 -- psql -U
   postgres -d genealogy -c "select id, status, version,
   updated_at from tenant_service.tenants where id =
   '<tenant_id>';"` — confirm current state.
3. If the suspension is a legal-hold freeze (R10), DO NOT
   restore without DPO sign-off; open an incident to
   `privacy-primary` first.
4. Otherwise `POST /api/v1/tenants/{id}/restore` with `If-Match`
   header carrying the current ETag. The controller invokes
   `TenantCommandService.restore` which flips the status, writes
   an audit event via `TenantAuditPublisher`, and updates the
   outbox row. (No new `TenantCreated` event is emitted on
   restore — `TenantCreated` is creation-only.)

### Invite token replay attempt

1. `Idempotency-Key` collision logged as
   `IdempotencyCache.replay` with the original cached response
   (status, ETag, body). Inspect the structured log line.
2. If the replay is a client retry (expected), no action needed.
3. If many replays hit from a single `actor_pseudo_id`, treat
   as a brute-force probe and forward to `security-primary`.

### Postgres primary failover

1. Operator runs `patronictl failover` (or platform playbook
   equivalent) — the new primary triggers the Hikari connection
   refresh inside `tenant-service` automatically (Hikari
   `maximum-pool-size: 10` per `application.yml`).
2. RPO = ≤ 15 min SaaS / ≤ 5 min tenant-data (`DRAFT`); RTO = ≤
   4 h (`DRAFT`).
3. `rls.bind()` runs inside the same `@Transactional` as the
   query, so a partial failover mid-transaction surfaces as
   `TransientDataAccessException` → 503 + `X-Correlation-Id` in
   the response; client retries are idempotent thanks to
   `Idempotency-Key`.

## Backup / restore

- **Postgres PITR** — `platform-primary` owns the PITR playbook.
  E3.2a Flyway V2 migration is backward-compatible so PITR
  replay does not need a code change. Tenant-service does not
  own its own backup/restore runbook.
- **Outbox** — `tenant_service.outbox_events` rows live in the
  same Postgres primary; once E4.7 ships, the relay will publish
  them to the appropriate `genealogy.*.v1.v1` topic (30-day
  retention per `platform/kafka/topics.yaml`).
- **In-memory idempotency cache** — process-local; a rolling
  restart loses replay history. The V2 unique
  `(tenant_id, idempotency_key)` index on `invitations` is the
  durable backstop. The dedicated durable idempotency backend
  lands with the platform Valkey cache in E2.7 / E15.

## Observability (deferred paths)

### Dashboards (DEFERRED — E2.10 / E13.1)

`grafana/dashboards/tenant-service.json` is the canonical
service dashboard path. The file does NOT exist yet — it ships
in E2.10 (Grafana OSS config-as-code) / E13.1 (SLO dashboard
wiring). When E13.1 lands the panel list MUST include:

- **Request rate** by route + status class (counter
  `http_server_requests_seconds_count`).
- **p95 latency** read / write split (histogram
  `http_server_requests_seconds_bucket`).
- **Error budget burn-rate** computed against the 99.95 % read /
  99.9 % write SLO per `ownership-catalog.md` §2.1.
- **Postgres connection pool** active / idle / pending
  (Hikari metrics; target `max-pool: 10` per `application.yml`).
- **Outbox lag** by event type — metric ships with the E4.7
  relay.
- **gRPC port readiness** — TCP probe to `${GRPC_SERVER_PORT:9090}`
  once the real `BindableService` lands in E4.x (today the
  `TenantGrpcService` stub is just a Spring bean; the gRPC port
  is bound by `spring-grpc-spring-boot-starter` but no tenant
  RPCs are exported).

### Alert rules (DEFERRED — E13.2)

`platform/observability/alerts/tenant-service.yaml` is the
canonical alert rule path. The file does NOT exist in E3.2e —
the prometheus-rules linter for service-scoped rules lands in
E13.2. When E13.2 ships the rule skeleton MUST be:

- **High error rate** —
  `sum(rate(http_server_requests_seconds_count{
    service="tenant-service",status=~"5..",uri=~"/api/v1/tenants.*"}[5m]))
   /
  sum(rate(http_server_requests_seconds_count{
    service="tenant-service",uri=~"/api/v1/tenants.*"}[5m])) > 0.05`
  for 10 min → page `sre-primary` (Tier-2).
- **p95 write-latency regression** —
  `histogram_quantile(0.95, sum by (le) (rate(
   http_server_requests_seconds_bucket{
     service="tenant-service",method="POST",
     uri="/api/v1/tenants"}[5m]))) > 0.6`
  for 15 min → ticket (Tier-1).
- **RLS denial counter spike** — once the
  `tenant_service_rls_denied_total` counter ships (deferred to
  E4.x with the protobuf plugin work); `for: 5m` → page
  `security-primary`.
- **Idempotency replay spike** — once the
  `tenant_service_idempotency_replay_total` counter ships (the
  `IdempotencyCache` already emits structured log lines; the
  counter proper lands with the Valkey backend in E2.7).

Severity ladder and `runbook_url` must match the existing
`platform/observability/alerts/kafka-rules.yaml` /
`istio-rules.yaml` pattern.

## Residual risk

- **gRPC surface is a stub only** — REST is the contract of
  record in E3.2. The `TenantGrpcService` Spring `@Component`
  exists so the gRPC port can be brought up by
  `spring-grpc-spring-boot-starter` (default `9090`); no tenant
  RPCs are exported. The implementation lands in E4.x once
  `com.google.protobuf` is wired into the build AND the sibling-
  enum collisions inside
  `contracts/protobuf/tenant/v1/tenant_service.proto`
  (`TenantStatus.ACTIVE` vs `MembershipStatus.ACTIVE`,
  `TenantStatus.SUSPENDED` vs `MembershipStatus.SUSPENDED`)
  are fixed. Until then, **no service depends on the gRPC
  surface**; the ownership-catalog §2.1 sync-dep budget
  (`n_sync ≤ 2`) only counts the current Postgres hop plus the
  future Keycloak hop.
- **Keycloak adapter is in-memory** — `KeycloakSubjectMirror`
  in E3.2c is an in-memory implementation; real Keycloak Admin
  API integration lands in E3.5.
- **Outbox relay is deferred** — `tenant_service.outbox_events`
  rows are only visible via SQL today; the Kafka relay ships in
  E4.7.
- **Plan/Quota numerics are DRAFT** per
  `architecture-decisions.md` §A + E3.2b evidence. The service
  accepts `0` (unlimited) for every quota field and does NOT
  block mutations on quota. Quota enforcement, usage metering,
  warnings and billing adapters land in **E11.4**, NOT in E13.4
  (E13.4 is resilience / chaos testing). The `Retention per
  plan` row in §A remains `TBD per plan` — E11.4 must define
  the per-plan retention days before enforcement can ship.
- **In-memory idempotency cache** — see Backup / restore above;
  durable idempotency lands with the Valkey cache in E2.7 / E15.
- **Per-service capacity numbers (RPS share, members-per-tenant
  p99, tenants-per-node p95) are TBD** until E13.3 lands
  `scale-and-slo.yml::tenant`.
- **E0.6 sign-off ceremony still pending** — the E0.6 task
  itself is DONE but the product + security + privacy +
  operations sign-off ceremony recorded in
  `evidence/E0.6-signoff.md` is not yet on file; until it is,
  every numeric in this runbook is `DRAFT` and operators MUST
  treat the SLO targets as planning inputs.

## Ownership

`OWNERS` mirror — primary = `identity-primary`, secondary =
`@genealogy/platform`, on-call = `sre-primary`.
