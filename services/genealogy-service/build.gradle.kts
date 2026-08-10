/*
 * E4.1 — `genealogy-service` tree aggregate.
 *
 * Implements the tree CRUD / archive / restore / transfer / delete
 * lifecycle, the visibility contract (PRIVATE / UNLISTED / PUBLIC)
 * and the UNLISTED-token issuance / revocation. Mirrors
 * `contracts/genealogy/tree-policy.yaml`,
 * `contracts/genealogy/collaboration-policy.yaml` and
 * `contracts/genealogy/unlisted-token.yaml`. The Java domain
 * (`com.genealogy.platform.services.genealogy.domain`) is the
 * executor; the YAML files are the source of truth per
 * `agent-execution.md` §4.4.
 *
 * Dependencies intentionally avoid coupling to other services.
 * Cross-service interaction happens via gRPC (E4.1+ gRPC stubs
 * land in E4.x) and Kafka events under
 * `contracts/events/genealogy/v1/`.
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
            resources.srcDirs("src/main/resources", "db/migration")
        }
        test {
            java.srcDirs("src/test/java")
        }
    }
}

dependencies {
    implementation(project(":libs:platform-spring-boot-starter"))
    implementation(project(":libs:platform-security"))
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.grpc)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.openfeature.sdk)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
}

tasks.named("processResources") {
    // Same handling as audit-service: EXCLUDE on duplicate path so
    // a stray build/ output or temp copy cannot break the build.
    (this as Copy).duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
