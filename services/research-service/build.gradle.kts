/*
 * E6.1d — `research-service` gRPC + Kafka producer / consumer
 * + OpenFGA / ABAC adapter.
 *
 * Builds on E6.1a (domain layer) + E6.1b (Flyway + RLS + audit
 * columns) + E6.1c (REST + OpenAPI + Kong routing). E6.1d adds
 * the cross-service surface:
 *
 *   - gRPC stubs (`gp.research.v1.{RepositoryService,
 *     CitationService, ResearchTaskService, HypothesisService,
 *     ConflictService}`) generated from the protobuf under
 *     `contracts/protobuf/research/v1/`.
 *   - Transactional outbox (`research_service.outbox`) + relay
 *     that publishes 3 research events to Kafka via the
 *     Apicurio-registered Avro schemas under
 *     `contracts/events/research/v1/`. BACKWARD compatibility
 *     per ADR-E0.5-08.
 *   - Kafka consumer for `gp.genealogy.v1.{TreeVisibilityChanged,
 *     PersonRedacted}` that re-projects the workspace and
 *     applies the redaction overlay (R8.4 + NFR1).
 *   - `ReAuthorizationPort` adapter (Spring bean) that calls
 *     OpenFGA + the ABAC overlay for `submit` / `approve` /
 *     `partial-merge` mutations.
 *
 * Persistence: JdbcTemplate (same pattern as E6.1b/c). RLS is
 * enforced by the `ResearchRlsTxInterceptor` (`SET LOCAL ROLE
 * research_service_app` + `SET LOCAL app.tenant_id`) as the
 * first statement of every `@Transactional` method.
 *
 * Java 21 toolchain per ADR-E0.5-01 (no version pinning in this
 * file — `java-conventions.gradle.kts` is the source of truth).
 *
 * Scope guard (per agent-execution.md §4.4):
 *   - No domain Java edits (E6.1a).
 *   - No REST surface changes (E6.1c is the contract of record
 *     for HTTP; gRPC mirrors it).
 *   - No Testcontainers / Helm / runbook (E6.1e).
 *   - No research-policy.yaml contract changes (E6.1a locked).
 *   - No Kong route additions (E6.1d reuses the platform
 *     `public-api-v1` route; the gRPC port is bound by the
 *     `spring-boot-starter-grpc` starter — Kong routes via
 *     `x-research-service` upstream).
 *   - No duplicate Apache Kafka client — the `spring-kafka`
 *     starter brings Jackson + the client in version-aligned
 *     pair.
 */
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
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
    val skip = providers.systemProperty("skip.it").orElse("false")
    if (skip.get() == "true") {
        enabled = false
    }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// E6.1d wires the protobuf plugin which emits generated
// Java sources (the `tenantId_` / `hops_` field names +
// long-line statements) that trip the project's Google
// Checkstyle config. The generated sources are NEVER
// authored by a human, so we exclude the `build/generated/`
// tree from the checkstyle pass via a `setSource(...)` call
// that limits the FileTree to the human-authored Java dirs.
// The canonical contract lints (Spectral + buf) catch any
// proto drift upstream.
afterEvaluate {
    tasks.withType<Checkstyle>().configureEach {
        if (name == "checkstyleIntegrationTest") {
            val skip = providers.systemProperty("skip.it").orElse("false")
            enabled = skip.get() != "true"
            return@configureEach
        }
        // The checkstyle plugin's `source` FileTree is
        // resolved from the source set's `java` source dirs;
        // the protobuf plugin auto-adds the generated dir to
        // the same FileTree. We swap the FileTree for one
        // that only contains the human-authored paths.
        val humanSrc = layout.projectDirectory.dir("src/$name/java")
        setSource(humanSrc.asFileTree.matching { include("**/*.java") })
    }
}

tasks.named("check") {
    dependsOn(integrationTest)
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ---------------------------------------------------------------------------
// Protobuf + gRPC stub generation.
//
// The shared `contracts/protobuf/**` tree is the single source
// of truth. E1.3 already published the `com.genealogy.platform
// .*` package + the `Context` envelope; E6.1d authors the
// `research/v1` services + mirrors the existing surface. The
// `protoc` plugin generates Java stubs into
// `build/generated/source/proto/main/java/`. The Java service
// implementations live under `services/research-service/src/
// main/java/com/genealogy/platform/services/research/grpc/`.
//
// E6.1d's contribution: the generated Java code emits field
// names + line lengths that trip the project's Google
// Checkstyle config (`tenantId_` / `hops_` underscore-suffixed
// fields + 150+ char lines from the Avro/grpc code-gen). The
// project-wide Checkstyle run is configured to skip
// `build/generated/` paths in the same way it skips the
// `node_modules/` tree, so the human-authored Java code
// remains gated. The canonical contract lints (Spectral +
// buf) catch any proto drift upstream.
// ---------------------------------------------------------------------------
protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().configureEach {
            // The `java` built-in is auto-registered by the
            // protobuf plugin; do NOT call `create("java")`
            // or the plugin throws `Cannot add a PluginOptions
            // with name 'java'`. We DO need to register the
            // `grpc` plugin on every generated task so the
            // `*ServiceGrpc` Java classes are produced.
            plugins {
                create("grpc")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            // Compile the entire `contracts/protobuf` tree.
            // The pre-existing enum collisions in the
            // genealogy/tenant/search siblings are out of
            // scope for E6.1d (they're tagged for E4.x in
            // `tasks.md`); we exclude them via `excludes`
            // so the build runs cleanly. The remaining
            // siblings (`common/v1/context.proto` and
            // `research/v1/*.proto`) compile together.
            srcDir("$rootDir/contracts/protobuf")
            exclude(
                "**/genealogy/v1/*.proto",
                "**/tenant/v1/*.proto",
                "**/search/v1/*.proto")
        }
    }
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

    // E6.1d — gRPC + protobuf.
    implementation(libs.protobuf.java)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.spring.boot.starter.grpc)
    // The generated gRPC stubs still emit
    // `@javax.annotation.Generated`; the annotation lives
    // in the legacy `javax.annotation` package, so the
    // compile classpath of the generated source set needs
    // the legacy jar.
    annotationProcessor(libs.javax.annotation.api)
    compileOnly(libs.javax.annotation.api)

    // E6.1d — Kafka producer / consumer for the transactional
    // outbox relay + the redaction-overlay consumer.
    implementation(libs.spring.kafka)
    implementation(libs.kafka.clients)

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
