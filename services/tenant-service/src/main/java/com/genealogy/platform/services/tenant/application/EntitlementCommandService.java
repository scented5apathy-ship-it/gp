package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.application.audit.TenantAuditPublisher;
import com.genealogy.platform.services.tenant.application.outbox.EventPayloads;
import com.genealogy.platform.services.tenant.application.outbox.OutboxEvent;
import com.genealogy.platform.services.tenant.application.outbox.OutboxWriter;
import com.genealogy.platform.services.tenant.application.persistence.EntitlementRepository;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.entitlement.Entitlement;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.spring.context.OutboxCorrelationContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entitlement (plan + quota) command service. Operates on a single
 * row per tenant; the entitlement is an entity, not an aggregate
 * root, so the change methods mutate the row state directly.
 *
 * <p>Plan/Quota numerics are DRAFT (architecture-decisions.md §A);
 * the service does NOT enforce quota against mutations — that
 * responsibility lives with E11.4.
 */
@Service
public class EntitlementCommandService {

    private final EntitlementRepository entitlementRepository;
    private final TenantRepository tenantRepository;
    private final OutboxWriter outboxWriter;
    private final TenantAuditPublisher audit;
    private final TenantRlsTxInterceptor rls;
    private final java.time.Clock clock;

    public EntitlementCommandService(
            EntitlementRepository entitlementRepository,
            TenantRepository tenantRepository,
            OutboxWriter outboxWriter,
            TenantAuditPublisher audit,
            TenantRlsTxInterceptor rls,
            java.time.Clock clock) {
        this.entitlementRepository =
                Objects.requireNonNull(entitlementRepository, "entitlementRepository");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.rls = Objects.requireNonNull(rls, "rls");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Results.EntitlementView change(Commands.ChangeEntitlement cmd, String actorId) {
        rls.bind();
        // Validate the tenant exists so the foreign-key CHECK fires
        // before the entitlement row update.
        tenantRepository.findById(cmd.tenantId()).orElseThrow(
                () -> new TenantCommandService.TenantNotFoundException(
                        "tenant " + cmd.tenantId() + " not found"));

        Entitlement current = entitlementRepository.findByTenantId(cmd.tenantId())
                .orElseThrow(() -> new EntitlementNotFoundException(
                        "entitlement for tenant " + cmd.tenantId() + " not found"));

        if (cmd.newPlan() != null) {
            current.changePlan(cmd.newPlan(), clock);
        }
        // Quota fields default to the current value when the caller
        // passes null (which mirrors the PATCH semantics the REST
        // surface uses — only fields present in the body are touched).
        current.changeQuotas(
                cmd.newMemberLimit() != null ? cmd.newMemberLimit() : current.memberLimit(),
                cmd.newTreeLimit() != null ? cmd.newTreeLimit() : current.treeLimit(),
                cmd.newStorageLimitMb() != null ? cmd.newStorageLimitMb() : current.storageLimitMb(),
                cmd.newRetentionDays() != null ? cmd.newRetentionDays() : current.retentionDays(),
                clock);
        if (cmd.billingExternalId() != null) {
            current.setBillingExternalId(cmd.billingExternalId(), clock);
        }
        entitlementRepository.update(current);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("plan", current.plan().name());
        audit.publish("entitlement.change", cmd.tenantId(), current.tenantId().getValue(),
                metadata);

        outboxWriter.append(new OutboxEvent(
                cmd.tenantId(),
                "entitlement",
                cmd.tenantId().getValue(),
                EventPayloads.EVENT_TYPE_ENTITLEMENT_CHANGED,
                EventPayloads.SCHEMA_ENTITLEMENT_CHANGED,
                EventPayloads.encode(EventPayloads.entitlementChanged(
                        cmd.tenantId().getValue(),
                        current.plan().name(),
                        current.memberLimit(),
                        current.treeLimit(),
                        current.storageLimitMb(),
                        current.retentionDays(),
                        current.billingExternalId(),
                        actorId,
                        current.updatedAt())),
                OutboxCorrelationContext.correlationId(),
                OutboxCorrelationContext.traceId(),
                metadata));

        return toView(current);
    }

    private static Results.EntitlementView toView(Entitlement e) {
        return new Results.EntitlementView(
                e.tenantId(),
                e.plan(),
                e.memberLimit(),
                e.treeLimit(),
                e.storageLimitMb(),
                e.retentionDays(),
                e.billingExternalId(),
                e.updatedAt());
    }

    public static class EntitlementNotFoundException extends RuntimeException {
        public EntitlementNotFoundException(String message) {
            super(message);
        }
    }
}
