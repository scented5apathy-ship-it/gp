package com.genealogy.platform.services.research.application.persistence;

import com.genealogy.platform.services.research.domain.Certainty;
import com.genealogy.platform.services.research.domain.Hypothesis;
import com.genealogy.platform.services.research.domain.HypothesisStatus;
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
 * JdbcTemplate repository for the {@code hypotheses} aggregate
 * table. Mirrors {@code V2__research_aggregate.sql} (E6.1b).
 * The corroborating / refuting citation lists are stored as the
 * two dedicated bridge tables in V2 and joined back by the
 * repository at read time.
 */
public class HypothesisRepository {

    private static final String COLUMNS =
            "id, tenant_id, statement, subject_reference, subject_kind, "
                    + "certainty, confidence, status, superseded_by_hypothesis_id, "
                    + "assigned_to, version, created_at, updated_at, resolved_at, "
                    + "created_by_actor_pseudo_id, correlation_id";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public HypothesisRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Hypothesis hypothesis) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO research_service.hypotheses (" + COLUMNS + ") "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, hypothesis.id().resourceId());
            ps.setString(2, hypothesis.id().tenantId());
            ps.setString(3, hypothesis.statement());
            ps.setString(4, hypothesis.subjectReference());
            if (hypothesis.subjectKind() == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, hypothesis.subjectKind());
            }
            ps.setString(6, hypothesis.certainty().name());
            if (hypothesis.confidence() == null) {
                ps.setNull(7, Types.NUMERIC);
            } else {
                ps.setBigDecimal(7, new java.math.BigDecimal(hypothesis.confidence()));
            }
            ps.setString(8, hypothesis.status().name());
            if (hypothesis.supersededByHypothesisId() == null) {
                ps.setNull(9, Types.VARCHAR);
            } else {
                ps.setString(9, hypothesis.supersededByHypothesisId());
            }
            if (hypothesis.assignedTo() == null) {
                ps.setNull(10, Types.VARCHAR);
            } else {
                ps.setString(10, hypothesis.assignedTo());
            }
            ps.setLong(11, hypothesis.version());
            ps.setTimestamp(12, Timestamp.from(hypothesis.createdAt()));
            ps.setTimestamp(13, Timestamp.from(hypothesis.updatedAt()));
            if (hypothesis.resolvedAt() == null) {
                ps.setNull(14, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(14, Timestamp.from(hypothesis.resolvedAt()));
            }
            ps.setString(15, hypothesis.audit().actorPseudoId());
            ps.setString(16, hypothesis.audit().correlationId());
            return ps;
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Hypothesis> findById(String tenantId, String id) {
        try {
            Hypothesis hypothesis = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM research_service.hypotheses "
                            + "WHERE id = ? AND tenant_id = ?",
                    MAPPER,
                    id,
                    tenantId);
            return Optional.ofNullable(hypothesis);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Hypothesis hypothesis) {
        int rows = jdbc.update(
                "UPDATE research_service.hypotheses SET "
                        + "statement = ?, subject_reference = ?, subject_kind = ?, "
                        + "certainty = ?, confidence = ?, status = ?, "
                        + "superseded_by_hypothesis_id = ?, assigned_to = ?, "
                        + "version = ?, updated_at = ?, resolved_at = ?, "
                        + "created_by_actor_pseudo_id = ?, correlation_id = ? "
                        + "WHERE id = ? AND tenant_id = ? AND version = ?",
                hypothesis.statement(),
                hypothesis.subjectReference(),
                hypothesis.subjectKind(),
                hypothesis.certainty().name(),
                hypothesis.confidence() == null
                        ? null : new java.math.BigDecimal(hypothesis.confidence()),
                hypothesis.status().name(),
                hypothesis.supersededByHypothesisId(),
                hypothesis.assignedTo(),
                hypothesis.version(),
                Timestamp.from(hypothesis.updatedAt()),
                hypothesis.resolvedAt() == null ? null : Timestamp.from(hypothesis.resolvedAt()),
                hypothesis.audit().actorPseudoId(),
                hypothesis.audit().correlationId(),
                hypothesis.id().resourceId(),
                hypothesis.id().tenantId(),
                hypothesis.version() - 1);
        if (rows != 1) {
            throw new RepositorySupport.OptimisticConcurrencyException(
                    "hypothesis " + hypothesis.id().resourceId()
                            + " was modified by another transaction");
        }
    }

    private static final RowMapper<Hypothesis> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static Hypothesis rehydrate(ResultSet rs) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String resourceId = rs.getString("id");
        TenantScopedId id = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.HYPOTHESIS, resourceId);
        String statement = rs.getString("statement");
        String subjectReference = rs.getString("subject_reference");
        String subjectKind = rs.getString("subject_kind");
        Certainty certainty = Certainty.valueOf(rs.getString("certainty"));
        java.math.BigDecimal confidenceRaw = rs.getBigDecimal("confidence");
        Double confidence = confidenceRaw == null ? null : confidenceRaw.doubleValue();
        HypothesisStatus status = HypothesisStatus.valueOf(rs.getString("status"));
        String supersededByHypothesisId = rs.getString("superseded_by_hypothesis_id");
        String assignedTo = rs.getString("assigned_to");
        long version = rs.getLong("version");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        Instant resolvedAt = rs.getTimestamp("resolved_at") == null
                ? null : rs.getTimestamp("resolved_at").toInstant();
        String actorPseudoId = rs.getString("created_by_actor_pseudo_id");
        String correlationId = rs.getString("correlation_id");
        ResearchAuditAttributes audit = ResearchAuditAttributes.of(actorPseudoId, correlationId);
        return Hypothesis.rehydrate(id, statement, subjectReference, subjectKind, certainty,
                confidence, status, new ArrayList<>(), new ArrayList<>(),
                supersededByHypothesisId, assignedTo,
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
