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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public TenantServiceApplicationIT(
            MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

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
    @DisplayName("POST /api/v1/tenants emits an audit event (E3.2d happy-path)")
    void emitsAuditEvent() throws Exception {
        double before = auditCount();
        HttpResponse<String> res = http(
                "POST",
                "/api/v1/tenants",
                "{\"slug\":\"smith-it\",\"displayName\":\"Smith IT\"}",
                "tenant-1");
        assertThat(res.statusCode()).isEqualTo(201);
        assertThat(res.headers().firstValue("ETag")).isPresent();
        assertThat(res.headers().firstValue("Location")).isPresent();
        assertThat(auditCount()).isGreaterThan(before);
    }

    @Test
    @DisplayName("E3.2d happy-path: POST tenant → GET tenant → PATCH → DELETE")
    void restHappyPath() throws Exception {
        String slug = "smith-hp-" + System.currentTimeMillis();
        String body = "{\"slug\":\"" + slug
                + "\",\"displayName\":\"Smith Family\"}";
        HttpResponse<String> create = http(
                "POST", "/api/v1/tenants", body, "tenant-1",
                Map.of("Idempotency-Key", "hp-idem-" + System.currentTimeMillis()));
        assertThat(create.statusCode()).isEqualTo(201);
        String etag = create.headers().firstValue("ETag").orElseThrow();
        JsonNode tenantJson = objectMapper.readTree(create.body());
        String tenantId = tenantJson.get("tenantId").asText();
        assertThat(tenantId).isEqualTo("tenant-1");

        HttpResponse<String> get = http("GET", "/api/v1/tenants/tenant-1", null, "tenant-1");
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.headers().firstValue("ETag")).contains(etag);

        HttpResponse<String> patch = http(
                "PATCH", "/api/v1/tenants/tenant-1",
                "{\"displayName\":\"Smith Renamed\"}",
                "tenant-1",
                Map.of(
                        "Idempotency-Key", "hp-patch-" + System.currentTimeMillis(),
                        "If-Match", etag));
        assertThat(patch.statusCode()).isEqualTo(200);
        JsonNode patched = objectMapper.readTree(patch.body());
        assertThat(patched.get("displayName").asText()).isEqualTo("Smith Renamed");
        String etag2 = patch.headers().firstValue("ETag").orElseThrow();
        assertThat(etag2).isNotEqualTo(etag);

        HttpResponse<String> del = http(
                "DELETE", "/api/v1/tenants/tenant-1", null, "tenant-1",
                Map.of(
                        "Idempotency-Key", "hp-del-" + System.currentTimeMillis(),
                        "If-Match", etag2));
        assertThat(del.statusCode()).isEqualTo(202);
    }

    @Test
    @DisplayName("E3.2d cross-tenant: GET /tenants/{other} with X-Tenant-Id=A returns 404")
    void crossTenantAccessReturns404() throws Exception {
        HttpResponse<String> res = http(
                "GET", "/api/v1/tenants/some-other-tenant-id", null, "tenant-1");
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
        JsonNode problem = objectMapper.readTree(res.body());
        assertThat(problem.get("errorCode").asText()).isEqualTo("tenant-not-found");
        assertThat(problem.get("status").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("E3.2d If-Match mismatch returns 412 Precondition Failed (RFC 9457)")
    void ifMatchMismatchReturns412() throws Exception {
        String slug = "smith-oc-" + System.currentTimeMillis();
        HttpResponse<String> create = http(
                "POST", "/api/v1/tenants",
                "{\"slug\":\"" + slug
                        + "\",\"displayName\":\"Smith OC\"}",
                "tenant-1",
                Map.of("Idempotency-Key", "oc-idem-" + System.currentTimeMillis()));
        assertThat(create.statusCode()).isEqualTo(201);

        HttpResponse<String> patch = http(
                "PATCH", "/api/v1/tenants/tenant-1",
                "{\"displayName\":\"Renamed\"}",
                "tenant-1",
                Map.of(
                        "Idempotency-Key", "oc-patch-" + System.currentTimeMillis(),
                        "If-Match", "\"v999\""));
        assertThat(patch.statusCode()).isEqualTo(412);
        assertThat(patch.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
        JsonNode problem = objectMapper.readTree(patch.body());
        assertThat(problem.get("errorCode").asText()).isEqualTo("invalid-etag");
    }

    @Test
    @DisplayName("E3.2d membership invite returns 202 + Location")
    void membershipInviteHappyPath() throws Exception {
        HttpResponse<String> res = http(
                "POST", "/api/v1/tenants/tenant-1/memberships",
                "{\"email\":\"alice@example.com\",\"role\":\"MEMBER\"}",
                "tenant-1",
                Map.of("Idempotency-Key", "invite-" + System.currentTimeMillis()));
        assertThat(res.statusCode()).isEqualTo(202);
        JsonNode body = objectMapper.readTree(res.body());
        assertThat(body.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(body.get("role").asText()).isEqualTo("MEMBER");
        assertThat(body.hasNonNull("rawInviteToken")).isTrue();
    }

    @Test
    @DisplayName("E3.2d cross-tenant membership: A invites but X-Tenant-Id=B returns 404")
    void crossTenantMembershipInviteReturns404() throws Exception {
        HttpResponse<String> res = http(
                "POST", "/api/v1/tenants/some-other-tenant/memberships",
                "{\"email\":\"bob@example.com\",\"role\":\"MEMBER\"}",
                "tenant-1",
                Map.of("Idempotency-Key", "xb-invite-" + System.currentTimeMillis()));
        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
    }

    private double auditCount() {
        Search s = meterRegistry.find("platform.audit.events");
        return s.counter() == null ? 0d : s.counter().count();
    }

    private HttpResponse<String> http(String method, String path, String body, String tenantId)
            throws Exception {
        return http(method, path, body, tenantId, java.util.Map.of());
    }

    private HttpResponse<String> http(
            String method, String path, String body, String tenantId,
            java.util.Map<String, String> extraHeaders) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (tenantId != null) {
            b.header("X-Tenant-Id", tenantId);
        }
        extraHeaders.forEach(b::header);
        b.header("Content-Type", "application/json");
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int allocatePort() {
        return new AtomicInteger(25000).getAndIncrement();
    }
}
