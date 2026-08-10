package com.genealogy.platform.services.genealogy.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox writer for the tree-service. Mirrors
 * {@code services/tenant-service/.../outbox/JdbcOutboxWriter.java}.
 *
 * <p>The command service writes the aggregate row + the outbox row
 * in the same PostgreSQL transaction. A separate relay process
 * (out of scope for E4.1) consumes the outbox table and publishes
 * to Kafka with the Apicurio schema.
 *
 * <p>Per {@code design.md} §7.3 the payload MUST NOT contain raw
 * DNA / PII / access tokens. The event payload records (see
 * {@link TreeEventPayloads}) are the canonical "no PII" shape.
 */
public class JdbcTreeOutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcTreeOutboxWriter(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.mapper = mapper;
    }

    public String enqueue(String aggregateId,
                          String tenantId,
                          String eventType,
                          Object payload,
                          Instant occurredAt,
                          String correlationId) {
        String eventId = UUID.randomUUID().toString();
        try {
            String json = mapper.writeValueAsString(payload);
            jdbc.update(
                    "INSERT INTO tree_service.outbox ("
                            + " event_id, aggregate_id, tenant_id, event_type, payload,"
                            + " occurred_at, correlation_id, created_at"
                            + ") VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                    eventId,
                    aggregateId,
                    tenantId,
                    eventType,
                    json,
                    Timestamp.from(occurredAt),
                    correlationId,
                    Timestamp.from(Instant.now()));
            return eventId;
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialise payload for " + eventType, e);
        }
    }
}
