package com.genealogy.platform.spring.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only structured audit event emitted by the
 * {@code AuditAutoConfiguration} hook.
 *
 * <p>Audit events never contain raw DNA, file content or access
 * tokens. The payload only references opaque ids (tenant id, user
 * sub, aggregate id) so the event can be safely forwarded to the
 * dedicated {@code audit-service} in E3.6.
 */
public final class AuditEvent {

    private final String eventId;
    private final String tenantId;
    private final String actorId;
    private final String action;
    private final String resource;
    private final String resourceId;
    private final String correlationId;
    private final Instant occurredAt;
    private final Map<String, String> metadata;

    public AuditEvent(
            String tenantId,
            String actorId,
            String action,
            String resource,
            String resourceId,
            String correlationId,
            Map<String, String> metadata) {
        this.eventId = UUID.randomUUID().toString();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorId = actorId;
        this.action = Objects.requireNonNull(action, "action");
        this.resource = resource;
        this.resourceId = resourceId;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
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

    public String getAction() {
        return action;
    }

    public String getResource() {
        return resource;
    }

    public String getResourceId() {
        return resourceId;
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
}
