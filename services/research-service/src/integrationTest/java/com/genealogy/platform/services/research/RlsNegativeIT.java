/*
 * Integration test for the E6.1b deliverables.
 *
 * <p>The test exercises the PostgreSQL Row-Level Security policies and
 * the runtime / owner role split defined in V2__research_aggregate.sql.
 * See {@link RlsNegativeIT} Javadoc for the test strategy.
 *
 * <h2>Why a fresh Testcontainers container</h2>
 *
 * The shared {@code com.genealogy.platform.testing.PostgresFixture}
 * connects as a superuser, which would bypass {@code FORCE ROW LEVEL
 * SECURITY}. To exercise the runtime role we need a container where we
 * control the principal we connect as.
 *
 * <h2>Why {@code SET LOCAL ROLE}</h2>
 *
 * The runtime role {@code research_service_app} is {@code NOLOGIN} in
 * production so the role can never be reached by a direct login. The
 * IT mirrors the production pattern: the Testcontainers owner user is
 * granted membership in {@code research_service_app}, then each
 * assertion issues {@code SET LOCAL ROLE research_service_app} so the
 * rest of the transaction runs as the runtime principal. This proves
 * the {@code FORCE} RLS + {@code tenant_isolation} policy without
 * relaxing the production posture.
 */
package com.genealogy.platform.services.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class RlsNegativeIT {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("postgres:16.4-alpine");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("research_service")
                    .withUsername("research_service_owner")
                    .withPassword("owner-secret")
                    .withReuse(true);

    private static String ownerUrl;
    private static String ownerUser;
    private static String ownerPassword;

    @BeforeAll
    static void startContainer() throws SQLException {
        POSTGRES.start();
        ownerUrl = POSTGRES.getJdbcUrl();
        ownerUser = POSTGRES.getUsername();
        ownerPassword = POSTGRES.getPassword();

        Flyway flyway = Flyway.configure()
                .dataSource(ownerUrl, ownerUser, ownerPassword)
                .schemas("research_service")
                .defaultSchema("research_service")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Allow the owner to switch into the runtime role for the
        // assertions. The role itself stays NOLOGIN; the switch is a
        // privileged operation that mirrors how the application
        // connection pool will run: a low-privileged login user issues
        // SET LOCAL ROLE research_service_app per transaction.
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement()) {
            s.execute("GRANT research_service_app TO " + ownerUser);
        }

        seedRepository("repo-aaaa-1111", "tenant-aaaa-1111");
        seedRepository("repo-bbbb-2222", "tenant-bbbb-2222");
        seedSource("src-aaaa-1111", "tenant-aaaa-1111", "repo-aaaa-1111");
        seedSource("src-bbbb-2222", "tenant-bbbb-2222", "repo-bbbb-2222");
        seedCitation("cit-aaaa-1111", "tenant-aaaa-1111", "src-aaaa-1111");
        seedResearchTask("tsk-aaaa-1111", "tenant-aaaa-1111");
        seedHypothesis("hyp-aaaa-1111", "tenant-aaaa-1111");
        seedConflict("cfl-aaaa-1111", "tenant-aaaa-1111");
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("V1 + V2 migrations apply cleanly and create the six aggregate tables")
    void migrationsApply() throws SQLException {
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = 'research_service' "
                                + "AND table_name IN ('repositories', 'sources', "
                                + "'citations', 'research_tasks', 'hypotheses', "
                                + "'conflicts') "
                                + "ORDER BY table_name")) {
            java.util.List<String> tables = new java.util.ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            assertThat(tables)
                    .as("V2 must create the six research aggregate tables")
                    .containsExactly("citations", "conflicts", "hypotheses",
                            "repositories", "research_tasks", "sources");
        }
    }

    @Test
    @DisplayName("V4 migration creates the consumer_inbox table with RLS FORCE")
    void consumerInboxAppliedWithRlsForce() throws SQLException {
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "SELECT relrowsecurity, relforcerowsecurity "
                            + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname = 'research_service' "
                            + "  AND c.relname = 'consumer_inbox'")) {
                assertThat(rs.next()).as("consumer_inbox table must exist").isTrue();
                assertThat(rs.getBoolean(1))
                        .as("RLS must be enabled on consumer_inbox").isTrue();
                assertThat(rs.getBoolean(2))
                        .as("FORCE RLS must be on for consumer_inbox").isTrue();
            }
        }
    }

    @Test
    @DisplayName("V2 also creates the five bridge tables")
    void bridgeTablesApply() throws SQLException {
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = 'research_service' "
                                + "AND table_name IN ("
                                + "'research_task_assignments', "
                                + "'hypothesis_corroborating_citations', "
                                + "'hypothesis_refuting_citations', "
                                + "'conflict_participants', "
                                + "'conflict_participant_supporting_citations') "
                                + "ORDER BY table_name")) {
            java.util.List<String> tables = new java.util.ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            assertThat(tables)
                    .as("V2 must create the five bridge tables")
                    .containsExactly("conflict_participant_supporting_citations",
                            "conflict_participants",
                            "hypothesis_corroborating_citations",
                            "hypothesis_refuting_citations",
                            "research_task_assignments");
        }
    }

    @Test
    @DisplayName("Row-Level Security is ENABLED + FORCE on every research-scoped table")
    void rlsEnabledAndForced() throws SQLException {
        String sql = ""
                + "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity "
                + "FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'research_service' "
                + "  AND c.relkind = 'r' "
                + "ORDER BY c.relname";
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                String name = rs.getString(1);
                boolean enabled = rs.getBoolean(2);
                boolean forced = rs.getBoolean(3);
                assertThat(enabled)
                        .as("RLS must be enabled on %s", name)
                        .isTrue();
                assertThat(forced)
                        .as("FORCE RLS must be on for %s so the owner role "
                                + "cannot bypass the policy", name)
                        .isTrue();
            }
            assertThat(rows)
                    .as("V2 must create exactly 11 tables (6 aggregate + 5 bridge)")
                    .isEqualTo(11);
        }
    }

    @Test
    @DisplayName("Audit columns exist on every aggregate table")
    void auditColumnsExist() throws SQLException {
        String sql = ""
                + "SELECT table_name, column_name "
                + "FROM information_schema.columns "
                + "WHERE table_schema = 'research_service' "
                + "  AND column_name IN ('created_at', 'updated_at', "
                + "                        'archived_at', 'version', "
                + "                        'created_by_actor_pseudo_id', "
                + "                        'correlation_id') "
                + "ORDER BY table_name, column_name";
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            java.util.Map<String, java.util.Set<String>> byTable = new java.util.HashMap<>();
            while (rs.next()) {
                byTable.computeIfAbsent(rs.getString(1), k -> new java.util.HashSet<>())
                        .add(rs.getString(2));
            }
            // The six aggregate tables MUST carry every audit column.
            for (String table : new String[]{"repositories", "sources", "citations",
                    "research_tasks", "hypotheses", "conflicts"}) {
                java.util.Set<String> cols = byTable.getOrDefault(table,
                        java.util.Set.of());
                assertThat(cols)
                        .as("aggregate %s must carry every audit column", table)
                        .contains("created_at", "updated_at", "archived_at",
                                "version", "created_by_actor_pseudo_id",
                                "correlation_id");
            }
        }
    }

    @Test
    @DisplayName("Runtime role research_service_app sees only its tenant's rows")
    void runtimeRoleScopedToTenant() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-aaaa-1111");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM research_service.repositories "
                                + "ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            assertThat(ids)
                    .as("tenant-aaaa-1111 session must see only "
                            + "tenant-aaaa-1111 rows")
                    .containsExactly("repo-aaaa-1111");
        }
    }

    @Test
    @DisplayName("Cross-tenant SELECT returns zero rows for research_service_app")
    void crossTenantSelectReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM research_service.sources "
                                + "WHERE id = ?")) {
            ps.setString(1, "src-aaaa-1111");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1))
                        .as("tenant-bbbb session must NOT see tenant-aaaa "
                                + "rows, even though the app role has SELECT "
                                + "privilege on the table. FORCE RLS + "
                                + "tenant_isolation policy must block the "
                                + "read.")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Unset app.tenant_id returns zero rows (defence-in-depth)")
    void unsetTenantIdReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession(null);
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM research_service.repositories");
                ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1))
                    .as("Without app.tenant_id set, current_tenant_id() "
                            + "returns NULL and the policy matches no rows.")
                    .isZero();
        }
    }

    @Test
    @DisplayName("Cross-tenant INSERT is rejected by WITH CHECK")
    void crossTenantInsertRejected() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-aaaa-1111");
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.sources "
                                + "(id, tenant_id, repository_id, title, "
                                + " source_kind, locator_raw, "
                                + " created_by_actor_pseudo_id, "
                                + " correlation_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, "src-cross-" + UUID.randomUUID());
            ps.setString(2, "tenant-bbbb-2222"); // wrong tenant
            ps.setString(3, "repo-bbbb-2222");
            ps.setString(4, "Cross-tenant probe");
            ps.setString(5, "OTHER");
            ps.setString(6, "p.1");
            ps.setString(7, "actor-probe");
            ps.setString(8, "corr-probe");
            SQLException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    SQLException.class, ps::executeUpdate);
            assertThat(ex.getSQLState() == null || ex.getSQLState().startsWith("42")
                    || ex.getMessage().toLowerCase().contains("row-level")
                    || ex.getMessage().toLowerCase().contains("policy"))
                    .as("WITH CHECK must reject a cross-tenant INSERT. "
                            + "Actual SQLState=%s, message=%s",
                            ex.getSQLState(), ex.getMessage())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Runtime role has no DDL privilege")
    void runtimeRoleHasNoDdlPrivilege() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-aaaa-1111");
                Statement s = c.createStatement()) {
            SQLException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    SQLException.class,
                    () -> s.executeUpdate(
                            "CREATE TABLE research_service.should_fail "
                                    + "(id INT)"));
            assertThat(ex.getSQLState() == null || ex.getSQLState().startsWith("42"))
                    .as("research_service_app must not have CREATE privilege. "
                            + "Actual SQLState=%s, message=%s",
                            ex.getSQLState(), ex.getMessage())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Cross-tenant SELECT on the hypothesis table returns zero rows")
    void crossTenantHypothesisSelectReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM research_service.hypotheses")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1))
                        .as("tenant-bbbb session must NOT see tenant-aaaa "
                                + "hypotheses")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Cross-tenant SELECT on the conflict table returns zero rows")
    void crossTenantConflictSelectReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM research_service.conflicts")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1))
                        .as("tenant-bbbb session must NOT see tenant-aaaa "
                                + "conflicts")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("E6.1e: workspace_projection rows are tenant-isolated for SELECT")
    void workspaceProjectionCrossTenantBlocked() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM research_service.workspace_projection")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1))
                        .as("tenant-bbbb session must NOT see tenant-aaaa "
                                + "workspace projections")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("E6.1e: consumer_inbox enforces tenant isolation on SELECT")
    void consumerInboxCrossTenantBlocked() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM research_service.consumer_inbox")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1))
                        .as("tenant-bbbb session must NOT see tenant-aaaa "
                                + "consumer inbox rows")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("E6.1e: consumer_inbox WITH CHECK rejects cross-tenant INSERT")
    void consumerInboxCrossTenantInsertRejected() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-aaaa-1111");
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.consumer_inbox "
                                + "(tenant_id, source_topic, event_id, event_type, "
                                + " payload_hash) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, "tenant-bbbb-2222"); // wrong tenant
            ps.setString(2, "genealogy.tree-visibility.v1.v1");
            ps.setString(3, "evt-cross-" + UUID.randomUUID());
            ps.setString(4, "gp.genealogy.v1.TreeVisibilityChanged");
            ps.setString(5, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            SQLException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    SQLException.class, ps::executeUpdate);
            assertThat(ex.getSQLState() == null || ex.getSQLState().startsWith("42")
                    || ex.getMessage().toLowerCase().contains("row-level")
                    || ex.getMessage().toLowerCase().contains("policy"))
                    .as("WITH CHECK must reject a cross-tenant INSERT into "
                            + "consumer_inbox. Actual SQLState=%s, message=%s",
                            ex.getSQLState(), ex.getMessage())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("E6.1e: outbox row visibility remains tenant-scoped")
    void outboxCrossTenantSelectReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM research_service.outbox")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1))
                        .as("tenant-bbbb session must NOT see tenant-aaaa "
                                + "outbox rows")
                        .isZero();
            }
        }
    }

    /**
     * Open a connection as the Testcontainers owner, switch into the
     * {@code research_service_app} runtime role, and (optionally) pin
     * {@code app.tenant_id} so the RLS policy resolves a tenant.
     *
     * <p>Production code path: a low-privileged login user holds
     * membership in {@code research_service_app}; the connection pool
     * runs {@code SET LOCAL ROLE research_service_app} and
     * {@code SET LOCAL app.tenant_id = ?} per transaction.
     */
    private static Connection newRuntimeSession(String tenantId) throws SQLException {
        Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
        try (Statement s = c.createStatement()) {
            // RESET ROLE ensures a clean switch even when the
            // connection came from a pool that previously ran as
            // another role.
            s.execute("RESET ROLE");
            s.execute("SET LOCAL ROLE research_service_app");
            if (tenantId != null) {
                s.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
            } else {
                s.execute("RESET app.tenant_id");
            }
        }
        return c;
    }

    private static void seedRepository(String id, String tenantId) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.repositories "
                                + "(id, tenant_id, name, kind, "
                                + " created_by_actor_pseudo_id, "
                                + " correlation_id) "
                                + "VALUES (?, ?, ?, 'ARCHIVE', ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, "Repository " + id);
            ps.setString(4, "actor-seed");
            ps.setString(5, "corr-seed");
            ps.executeUpdate();
        }
    }

    private static void seedSource(String id, String tenantId, String repositoryId)
            throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.sources "
                                + "(id, tenant_id, repository_id, title, "
                                + " source_kind, locator_raw, "
                                + " created_by_actor_pseudo_id, "
                                + " correlation_id) "
                                + "VALUES (?, ?, ?, ?, 'OTHER', 'p.1', ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, repositoryId);
            ps.setString(4, "Source " + id);
            ps.setString(5, "actor-seed");
            ps.setString(6, "corr-seed");
            ps.executeUpdate();
        }
    }

    private static void seedCitation(String id, String tenantId, String sourceId)
            throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.citations "
                                + "(id, tenant_id, source_id, claim_reference, "
                                + " locator_raw, quality, disposition, "
                                + " certainty, "
                                + " created_by_actor_pseudo_id, "
                                + " correlation_id) "
                                + "VALUES (?, ?, ?, ?, 'p.1', 'ORIGINAL', "
                                + "        'SUPPORTS', 'ASSERTED', ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, sourceId);
            ps.setString(4, "claim-" + id);
            ps.setString(5, "actor-seed");
            ps.setString(6, "corr-seed");
            ps.executeUpdate();
        }
    }

    private static void seedResearchTask(String id, String tenantId)
            throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.research_tasks "
                                + "(id, tenant_id, title, subject_reference, "
                                + " created_by_actor_pseudo_id, "
                                + " correlation_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, "Task " + id);
            ps.setString(4, "subject-" + id);
            ps.setString(5, "actor-seed");
            ps.setString(6, "corr-seed");
            ps.executeUpdate();
        }
    }

    private static void seedHypothesis(String id, String tenantId)
            throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.hypotheses "
                                + "(id, tenant_id, statement, "
                                + " subject_reference, certainty, "
                                + " created_by_actor_pseudo_id, "
                                + " correlation_id) "
                                + "VALUES (?, ?, ?, ?, 'HYPOTHESIS', ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, "Hypothesis " + id);
            ps.setString(4, "subject-" + id);
            ps.setString(5, "actor-seed");
            ps.setString(6, "corr-seed");
            ps.executeUpdate();
        }
    }

    private static void seedConflict(String id, String tenantId) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.conflicts "
                                + "(id, tenant_id, summary, kind, "
                                + " created_by_actor_pseudo_id, "
                                + " correlation_id) "
                                + "VALUES (?, ?, ?, 'OTHER', ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, tenantId);
            ps.setString(3, "Conflict " + id);
            ps.setString(4, "actor-seed");
            ps.setString(5, "corr-seed");
            ps.executeUpdate();
        }
        // Add two participants so the invariant
        // CONFLICT_REQUIRE_MULTIPLE_PARTICIPANTS stays happy (the
        // application layer enforces ≥ 2 participants; we honour
        // that here by seeding two rows so the bridge-table test
        // can verify RLS does not block the in-tenant read).
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.conflict_participants "
                                + "(conflict_id, ordinal, tenant_id, "
                                + " reference) "
                                + "VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setShort(2, (short) 1);
            ps.setString(3, tenantId);
            ps.setString(4, "reference-1-" + id);
            ps.executeUpdate();
            ps.setShort(2, (short) 2);
            ps.setString(4, "reference-2-" + id);
            ps.executeUpdate();
        }
    }
}