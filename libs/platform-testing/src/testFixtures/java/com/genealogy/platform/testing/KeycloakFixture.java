package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Keycloak Testcontainers fixture. Uses the official Keycloak
 * image at the version pinned in ADR-E0.5-01 (26.x) and starts
 * Keycloak in {@code start-dev} mode with a `genealogy-shared`
 * realm import placeholder. E3.1 wires the realm import file
 * (config-as-code). For E1.4 the fixture only proves that the
 * service can validate JWTs against a Keycloak issuer.
 */
public class KeycloakFixture implements TestcontainersFixture {

    private static final DockerImageName IMAGE = DockerImageName.parse("quay.io/keycloak/keycloak:26.0.0");

    private static final int PORT = 8080;

    private final GenericContainer<?> container;

    public KeycloakFixture() {
        this(new GenericContainer<>(IMAGE)
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                .withCommand("start-dev --http-port=" + PORT)
                .withExposedPorts(PORT)
                .withReuse(true));
    }

    public KeycloakFixture(GenericContainer<?> container) {
        this.container = container;
    }

    @Override
    public void overrideProperties(DynamicPropertyRegistry registry) {
        if (!container.isRunning()) {
            container.start();
        }
        String issuer = String.format("http://%s:%d/realms/genealogy-shared",
                container.getHost(), container.getMappedPort(PORT));
        registry.add("platform.security.issuer-uri", () -> issuer);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
    }

    public GenericContainer<?> container() {
        return container;
    }

    @Override
    public void stop() {
        // no-op; see PostgresFixture
    }
}
