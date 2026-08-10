/*
 * Shared cross-cutting library `platform-security`.
 *
 * E3.4 ships the ABAC domain layer (AbacPolicyEngine, AbacDecision,
 * obligations, redaction, decision cache, condition predicates)
 * consumed by every Java service. Other concerns of this starter
 * (OidcTokenValidator, OpenFgaClient, SignedUrlSigner) ship in
 * later epics (E3.1 already wires OIDC + Keycloak subject mirror
 * into tenant-service; OpenFGA client + ABAC overlay land here).
 *
 * NOTE: this module does NOT apply
 * `gradle/conventions/java-conventions.gradle.kts` because that
 * convention adds `ch.qos.logback:logback-classic` to the
 * `implementation` configuration without a version, which only
 * resolves when the Spring Boot dependency-management plugin
 * supplies a BOM. The Java 21 toolchain + Checkstyle baseline is
 * applied directly below. Services that depend on this library
 * get their toolchain from `services/<svc>/build.gradle.kts`.
 */
plugins {
    `java-library`
    id("checkstyle")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

extensions.configure<org.gradle.api.plugins.quality.CheckstyleExtension> {
    toolVersion = "10.20.0"
    config = resources.text.fromFile(rootProject.file("config/checkstyle/checkstyle.xml"))
    maxWarnings = 0
}

dependencies {
    // E3.4 keeps the dependency surface minimal — pure Java, no
    // Spring required so unit tests stay fast. Spring wiring
    // (auto-configuration + decision-cache bean) lands in E4.x
    // once services need runtime cache invalidation listeners.
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.testing.unit)
}
