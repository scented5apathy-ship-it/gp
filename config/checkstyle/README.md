# config/checkstyle

Checkstyle configuration applied by the
`platform-build-logic/java-conventions` Gradle plugin to every Java
module (`services/*`, `apps/*`, `libs/*`, `workers/*`). Per
`AGENTS.md` §2 the Java toolchain is pinned to 21 and Checkstyle runs
in `pnpm check:java` (and the Gradle `check` task).

Owner: platform-primary. Reviewers: every Java module owner.