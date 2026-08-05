/*
 * Shared cross-cutting library `platform-feature-flags`.
 */
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    testImplementation(libs.bundles.testing.unit)
}
