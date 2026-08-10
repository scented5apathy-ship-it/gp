package com.genealogy.platform.services.audit.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.audit.domain.AuditEntry;
import com.genealogy.platform.services.audit.domain.HashChainComputer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * jOOQ-free jOOQ-compatible repository backed by
 * {@link NamedParameterJdbcTemplate}. The SQL matches the schema
 * created by {@code V1__audit_ledger.sql} / {@code V2__deletion_evidence.sql}.
 *
 * <p>The repository enforces the append-only contract by relying
 * on the database trigger ({@code trg_audit_entry_append_only}).
 * The trigger raises on UPDATE / DELETE / TRUNCATE so even an
 * application bug cannot corrupt the ledger.
 */
public class JdbcAuditEntryRepository implements AuditEntryRepository {

    private static final String SCHEMA = "audit_service";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditEntryRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public boolean exists(String tenantId, String eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + SCHEMA + ".audit_entry WHERE tenant_id = :tenantId AND event_id = :eventId",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("eventId", eventId),
                Integer.class);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public AuditEntry append(AuditEntry entry) {
        if (exists(entry.tenantId(), entry.eventId())) {
            // Idempotent: the inbox dedupes by eventId; returning the
            // existing entry keeps the chain head unchanged.
            return entry;
        }
        Optional<AuditEntry> head = chainHead(entry.tenantId());
        String previousHash = head.map(h -> h.entryHash()).orElse(HashChainComputer.GENESIS_HASH);
        // Override the producer-supplied previousHash with the
        // authoritative chain head so a racing producer cannot fork
        // the chain, then recompute the entryHash from the
        // canonical bytes (which now include the real
        // previousHash). The IntegrityVerifier later recomputes
        // the entryHash from the persisted row's bytes; any drift
        // is reported as a tamper.
        AuditEntry withPrevious = new AuditEntry(
                entry.eventId(),
                entry.tenantId(),
                entry.actorId(),
                entry.auditClass(),
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                entry.reasonCode(),
                entry.correlationId(),
                entry.occurredAt(),
                entry.receivedAt(),
                entry.metadata(),
                previousHash,
                "0".repeat(64));
        AuditEntry canonical = new AuditEntry(
                withPrevious.eventId(),
                withPrevious.tenantId(),
                withPrevious.actorId(),
                withPrevious.auditClass(),
                withPrevious.action(),
                withPrevious.resourceType(),
                withPrevious.resourceId(),
                withPrevious.reasonCode(),
                withPrevious.correlationId(),
                withPrevious.occurredAt(),
                withPrevious.receivedAt(),
                withPrevious.metadata(),
                withPrevious.previousHash(),
                HashChainComputer.entryHash(withPrevious));
        try {
            jdbc.update(
                    "INSERT INTO " + SCHEMA + ".audit_entry "
                            + "(event_id, tenant_id, actor_id, audit_class, action, resource_type, "
                            + " resource_id, reason_code, correlation_id, occurred_at, received_at, "
                            + " payload, previous_hash, entry_hash) "
                            + "VALUES (:eventId, :tenantId, :actorId, :auditClass, :action, "
                            + " :resourceType, :resourceId, :reasonCode, :correlationId, :occurredAt, "
                            + " :receivedAt, :payload, :previousHash, :entryHash)",
                    new MapSqlParameterSource()
                            .addValue("eventId", canonical.eventId())
                            .addValue("tenantId", canonical.tenantId())
                            .addValue("actorId", canonical.actorId())
                            .addValue("auditClass", canonical.auditClass())
                            .addValue("action", canonical.action())
                            .addValue("resourceType", canonical.resourceType())
                            .addValue("resourceId", canonical.resourceId())
                            .addValue("reasonCode", canonical.reasonCode())
                            .addValue("correlationId", canonical.correlationId())
                            .addValue("occurredAt", Timestamp.from(canonical.occurredAt()))
                            .addValue("receivedAt", Timestamp.from(canonical.receivedAt()))
                            .addValue("payload", serializePayload(canonical))
                            .addValue("previousHash", canonical.previousHash())
                            .addValue("entryHash", canonical.entryHash()));
            return canonical;
        } catch (RuntimeException e) {
            throw new AuditAppendException(canonical.eventId(), canonical.tenantId(), e);
        }
    }

