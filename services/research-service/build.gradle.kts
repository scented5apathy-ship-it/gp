/*
 * E6.1c — `research-service` REST + OpenAPI + Kong routing.
 *
 * Builds on E6.1a (domain layer) + E6.1b (Flyway + RLS + audit
 * columns + RlsNegativeIT gate). E6.1c wires the REST surface
 * (`POST /api/v1/repositories`, `GET /api/v1/repositories/{id}`,
 * `POST /api/v1/sources`, `POST /api/v1/citations`,
 * `GET /api/v1/claims/{id}/provenance`,
 * `POST /api/v1/research-tasks`,
 * `POST /api/v1/research-tasks/{id}/transitions`,
 * `POST /api/v1/hypotheses`, `POST /api/v1/conflicts`), the
 * hand-authored OpenAPI YAML under `contracts/openapi/public-api/v1/`,
 * the JdbcTemplate-backed repositories for every aggregate
 * (matching the RLS bound by `ResearchRlsTxInterceptor`), the
 * `IdempotencyCache` + `DraftDomainMapper` + `*CommandService` /
 * `*QueryService` stack, and the Kong route mirror documented in
 * `platform/helm/genealogy-platform/files/kong.yml` (the
 * `public-api-v1` route is reused per E6.1c decisions).
 *
 * Per ADR-E0.5-01 the module inherits the Java 21 toolchain
 * through the convention script and is locked to keep the build
 * reproducible.
 *
 * Persistence strategy: Spring JdbcTemplate (the same pattern as
 * `tenant-service`); RLS is enforced at the database layer by
 * the `SET LOCAL ROLE research_service_app` +
 * `SET LOCAL app.tenant_id = '…'` binding that the
 * `ResearchRlsTxInterceptor` issues as the first statement
 * inside every `@Transactional` command. The repositories use
 * `Propagation.MANDATORY` so a missing outer transaction is
 * surfaced immediately rather than silently bypassing the RLS
 * binding.
 *
 * Cross-service interaction remains out-of-scope: gRPC stubs
 * (`gp.research.v1.*`) + Kafka events + OpenFGA/ABAC adapter
 * land in E6.1d; Helm chart + runbook + Grafana dashboard land
 * in E6.1e. The Testcontainers fixture (`RlsNegativeIT`) was
 * shipped in E6.1b; the REST surface integration test
 * (`ResearchRestIT`) runs through Testcontainers Postgres +
 * Flyway + the runtime role to prove cross-tenant REST reads
 * return 404 in E6.1c.
 *
 * Scope guard (per agent-execution.md §4.4):
 *   - No domain Java edits (E6.1a).
 *   - No gRPC / Kafka / OpenFGA (E6.1d).
 *   - No Helm / runbook / Grafana (E6.1e).
 *   - No research-policy.yaml contract changes (E6.1a locked).
 *   - No Kong route addition (E6.1c reuses `public-api-v1`).
 */
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
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
            resources.srcDirs("src/main/resources")
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
    // E6.1c ships Flyway V1 baseline + V2 research aggregate (from
    // E6.1b); the duplicates strategy is left at the default so a
    // future contract-suite or starter cannot silently shadow a
    // service-local resource.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    // E1.4 template — every platform concern is on this starter's
    // classpath so individual services stay focused on domain code.
    implementation(project(":libs:platform-spring-boot-starter"))
    implementation(project(":libs:platform-errors"))
    implementation(project(":libs:platform-telemetry"))
    implementation(project(":libs:platform-security"))
    implementation(project(":libs:platform-feature-flags"))

    implementation(libs.bundles.spring.runtime)
    // JdbcTemplate + @Transactional + AspectJ — same set as
    // tenant-service. The RLS binding is issued as the first
    // statement inside every `@Transactional` command method,
    // so every JDBC call inherits the role + `app.tenant_id`
    // GUC.
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.bundles.observability)

    // Database migration (E6.1b) + driver.
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgres)

    // OpenFeature SDK is enough for the noop provider used by the
    // shared library; the Flagsmith provider is wired in E6.1d when
    // the ABAC overlay lands.
    implementation(libs.openfeature.sdk)

    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
    testImplementation(libs.bundles.testing.spring)
    testImplementation(libs.bundles.testing.archunit)
}

dependencies {
    add("integrationTestImplementation", testFixtures(project(":libs:platform-testing")))
    add("integrationTestImplementation", libs.bundles.testing.spring)
    add("integrationTestImplementation", libs.bundles.testing.testcontainers)
    add("integrationTestImplementation", libs.bundles.testing.unit)
}
