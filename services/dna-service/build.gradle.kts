/*
 * E1.1 + E10 — `dna-service` skeleton + DNA isolation + consent +
 * raw-upload/matching + revoke/export/delete. E1.1 wires the Gradle
 * module so the monorepo build, lockfile and CI smoke run end-to-end.
 * E10 adds the four DNA contract-side guardrails (DnaIsolationGuard,
 * DnaConsentGuard, DnaRawUploadGuard, DnaRevokeGuard) plus the
 * closed-set enums and shared limits. Flyway migration + jOOQ
 * repository + Kafka consumer + Apicurio codec + Temporal worker +
 * OpenFeature wiring land in the later E10.x / E11.x sub-epics.
 *
 * Per ADR-E0.5-01 the module inherits the Java 21 toolchain
 * through the convention script and is locked to keep the build
 * reproducible.
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
