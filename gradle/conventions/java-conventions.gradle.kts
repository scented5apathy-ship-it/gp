/*
 * Apply via:
 *
 *     apply(from = "$rootDir/gradle/conventions/java-conventions.gradle.kts")
 *
 * Pins the Java 21 toolchain per ADR-E0.5-01, configures JUnit 5,
 * logging and reproducible JARs, and enables strict dependency locking
 * so `./gradlew --write-locks` reproduces the build byte-for-byte.
 *
 * This script is intended to be applied to a subproject that has
 * already applied the `java` plugin (via the root `build.gradle.kts`).
 */
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
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