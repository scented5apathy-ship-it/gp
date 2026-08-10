/*
 * Web BFF Spring Boot application. E1.1 + E1.4 ship the skeleton;
 * E3.5 wires the BFF-side tenant reconciliation:
 *
 *   - `MembershipReconciler` — calls
 *     `tenant-service::ListMemberships` for the Keycloak subject
 *     and confirms the requested `X-Tenant-Id` matches an
 *     `ACTIVE` membership. Mismatches return 404 to avoid leaking
 *     the existence of the foreign tenant (E3.2d DoD).
 *   - `TenantSelectionGuard` — the servlet filter that the BFF
 *     installs in front of every route that requires a tenant
 *     context. Calls the reconciler, populates the thread-local
 *     `TrustedTenantContext` with the membership role, and
 *     forwards the request.
 *   - `TenantClient` — typed Spring `RestClient` wrapper for the
 *     tenant-service REST surface (used by the reconciler).
 *
 * The BFF does NOT call gRPC directly in E3.5 — the REST hop to
 * tenant-service is the contract of record; the gRPC client
 * interceptor wired in `platform-spring-boot-starter` is the
 * belt-and-braces path for future gRPC calls.
 */
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyLocking { lockAllConfigurations() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.openfeature.sdk)
    implementation(project(":libs:platform-spring-boot-starter"))
    implementation(project(":libs:platform-security"))
    implementation(project(":libs:platform-errors"))
    testImplementation(libs.bundles.testing.unit)
    testImplementation(libs.bundles.testing.spring)
    testImplementation(libs.spring.boot.starter.test)
}
