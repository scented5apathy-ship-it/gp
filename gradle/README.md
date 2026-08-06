# gradle

Gradle build configuration shared by every Java/Kotlin module. Per
`AGENTS.md` §2 ("Java toolchain = 21") the toolchain is enforced by
`platform-build-logic` and every subproject applies the
`java-conventions` plugin.

Sub-directories:

- `conventions/` — `java-conventions.gradle.kts` and
  `service-conventions.gradle.kts` (Checkstyle, jOOQ, Flyway,
  ArchUnit, JUnit, Testcontainers).
- `wrapper/` — Gradle wrapper jar + properties pinned to a single
  version per ADR-E0.5-01.

Root files:

- `libs.versions.toml` — version catalog. Dependency versions come
  from here only; no literal versions inside service
  `build.gradle.kts`.

Owner: platform-primary. Reviewers: every Java module owner.