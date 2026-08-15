/*
 * E6.1e — unit tests for the durable consumer inbox. The
 * repository is exercised against a Testcontainers Postgres
 * so the `FORCE ROW LEVEL SECURITY` posture is the production
 * one.
 */
package com.genealogy.platform.services.research.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ResearchJdbcConsumerInboxIT {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("postgres:16.4-alpine");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("research_service")
                    .withUsername("research_service_owner")
                    .withPassword("owner-secret")
                    .withReuse(true);

    private static DriverManagerDataSource dataSource;
    private static ResearchJdbcConsumerInboxRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("research_service")
                .defaultSchema("research_service")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement s = c.createStatement()) {
            s.execute("CREATE USER research_service_app NOLOGIN");
        }
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement s = c.createStatement()) {
            s.execute("GRANT research_service_app TO " + POSTGRES.getUsername());
            s.execute("GRANT SELECT, INSERT, UPDATE, DELETE "
                    + "ON research_service.consumer_inbox TO research_service_app");
        }
        repository = new ResearchJdbcConsumerInboxRepository(new JdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDown() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("tryClaim inserts the first row; the second delivery returns false")
    void tryClaimIsIdempotent() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String topic = "genealogy.tree-visibility.v1.v1";
        String eventId = "evt-" + UUID.randomUUID();
        Instant now = Instant.now();
        ResearchConsumerInboxRow row = new ResearchConsumerInboxRow(
                tenantId, topic, eventId, "gp.genealogy.v1.TreeVisibilityChanged",
                ResearchConsumerInboxService.sha256Hex("{\"x\":1}"), now,
                null, ResearchConsumerInboxRow.Outcome.IN_FLIGHT, null,
                "actor-test", "corr-test");
        try (Connection c = dataSource.getConnection()) {
            bindTenant(c, tenantId);
            assertThat(repository.tryClaim(row)).isTrue();
            assertThat(repository.tryClaim(row)).isFalse();
            Optional<ResearchConsumerInboxRow> found = repository.find(tenantId, topic, eventId);
            assertThat(found).isPresent();
            assertThat(found.get().outcome()).isEqualTo(ResearchConsumerInboxRow.Outcome.IN_FLIGHT);
        }
    }

    @Test
    @DisplayName("finalizeOutcome flips IN_FLIGHT to PROCESSED + sets processed_at")
    void finalizeOutcomePersists() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        String topic = "genealogy.person-redacted.v1.v1";
        String eventId = "evt-" + UUID.randomUUID();
        Instant now = Instant.now();
        ResearchConsumerInboxRow row = new ResearchConsumerInboxRow(
                tenantId, topic, eventId, "gp.genealogy.v1.PersonRedacted",
                ResearchConsumerInboxService.sha256Hex("{\"p\":\"x\"}"), now,
                null, ResearchConsumerInboxRow.Outcome.IN_FLIGHT, null,
                "actor-test", "corr-test");
        try (Connection c = dataSource.getConnection()) {
            bindTenant(c, tenantId);
            assertThat(repository.tryClaim(row)).isTrue();
            Instant processedAt = Instant.now();
            ResearchConsumerInboxRow processed = row.withOutcome(
                    ResearchConsumerInboxRow.Outcome.PROCESSED, processedAt, null);
            repository.finalizeOutcome(processed);
            ResearchConsumerInboxRow found = repository.find(tenantId, topic, eventId).orElseThrow();
            assertThat(found.outcome()).isEqualTo(ResearchConsumerInboxRow.Outcome.PROCESSED);
            assertThat(found.processedAt()).isNotNull();
        }
    }

    private static void bindTenant(Connection c, String tenantId) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("RESET ROLE");
            s.execute("SET LOCAL ROLE research_service_app");
            s.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        }
    }
}
