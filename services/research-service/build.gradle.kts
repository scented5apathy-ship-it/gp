/*
 * E6.1 — `research-service` research log + citations +
 * provenance aggregate.
 *
 * Implements the Source, Citation, ResearchTask, Hypothesis and
 * Conflict aggregates, the provenance query and the in-memory
 * reference executor. Mirrors
 * `contracts/research/research-policy.yaml`. The Java domain
 * (`com.genealogy.platform.services.research.domain`) is the
 * executor; the YAML file is the source of truth per
 * `agent-execution.md` §4.4.
 *
 * E6.1 ships the domain + invariants + provenance query only.
 * REST surface, gRPC stubs, Flyway migration, jOOQ persistence,
 * Kafka producer/consumer and OpenFeature wiring land in the
 * later E6.x / E11.x sub-epics. Per ADR-E0.5-01 the module
 * inherits the Java 21 toolchain through the convention script
 * and is locked to keep the build reproducible.
 *
 * Dependencies intentionally avoid coupling to other services.
 * Cross-service interaction happens via gRPC (E6.x gRPC stubs
 * are deferred) and Kafka events under
 * `contracts/events/research/v1/`.
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
