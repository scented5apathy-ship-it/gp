/*
 * Integration test for the E3.2a deliverables.
 *
 * <p>The test exercises the PostgreSQL Row-Level Security policies and
 * the runtime / owner role split defined in V2__tenant_aggregate.sql.
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
 * The runtime role {@code tenant_service_app} is {@code NOLOGIN} in
 * production so the role can never be reached by a direct login. The
 * IT mirrors the production pattern: the Testcontainers owner user is
 * granted membership in {@code tenant_service_app}, then each
 * assertion issues {@code SET LOCAL ROLE tenant_service_app} so the
 * rest of the transaction runs as the runtime principal. This proves
 * the {@code FORCE} RLS + {@code tenant_isolation} policy without
 * relaxing the production posture.
 */
package com.genealogy.platform.services.tenant;

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
                    .withDatabaseName("tenant_service")
                    .withUsername("tenant_service_owner")
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
                .schemas("tenant_service")
                .defaultSchema("tenant_service")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Allow the owner to switch into the runtime role for the
        // assertions. The role itself stays NOLOGIN; the switch is a
        // privileged operation that mirrors how the application
        // connection pool will run: a low-privileged login user issues
        // SET LOCAL ROLE tenant_service_app per transaction.
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement()) {
            s.execute("GRANT tenant_service_app TO " + ownerUser);
        }

        seedTenant("tenant-aaaa-1111", "smith", "Smith Family");
        seedTenant("tenant-bbbb-2222", "jones", "Jones Family");
        seedMembership("tenant-aaaa-1111", "user-alice-aaaa", "OWNER");
        seedMembership("tenant-bbbb-2222", "user-bob-bbbbb", "OWNER");
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("V1 + V2 migrations apply cleanly and create the five tables")
    void migrationsApply() throws SQLException {
        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = 'tenant_service' "
                                + "ORDER BY table_name")) {
            java.util.Set<String> tables = new java.util.HashSet<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            assertThat(tables)
                    .as("V2 must create the four aggregate tables + outbox")
                    .contains("tenants", "memberships", "invitations",
                            "entitlements", "outbox_events");
        }
    }

    @Test
    @DisplayName("Row-Level Security is ENABLED + FORCE on every tenant-scoped table")
    void rlsEnabledAndForced() throws SQLException {
        String sql = ""
                + "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity "
                + "FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'tenant_service' "
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
            assertThat(rows).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    @DisplayName("Runtime role tenant_service_app sees only its tenant's rows")
    void runtimeRoleScopedToTenant() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-aaaa-1111");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, slug FROM tenant_service.tenants ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
            assertThat(ids)
                    .as("tenant-aaaa-1111 session must see only tenant-aaaa-1111")
                    .containsExactly("tenant-aaaa-1111");
        }
    }

    @Test
    @DisplayName("Cross-tenant SELECT returns zero rows for tenant_service_app")
    void crossTenantSelectReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM tenant_service.tenants "
                                + "WHERE id = ?")) {
            ps.setString(1, "tenant-aaaa-1111");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1))
                        .as("tenant-bbbb session must NOT see tenant-aaaa rows, "
                                + "even though the app role has SELECT privilege "
                                + "on the table. FORCE RLS + tenant_isolation "
                                + "policy must block the read.")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Unset app.tenant_id returns zero rows (defence-in-depth)")
    void unsetTenantIdReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession(null);
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM tenant_service.tenants");
                ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1))
                    .as("Without app.tenant_id set, current_tenant_id() returns "
                            + "NULL and the policy matches no rows.")
                    .isZero();
        }
    }

    @Test
    @DisplayName("Cross-tenant membership SELECT returns zero rows")
    void crossTenantMembershipSelectReturnsZeroRows() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-bbbb-2222");
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM tenant_service.memberships");
                ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1))
                    .as("tenant-bbbb session must NOT see tenant-aaaa memberships")
                    .isZero();
        }
    }

    @Test
    @DisplayName("Cross-tenant INSERT is rejected by WITH CHECK")
    void crossTenantInsertRejected() throws SQLException {
        try (Connection c = newRuntimeSession("tenant-aaaa-1111");
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tenant_service.memberships "
                                + "(id, tenant_id, user_id, role, status, invited_at) "
                                + "VALUES (?, ?, ?, ?, ?, now())")) {
            ps.setString(1, "mship-" + UUID.randomUUID());
            ps.setString(2, "tenant-bbbb-2222"); // wrong tenant
            ps.setString(3, "user-eve-bbbbbbb");
            ps.setString(4, "MEMBER");
            ps.setString(5, "INVITED");
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
                            "CREATE TABLE tenant_service.should_fail (id INT)"));
            assertThat(ex.getSQLState() == null || ex.getSQLState().startsWith("42"))
                    .as("tenant_service_app must not have CREATE privilege. "
                            + "Actual SQLState=%s, message=%s",
                            ex.getSQLState(), ex.getMessage())
                    .isTrue();
        }
    }

    /**
     * Open a connection as the Testcontainers owner, switch into the
     * {@code tenant_service_app} runtime role, and (optionally) pin
     * {@code app.tenant_id} so the RLS policy resolves a tenant.
     *
     * <p>Production code path: a low-privileged login user holds
     * membership in {@code tenant_service_app}; the connection pool
     * runs {@code SET LOCAL ROLE tenant_service_app} and
     * {@code SET LOCAL app.tenant_id = ?} per transaction.
     */
    private static Connection newRuntimeSession(String tenantId) throws SQLException {
        Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
        try (Statement s = c.createStatement()) {
            // RESET ROLE ensures a clean switch even when the
            // connection came from a pool that previously ran as
            // another role.
            s.execute("RESET ROLE");
            s.execute("SET LOCAL ROLE tenant_service_app");
            if (tenantId != null) {
                s.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
            } else {
                s.execute("RESET app.tenant_id");
            }
        }
        return c;
    }

    private static void seedTenant(String id, String slug, String displayName)
            throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tenant_service.tenants "
                                + "(id, slug, display_name, plan, status, tenant_id) "
                                + "VALUES (?, ?, ?, 'FREE', 'ACTIVE', ?)")) {
            ps.setString(1, id);
            ps.setString(2, slug);
            ps.setString(3, displayName);
            ps.setString(4, id);
            ps.executeUpdate();
        }
    }

    private static void seedMembership(String tenantId, String userId, String role)
            throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tenant_service.memberships "
                                + "(id, tenant_id, user_id, role, status, invited_at) "
                                + "VALUES (?, ?, ?, ?, 'INVITED', now())")) {
            ps.setString(1, "mship-" + UUID.randomUUID());
            ps.setString(2, tenantId);
            ps.setString(3, userId);
            ps.setString(4, role);
            ps.executeUpdate();
        }
    }
}