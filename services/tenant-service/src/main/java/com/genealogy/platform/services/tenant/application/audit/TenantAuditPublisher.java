package com.genealogy.platform.services.tenant.application.audit;

import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.spring.audit.AuditEvent;
import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.Map;
import java.util.Objects;

/**
 * Tiny facade that emits the canonical {@link AuditEvent}s for
 * every tenant command. Keeps the command services free of
 * repetitive audit plumbing; the publisher itself is the
 * platform-provided interface so E3.6 can swap to the
 * dedicated audit-service transport without a code change.
 *
 * <p>Payloads intentionally carry opaque ids only — no raw
 * emails, no PII, no DNA, no secrets. Email / role / reason
 * fields are folded into the {@code metadata} map under
 * well-known keys for the audit-service consumer.
 */
public class TenantAuditPublisher {

    private final AuditPublisher publisher;

    public TenantAuditPublisher(AuditPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public void publish(String action, TenantId tenantId, String resourceId, Map<String, String> metadata) {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        publisher.publish(new AuditEvent(
                tenantId.getValue(),
                ctx.getActorId(),
                action,
                "tenant",
                resourceId,
                ctx.getCorrelationId(),
                metadata == null ? Map.of() : metadata));
    }

    public void publishMembership(String action, TenantId tenantId, String membershipId,
                                   Map<String, String> metadata) {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        publisher.publish(new AuditEvent(
                tenantId.getValue(),
                ctx.getActorId(),
                action,
                "membership",
                membershipId,
                ctx.getCorrelationId(),
                metadata == null ? Map.of() : metadata));
    }
}
