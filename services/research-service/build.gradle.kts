/*
 * E6.1b — `research-service` persistence: Flyway V2 migration,
 * PostgreSQL Row-Level Security + jOOQ-style repository bind
 * (Spring JdbcTemplate, see ADR-E0.5-02 / `service-conventions`)
 * + audit columns (`created_at`, `updated_at`, `archived_at`,
 * `version`, `created_by_actor_pseudo_id`, `correlation_id`).
 *
 * Mirrors `contracts/research/research-policy.yaml` +
 * `domain/Repository.java` / `Source.java` / `Citation.java` /
 * `ResearchTask.java` / `Hypothesis.java` / `Conflict.java`.
 *
 * E6.1b ships ONLY the schema, the runtime role, the RLS policies
 * and the RlsNegativeIT (Testcontainers) gate that proves the
 * tenant-isolation contract. The application service surface
 * (TenantCommandService / OutboxWriter / REST controllers / gRPC
 * stubs / Kafka producers / Helm chart / runbook) lands in
 * E6.1c / E6.1d / E6.1e per `tasks.md` §E6.1.
 *
 * Per ADR-E0.5-01 the module inherits the Java 21 toolchain
 * through the convention script and is locked to keep the build
 * reproducible.
 *
 * Dependencies intentionally avoid coupling to other services.
 * Cross-service interaction happens via gRPC stubs (E6.1d) and
 * Kafka events under `contracts/events/research/v1/`.
 *
 * Schema-per-service (ADR-E0.5-02) — the migration creates the
 * `research_service` schema in V1 and the aggregate tables +
 * bridge tables + role + RLS policies in V2. No cross-schema
 * FK, no cross-schema privileges.
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
        create("integrationTest") {
            java.srcDirs("src/integrationTest/java")
            resources.srcDirs("src/integrationTest/resources")
            compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
            runtimeClasspath += sourceSets["main"].output + output + sourceSets["test"].output + configurations["testRuntimeClasspath"]
        }
    }
}

configurations {
    named("integrationTestImplementation") {
        extendsFrom(configurations["testImplementation"])
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(configurations["testRuntimeOnly"])
    }
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests that require Docker (Testcontainers)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")
    useJUnitPlatform()
    // CI runs with Docker; developers that don't have it locally
    // can opt out with `-Dskip.it=true`. We read the property via
    // the system-properties provider so a `-D` on the Gradle
    // command line is honoured the same way as a Gradle property.
    val skip = providers.systemProperty("skip.it").orElse("false")
    if (skip.get() == "true") {
        enabled = false
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.withType<Checkstyle>().configureEach {
    // The integration test source set is opt-in (developers skip
    // it locally with `-Dskip.it=true`); the dedicated
    // `checkstyleIntegrationTest` task follows the same flag so
    // the default `check` aggregate does not pull in a Docker-
    // dependent task.
    if (name == "checkstyleIntegrationTest") {
        val skip = providers.systemProperty("skip.it").orElse("false")
        enabled = skip.get() != "true"
    }
}

tasks.named("check") {
    dependsOn(integrationTest)
}

tasks.withType<Copy>().configureEach {
    // E6.1b ships only two Flyway migrations (V1 baseline + V2
    // research aggregate); the duplicates strategy is left at the
    // default so a future contract-suite or starter cannot silently
    // shadow a service-local resource.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.openfeature.sdk)
    // E6.1b wires Spring JDBC for the jOOQ-style repository bind
    // (see ADR-E0.5-02). The full Spring Boot + jOOQ starters land
    // in E6.1c/d when the application services land; E6.1b only
    // needs the JDBC driver + the contract-test scaffolding so the
    // RlsNegativeIT can prove tenant isolation at the database.
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
    // The integrationTest source set inherits `testImplementation`
    // (declared in `configurations { }` above); the block below
    // only adds the test-fixtures artefacts (postgresql + assertj
    // + flyway are already on the test classpath).
    add("integrationTestImplementation", libs.bundles.testing.unit)
    add("integrationTestImplementation", libs.bundles.testing.testcontainers)
}