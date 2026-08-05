/*
 * Shared cross-cutting library `platform-errors`.
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
