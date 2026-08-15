package com.genealogy.platform.services.research.workspace;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate-backed {@link ResearchConsumerInboxRepository}.
 *
 * <p>The {@link #tryClaim} method runs {@code INSERT ... ON
 * CONFLICT DO NOTHING} so the first delivery wins the row;
 * every subsequent delivery finds the row already present and
 * returns {@code false}, letting the
 * {@link ResearchConsumerInboxService} skip the projection
 * mutation without forking state.
 *
 * <p>Closes E6.1d Gap 5 — consumer durable inbox / idempotency
 * table. The schema lands in
 * {@code V4__consumer_inbox.sql}; the production driver is the
 * Spring {@code @KafkaListener} wired in
 * {@link ResearchConsumerInboxListener}.
 *
 * <p>Scope guard (per {@code agent-execution.md} §4.4):
 *   - Schema lives in V4 (this commit); no aggregate table change.
 *   - No domain code change; the projection service is untouched.
 *   - No Kafka topic / ACL change.
 */
public class ResearchJdbcConsumerInboxRepository implements ResearchConsumerInboxRepository {

    private final JdbcTemplate jdbc;

    public ResearchJdbcConsumerInboxRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryClaim(ResearchConsumerInboxRow row) {
        Objects.requireNonNull(row, "row");
        int inserted = jdbc.update(
                ""
                        + "INSERT INTO research_service.consumer_inbox "
                        + "  (tenant_id, source_topic, event_id, event_type, "
                        + "   payload_hash, received_at, outcome, "
                        + "   actor_pseudo_id, correlation_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'IN_FLIGHT', ?, ?) "
                        + "ON CONFLICT (tenant_id, source_topic, event_id) DO NOTHING",
                ps -> {
                    ps.setString(1, row.tenantId());
                    ps.setString(2, row.sourceTopic());
                    ps.setString(3, row.eventId());
                    ps.setString(4, row.eventType());
                    ps.setString(5, row.payloadHash());
                    ps.setObject(6, Timestamp.from(row.receivedAt()));
                    if (row.actorPseudoId() == null) {
                        ps.setNull(7, Types.VARCHAR);
                    } else {
                        ps.setString(7, row.actorPseudoId());
                    }
                    if (row.correlationId() == null) {
                        ps.setNull(8, Types.VARCHAR);
                    } else {
                        ps.setString(8, row.correlationId());
                    }
                });
        return inserted == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ResearchConsumerInboxRow> find(
            String tenantId, String sourceTopic, String eventId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(eventId, "eventId");
        List<ResearchConsumerInboxRow> rows = jdbc.query(
                ""
                        + "SELECT tenant_id, source_topic, event_id, event_type, "
                        + "       payload_hash, received_at, processed_at, outcome, "
                        + "       last_error, actor_pseudo_id, correlation_id "
                        + "  FROM research_service.consumer_inbox "
                        + " WHERE tenant_id = ? AND source_topic = ? AND event_id = ?",
                ps -> {
                    ps.setString(1, tenantId);
                    ps.setString(2, sourceTopic);
                    ps.setString(3, eventId);
                },
                this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void finalizeOutcome(ResearchConsumerInboxRow row) {
        Objects.requireNonNull(row, "row");
        int updated = jdbc.update(
                ""
                        + "UPDATE research_service.consumer_inbox "
                        + "   SET outcome = ?, processed_at = ?, last_error = ? "
                        + " WHERE tenant_id = ? AND source_topic = ? AND event_id = ?",
                ps -> {
                    ps.setString(1, row.outcome().name());
                    bindInstant(ps, 2, row.processedAt());
                    if (row.lastError() == null) {
                        ps.setNull(3, Types.VARCHAR);
                    } else {
                        ps.setString(3, row.lastError());
                    }
                    ps.setString(4, row.tenantId());
                    ps.setString(5, row.sourceTopic());
                    ps.setString(6, row.eventId());
                });
        if (updated == 0) {
            throw new IllegalStateException(
                    "consumer inbox row vanished under the lock: tenantId="
                            + row.tenantId() + ", sourceTopic=" + row.sourceTopic()
                            + ", eventId=" + row.eventId());
        }
    }

    private ResearchConsumerInboxRow map(ResultSet rs, int rowNum) throws SQLException {
        return new ResearchConsumerInboxRow(
                rs.getString("tenant_id"),
                rs.getString("source_topic"),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("payload_hash"),
                readInstant(rs, "received_at"),
                readInstant(rs, "processed_at"),
                ResearchConsumerInboxRow.Outcome.valueOf(rs.getString("outcome")),
                rs.getString("last_error"),
                rs.getString("actor_pseudo_id"),
                rs.getString("correlation_id"));
    }

    private static Instant readInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static void bindInstant(PreparedStatement ps, int idx, Instant value)
            throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(idx, Timestamp.from(value));
        }
    }
}
