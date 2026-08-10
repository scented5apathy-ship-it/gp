package com.genealogy.platform.spring.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wire-format envelope used by every {@code audit-service} producer.
 * Mirrors the canonical {@link AuditEvent} shape (pseudonymous ids
 * only) plus the catalogue fields from
 * {@code contracts/audit/policy.yaml}:
 *
 * <ul>
 *   <li>{@code auditClass} — one of {@code auth} / {@code authorization} /
 *       {@code policy} / {@code support} / {@code download} / {@code consent}
 *       (closed set, enforced by {@link AuditEventValidator}).
 *   <li>{@code action} — dotted identifier scoped to the audit class
 *       (e.g. {@code membership.revoked}); the catalogue is closed.
 *   <li>{@code reasonCode} — optional, references the ABAC reason
 *       registry when the action triggered an ABAC obligation.
 * </ul>
 *
 * <p>The envelope is intentionally framework-free: it is the unit the
 * {@code audit-service} consumes off the Kafka audit topic, the unit the
 * redaction filter transforms, and the unit the integrity hash chain
 * includes. JSON is the canonical encoding (matches the Avro payload
 * layout that E2.3 wiring produces); production paths use Avro but
 * the JSON shape is identical so test doubles stay simple.
 */
public final class AuditEventEnvelope {

    private final String eventId;
    private final String tenantId;
    private final String actorId;
    private final String auditClass;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final String reasonCode;
    private final String correlationId;
    private final Instant occurredAt;
    private final Map<String, String> metadata;

    public AuditEventEnvelope(
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
            Map<String, String> metadata) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorId = actorId;
        this.auditClass = Objects.requireNonNull(auditClass, "auditClass");
        this.action = Objects.requireNonNull(action, "action");
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.reasonCode = reasonCode;
        this.correlationId = correlationId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static AuditEventEnvelope from(AuditEvent event, String auditClass, String reasonCode) {
        return new AuditEventEnvelope(
                event.getEventId(),
                event.getTenantId(),
                event.getActorId(),
                auditClass,
                event.getAction(),
                event.getResource(),
                event.getResourceId(),
                reasonCode,
                event.getCorrelationId(),
                event.getOccurredAt(),
                event.getMetadata());
    }

    public String getEventId() {
        return eventId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAuditClass() {
        return auditClass;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Stable JSON encoding consumed by {@code audit-service} (Kafka
     * audit topic + WORM persistence). The order of keys is fixed so
     * the integrity hash chain is deterministic across producers.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendStringField(sb, "eventId", eventId, true);
        appendStringField(sb, "tenantId", tenantId, false);
        appendStringField(sb, "actorId", actorId == null ? "" : actorId, false);
        appendStringField(sb, "auditClass", auditClass, false);
        appendStringField(sb, "action", action, false);
        appendStringField(sb, "resourceType", resourceType == null ? "" : resourceType, false);
        appendStringField(sb, "resourceId", resourceId == null ? "" : resourceId, false);
        appendStringField(sb, "reasonCode", reasonCode == null ? "" : reasonCode, false);
        appendStringField(sb, "correlationId", correlationId == null ? "" : correlationId, false);
        sb.append("\"occurredAt\":\"").append(occurredAt.toString()).append("\",");
        sb.append("\"metadata\":{");
        boolean firstMeta = true;
        for (Map.Entry<String, String> entry : new LinkedHashMap<>(metadata).entrySet()) {
            if (!firstMeta) {
                sb.append(',');
            }
            firstMeta = false;
            appendStringField(sb, entry.getKey(), entry.getValue() == null ? "" : entry.getValue(), true);
        }
        sb.append("}}");
        return sb.toString();
    }

    private static void appendStringField(StringBuilder sb, String key, String value, boolean leading) {
        if (!leading) {
            sb.append(',');
        }
        sb.append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
