package com.genealogy.platform.services.research.application.persistence;

import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
import com.genealogy.platform.services.research.domain.ResearchTask;
import com.genealogy.platform.services.research.domain.ResearchTaskStatus;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for the {@code research_tasks} aggregate
 * table. Mirrors {@code V2__research_aggregate.sql} (E6.1b).
 * Assignments are stored as JSONB on the parent row; the
 * {@code research_task_assignments} bridge table is reserved for
 * the E6.1d dedicated assignment projection.
 */
public class ResearchTaskRepository {

    private static final String COLUMNS =
            "id, tenant_id, title, description, subject_reference, subject_kind, "
                    + "status, blocked_reason, resolved_proof, linked_citation_ids, "
                    + "version, created_at, updated_at, resolved_at, "
                    + "created_by_actor_pseudo_id, correlation_id";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ResearchTaskRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ResearchTask task) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO research_service.research_tasks (" + COLUMNS + ") "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)");
            ps.setString(1, task.id().resourceId());
            ps.setString(2, task.id().tenantId());
            ps.setString(3, task.title());
            if (task.description() == null) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, task.description());
            }
            ps.setString(5, task.subjectReference());
            if (task.subjectKind() == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, task.subjectKind());
            }
            ps.setString(7, task.status().name());
            if (task.blockedReason() == null) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, task.blockedReason());
            }
            if (task.resolvedProof() == null) {
                ps.setNull(9, Types.VARCHAR);
            } else {
                ps.setString(9, task.resolvedProof());
            }
            ps.setString(10, "[]");
            ps.setLong(11, task.version());
            ps.setTimestamp(12, Timestamp.from(task.createdAt()));
            ps.setTimestamp(13, Timestamp.from(task.updatedAt()));
            if (task.resolvedAt() == null) {
                ps.setNull(14, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(14, Timestamp.from(task.resolvedAt()));
            }
            ps.setString(15, task.audit().actorPseudoId());
            ps.setString(16, task.audit().correlationId());
            return ps;
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(ResearchTask task) {
        int rows = jdbc.update(
                "UPDATE research_service.research_tasks SET "
                        + "title = ?, description = ?, subject_reference = ?, "
                        + "subject_kind = ?, status = ?, blocked_reason = ?, "
                        + "resolved_proof = ?, version = ?, updated_at = ?, "
                        + "resolved_at = ?, "
                        + "created_by_actor_pseudo_id = ?, correlation_id = ? "
                        + "WHERE id = ? AND tenant_id = ? AND version = ?",
                task.title(),
                task.description(),
                task.subjectReference(),
                task.subjectKind(),
                task.status().name(),
                task.blockedReason(),
                task.resolvedProof(),
                task.version(),
                Timestamp.from(task.updatedAt()),
                task.resolvedAt() == null ? null : Timestamp.from(task.resolvedAt()),
                task.audit().actorPseudoId(),
                task.audit().correlationId(),
                task.id().resourceId(),
                task.id().tenantId(),
                task.version() - 1);
        if (rows != 1) {
            throw new RepositorySupport.OptimisticConcurrencyException(
                    "researchTask " + task.id().resourceId()
                            + " was modified by another transaction");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ResearchTask> findById(String tenantId, String id) {
        try {
            ResearchTask task = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM research_service.research_tasks "
                            + "WHERE id = ? AND tenant_id = ?",
                    MAPPER,
                    id,
                    tenantId);
            return Optional.ofNullable(task);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static final RowMapper<ResearchTask> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static ResearchTask rehydrate(ResultSet rs) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String resourceId = rs.getString("id");
        TenantScopedId id = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.RESEARCH_TASK, resourceId);
        String title = rs.getString("title");
        String description = rs.getString("description");
        String subjectReference = rs.getString("subject_reference");
        String subjectKind = rs.getString("subject_kind");
        ResearchTaskStatus status = ResearchTaskStatus.valueOf(rs.getString("status"));
        String blockedReason = rs.getString("blocked_reason");
        String resolvedProof = rs.getString("resolved_proof");
        long version = rs.getLong("version");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        Instant resolvedAt = rs.getTimestamp("resolved_at") == null
                ? null : rs.getTimestamp("resolved_at").toInstant();
        String actorPseudoId = rs.getString("created_by_actor_pseudo_id");
        String correlationId = rs.getString("correlation_id");
        ResearchAuditAttributes audit = ResearchAuditAttributes.of(actorPseudoId, correlationId);
        return ResearchTask.rehydrate(id, title, description, subjectReference, subjectKind,
                status, new ArrayList<>(), new ArrayList<>(),
                blockedReason, resolvedProof, createdAt, updatedAt, resolvedAt, version, audit);
    }

    /** Visible for the controllers. */
    public static String etagFor(long version) {
        return RepositorySupport.etagFor(version);
    }

    /** Visible for the controllers. */
    public static long parseEtag(String ifMatch) {
        return RepositorySupport.parseEtag(ifMatch);
    }
}
