package com.genealogy.platform.services.research.outbox;

import com.genealogy.platform.services.research.outbox.ResearchOutboxRelay.ResearchOutboxAuditHook;
import com.genealogy.platform.spring.audit.AuditEvent;
import com.genealogy.platform.spring.audit.AuditPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Spring bean that emits the canonical audit events for the
 * outbox relay. The {@link ResearchOutboxAuditHook} is the
 * seam that keeps the relay framework-free; this adapter
 * pushes the audit envelope onto the platform
 * {@link AuditPublisher} so the audit-service ledger receives
 * the publish / retry / DLQ outcomes.
 */
public class ResearchOutboxAuditHookAdapter implements ResearchOutboxAuditHook {

    private final AuditPublisher publisher;

    public ResearchOutboxAuditHookAdapter(AuditPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public void onPublished(ResearchOutboxEventRecord row) {
        publisher.publish(toAudit("research.outbox.published", row));
    }

    @Override
    public void onRetried(ResearchOutboxEventRecord row) {
        publisher.publish(toAudit("research.outbox.retried", row));
    }

    @Override
    public void onDeadLettered(ResearchOutboxEventRecord row) {
        publisher.publish(toAudit("research.outbox.deadLettered", row));
    }

    private static AuditEvent toAudit(String action, ResearchOutboxEventRecord row) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("eventId", row.eventId());
        meta.put("eventType", row.eventType());
        meta.put("schemaId", row.schemaId());
        meta.put("attempts", Integer.toString(row.attempts()));
        meta.put("status", row.status().name());
        if (row.dlqReason() != null) {
            meta.put("dlqReason", row.dlqReason().name());
        }
        return new AuditEvent(
                row.tenantId(),
                row.correlationId(),
                action,
                "researchOutboxEvent",
                row.eventId(),
                row.correlationId(),
                meta);
    }
}
