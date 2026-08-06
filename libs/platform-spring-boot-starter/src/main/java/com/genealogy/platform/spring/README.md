# libs/platform-spring-boot-starter

Shared Spring Boot starter consumed by every Java service, BFF,
public-api and worker. Per `design.md` §14 this is one of the few
shared libraries allowed under `libs/` because it carries only
cross-cutting concerns (error envelope, telemetry, security glue,
feature flags, audit), never an aggregate root.

Contents:

- `src/main/java/com/genealogy/platform/spring/` — Java packages
  organised by concern:
  - `audit/` — `AuditEvent`, `AuditPublisher`,
    `MicrometerAuditPublisher` (writes via the audit Kafka topic).
  - `autoconfigure/` — Spring `@AutoConfiguration` classes wired via
    `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  - `context/` — `TrustedTenantContext` (carries
    `tenantPseudoId`, `userPseudoId`, `traceId`, `requestId`).
  - `featureflags/` — `SafeFeatureClient` wrapping OpenFeature.
  - `web/` — `TrustedContextFilter` validating `tenantPseudoId`
    and request id headers.
- `src/main/resources/`
  - `logback-spring.xml` — structured JSON logger with the
    pseudonymous label taxonomy.
  - `META-INF/genealogy/platform-spring-boot-defaults.yml` —
    Spring Boot defaults.
  - `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
    — list of `@AutoConfiguration` classes to load.
- `src/test/java/com/genealogy/platform/spring/` — starter sanity
  test (`PlatformSpringBootStarterTest.java`).

Owner: platform-secondary. Reviewers: Security, Privacy.