# services/tenant-service/src/main/resources

Java/Kotlin classpath resources shipped with the `tenant-service`
Gradle module. Per `AGENTS.md` §2 and `gradle/service-conventions.gradle.kts`
these are the only files that may carry per-service configuration
overrides; everything else lives under
`libs/platform-spring-boot-starter/src/main/resources/META-INF/genealogy/`.

Contents:

- `application.yml` — Spring Boot configuration (datasource,
  OpenFGA, Keycloak admin URL, Vault address, feature flag
  defaults, log level). Secrets come from Vault, not from this
  file.
- `db/migration/` — Flyway expand-contract migrations
  (`V1__baseline_schema.sql`). Per `design.md` §13 ("Database
  migration dùng Flyway theo expand-contract; jOOQ code generation
  chạy từ migration/schema kiểm soát trong build") the migration
  is the source of truth for the schema; jOOQ codegen consumes it.

Privacy gate: this directory MUST NOT contain raw DNA, PII or
webhook payload. Tenant seed data used by integration tests lives
in `src/integrationTest/resources/`, not here.

Owner: Identity & Tenant team. Reviewer: Privacy, Security.