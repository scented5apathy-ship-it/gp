/*
 * E6.2 + E6.3 — `collaboration-service` change proposal + review
 * model and mixed-collaboration policy.
 *
 * Implements the ChangeProposal + Review aggregates, the
 * normalized DomainCommand / DomainDiff value objects, the
 * state-transition matrices and the partial-merge executor
 * (E6.2). Adds the DirectEditMatrix + RoutingExecutor +
 * MergeCommandFactory + PatchValidator + FlagsmithRolloutSync
 * (E6.3). Mirrors
 * `contracts/collaboration/collaboration-policy.yaml` (E6.2)
 * and `contracts/collaboration/mixed-collaboration-policy.yaml`
 * (E6.3). The Java domain
 * (`com.genealogy.platform.services.collaboration.domain`) is
 * the executor; the YAML files are the source of truth per
 * `agent-execution.md` §4.4.
 *
 * E6.2 + E6.3 ship the domain + invariants + partial-merge
 * executor + mixed-collaboration routing + merge command
 * factory + Flagsmith rollout sync only. REST surface, gRPC
 * stubs, Flyway migration, jOOQ persistence, Kafka
 * producer/consumer and OpenFeature wiring land in the later
 * E6.x / E11.x sub-epics. Per ADR-E0.5-01 the module inherits
 * the Java 21 toolchain through the convention script and is
 * locked to keep the build reproducible.
 *
 * Dependencies intentionally avoid coupling to other services.
 * Cross-service interaction happens via gRPC + Kafka events
 * (stubs deferred). The proposal review re-authorizes through
 * OpenFGA + ABAC at submit + every review decision via an
 * injected port — the platform never mutates another
 * service's domain record directly from the executor.
 */
plugins {
    java
}

apply(from = "$rootDir/gradle/conventions/java-conventions.gradle.kts")
apply(from = "$rootDir/gradle/conventions/service-conventions.gradle.kts")

dependencyLocking {
    lockAllConfigurations()
}

java {
    sourceSets {
        main {
            java.srcDirs("src/main/java")
            resources.srcDirs("src/main/resources", "db/migration")
        }
        test {
            java.srcDirs("src/test/java")
        }
    }
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.openfeature.sdk)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
}