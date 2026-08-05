/*
 * Shared cross-testing library `platform-testing`.
 *
 * Provides the Testcontainers fixtures every Java service can
 * declare on a per-test basis (E1.4 acceptance criterion). The
 * fixtures cover every external dependency the E1.4 Spring Boot
 * template talks to: PostgreSQL (jOOQ + Flyway), Kafka (event
 * publication), Keycloak (JWT validation), OpenFGA (relationship
 * authorization), Temporal (workflow), S3 LocalStack (object
 * storage) and Valkey (cache + idempotency state).
 *
 * <p>The library uses the Gradle `java-test-fixtures` plugin so
 * services can depend on the fixtures via
 * `testFixtures(project(":libs:platform-testing"))` (or
 * `integrationTestImplementation(testFixtures(...))`). The
 * fixtures are not on the main compile classpath because they
 * pull in Testcontainers and Spring Boot Test, which would
 * bloat every production jar.
 */
plugins {
    `java-library`
    `java-test-fixtures`
    alias(libs.plugins.spring.dependency.management)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // The fixtures are a public API for the tests of every service,
    // so they are `api` dependencies and appear on the consumer's
    // compile classpath.
    api(libs.spring.boot.starter.test)
    api(libs.bundles.testing.testcontainers)
    api(libs.spring.boot.starter.actuator)
    testImplementation(libs.bundles.testing.unit)
}

