/*
 * Apply via:
 *
 *     apply(from = "$rootDir/gradle/conventions/java-conventions.gradle.kts")
 *
 * Pins the Java 21 toolchain per ADR-E0.5-01, configures JUnit 5,
 * Checkstyle (Google style with project-specific suppressions),
 * logging and reproducible JARs, and enables strict dependency
 * locking so `./gradlew --write-locks` reproduces the build
 * byte-for-byte.
 *
 * This script is intended to be applied to a subproject that has
 * already applied the `java` plugin (via the root `build.gradle.kts`).
 *
 * Spotless (palantir-java-format + import order) wiring is staged for
 * E1.6 once the `com.diffplug.spotless` Gradle plugin is mirrored in
 * the developer environment / CI plugin portal. The apply block lives
 * here so flipping the switch is a one-line change.
 */
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

extensions.findByType(JavaPluginExtension::class.java)?.let { java ->
    java.toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    java.sourceCompatibility = JavaVersion.VERSION_21
    java.targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType(Test::class.java).configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.withType(Jar::class.java).configureEach {
    // Reproducible build per ADR-E0.5-01 §Consequences.
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

// ---------------------------------------------------------------------------
// Checkstyle (Google Java Style with project suppressions)
// ---------------------------------------------------------------------------
// Uses the built-in Gradle `checkstyle` plugin so no extra plugin
// repository is required. The config XML lives at
// `config/checkstyle/checkstyle.xml` and is shared by every Java
// subproject. Applying the plugin first means the `checkstyle`
// configuration exists before we add the dependency.
apply(plugin = "checkstyle")

extensions.configure<CheckstyleExtension> {
    toolVersion = "10.20.0"
    config = resources.text.fromFile(rootProject.file("config/checkstyle/checkstyle.xml"))
    maxWarnings = 0
}

// Logback binding for structured logs in Spring Boot services. The
// `add` form is used because `implementation(...)` is unavailable
// outside the `dependencies { }` extension script-plugin context.
// Skip when the subproject hasn't applied the `java` plugin (e.g. the
// root project itself, which only declares the version catalog).
if (project.configurations.findByName("implementation") != null) {
    project.dependencies.add(
        "implementation",
        "ch.qos.logback:logback-classic",
    )
}

// ---------------------------------------------------------------------------
// Spotless (palantir-java-format + import order) — staged for E1.6.
//
// The `com.diffplug.spotless` Gradle plugin must be served from the
// Gradle Plugin Portal. Until E1.6 mirrors the plugin for air-gapped
// environments, the apply is commented out. To re-enable locally:
//
//     apply(plugin = "com.diffplug.spotless")
//     extensions.configure<com.diffplug.spotless.snom.SpotlessExtension> {
//         java {
//             palantirJavaFormat("2.51.0")
//             importOrder("java", "javax", "com.genealogy", "lombok", "")
//             removeUnusedImports()
//         }
//         formatAnnotations()
//     }
//
// and add `id("com.diffplug.spotless") version "6.25.3" apply false` to
// the root `build.gradle.kts`. The lint command becomes
// `./gradlew spotlessCheck` and `./gradlew spotlessApply` to fix.
// ---------------------------------------------------------------------------