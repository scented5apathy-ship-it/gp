# tenant-service

Service owner: see `OWNERS` (per-service CODEOWNERS file mirrored from
`config/teams.yaml`).

`tenant-service` backs the `TenantService` gRPC contract in
`contracts/protobuf/tenant/v1/tenant_service.proto` and the
`/api/v1/tenants` REST surface in
`contracts/openapi/public-api/v1/tenant.yaml`. E1.4 wires the
shared Spring Boot template (`platform-spring-boot-starter`); the
tenant aggregate, membership and OpenFGA mapping land in E3.2.

## Quick start

```bash
# Local development (requires Docker for the integration test).
./gradlew :services:tenant-service:test

# Integration test (requires Docker).
./gradlew :services:tenant-service:integrationTest

# Skip the integration test if you don't have a Docker daemon.
./gradlew :services:tenant-service:test -Dskip.it=true
```

## What the template provides

`tenant-service` is the first service to consume
`platform-spring-boot-starter`. The starter wires:

- **OpenTelemetry** — `OpenTelemetryAutoConfiguration` registers
  the SDK + OTLP exporter + Micrometer bridge. The service name
  is `tenant-service`.
- **OpenFeature** — `OpenFeatureAutoConfiguration` installs the
  safe `NoOpProvider` so the service starts even when Flagsmith
  is unreachable. `SafeFeatureClient#getBoolean` /
  `getString` never throw and always return the default when the
  flag is missing.
- **Trusted tenant context** — `TrustedContextFilter` extracts
  the `X-Tenant-Id` header + the Keycloak JWT subject and
  exposes them via `TrustedTenantContext.current()`. The filter
  returns RFC 9457 Problem Details when the header is missing
  and `platform.tenant.header-required=true` (the default).
- **Audit hook** — `AuditAutoConfiguration` installs a
  `MicrometerAuditPublisher` that increments
  `platform.audit.events` and emits a structured log line. The
  hook is consumed via the `AuditPublisher` interface so a
  Kafka-backed publisher can be swapped in for E3.6.
- **Graceful shutdown** — `server.shutdown=graceful` +
  `spring.lifecycle.timeout-per-shutdown-phase=30s` give
  in-flight requests 30 s to finish before SIGTERM becomes a
  hard kill. Matches `platform.shutdown.timeout-seconds=30`.
- **Probes** — `management.endpoint.health.probes.enabled=true`
  exposes `/actuator/health/liveness` and
  `/actuator/health/readiness`. Liveness only fails on JVM
  death; readiness fails when the JDBC connection is
  unreachable.
- **jOOQ + Flyway** — `spring-boot-starter-jooq` +
  `flyway-core` + `flyway-database-postgresql` +
  `tenant-service/src/main/resources/db/migration` are wired
  with `spring.flyway.schemas=tenant_service` (per
  ADR-E0.5-02 schema-per-service).

## Where to plug in domain code

| File | E1.4 state | Lands in |
| --- | --- | --- |
| `TenantServiceApplication.java` | minimal `@SpringBootApplication` | unchanged |
| `web/TenantInfoController.java` | `/api/v1/info` smoke endpoint | unchanged |
| `web/TenantController.java` | `POST /api/v1/tenants` skeleton | E3.2 fills the aggregate |
| `grpc/TenantGrpcServicePlaceholder.java` | gRPC port bound, no service | E3.2 implements `TenantServiceImplBase` |
| `db/migration/V1__baseline_schema.sql` | creates `tenant_service` schema | E3.2 adds tenant + membership tables |

## Protobuf codegen

`tenant-service` does not yet generate Java stubs from
`contracts/protobuf/**`. The `com.google.protobuf` Gradle plugin
is staged for the E4 epic, which will fix the duplicate-enum-
value collisions in `tenant_service.proto` and
`person_service.proto` (E1.3 ships the contracts but `protoc`
rejects the duplicate `ACTIVE` / `SUSPENDED` values in the
`TenantStatus` + `MembershipStatus` enums in the same package).
The shared `TenantService` gRPC implementation lands in E3.2
once the collisions are resolved and codegen runs end-to-end.
