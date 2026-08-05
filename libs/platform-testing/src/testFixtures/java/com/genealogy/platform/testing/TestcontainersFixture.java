package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Shared Testcontainers fixture contract.
 *
 * <p>Subclasses start exactly the infrastructure they need
 * (PostgreSQL, Kafka, Keycloak, OpenFGA, Temporal, S3 LocalStack,
 * Valkey) and expose the dynamic properties through
 * {@link #overrideProperties(DynamicPropertyRegistry)}. The
 * fixture follows the E2.x platform baseline (single-node
 * containers, ephemeral ports, network aliases) so the test
 * configuration is reproducible across CI agents and developer
 * workstations.
 *
 * <p>Each fixture is intentionally opt-in — a service test that
 * only needs PostgreSQL does not pay the cost of starting Kafka.
 */
public interface TestcontainersFixture {

    /**
     * Start the fixture (idempotent) and bind the resulting
     * dynamic properties (URLs, ports, credentials) onto the
     * Spring environment.
     */
    void overrideProperties(DynamicPropertyRegistry registry);

    /** Stop the fixture. Safe to call multiple times. */
    void stop();
}
