package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * OpenFGA Testcontainers fixture. Uses the official OpenFGA image
 * at the version pinned in ADR-E0.5-01 (1.x) and exposes the
 * HTTP + gRPC ports. The dedicated store-per-tenant topology from
 * ADR-E0.5-06 is wired by E3.3; for E1.4 the fixture only proves
 * the SDK can reach the server and that the service can publish
 * tuples against an HTTP API URL.
 */
public class OpenFgaFixture implements TestcontainersFixture {

    private static final DockerImageName IMAGE = DockerImageName.parse("openfga/openfga:1.8.4");

    private static final int HTTP_PORT = 8080;
    private static final int GRPC_PORT = 8081;

    private final GenericContainer<?> container;

    public OpenFgaFixture() {
        this(new GenericContainer<>(IMAGE)
                .withCommand("run")
                .withExposedPorts(HTTP_PORT, GRPC_PORT)
                .withReuse(true));
    }

    public OpenFgaFixture(GenericContainer<?> container) {
        this.container = container;
    }

    @Override
    public void overrideProperties(DynamicPropertyRegistry registry) {
        if (!container.isRunning()) {
            container.start();
        }
        String httpUrl = String.format("http://%s:%d",
                container.getHost(), container.getMappedPort(HTTP_PORT));
        registry.add("platform.openfga.api-url", () -> httpUrl);
        registry.add("platform.openfga.grpc-url", () -> String.format(
                "%s:%d", container.getHost(), container.getMappedPort(GRPC_PORT)));
    }

    public GenericContainer<?> container() {
        return container;
    }

    @Override
    public void stop() {
        // no-op; see PostgresFixture
    }
}
