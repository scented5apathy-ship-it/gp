package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Valkey Testcontainers fixture. Valkey is the cache + idempotency
 * state substrate for E2.7; per ADR-E0.5-01 the default is the
 * latest 8.x release. The fixture exposes the standard Spring
 * Data Redis properties so services can use
 * {@code spring.data.redis.*} transparently.
 */
public class ValkeyFixture implements TestcontainersFixture {

    private static final DockerImageName IMAGE = DockerImageName.parse("valkey/valkey:8.0-alpine");

    private static final int PORT = 6379;

    private final GenericContainer<?> container;

    public ValkeyFixture() {
        this(new GenericContainer<>(IMAGE)
                .withExposedPorts(PORT)
                .withCommand("valkey-server --save", "")
                .withReuse(true));
    }

    public ValkeyFixture(GenericContainer<?> container) {
        this.container = container;
    }

    @Override
    public void overrideProperties(DynamicPropertyRegistry registry) {
        if (!container.isRunning()) {
            container.start();
        }
        String host = container.getHost();
        int port = container.getMappedPort(PORT);
        registry.add("spring.data.redis.host", () -> host);
        registry.add("spring.data.redis.port", () -> port);
        registry.add("platform.valkey.host", () -> host);
        registry.add("platform.valkey.port", () -> port);
    }

    public GenericContainer<?> container() {
        return container;
    }

    @Override
    public void stop() {
        // no-op; see PostgresFixture
    }
}
