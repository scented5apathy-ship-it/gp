/*
 * E6.1e — unit tests for the JdbcTemplate-backed outbox
 * repository contract. The contract is verified against a
 * fresh Testcontainers Postgres so the
 * `FORCE ROW LEVEL SECURITY` posture is the production one.
 */
package com.genealogy.platform.services.research.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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

class ResearchJdbcOutboxRepositoryIT {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("postgres:16.4-alpine");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("research_service")
                    .withUsername("research_service_owner")
                    .withPassword("owner-secret")
                    .withReuse(true);

    private static DriverManagerDataSource dataSource;
    private static ResearchJdbcOutboxRepository repository;

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
        try (var c = dataSource.getConnection();
                var s = c.createStatement()) {
            s.execute("CREATE USER research_service_app NOLOGIN");
        } catch (Exception e) {
            // role may already exist on a reused container
        }
        try (var c = dataSource.getConnection();
                var s = c.createStatement()) {
            s.execute("GRANT research_service_app TO " + POSTGRES.getUsername());
            s.execute("GRANT SELECT, INSERT, UPDATE, DELETE "
                    + "ON research_service.outbox TO research_service_app");
        }
        repository = new ResearchJdbcOutboxRepository(new JdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDown() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("claimPending returns only the tenant's PENDING rows that are not on lease")
    void claimPendingFiltersByTenantAndLease() throws Exception {
        String tenantA = "tenant-" + UUID.randomUUID();
        String tenantB = "tenant-" + UUID.randomUUID();
        Instant now = Instant.now();
        ResearchOutboxEventRecord a = newPending(tenantA, "agg-1");
        ResearchOutboxEventRecord b = newPending(tenantB, "agg-2");
        try (var c = dataSource.getConnection()) {
            bindTenant(c, tenantA);
            insert(a);
        }
        try (var c = dataSource.getConnection()) {
            bindTenant(c, tenantB);
            insert(b);
        }

        try (var c = dataSource.getConnection()) {
            bindTenant(c, tenantA);
            List<ResearchOutboxEventRecord> claimed = repository.claimPending(tenantA, 10, now);
            assertThat(claimed).extracting(ResearchOutboxEventRecord::eventId)
                    .containsExactly(a.eventId());
        }
    }

    @Test
    @DisplayName("save persists the next state and findById returns the updated row")
    void saveUpdatesState() throws Exception {
        String tenantId = "tenant-" + UUID.randomUUID();
        ResearchOutboxEventRecord row = newPending(tenantId, "agg-3");
        try (var c = dataSource.getConnection()) {
            bindTenant(c, tenantId);
            insert(row);
            repository.save(row.withAttempt(Instant.now(), "transient"));
            Optional<ResearchOutboxEventRecord> reloaded = repository.findById(row.eventId());
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().status()).isEqualTo(ResearchOutboxStatus.PENDING);
            assertThat(reloaded.get().attempts()).isEqualTo(1);
        }
    }

    private static void bindTenant(java.sql.Connection c, String tenantId) throws Exception {
        try (var s = c.createStatement()) {
            s.execute("RESET ROLE");
            s.execute("SET LOCAL ROLE research_service_app");
            s.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        }
    }

    private static void insert(ResearchOutboxEventRecord row) throws Exception {
        try (var c = dataSource.getConnection();
                var ps = c.prepareStatement(
                        "INSERT INTO research_service.outbox "
                                + "(event_id, tenant_id, aggregate_id, event_type, schema_id, "
                                + " payload, payload_byte_size, occurred_at, correlation_id, "
                                + " trace_id, partition_key, partition_key_class, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, 'PENDING')")) {
            ps.setString(1, row.eventId());
            ps.setString(2, row.tenantId());
            ps.setString(3, row.aggregateId());
            ps.setString(4, row.eventType());
            ps.setString(5, row.schemaId());
            ps.setString(6, row.payloadJson());
            ps.setInt(7, row.payloadByteSize());
            ps.setObject(8, java.sql.Timestamp.from(row.occurredAt()));
            ps.setString(9, row.correlationId());
            ps.setString(10, row.traceId());
            ps.setString(11, row.partitionKey());
            ps.setString(12, row.partitionKeyClass().name());
            ps.executeUpdate();
        }
    }

    private static ResearchOutboxEventRecord newPending(String tenantId, String aggregateId) {
        String eventId = "evt-" + UUID.randomUUID();
        String payload = "{\"x\":1}";
        return new ResearchOutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                "gp.research.v1.CitationCreated",
                "research/v1/citation-created.avsc",
                payload,
                payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                Instant.now(),
                "corr-" + UUID.randomUUID(),
                "trace-" + UUID.randomUUID(),
                tenantId + "|" + aggregateId,
                ResearchPartitionKeyClass.TENANT_PLUS_AGGREGATE,
                ResearchOutboxStatus.PENDING,
                0,
                null, null, null, null, null,
                null, null, null);
    }
}
