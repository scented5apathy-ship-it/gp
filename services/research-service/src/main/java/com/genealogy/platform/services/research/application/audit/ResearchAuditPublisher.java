package com.genealogy.platform.services.research.application.audit;

import com.genealogy.platform.services.research.domain.ids.TenantId;
import com.genealogy.platform.spring.audit.AuditEvent;
import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.Map;
import java.util.Objects;

/**
 * Tiny facade that emits the canonical {@link AuditEvent}s for
 * every research command. Keeps the command services free of
 * repetitive audit plumbing; the publisher itself is the
 * platform-provided interface so E6.1d can swap to the dedicated
 * audit-service transport without a code change.
 *
 * <p>Payloads intentionally carry opaque ids only — no raw
 * emails, no PII, no DNA, no secrets. Aggregate name + version
 * + correlation id are folded into the {@code metadata} map
 * under well-known keys for the audit-service consumer.
 */
public class ResearchAuditPublisher {

    private final AuditPublisher publisher;

    public ResearchAuditPublisher(AuditPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public void publish(
            String action,
            TenantId tenantId,
            String resourceKind,
            String resourceId,
            long version,
            Map<String, String> metadata) {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        java.util.Map<String, String> meta = metadata == null
                ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(metadata);
        meta.put("version", Long.toString(version));
        meta.putIfAbsent("resourceKind", resourceKind);
        publisher.publish(new AuditEvent(
                tenantId.getValue(),
                ctx.getActorId(),
                action,
                resourceKind,
                resourceId,
                ctx.getCorrelationId(),
                meta));
    }
}
