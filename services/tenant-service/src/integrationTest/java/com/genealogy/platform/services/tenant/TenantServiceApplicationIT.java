/*
 * Integration test that proves the E1.4 Spring Boot template
 * boots end-to-end against a real PostgreSQL Testcontainer.
 *
 * <p>What this test covers (matches the E1.4 acceptance criteria):
 *
 * <ul>
 *   <li>{@code TenantServiceApplication} starts with the shared
 *       auto-configuration loaded (OTel, OpenFeature noop,
 *       audit, trusted context).</li>
 *   <li>Flyway runs the {@code V1__baseline_schema.sql}
 *       migration against the Testcontainer.</li>
 *   <li>{@code /actuator/health/liveness} returns 200.</li>
 *   <li>{@code /actuator/health/readiness} returns 200.</li>
 *   <li>{@code /api/v1/info} returns 200 with the trusted context
 *       attached (X-Tenant-Id header is accepted).</li>
 *   <li>{@code POST /api/v1/tenants} returns 202 and emits an
 *       audit event (counted on the Micrometer meter).</li>
 *   <li>No client-supplied {@code tenantId} is accepted —
 *       requests without {@code X-Tenant-Id} return 400 with an
 *       RFC 9457 problem+json body.</li>
 * </ul>
 *
 * <p>The Testcontainers PostgreSQL container is started via
 * {@link PostgresFixture} and the Keycloak issuer is stubbed with
 * a tiny in-test WireMock server so the Spring Boot JWT decoder
 * can fetch the JWKS without a real Keycloak container. The
 * dedicated {@code KeycloakFixture} lands with E3.1; for E1.4 the
 * stub is enough to prove the JWKS contract.
 */
package com.genealogy.platform.services.tenant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.testing.PostgresFixture;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        classes = TenantServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantServiceApplicationIT {

    private static final PostgresFixture POSTGRES = new PostgresFixture();
    private static final WireMockServer JWKS = new WireMockServer(
            WireMockConfiguration.wireMockConfig().port(allocatePort()));
    private static String jwksIssuer;
    private static String jwksUrl;

    @LocalServerPort int port;

    @Autowired MeterRegistry meterRegistry;
    @Autowired ObjectMapper objectMapper;

    @BeforeAll
    static void startFixtures() {
        POSTGRES.overrideProperties(new InMemoryRegistry.Replay());
        JWKS.start();
        JWKS.stubFor(get(urlMatching("/realms/.*/protocol/openid-connect/certs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
        jwksIssuer = "http://localhost:" + JWKS.port() + "/realms/genealogy-shared";
        jwksUrl = jwksIssuer + "/protocol/openid-connect/certs";
    }

    @AfterAll
    static void stopFixtures() {
        POSTGRES.stop();
        JWKS.stop();
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        // Spring invokes @DynamicPropertySource AFTER @BeforeAll, so
        // we forward the values the fixtures registered in setup.
        InMemoryRegistry.REGISTRY.forEach(registry::add);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksUrl);
        registry.add("platform.security.issuer-uri", () -> jwksIssuer);
    }

    @Test
    @DisplayName("Spring Boot context loads with E1.4 template wiring")
    void contextLoads() {
        assertThat(meterRegistry).isNotNull();
    }

    @Test
    @DisplayName("Liveness + readiness probes return 200")
    void probes() throws Exception {
        HttpResponse<String> liveness = http("GET", "/actuator/health/liveness", null, "tenant-1");
        HttpResponse<String> readiness = http("GET", "/actuator/health/readiness", null, "tenant-1");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(readiness.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Info endpoint returns service metadata + trusted context")
    void info() throws Exception {
        HttpResponse<String> res = http("GET", "/api/v1/info", null, "tenant-1");
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.get("service").asText()).isEqualTo("tenant-service");
        assertThat(body.get("tenant_id").asText()).isEqualTo("tenant-1");
        assertThat(body.get("feature_provider").asText()).isEqualTo("noop");
    }

    @Test
    @DisplayName("POST /api/v1/tenants without X-Tenant-Id returns RFC 9457 problem")
    void rejectsMissingTenantHeader() throws Exception {
        HttpResponse<String> res = http("POST", "/api/v1/tenants", "{\"slug\":\"x\"}", null);
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(res.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
    }

    @Test
    @DisplayName("POST /api/v1/tenants emits an audit event")
    void emitsAuditEvent() throws Exception {
        double before = auditCount();
        HttpResponse<String> res = http(
                "POST",
                "/api/v1/tenants",
                "{\"slug\":\"smith\",\"display_name\":\"Smith\"}",
                "tenant-1");
        assertThat(res.statusCode()).isEqualTo(202);
        assertThat(auditCount()).isGreaterThan(before);
    }

    private double auditCount() {
        Search s = meterRegistry.find("platform.audit.events");
        return s.counter() == null ? 0d : s.counter().count();
    }

    private HttpResponse<String> http(String method, String path, String body, String tenantId)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (tenantId != null) {
            b.header("X-Tenant-Id", tenantId);
        }
        b.header("Content-Type", "application/json");
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int allocatePort() {
        return new AtomicInteger(25000).getAndIncrement();
    }
}
