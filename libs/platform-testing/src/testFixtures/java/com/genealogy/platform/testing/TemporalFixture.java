package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Temporal Testcontainers fixture. Uses the official
 * {@code temporalio/auto-setup} image (PostgreSQL + Temporal
 * server + visibility + UI in one container) at the version
 * pinned in ADR-E0.5-01 (1.26.x). The dedicated production
 * topology (separate PostgreSQL + Temporal + visibility) lands
 * with E2.4; E1.4 only proves the service can reach the gRPC
 * frontend.
 */
public class TemporalFixture implements TestcontainersFixture {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("temporalio/auto-setup:1.26.0");

    private static final int GRPC_PORT = 7233;

    private final GenericContainer<?> container;

    public TemporalFixture() {
        this(new GenericContainer<>(IMAGE)
                .withEnv("DB", "sqlite")
                .withEnv("SQLITE_PRAGMA", "journal_mode=WAL")
                .withExposedPorts(GRPC_PORT)
                .withReuse(true));
    }

    public TemporalFixture(GenericContainer<?> container) {
        this.container = container;
    }

    @Override
    public void overrideProperties(DynamicPropertyRegistry registry) {
        if (!container.isRunning()) {
            container.start();
        }
        String target = String.format("%s:%d", container.getHost(), container.getMappedPort(GRPC_PORT));
        registry.add("platform.temporal.target", () -> target);
    }

    public GenericContainer<?> container() {
        return container;
    }

    @Override
    public void stop() {
        // no-op; see PostgresFixture
    }
}
