/*
 * `operations-service` Gradle module. E1.1 wires the skeleton so
 * the monorepo build, lockfile and CI smoke run end-to-end. E11.4
 * + E11.5 add the entitlement / quota / billing + admin / support /
 * operations contract-side guardrails (`EntitlementGuard`,
 * `AdminSupportGuard`) plus the shared `E11Limits` + `E11ForbiddenPayloadKeys`
 * catalogues.
 *
 * Per ADR-E0.5-01 the module inherits the Java 21 toolchain through
 * the convention script and is locked to keep the build reproducible.
 * Per ADR-E0.5-12 the domain entitlement source-of-truth lives here,
 * NOT in Kong rate-limit metrics (this is enforced by the E11.4
 * `kongRateLimitIsNotSourceOfTruth` guard).
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