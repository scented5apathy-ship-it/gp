/*
 * Web BFF Spring Boot application skeleton. Implementation follows in
 * E1.4 + E6.x. E1.1 only registers the Gradle module so the
 * monorepo build, lockfile and CI smoke run end-to-end.
 */
plugins { java }

dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.openfeature.sdk)
    testImplementation(libs.bundles.testing.unit)
}
