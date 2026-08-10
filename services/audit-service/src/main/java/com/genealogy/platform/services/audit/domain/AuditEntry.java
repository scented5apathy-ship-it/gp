package com.genealogy.platform.services.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Domain record for an audit-service ledger row. The
 * {@code eventId} is the wire-level idempotency key (also the
 * natural primary key in the database). The hash chain fields
 * ({@code previousHash}, {@code entryHash}) are the integrity
 * proof consumed by {@code IntegrityVerifier}.
 *
 * <p>The record is intentionally framework-free: jOOQ maps the
 * row, the ingest service hydrates this record, and the export
 * service renders it for DPO consumption.
 */
public record AuditEntry(
        String eventId,
        String tenantId,
        String actorId,
        String auditClass,
        String action,
        String resourceType,
        String resourceId,
        String reasonCode,
        String correlationId,
        Instant occurredAt,
        Instant receivedAt,
        Map<String, String> metadata,
        String previousHash,
        String entryHash) {

    public AuditEntry {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(auditClass, "auditClass");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(previousHash, "previousHash");
        Objects.requireNonNull(entryHash, "entryHash");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * Canonical byte sequence used by {@link HashChainComputer} to
     * derive {@code entryHash}. The order is FIXED; do not reorder
     * without bumping the contract version and the genesis hash.
     */
    public String canonicalBytes() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("v1|");
        sb.append(eventId).append('|');
        sb.append(tenantId).append('|');
        sb.append(actorId == null ? "" : actorId).append('|');
        sb.append(auditClass).append('|');
        sb.append(action).append('|');
        sb.append(resourceType == null ? "" : resourceType).append('|');
        sb.append(resourceId == null ? "" : resourceId).append('|');
        sb.append(reasonCode == null ? "" : reasonCode).append('|');
        sb.append(correlationId == null ? "" : correlationId).append('|');
        sb.append(occurredAt.toString()).append('|');
        sb.append(previousHash).append('|');
        sb.append(metadataAsString(metadata));
        return sb.toString();
    }

    private static String metadataAsString(Map<String, String> metadata) {
        // deterministic, insertion-ordered serialisation
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue() == null ? "" : entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }
}
