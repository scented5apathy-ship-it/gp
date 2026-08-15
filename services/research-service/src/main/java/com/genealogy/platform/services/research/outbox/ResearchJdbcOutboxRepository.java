package com.genealogy.platform.services.research.outbox;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate-backed implementation of {@link ResearchOutboxRepository}.
 *
 * <p>The production driver (the
 * {@code ResearchOutboxRelayRunner} scheduled bean) calls this
 * repository inside a per-tenant transaction. The tenant
 * context is bound via {@code SET LOCAL ROLE
 * research_service_app} + {@code SET LOCAL app.tenant_id} so
 * the {@code FORCE ROW LEVEL SECURITY} posture from V3 is
 * honoured end-to-end. The {@code SELECT ... FOR UPDATE SKIP
 * LOCKED} query claims pending rows without blocking another
 * relay replica; the {@code save(...)} writes the next state
 * back inside the same transaction.
 *
 * <p>This class closes E6.1d Gap 3. The unit-test path
 * continues to use the {@code InMemoryOutboxRepository}
 * declared on {@link ResearchOutboxRelay}; both
 * implementations are validated by
 * {@code ResearchOutboxRepositoryContractTest}.
 *
 * <p>Scope guard (per {@code agent-execution.md} §4.4):
 *   - The schema is the V3 {@code research_service.outbox}
 *     table; this class adds no DDL.
 *   - No topic / ACL / Kafka client change.
 *   - No domain code change.
 */
public class ResearchJdbcOutboxRepository implements ResearchOutboxRepository {

    private static final RowMapper<ResearchOutboxEventRecord> ROW_MAPPER =
            new ResearchOutboxRowMapper();

    private final JdbcTemplate jdbc;

    public ResearchJdbcOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /**
     * Claim up to {@code limit} pending rows for the tenant.
     * The caller MUST have bound the tenant context
     * ({@code SET LOCAL ROLE research_service_app} +
     * {@code SET LOCAL app.tenant_id = ?}) before invoking;
     * the {@code WHERE tenant_id = current_tenant_id()}
     * predicate is enforced by RLS-FORCE regardless.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ResearchOutboxEventRecord> claimPending(String tenantId, int limit, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(now, "now");
        if (limit <= 0) {
            return List.of();
        }
        List<ResearchOutboxEventRecord> result = jdbc.query(
                ""
                        + "SELECT event_id, tenant_id, aggregate_id, event_type, schema_id, "
                        + "       payload::text AS payload_text, payload_byte_size, "
                        + "       occurred_at, correlation_id, trace_id, partition_key, "
                        + "       partition_key_class, status, attempts, last_attempt_at, "
                        + "       next_attempt_at, published_at, claimed_at, "
                        + "       claim_lease_until, last_error, dlq_reason, audit_event_id "
                        + "  FROM research_service.outbox "
                        + " WHERE status = 'PENDING' "
                        + "   AND (next_attempt_at IS NULL OR next_attempt_at <= ?) "
                        + "   AND (claim_lease_until IS NULL OR claim_lease_until <= ?) "
                        + " ORDER BY occurred_at ASC "
                        + " LIMIT ? "
                        + " FOR UPDATE SKIP LOCKED",
                ROW_MAPPER,
                Timestamp.from(now),
                Timestamp.from(now),
                limit);
        return result == null ? List.of() : result;
    }

    /**
     * Persist the next state of the row. The caller MUST
     * already hold the row lock from {@link #claimPending}
     * so the {@code WHERE event_id = ?} predicate is safe
     * inside the same transaction.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(ResearchOutboxEventRecord row) {
        Objects.requireNonNull(row, "row");
        int updated = jdbc.update(
                ""
                        + "UPDATE research_service.outbox "
                        + "   SET status = ?, "
                        + "       attempts = ?, "
                        + "       last_attempt_at = ?, "
                        + "       next_attempt_at = ?, "
                        + "       published_at = ?, "
                        + "       claimed_at = ?, "
                        + "       claim_lease_until = ?, "
                        + "       last_error = ?, "
                        + "       dlq_reason = ?, "
                        + "       audit_event_id = ? "
                        + " WHERE event_id = ?",
                ps -> bindState(ps, row));
        if (updated == 0) {
            throw new IllegalStateException(
                    "outbox row vanished under the lock: eventId=" + row.eventId());
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ResearchOutboxEventRecord> findById(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        List<ResearchOutboxEventRecord> rows = jdbc.query(
                ""
                        + "SELECT event_id, tenant_id, aggregate_id, event_type, schema_id, "
                        + "       payload::text AS payload_text, payload_byte_size, "
                        + "       occurred_at, correlation_id, trace_id, partition_key, "
                        + "       partition_key_class, status, attempts, last_attempt_at, "
                        + "       next_attempt_at, published_at, claimed_at, "
                        + "       claim_lease_until, last_error, dlq_reason, audit_event_id "
                        + "  FROM research_service.outbox "
                        + " WHERE event_id = ?",
                ROW_MAPPER,
                eventId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static void bindState(PreparedStatement ps, ResearchOutboxEventRecord row)
            throws SQLException {
        ps.setString(1, row.status().name());
        ps.setInt(2, row.attempts());
        bindInstant(ps, 3, row.lastAttemptAt());
        bindInstant(ps, 4, row.nextAttemptAt());
        bindInstant(ps, 5, row.publishedAt());
        bindInstant(ps, 6, row.claimedAt());
        bindInstant(ps, 7, row.claimLeaseUntil());
        if (row.lastError() == null) {
            ps.setNull(8, Types.VARCHAR);
        } else {
            ps.setString(8, row.lastError());
        }
        if (row.dlqReason() == null) {
            ps.setNull(9, Types.VARCHAR);
        } else {
            ps.setString(9, row.dlqReason().name());
        }
        if (row.auditEventId() == null) {
            ps.setNull(10, Types.VARCHAR);
        } else {
            ps.setString(10, row.auditEventId());
        }
    }

    private static void bindInstant(PreparedStatement ps, int idx, Instant value)
            throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(idx, Timestamp.from(value));
        }
    }

    private static final class ResearchOutboxRowMapper
            implements RowMapper<ResearchOutboxEventRecord> {

        @Override
        public ResearchOutboxEventRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ResearchOutboxEventRecord(
                    rs.getString("event_id"),
                    rs.getString("tenant_id"),
                    rs.getString("aggregate_id"),
                    rs.getString("event_type"),
                    rs.getString("schema_id"),
                    rs.getString("payload_text"),
                    rs.getInt("payload_byte_size"),
                    readInstant(rs, "occurred_at"),
                    rs.getString("correlation_id"),
                    rs.getString("trace_id"),
                    rs.getString("partition_key"),
                    ResearchPartitionKeyClass.valueOf(rs.getString("partition_key_class")),
                    ResearchOutboxStatus.valueOf(rs.getString("status")),
                    rs.getInt("attempts"),
                    readInstant(rs, "last_attempt_at"),
                    readInstant(rs, "next_attempt_at"),
                    readInstant(rs, "published_at"),
                    readInstant(rs, "claimed_at"),
                    readInstant(rs, "claim_lease_until"),
                    rs.getString("last_error"),
                    readDlq(rs.getString("dlq_reason")),
                    rs.getString("audit_event_id"));
        }

        private static Instant readInstant(ResultSet rs, String column) throws SQLException {
            Timestamp ts = rs.getTimestamp(column);
            return ts == null ? null : ts.toInstant();
        }

        private static ResearchDlqReason readDlq(String raw) {
            return raw == null ? null : ResearchDlqReason.valueOf(raw);
        }
    }
}
