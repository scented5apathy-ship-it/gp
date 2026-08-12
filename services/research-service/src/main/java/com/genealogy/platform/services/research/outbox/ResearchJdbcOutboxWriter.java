package com.genealogy.platform.services.research.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox writer for research-service. Mirrors
 * {@code services/genealogy-service/.../JdbcTreeOutboxWriter.java}.
 *
 * <p>The command service writes the aggregate row + the outbox
 * row in the same PostgreSQL transaction. The
 * {@link ResearchOutboxRelay} polls the outbox in a separate
 * process and publishes to Kafka via the Apicurio-registered
 * Avro schemas under {@code contracts/events/research/v1/}.
 *
 * <p>Per {@code design.md} §7.3 the payload MUST NOT contain raw
 * DNA / PII / access tokens. The event payload records (see
 * {@code ResearchEventPayloads}) are the canonical "no PII"
 * shape; the relay re-validates the payload against the
 * forbidden-field scan before publishing.
 */
@Component
public class ResearchJdbcOutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ResearchJdbcOutboxWriter(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * No-op ctor for unit tests that need a subclass to
     * override the {@code enqueue} method without standing
     * up a real JDBC data source. Production code MUST use
     * the {@link #ResearchJdbcOutboxWriter(DataSource, ObjectMapper)}
     * ctor so the {@code JdbcTemplate} is bound.
     */
    protected ResearchJdbcOutboxWriter() {
        this.jdbc = null;
        this.mapper = null;
    }

    public String enqueue(String aggregateId,
                          String tenantId,
                          String eventType,
                          Object payload,
                          String actorPseudoId,
                          String correlationId,
                          String traceId,
                          java.time.Instant occurredAt) {
        String eventId = java.util.UUID.randomUUID().toString();
        try {
            String json = mapper.writeValueAsString(payload);
            int byteSize = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (byteSize < 1 || byteSize > 921600) {
                throw new IllegalStateException(
                        "payload byte size out of range [1, 921600]: " + byteSize);
            }
            String partitionKey = ResearchPartitionKeyPolicy.derive(
                    eventType, tenantId, aggregateId);
            ResearchPartitionKeyClass keyClass = ResearchPartitionKeyPolicy.classify(eventType);
            String schemaId = ResearchPartitionKeyPolicy.schemaId(eventType);
            jdbc.update(
                    "INSERT INTO research_service.outbox ("
                            + " event_id, aggregate_id, tenant_id, event_type, schema_id,"
                            + " payload, payload_byte_size, occurred_at, correlation_id,"
                            + " trace_id, partition_key, partition_key_class, status"
                            + ") VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, 'PENDING')",
                    eventId,
                    aggregateId,
                    tenantId,
                    eventType,
                    schemaId,
                    json,
                    byteSize,
                    java.sql.Timestamp.from(occurredAt),
                    correlationId,
                    traceId,
                    partitionKey,
                    keyClass.name());
            return eventId;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "cannot serialise payload for " + eventType, e);
        }
    }
}
