# libs/platform-spring-boot-starter/.../spring/autoconfigure

Spring `@AutoConfiguration` classes wired via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
Each class is the entry point for one concern of the starter:

- `AuditAutoConfiguration` — wires `AuditPublisher` bean and the
  Kafka audit topic producer factory.
- `OpenFeatureAutoConfiguration` — wires the OpenFeature SDK with
  the Flagsmith provider and a safe default.
- `PlatformProperties` — typed configuration (`@ConfigurationProperties`)
  bound from `META-INF/genealogy/platform-spring-boot-defaults.yml`.
- `PlatformSpringBootAutoConfiguration` — umbrella entry point.
- `TrustedContextAutoConfiguration` — wires the trusted tenant
  context filter and MDC propagation.

Owner: platform-secondary. Reviewers: Security, Privacy.