# platform-spring-boot-starter

Shared Spring Boot 3 auto-configuration consumed by every Java
service in the Genealogy Platform monorepo. The starter is the
E1.4 acceptance artefact: services only have to depend on
`platform-spring-boot-starter` to inherit the standard
production wiring (OTel, OpenFeature, audit, trusted tenant
context, graceful shutdown, Kubernetes probes).

Service owner: see `OWNERS` (per-service CODEOWNERS file
mirrored from `config/teams.yaml`).

## Quick start

```kotlin
// settings.gradle.kts (already includes this lib)
include(":libs:platform-spring-boot-starter")

// services/<svc>/build.gradle.kts
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":libs:platform-spring-boot-starter"))
}
```

```java
// services/<svc>/src/main/java/.../ServiceApplication.java
@SpringBootApplication
public class ServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }
}
```

## What gets auto-configured

| Concern | Auto-configuration | Bean |
| --- | --- | --- |
| Tracing + metrics | `OpenTelemetryAutoConfiguration` (OTLP exporter + Micrometer bridge) | `OpenTelemetry` |
| Feature flags | `OpenFeatureAutoConfiguration` (NoOpProvider safe fallback) | `OpenFeatureAPI`, `SafeFeatureClient` |
| Trusted context | `TrustedContextAutoConfiguration` (servlet filter) | `TrustedContextFilter`, `TrustedTenantContext` |
| Audit hook | `AuditAutoConfiguration` (Micrometer + structured log) | `AuditPublisher` |
| Configuration | `PlatformProperties` (bound from `platform.*` prefix) | `PlatformProperties` |
| Logging | `META-INF/genealogy/platform-spring-boot-defaults.yml` + `logback-spring.xml` | structured MDC pattern with `tenant_id` / `correlation_id` |

Every value in `platform-spring-boot-defaults.yml` is the safe
production default. Operators override only the bits that must
change per environment (datasource URL, Keycloak issuer, OTel
endpoint).

## Security / privacy guarantees

- The `X-Tenant-Id` header is required by default. Requests
  without it receive a `400` with an RFC 9457 Problem Details
  body (`application/problem+json`).
- The header value is validated against a configurable
  `platform.tenant.max-id-length` (default 64) and never
  accepts a client-supplied `tenantId` in a request body —
  the contract-test suite enforces the same rule.
- `AuditEvent` only carries opaque ids (tenant id, user sub,
  aggregate id, correlation id). No raw DNA, file content or
  access tokens ever end up in the audit log.
- `OpenFeatureAutoConfiguration` always installs a `NoOpProvider`
  so the service starts when Flagsmith is unreachable. Flag
  evaluations never throw and always return the default value.
- `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=30s`
  ensure SIGTERM-driven shutdown finishes in-flight requests
  before the process exits.

## Test fixtures

The starter does not ship with Testcontainers fixtures; those
live in `libs/platform-testing` (consumed via
`testFixtures(project(":libs:platform-testing"))`). The
fixtures cover PostgreSQL, Kafka, Keycloak, OpenFGA, Temporal,
S3 LocalStack and Valkey — see the `platform-testing` README.
