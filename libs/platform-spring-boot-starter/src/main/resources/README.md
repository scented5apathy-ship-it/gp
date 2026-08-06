# libs/platform-spring-boot-starter/src/main/resources

Classpath resources shipped with the platform Spring Boot starter.

- `logback-spring.xml` — JSON encoder, MDC propagation for
  pseudonymous IDs, redaction filter (no raw PII/DNA/tokens).
- `META-INF/genealogy/` — starter defaults loaded by
  `PlatformProperties`.
- `META-INF/spring/` — Spring Boot autoconfiguration metadata
  (`AutoConfiguration.imports`).

Owner: platform-secondary. Reviewers: SRE, Security, Privacy.