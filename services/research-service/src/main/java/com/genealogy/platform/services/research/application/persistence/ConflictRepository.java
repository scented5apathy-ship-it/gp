package com.genealogy.platform.services.research.application.persistence;

import com.genealogy.platform.services.research.domain.Conflict;
import com.genealogy.platform.services.research.domain.Conflict.ConflictStatus;
import com.genealogy.platform.services.research.domain.ConflictKind;
import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
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
 * JdbcTemplate repository for the {@code conflicts} aggregate
 * table. Mirrors {@code V2__research_aggregate.sql} (E6.1b).
 * Participants are stored as the {@code conflict_participants}
 * bridge table in V2; the {@code linked_citation_ids} JSONB list
 * is denormalised on the parent row.
 */
public class ConflictRepository {

    private static final String COLUMNS =
            "id, tenant_id, summary, kind, kind_note, status, resolution, "
                    + "resolution_proof, linked_citation_ids, version, "
                    + "created_at, updated_at, resolved_at, "
                    + "created_by_actor_pseudo_id, correlation_id";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ConflictRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Conflict conflict) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO research_service.conflicts (" + COLUMNS + ") "
                            + "VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)");
            ps.setString(1, conflict.id().resourceId());
            ps.setString(2, conflict.id().tenantId());
            ps.setString(3, conflict.summary());
            ps.setString(4, conflict.kind().name());
            if (conflict.kindNote() == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, conflict.kindNote());
            }
            ps.setString(6, conflict.status().name());
            if (conflict.resolution() == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, conflict.resolution());
            }
            if (conflict.resolutionProof() == null) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, conflict.resolutionProof());
            }
            ps.setString(9, "[]");
            ps.setLong(10, conflict.version());
            ps.setTimestamp(11, Timestamp.from(conflict.createdAt()));
            ps.setTimestamp(12, Timestamp.from(conflict.updatedAt()));
            if (conflict.resolvedAt() == null) {
                ps.setNull(13, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(13, Timestamp.from(conflict.resolvedAt()));
            }
            ps.setString(14, conflict.audit().actorPseudoId());
            ps.setString(15, conflict.audit().correlationId());
            return ps;
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Conflict conflict) {
        int rows = jdbc.update(
                "UPDATE research_service.conflicts SET "
                        + "summary = ?, kind = ?, kind_note = ?, status = ?, "
                        + "resolution = ?, resolution_proof = ?, version = ?, "
                        + "updated_at = ?, resolved_at = ?, "
                        + "created_by_actor_pseudo_id = ?, correlation_id = ? "
                        + "WHERE id = ? AND tenant_id = ? AND version = ?",
                conflict.summary(),
                conflict.kind().name(),
                conflict.kindNote(),
                conflict.status().name(),
                conflict.resolution(),
                conflict.resolutionProof(),
                conflict.version(),
                Timestamp.from(conflict.updatedAt()),
                conflict.resolvedAt() == null ? null : Timestamp.from(conflict.resolvedAt()),
                conflict.audit().actorPseudoId(),
                conflict.audit().correlationId(),
                conflict.id().resourceId(),
                conflict.id().tenantId(),
                conflict.version() - 1);
        if (rows != 1) {
            throw new RepositorySupport.OptimisticConcurrencyException(
                    "conflict " + conflict.id().resourceId()
                            + " was modified by another transaction");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Conflict> findById(String tenantId, String id) {
        try {
            Conflict conflict = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM research_service.conflicts "
                            + "WHERE id = ? AND tenant_id = ?",
                    MAPPER,
                    id,
                    tenantId);
            return Optional.ofNullable(conflict);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static final RowMapper<Conflict> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static Conflict rehydrate(ResultSet rs) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String resourceId = rs.getString("id");
        TenantScopedId id = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.CONFLICT, resourceId);
        String summary = rs.getString("summary");
        ConflictKind kind = ConflictKind.valueOf(rs.getString("kind"));
        String kindNote = rs.getString("kind_note");
        ConflictStatus status = ConflictStatus.valueOf(rs.getString("status"));
        String resolution = rs.getString("resolution");
        String resolutionProof = rs.getString("resolution_proof");
        long version = rs.getLong("version");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        Instant resolvedAt = rs.getTimestamp("resolved_at") == null
                ? null : rs.getTimestamp("resolved_at").toInstant();
        String actorPseudoId = rs.getString("created_by_actor_pseudo_id");
        String correlationId = rs.getString("correlation_id");
        ResearchAuditAttributes audit = ResearchAuditAttributes.of(actorPseudoId, correlationId);
        return Conflict.rehydrate(id, summary, kind, kindNote, new ArrayList<>(),
                new ArrayList<>(), status, resolution, resolutionProof,
                createdAt, updatedAt, resolvedAt, version, audit);
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
