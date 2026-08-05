package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL Testcontainers fixture. Spins a single-node
 * PostgreSQL 16 (per ADR-E0.5-01) with the {@code test_db} schema
 * and exposes the JDBC URL / username / password through Spring
 * dynamic properties so services can use the standard
 * {@code spring.datasource.url} configuration.
 *
 * <p>The container is shared across the test class via the
 * standard Ryuk reaper; the {@link #stop()} method is a no-op
 * because the JVM shutdown hook stops the container.
 */
public class PostgresFixture implements TestcontainersFixture {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16.4-alpine");

    private final PostgreSQLContainer<?> container;

    public PostgresFixture() {
        this(new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("tenant_service")
                .withUsername("tenant_service")
                .withPassword("tenant_service")
                .withTmpFs(java.util.Map.of("/var/lib/postgresql/data", "rw"))
                .withReuse(true));
    }

    public PostgresFixture(PostgreSQLContainer<?> container) {
        this.container = container;
    }

    @Override
    public void overrideProperties(DynamicPropertyRegistry registry) {
        if (!container.isRunning()) {
            container.start();
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.flyway.url", container::getJdbcUrl);
        registry.add("spring.flyway.user", container::getUsername);
        registry.add("spring.flyway.password", container::getPassword);
    }

    public PostgreSQLContainer<?> container() {
        return container;
    }

    @Override
    public void stop() {
        // Ryuk reaps the container on JVM exit; explicit stop is a
        // no-op so the fixture is safe to share across tests.
    }
}
