/*
 * `notification-service` Gradle module. E1.1 wires the skeleton so
 * the monorepo build, lockfile and CI smoke run end-to-end. E11.1 +
 * E11.2 add the dispatch + privacy-safe-delivery contract-side
 * guardrails (`NotificationGuard`, `PrivacySafeDeliveryGuard`) plus
 * the shared `E11Limits` + `E11ForbiddenPayloadKeys` catalogues.
 *
 * Per ADR-E0.5-01 the module inherits the Java 21 toolchain through
 * the convention script and is locked to keep the build reproducible.
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
