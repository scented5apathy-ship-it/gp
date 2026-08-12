package com.genealogy.platform.services.research.workspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JdbcTemplate implementation of the workspace projection
 * repository. The SQL is hand-written (no jOOQ codegen for the
 * E6.1d slice); the column list mirrors
 * {@code V3__outbox_and_workspace.sql} byte-for-byte.
 */
@Component
public class ResearchJdbcWorkspaceProjectionRepository
        implements ResearchWorkspaceProjectionRepository {

    private final JdbcTemplate jdbc;

    public ResearchJdbcWorkspaceProjectionRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<ResearchWorkspaceProjection> find(
            String tenantId, String treeId, String claimReference) {
        String sql = "SELECT tenant_id, tree_id, claim_reference, subject_reference,"
                + " subject_kind, visibility, redacted, last_redaction_reason,"
                + " last_redacted_at, claim_verified_at, projection_version,"
                + " created_at, updated_at"
                + " FROM research_service.workspace_projection"
                + " WHERE tenant_id = ? AND tree_id = ? AND claim_reference = ?";
        List<ResearchWorkspaceProjection> rows = jdbc.query(sql, (rs, rowNum) -> toRow(rs),
                tenantId, treeId, claimReference);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void upsert(ResearchWorkspaceProjection row) {
        jdbc.update(
                "INSERT INTO research_service.workspace_projection ("
                        + " tenant_id, tree_id, claim_reference, subject_reference,"
                        + " subject_kind, visibility, redacted, last_redaction_reason,"
                        + " last_redacted_at, claim_verified_at, projection_version,"
                        + " created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (tenant_id, tree_id, claim_reference) DO UPDATE"
                        + " SET subject_reference = EXCLUDED.subject_reference,"
                        + " subject_kind = EXCLUDED.subject_kind,"
                        + " visibility = EXCLUDED.visibility,"
                        + " redacted = EXCLUDED.redacted,"
                        + " last_redaction_reason = EXCLUDED.last_redaction_reason,"
                        + " last_redacted_at = EXCLUDED.last_redacted_at,"
                        + " claim_verified_at = EXCLUDED.claim_verified_at,"
                        + " projection_version = EXCLUDED.projection_version,"
                        + " updated_at = EXCLUDED.updated_at",
                row.tenantId(),
                row.treeId(),
                row.claimReference(),
                row.subjectReference(),
                row.subjectKind(),
                row.visibility().name(),
                row.redacted(),
                row.lastRedactionReason() == null ? null : row.lastRedactionReason().name(),
                row.lastRedactedAt() == null ? null : java.sql.Timestamp.from(row.lastRedactedAt()),
                row.claimVerifiedAt() == null ? null : java.sql.Timestamp.from(row.claimVerifiedAt()),
                row.projectionVersion(),
                java.sql.Timestamp.from(row.createdAt()),
                java.sql.Timestamp.from(row.updatedAt()));
    }

    @Override
    public List<ResearchWorkspaceProjection> findBySubject(
            String tenantId, String subjectReference) {
        String sql = "SELECT tenant_id, tree_id, claim_reference, subject_reference,"
                + " subject_kind, visibility, redacted, last_redaction_reason,"
                + " last_redacted_at, claim_verified_at, projection_version,"
                + " created_at, updated_at"
                + " FROM research_service.workspace_projection"
                + " WHERE tenant_id = ? AND subject_reference = ?";
        return new ArrayList<>(jdbc.query(sql, (rs, rowNum) -> toRow(rs),
                tenantId, subjectReference));
    }

    @Override
    public List<ResearchWorkspaceProjection> findByTree(
            String tenantId, String treeId) {
        String sql = "SELECT tenant_id, tree_id, claim_reference, subject_reference,"
                + " subject_kind, visibility, redacted, last_redaction_reason,"
                + " last_redacted_at, claim_verified_at, projection_version,"
                + " created_at, updated_at"
                + " FROM research_service.workspace_projection"
                + " WHERE tenant_id = ? AND tree_id = ?";
        return new ArrayList<>(jdbc.query(sql, (rs, rowNum) -> toRow(rs),
                tenantId, treeId));
    }

    @Override
    public int applyRedactionOverlay(
            String tenantId,
            String subjectReference,
            ResearchWorkspaceProjection.RedactionReason reason,
            Instant at) {
        return jdbc.update(
                "UPDATE research_service.workspace_projection"
                        + " SET redacted = TRUE,"
                        + " last_redaction_reason = ?,"
                        + " last_redacted_at = ?,"
                        + " projection_version = projection_version + 1,"
                        + " updated_at = ?"
                        + " WHERE tenant_id = ? AND subject_reference = ?",
                reason.name(),
                java.sql.Timestamp.from(at),
                java.sql.Timestamp.from(at),
                tenantId,
                subjectReference);
    }

    private static ResearchWorkspaceProjection toRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Instant lastRedactedAt = rs.getTimestamp("last_redacted_at") == null
                ? null : rs.getTimestamp("last_redacted_at").toInstant();
        Instant claimVerifiedAt = rs.getTimestamp("claim_verified_at") == null
                ? null : rs.getTimestamp("claim_verified_at").toInstant();
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        String reasonRaw = rs.getString("last_redaction_reason");
        ResearchWorkspaceProjection.RedactionReason reason = reasonRaw == null
                ? null : ResearchWorkspaceProjection.RedactionReason.valueOf(reasonRaw);
        return new ResearchWorkspaceProjection(
                rs.getString("tenant_id"),
                rs.getString("tree_id"),
                rs.getString("claim_reference"),
                rs.getString("subject_reference"),
                rs.getString("subject_kind"),
                ResearchWorkspaceProjection.Visibility.valueOf(rs.getString("visibility")),
                rs.getBoolean("redacted"),
                reason,
                lastRedactedAt,
                claimVerifiedAt,
                rs.getLong("projection_version"),
                createdAt,
                updatedAt);
    }

    /**
     * In-memory fake for unit tests. Mirrors the upsert
     * semantics; the {@link #applyRedactionOverlay} method
     * tracks every row that flipped so a test can assert.
     */
    public static final class InMemory implements ResearchWorkspaceProjectionRepository {
        private final Map<String, ResearchWorkspaceProjection> byKey = new ConcurrentHashMap<>();
        private final List<RedactionEvent> redactions = new ArrayList<>();

        public InMemory() {
        }

        public InMemory(List<ResearchWorkspaceProjection> seed) {
            for (ResearchWorkspaceProjection r : seed) {
                byKey.put(key(r.tenantId(), r.treeId(), r.claimReference()), r);
            }
        }

        @Override
        public Optional<ResearchWorkspaceProjection> find(
                String tenantId, String treeId, String claimReference) {
            return Optional.ofNullable(byKey.get(key(tenantId, treeId, claimReference)));
        }

        @Override
        public void upsert(ResearchWorkspaceProjection row) {
            byKey.put(key(row.tenantId(), row.treeId(), row.claimReference()), row);
        }

        @Override
        public List<ResearchWorkspaceProjection> findBySubject(
                String tenantId, String subjectReference) {
            List<ResearchWorkspaceProjection> out = new ArrayList<>();
            for (ResearchWorkspaceProjection r : byKey.values()) {
                if (r.tenantId().equals(tenantId) && r.subjectReference().equals(subjectReference)) {
                    out.add(r);
                }
            }
            return out;
        }

        @Override
        public List<ResearchWorkspaceProjection> findByTree(
                String tenantId, String treeId) {
            List<ResearchWorkspaceProjection> out = new ArrayList<>();
            for (ResearchWorkspaceProjection r : byKey.values()) {
                if (r.tenantId().equals(tenantId) && r.treeId().equals(treeId)) {
                    out.add(r);
                }
            }
            return out;
        }

        @Override
        public int applyRedactionOverlay(
                String tenantId,
                String subjectReference,
                ResearchWorkspaceProjection.RedactionReason reason,
                Instant at) {
            int touched = 0;
            for (Map.Entry<String, ResearchWorkspaceProjection> entry : byKey.entrySet()) {
                ResearchWorkspaceProjection r = entry.getValue();
                if (r.tenantId().equals(tenantId) && r.subjectReference().equals(subjectReference)) {
                    ResearchWorkspaceProjection next = r.withRedactionOverlay(reason, at);
                    byKey.put(entry.getKey(), next);
                    redactions.add(new RedactionEvent(tenantId, subjectReference, reason, at));
                    touched += 1;
                }
            }
            return touched;
        }

        public List<RedactionEvent> redactionEvents() {
            return List.copyOf(redactions);
        }

        private static String key(String tenantId, String treeId, String claimReference) {
            return tenantId + "|" + treeId + "|" + claimReference;
        }

        public record RedactionEvent(
                String tenantId,
                String subjectReference,
                ResearchWorkspaceProjection.RedactionReason reason,
                Instant at) {
        }
    }
}
