package com.genealogy.platform.services.tenant.application.outbox;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate-backed implementation of {@link OutboxWriter}.
 *
 * <p>The insert runs with {@link Propagation#MANDATORY} so the
 * caller MUST already have a transaction open (the
 * {@code TenantCommandService} / {@code MembershipCommandService}
 * methods are {@code @Transactional}); without an active
 * transaction the {@code TransactionRequiredException} fires
 * before any JDBC work is attempted. This is the defense-in-depth
 * branch that prevents an outbox row from being written without the
 * matching aggregate row.
 *
 * <p>The writer also runs inside the
 * {@code TenantRlsTxInterceptor} AOP advice that issues
 * {@code SET LOCAL ROLE tenant_service_app} and
 * {@code SET LOCAL app.tenant_id} on the same JDBC connection,
 * so RLS is bound to the outbox row as well.
 */
public class JdbcOutboxWriter implements OutboxWriter {

    private static final String SQL_INSERT = """
            INSERT INTO tenant_service.outbox_events
                (id, tenant_id, aggregate_type, aggregate_id, event_type,
                 payload, schema_id, correlation_id, trace_id, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;

    private final JdbcTemplate jdbc;
    private final com.genealogy.platform.services.tenant.domain.ids.IdGenerator idGenerator;

    public JdbcOutboxWriter(
            JdbcTemplate jdbc,
            com.genealogy.platform.services.tenant.domain.ids.IdGenerator idGenerator) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxEvent event) {
        jdbc.update(
                SQL_INSERT,
                idGenerator.nextId(),
                event.tenantId().getValue(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.schemaId(),
                event.correlationId(),
                event.traceId(),
                serializeMetadata(event.metadata()));
    }

    private static String serializeMetadata(java.util.Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
