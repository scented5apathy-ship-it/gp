/*
 * Genealogy Platform — root settings.gradle.kts
 *
 * Multi-project build that owns every Java 21 deployable service, the BFF
 * application and shared cross-cutting libraries. Each domain service is
 * the sole owner of its `db/` (Flyway) and `domain/` modules per the
 * design.md §5.1 ownership rule; cross-service imports are blocked by
 * the ArchUnit guard wired through
 * `gradle/conventions/service-conventions.gradle.kts` and the workspace
 * boundary check in `scripts/check-monorepo-boundaries.mjs`.
 */
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "genealogy-platform"

include(
    // shared cross-cutting libraries
    ":libs:platform-errors",
    ":libs:platform-telemetry",
    ":libs:platform-testing",
    ":libs:platform-security",
    ":libs:platform-feature-flags",
    ":libs:platform-spring-boot-starter",
    // edge / BFF / public API
    ":apps:web-bff",
    ":apps:public-api",
    // domain services
    ":services:tenant-service",
    ":services:genealogy-service",
    ":services:research-service",
    ":services:collaboration-service",
    ":services:media-service",
    ":services:search-service",
    ":services:importexport-service",
    ":services:dna-service",
    ":services:notification-service",
    ":services:reporting-service",
    ":services:operations-service",
    ":services:audit-service",
    // infrastructure workers
    ":workers:media-worker",
    ":workers:search-indexer",
    ":workers:export-worker",
)