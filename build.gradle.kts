/*
 * Genealogy Platform — root build.gradle.kts.
 *
 * Every subproject applies the shared conventions from
 * `gradle/conventions/` so that:
 *   - the Java 21 toolchain (ADR-E0.5-01) is configured in one place;
 *   - dependency locking produces `gradle.lockfile` per module;
 *   - the cross-service ArchUnit boundary guard runs in every service.
 *
 * The Java 21 toolchain configuration is applied to all subprojects
 * eagerly (before each subproject's `build.gradle.kts` is evaluated)
 * because it sets `sourceCompatibility` / `targetCompatibility` which
 * the subproject's own `java { }` block depends on. The
 * ArchUnit-boundary dependency is added lazily in `afterEvaluate` so
 * it lands in the testCompileClasspath after the subproject has
 * registered its own `testImplementation` configurations.
 *
 * Spotless + Checkstyle configuration is staged for E1.6 (CI
 * security + supply-chain task) because the `com.diffplug.spotless`
 * plugin must come from the Gradle Plugin Portal which is not yet
 * provisioned in every developer environment. The Gradle script
 * plugin `gradle/conventions/java-conventions.gradle.kts` carries the
 * `apply(plugin = "com.diffplug.spotless")` block — comment it back
 * in after E1.6 provisions the plugin mirror.
 */
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
}

group = "com.genealogy.platform"
version = "0.1.0"

allprojects {
    group = rootProject.group
    version = rootProject.version
    // Repositories are configured centrally in settings.gradle.kts via
    // `dependencyResolutionManagement`. Don't redeclare here, otherwise
    // `repositoriesMode = FAIL_ON_PROJECT_REPOS` will fail the build.

    // Apply the shared Java 21 toolchain + JUnit 5 + reproducible-jar
    // configuration eagerly so that subproject `java { }` blocks can
    // rely on the convention.
    apply(from = rootProject.file("gradle/conventions/java-conventions.gradle.kts"))

    tasks.withType<Test>().configureEach {
        testLogging {
            events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
            showStandardStreams = false
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

subprojects {
    afterEvaluate {
        // Add the ArchUnit test dependency last so it appears in the
        // resolved testCompileClasspath regardless of whether the
        // subproject's own `build.gradle.kts` already declared a
        // `dependencies { testImplementation(...) }` block.
        val isServiceWorkerOrApp =
            projectDir.absolutePath.contains("/services/") ||
                projectDir.absolutePath.contains("/workers/") ||
                projectDir.absolutePath.contains("/apps/")
        if (isServiceWorkerOrApp &&
            configurations.findByName("testImplementation") != null
        ) {
            dependencies.add(
                "testImplementation",
                "com.tngtech.archunit:archunit-junit5:1.3.0",
            )
        }
    }
}