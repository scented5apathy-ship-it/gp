/*
 * Apply via:
 *
 *     apply(from = "$rootDir/gradle/conventions/service-conventions.gradle.kts")
 *
 * Adds the cross-service ArchUnit guard that blocks `services/<svc>/`
 * from importing another service's `db/` (Flyway / jOOQ) or `domain/`
 * (entities, value objects) packages. This is the E1.1 acceptance
 * criterion expressed as a unit test the build runs on every commit.
 *
 * The test source itself lives at
 * `services/<svc>/src/test/java/.../ServiceBoundaryTest.java` so that
 * each service module owns its own ArchUnit configuration; this script
 * only declares the dependency.
 */
if (project.configurations.findByName("testImplementation") != null) {
    project.dependencies.add(
        "testImplementation",
        "com.tngtech.archunit:archunit-junit5:1.3.0",
    )
}