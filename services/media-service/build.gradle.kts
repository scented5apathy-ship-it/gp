/*
 * E1.1 + E7.1 — `media-service` skeleton + upload lifecycle.
 *
 * E1.1 wires the Gradle module so the monorepo build, lockfile
 * and CI smoke run end-to-end. E7.1 adds the upload-lifecycle
 * domain records + executors (UploadSession, MultipartPart,
 * QuotaLedger, MimePolicy, ChecksumVerifier, QuarantineGate,
 * FinalizeOutcome, AbandonedMultipartSweeper) plus the
 * ArchUnit boundary guard.
 *
 * The contract lives at
 * `contracts/media/upload-lifecycle-policy.yaml` (E7.1) and
 * `platform/helm/genealogy-platform/files/media-upload-lifecycle-policy.yaml`.
 * The Java executor mirrors the contract per
 * `agent-execution.md` §4.4.
 *
 * E7.1 ships the domain + invariants + executor only. The
 * Flyway migration + jOOQ repository + S3 / MinIO adapter +
 * Kafka producer / consumer + OpenFeature wiring land in the
 * later E7.x / E11.x sub-epics.
 *
 * Per ADR-E0.5-01 the module inherits the Java 21 toolchain
 * through the convention script and is locked to keep the
 * build reproducible.
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
