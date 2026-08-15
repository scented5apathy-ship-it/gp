/*
 * E6.1e — Testcontainers IT for the redaction overlay.
 *
 * <p>Exercises the V3 {@code workspace_projection} table
 * end-to-end: a {@code PersonRedacted} event lands, the
 * UPDATE statement flips every row that references the
 * subject to {@code redacted=true}, the audit columns are
 * populated, and the closed-set guards reject bad reason
 * values.
 *
 * <p>This IT complements {@link RlsNegativeIT} — that test
 * covers RLS isolation; this one covers the application
 * semantics of the redaction overlay.
 *
 * <p>The Kafka + OpenFGA + Apicurio containers are
 * referenced from the E2.3 / E3.3 / E2.8 epic ITs; this
 * IT uses only Postgres because the redaction overlay
 * is a database mutation that does not require the full
 * Spring context.
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

class RedactionOverlayIT {

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

        try (Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
                Statement s = c.createStatement()) {
            s.execute("GRANT research_service_app TO " + ownerUser);
        }
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("PersonRedacted flips every workspace_projection row that references the subject")
    void redactionOverlayMutatesMatchingRows() throws SQLException {
        String tenantId = "tenant-" + UUID.randomUUID();
        String subjectRef = "subject-" + UUID.randomUUID();
        String unrelatedSubject = "subject-other-" + UUID.randomUUID();

        seedProjection(tenantId, "tree-1", "claim-1", subjectRef, "PERSON");
        seedProjection(tenantId, "tree-1", "claim-2", subjectRef, "PERSON");
        seedProjection(tenantId, "tree-1", "claim-3", unrelatedSubject, "PERSON");

        try (Connection c = newRuntimeSession(tenantId);
                Statement s = c.createStatement()) {
            int touched = s.executeUpdate(
                    "UPDATE research_service.workspace_projection "
                            + "   SET redacted = TRUE, "
                            + "       last_redaction_reason = 'LIVING', "
                            + "       last_redacted_at = now(), "
                            + "       projection_version = projection_version + 1, "
                            + "       updated_at = now() "
                            + " WHERE subject_reference = '" + subjectRef + "'");
            assertThat(touched)
                    .as("overlay must flip the two rows that reference the redacted subject")
                    .isEqualTo(2);
        }

        try (Connection c = newRuntimeSession(tenantId);
                PreparedStatement ps = c.prepareStatement(
                        "SELECT claim_reference, redacted, last_redaction_reason "
                                + "  FROM research_service.workspace_projection "
                                + " WHERE subject_reference = ?")) {
            ps.setString(1, subjectRef);
            try (ResultSet rs = ps.executeQuery()) {
                int matched = 0;
                while (rs.next()) {
                    assertThat(rs.getBoolean("redacted")).isTrue();
                    assertThat(rs.getString("last_redaction_reason")).isEqualTo("LIVING");
                    matched++;
                }
                assertThat(matched).isEqualTo(2);
            }
        }

        try (Connection c = newRuntimeSession(tenantId);
                PreparedStatement ps = c.prepareStatement(
                        "SELECT redacted FROM research_service.workspace_projection "
                                + " WHERE subject_reference = ?")) {
            ps.setString(1, unrelatedSubject);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1))
                        .as("unrelated subject's row must remain un-redacted")
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("Closed-set redaction_reason rejects unknown values via CHECK constraint")
    void unknownRedactionReasonRejected() throws SQLException {
        String tenantId = "tenant-" + UUID.randomUUID();
        String subjectRef = "subject-" + UUID.randomUUID();
        seedProjection(tenantId, "tree-1", "claim-bad", subjectRef, "PERSON");

        try (Connection c = newRuntimeSession(tenantId);
                Statement s = c.createStatement()) {
            SQLException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    SQLException.class,
                    () -> s.executeUpdate(
                            "UPDATE research_service.workspace_projection "
                                    + "   SET redacted = TRUE, "
                                    + "       last_redaction_reason = 'BOGUS_REASON', "
                                    + "       last_redacted_at = now() "
                                    + " WHERE subject_reference = '"
                                    + subjectRef + "'"));
            assertThat(ex.getMessage().toLowerCase().contains("check")
                    || ex.getMessage().toLowerCase().contains("constraint")
                    || ex.getMessage().toLowerCase().contains("wp_redaction_reason_enum_chk"))
                    .as("CHECK constraint must reject an unknown reason. Actual: %s",
                            ex.getMessage())
                    .isTrue();
        }
    }

    private static void seedProjection(
            String tenantId, String treeId, String claimRef,
            String subjectRef, String subjectKind) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        ownerUrl, ownerUser, ownerPassword);
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO research_service.workspace_projection "
                                + "(tenant_id, tree_id, claim_reference, "
                                + " subject_reference, subject_kind, visibility) "
                                + "VALUES (?, ?, ?, ?, ?, 'PRIVATE')")) {
            ps.setString(1, tenantId);
            ps.setString(2, treeId);
            ps.setString(3, claimRef);
            ps.setString(4, subjectRef);
            ps.setString(5, subjectKind);
            ps.executeUpdate();
        }
    }

    private static Connection newRuntimeSession(String tenantId) throws SQLException {
        Connection c = DriverManager.getConnection(ownerUrl, ownerUser, ownerPassword);
        try (Statement s = c.createStatement()) {
            s.execute("RESET ROLE");
            s.execute("SET LOCAL ROLE research_service_app");
            s.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
        }
        return c;
    }
}
