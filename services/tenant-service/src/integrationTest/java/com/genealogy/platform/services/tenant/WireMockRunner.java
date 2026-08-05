/*
 * Test-local helper: a tiny WireMock server that mimics the
 * Keycloak JWKS endpoint so the Spring Boot JWT decoder can
 * initialise without a real Keycloak container. Kept package
 * private because it is an implementation detail of the IT
 * scaffolding in E1.4; service tests in E3.x will replace it
 * with the {@code KeycloakFixture} once those tests need real
 * OIDC flows.
 */
package com.genealogy.platform.services.tenant;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.concurrent.atomic.AtomicInteger;

final class WireMockRunner {

    private static final AtomicInteger PORTS = new AtomicInteger(25000);

    private WireMockServer server;
    private int port;

    void start() {
        port = PORTS.getAndIncrement();
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port));
        server.start();
        server.stubFor(WireMock.get(WireMock.urlMatching("/realms/.*/protocol/openid-connect/certs"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
    }

    String issuer() {
        return "http://localhost:" + port + "/realms/genealogy-shared";
    }

    String jwksUrl() {
        return issuer() + "/protocol/openid-connect/certs";
    }

    void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
