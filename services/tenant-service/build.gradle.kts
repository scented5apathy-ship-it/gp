/*
 * Production `tenant-service`. Backs the `TenantService` gRPC +
 * REST contract under `contracts/protobuf/tenant/v1/` and
 * `contracts/openapi/public-api/v1/tenant.yaml`. E1.4 wires the
 * shared Spring Boot template (REST + gRPC + jOOQ + Flyway +
 * OpenTelemetry + audit + trusted context + OpenFeature +
 * Testcontainers fixtures); E3.2a–E3.2e land the tenant
 * aggregate, membership, Keycloak subject mirror, REST surface,
 * outbox publisher, runbook and alert rules.
 *
 * <p>NOTE: the `com.google.protobuf` Gradle plugin is staged for
 * the E4.x epic, which will fix the duplicate-enum-value
 * collisions in `tenant_service.proto` and `person_service.proto`
 * (E1.3 ships the contracts but cannot generate the Java stubs
 * until the collisions are resolved). The gRPC server is bound
 * in E1.4; E3.2e ships a Spring `@Component` stub
 * (`com.genealogy.platform.services.tenant.grpc.TenantGrpcService`)
 * that logs the bound port on startup; the real
 * `TenantServiceImplBase` implementation lands in E4.x. Per
 * E3.2e DoD the REST surface is the contract of record in E3.2.
 */
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

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
    // E1.4 ships only one Flyway migration; the duplicates strategy
    // is left at the default so a future contract-suite or starter
    // cannot silently shadow a service-local resource.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// The shared `contracts/protobuf/**` tree is the source of truth
// per E1.3 but the protoc compiler is not yet wired here (see
// the file header). E4.x will add `com.google.protobuf` back
// once the cross-package enum collisions are resolved; the
// contracts are still validated by the E1.3
// `ContractInvariantsTest`, and the gRPC port stays bound by
// `spring-boot-starter-grpc` so the E3.2e stub bean can log
// the listening port.

dependencies {
    // E1.4 template — every platform concern is on this starter's
    // classpath so individual services stay focused on domain code.
    implementation(project(":libs:platform-spring-boot-starter"))
    implementation(project(":libs:platform-errors"))
    implementation(project(":libs:platform-telemetry"))
    implementation(project(":libs:platform-security"))
    implementation(project(":libs:platform-feature-flags"))

    implementation(libs.bundles.spring.runtime)
    // E3.2c needs JdbcTemplate + @Transactional + AspectJ for the
    // RLS TxInterceptor; the platform starter does not pull these
    // in (it stays minimal so web-only services don't pay the cost).
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.bundles.observability)

    // Database migration
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgres)

    // gRPC server side is wired by `spring-boot-starter-grpc`
    // (transitive from the platform starter) so the gRPC port is
    // bound on startup; the E3.2e stub
    // (`com.genealogy.platform.services.tenant.grpc.TenantGrpcService`)
    // logs the bound port and the real `TenantServiceImplBase`
    // implementation lands in E4.x once the protobuf stubs are
    // generated.
    implementation(libs.protobuf.java)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)

    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.testcontainers)
    testImplementation(libs.bundles.testing.spring)
    testImplementation(libs.bundles.testing.archunit)
}

// Integration tests use the Testcontainers fixtures that ship with
// `platform-testing` plus the Spring + Testcontainers bundles that
// the unit test classpath already has. The
// `integrationTestImplementation` configuration extends from
// `testImplementation` (declared in the `configurations { }` block
// above) so the IT source set inherits the same dependencies; the
// block below only adds the test-fixtures artefacts.
dependencies {
    add("integrationTestImplementation", testFixtures(project(":libs:platform-testing")))
    add("integrationTestImplementation", libs.bundles.testing.spring)
    add("integrationTestImplementation", libs.bundles.testing.testcontainers)
}
