/*
 * Skeleton for `research-service`. Implementation lands in later
 * epics (E3.x for tenant, E4.x for genealogy, E5.x for sharing/DNA,
 * etc.). E1.1 only wires the Gradle module so the monorepo build,
 * lockfile and CI smoke run end-to-end.
 */
plugins {
    java
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
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.openfeature.sdk)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
}
