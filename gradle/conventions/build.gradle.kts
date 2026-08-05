/*
 * Shared convention scripts for the Genealogy Platform monorepo.
 *
 * Subprojects apply these via `apply(from = ...)` so that the Java 21
 * toolchain, JUnit 5 configuration and dependency locking are
 * configured in exactly one place.
 *
 * Why script-plugin instead of compiled-plugin?
 *   - Compiled convention plugins require a Kotlin/Groovy compile step
 *     in an included composite build, which adds friction to local
 *     iteration. Script plugins (`.gradle.kts`) are evaluated directly
 *     by Gradle's Kotlin DSL with no build step.
 *   - The downside is that plugin ids cannot be applied via `id()` —
 *     subprojects must `apply(from = "../../gradle/conventions/...")`
 *     explicitly. This is acceptable for E1.1 because the monorepo has
 *     only ~25 subprojects; E1.2 can migrate to compiled plugins if
 *     the friction becomes painful.
 *
 * Scripts:
 *   - java-conventions.gradle.kts    Java 21 toolchain + JUnit 5
 *   - service-conventions.gradle.kts Service-specific boundary guard
 */
plugins {
    java
}