    @Override
    public Optional<AuditEntry> chainHead(String tenantId) {
        List<AuditEntry> heads = jdbc.query(
                "SELECT event_id, tenant_id, actor_id, audit_class, action, resource_type, resource_id, "
                        + "reason_code, correlation_id, occurred_at, received_at, payload, previous_hash, entry_hash "
                        + "FROM " + SCHEMA + ".audit_entry WHERE tenant_id = :tenantId "
                        + "ORDER BY id DESC LIMIT 1",
                new MapSqlParameterSource().addValue("tenantId", tenantId),
                this::mapRow);
        return heads.isEmpty() ? Optional.empty() : Optional.of(heads.get(0));
    }

    @Override
    public List<AuditEntry> findInWindow(String tenantId, String auditClass, Instant from, Instant to) {
        return jdbc.query(
                "SELECT event_id, tenant_id, actor_id, audit_class, action, resource_type, resource_id, "
                        + "reason_code, correlation_id, occurred_at, received_at, payload, previous_hash, entry_hash "
                        + "FROM " + SCHEMA + ".audit_entry "
                        + "WHERE tenant_id = :tenantId AND audit_class = :auditClass "
                        + "AND occurred_at >= :from AND occurred_at < :to "
                        + "ORDER BY id ASC",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("auditClass", auditClass)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to)),
                this::mapRow);
    }

    @Override
    public long countOlderThan(String tenantId, String auditClass, Instant before) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM " + SCHEMA + ".audit_entry "
                        + "WHERE tenant_id = :tenantId AND audit_class = :auditClass AND occurred_at < :before",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("auditClass", auditClass)
                        .addValue("before", Timestamp.from(before)),
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public List<AuditEntry> findOlderThan(String tenantId, String auditClass, Instant before, int limit) {
        return jdbc.query(
                "SELECT event_id, tenant_id, actor_id, audit_class, action, resource_type, resource_id, "
                        + "reason_code, correlation_id, occurred_at, received_at, payload, previous_hash, entry_hash "
                        + "FROM " + SCHEMA + ".audit_entry "
                        + "WHERE tenant_id = :tenantId AND audit_class = :auditClass AND occurred_at < :before "
                        + "ORDER BY id ASC LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("auditClass", auditClass)
                        .addValue("before", Timestamp.from(before))
                        .addValue("limit", limit),
                this::mapRow);
    }

    @Override
    public Map<String, Long> classCounts(String tenantId, Instant from, Instant to) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(
                "SELECT audit_class, count(*) AS c FROM " + SCHEMA + ".audit_entry "
                        + "WHERE tenant_id = :tenantId AND occurred_at >= :from AND occurred_at < :to "
                        + "GROUP BY audit_class",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to)),
                rs -> {
                    counts.put(rs.getString("audit_class"), rs.getLong("c"));
                });
        return counts;
    }

    private AuditEntry mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        try {
            Map<String, String> payload = objectMapper.readValue(
                    rs.getString("payload"), new TypeReference<Map<String, String>>() {
                    });
            return new AuditEntry(
                    rs.getString("event_id"),
                    rs.getString("tenant_id"),
                    rs.getString("actor_id"),
                    rs.getString("audit_class"),
                    rs.getString("action"),
                    rs.getString("resource_type"),
                    rs.getString("resource_id"),
                    rs.getString("reason_code"),
                    rs.getString("correlation_id"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getTimestamp("received_at").toInstant(),
                    payload,
                    rs.getString("previous_hash"),
                    rs.getString("entry_hash"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit_entry.payload is not valid JSON", e);
        }
    }

    private String serializePayload(AuditEntry entry) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("metadata", String.valueOf(entry.metadata()));
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise audit payload", e);
        }
    }

    public static class AuditAppendException extends RuntimeException {
        public AuditAppendException(String eventId, String tenantId, Throwable cause) {
            super("failed to append audit entry event_id=" + eventId + " tenant_id=" + tenantId, cause);
        }
    }
}
