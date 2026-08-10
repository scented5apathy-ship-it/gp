/*
 * E3.6 — `audit-service` ledger.
 *
 * Implements the append-only WORM ledger that backs every audit
 * event emitted by every other service via the
 * `platform-spring-boot-starter` `KafkaAuditPublisher`. The
 * service consumes the Kafka audit topic with an idempotent
 * inbox (event_id is the dedupe key) and persists each entry to
 * `audit_service.audit_entry` with a per-tenant SHA-256 hash
 * chain.
 *
 * The Flyway migrations under `db/migration` create the schema,
 * the append-only table, the deletion-evidence table, the RLS
 * policy and the row trigger. Schema-per-service per
 * ADR-E0.5-02; RLS + append-only + integrity hash chain per
 * `privacy-and-legal-gate.md` TM-03.
 */
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyLocking {
    lockAllConfigurations()
}

java {
    sourceSets {
        main {
            java.srcDirs("src/main/java")
            resources.srcDirs("src/main/resources")
        }
        test {
            java.srcDirs("src/test/java")
        }
    }
}

println("BUILD MAIN SRC: " + sourceSets["main"].resources.srcDirs)

dependencies {
    implementation(project(":libs:platform-spring-boot-starter"))
    implementation(project(":libs:platform-security"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.grpc)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.openfeature.sdk)
    implementation(libs.bundles.testing.spring)

    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
}

tasks.named("processResources") {
    // The default Gradle behaviour fails with `duplicatesStrategy`
    // when the file tree under `src/main/resources` yields the
    // same relative path more than once. We pin the strategy to
    // EXCLUDE so a stray build/ output or temp copy cannot break
    // the build. The linter (`scripts/lint-audit-config.mjs`)
    // asserts the source-of-truth is canonical.
    (this as Copy).duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
