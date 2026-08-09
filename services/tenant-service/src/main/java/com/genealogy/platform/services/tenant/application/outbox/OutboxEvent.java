package com.genealogy.platform.services.tenant.application.outbox;

import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable outbox row written in the same transaction as the
 * aggregate mutation. Mirrors the {@code tenant_service.outbox_events}
 * table (E3.2a) and the {@code EventEnvelope} shape (E1.3).
 *
 * <p>The {@code schemaId} is the dotted Apicurio / Avro identifier
 * (e.g. {@code com.genealogy.platform.events.tenant.v1.TenantCreated})
 * so the E4.7 relay can resolve the canonical schema for the
 * payload bytes. The {@code payload} bytes are JSON UTF-8 in
 * E3.2c (the relay upgrades to binary Avro without changing the
 * column shape — BYTEA is encoding-agnostic).
 *
 * <p>Compatibility policy follows ADR-E0.5-08 (BACKWARD); removing
 * a field requires bumping the schema to {@code v2}.
 */
public record OutboxEvent(
        TenantId tenantId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String schemaId,
        byte[] payload,
        String correlationId,
        String traceId,
        Map<String, String> metadata) {

    public OutboxEvent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(schemaId, "schemaId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(traceId, "traceId");
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload must not be empty");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
