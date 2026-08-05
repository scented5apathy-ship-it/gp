/*
 * Worker module `media-worker`.
 */
plugins {
    java
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.postgres)
    implementation(libs.temporal.sdk)
    implementation(libs.openfeature.sdk)
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
}
