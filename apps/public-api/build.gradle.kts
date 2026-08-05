/*
 * Public API Spring Boot application skeleton. Implementation follows
 * in E1.4 + E15.x. E1.1 only registers the module so the monorepo
 * build, lockfile and ArchUnit checks can run end-to-end.
 */
plugins { java }

dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.openfeature.sdk)
    testImplementation(libs.bundles.testing.unit)
}
