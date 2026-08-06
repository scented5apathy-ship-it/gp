# gradle/conventions

Gradle convention plugins applied by every Java/Kotlin subproject.
Per `AGENTS.md` §2 these plugins enforce the platform baseline
(Java 21, Checkstyle, jOOQ, Flyway, ArchUnit, JUnit, Testcontainers,
dependency lockfile).

Plugins:

- `java-conventions.gradle.kts` — baseline applied by
  `services/<svc>/build.gradle.kts`. Pins the JDK toolchain to 21,
  wires `config/checkstyle/checkstyle.xml`, applies the dependency
  version catalog from `libs.versions.toml`, enforces ArchUnit
  boundary rules.
- `service-conventions.gradle.kts` — adds the Spring Boot plugin,
  contract-test integration, OpenTelemetry SDK wiring and the
  transactional outbox baseline used by every domain service.

Owner: platform-primary. Reviewers: every Java module owner.