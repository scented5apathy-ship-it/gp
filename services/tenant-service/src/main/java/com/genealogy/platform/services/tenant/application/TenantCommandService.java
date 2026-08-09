package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.application.audit.TenantAuditPublisher;
import com.genealogy.platform.services.tenant.application.outbox.EventPayloads;
import com.genealogy.platform.services.tenant.application.outbox.OutboxEvent;
import com.genealogy.platform.services.tenant.application.outbox.OutboxWriter;
import com.genealogy.platform.services.tenant.application.persistence.EntitlementRepository;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.entitlement.Entitlement;
import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.Tenant;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.spring.context.OutboxCorrelationContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant aggregate command service.
 *
 * <p>Every public method is {@link Transactional} and the first
 * statement is {@code rls.bind()} so:
 *
 * <ol>
 *   <li>the aggregate, the default entitlement and the outbox row
 *       commit together (per design.md §7.3 — outbox pattern);</li>
 *   <li>the {@code TenantRlsTxInterceptor} binds the runtime role +
 *       {@code app.tenant_id} on the active JDBC connection so the
 *       RLS policy does not block legitimate reads.</li>
 * </ol>
 *
 * <p>Optimistic concurrency is enforced by comparing
 * {@code expectedVersion} against {@link Tenant#version()}; a
 * mismatch raises {@link OptimisticConcurrencyException} which the
 * REST layer (E3.2d) maps to {@code 412 Precondition Failed}.
 */
@Service
public class TenantCommandService {

    private final TenantRepository tenantRepository;
    private final EntitlementRepository entitlementRepository;
    private final OutboxWriter outboxWriter;
    private final IdGenerator idGenerator;
    private final TenantAuditPublisher audit;
    private final TenantRlsTxInterceptor rls;
    private final java.time.Clock clock;

    public TenantCommandService(
            TenantRepository tenantRepository,
            EntitlementRepository entitlementRepository,
            OutboxWriter outboxWriter,
            IdGenerator idGenerator,
            TenantAuditPublisher audit,
            TenantRlsTxInterceptor rls,
            java.time.Clock clock) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.entitlementRepository =
                Objects.requireNonNull(entitlementRepository, "entitlementRepository");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.rls = Objects.requireNonNull(rls, "rls");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Results.TenantView create(Commands.CreateTenant cmd, String actorId) {
        rls.bind();
        Tenant tenant = Tenant.create(idGenerator, cmd.slug(), cmd.displayName(),
                cmd.plan() == null ? TenantPlan.FREE : cmd.plan(),
                cmd.locale(), cmd.timezone(), cmd.calendar(), clock);
        tenantRepository.insert(tenant);

        Entitlement entitlement = Entitlement.defaultFor(tenant.id(), clock);
        entitlementRepository.insert(entitlement);

        outboxWriter.append(buildOutboxEvent(tenant, actorId));

        audit.publish("tenant.create", tenant.id(), tenant.slug().value(),
                Map.of("version", Long.toString(tenant.version())));

        return toView(tenant);
    }

    @Transactional
    public Results.TenantView update(Commands.UpdateTenant cmd) {
        rls.bind();
        Tenant tenant = loadOrThrow(cmd.tenantId());
        ensureVersion(tenant, cmd.expectedVersion());
        tenant.rename(cmd.displayName(), clock);
        tenantRepository.update(tenant);
        audit.publish("tenant.update", tenant.id(), tenant.slug().value(),
                Map.of("version", Long.toString(tenant.version())));
        return toView(tenant);
    }

    @Transactional
    public Results.TenantView changePlan(Commands.ChangePlan cmd) {
        rls.bind();
        Tenant tenant = loadOrThrow(cmd.tenantId());
        ensureVersion(tenant, cmd.expectedVersion());
        tenant.changePlan(cmd.newPlan(), clock);
        tenantRepository.update(tenant);
        audit.publish("tenant.change_plan", tenant.id(), tenant.slug().value(),
                Map.of("plan", cmd.newPlan().name(), "version",
                        Long.toString(tenant.version())));
        return toView(tenant);
    }

    @Transactional
    public Results.TenantView suspend(Commands.SuspendTenant cmd) {
        rls.bind();
        Tenant tenant = loadOrThrow(cmd.tenantId());
        ensureVersion(tenant, cmd.expectedVersion());
        tenant.suspend(clock);
        tenantRepository.update(tenant);
        audit.publish("tenant.suspend", tenant.id(), tenant.slug().value(),
                Map.of("version", Long.toString(tenant.version())));
        return toView(tenant);
    }

    @Transactional
    public Results.TenantView restore(Commands.RestoreTenant cmd) {
        rls.bind();
        Tenant tenant = loadOrThrow(cmd.tenantId());
        ensureVersion(tenant, cmd.expectedVersion());
        tenant.restore(clock);
        tenantRepository.update(tenant);
        audit.publish("tenant.restore", tenant.id(), tenant.slug().value(),
                Map.of("version", Long.toString(tenant.version())));
        return toView(tenant);
    }

    @Transactional
    public Results.TenantView softDelete(Commands.SoftDeleteTenant cmd) {
        rls.bind();
        Tenant tenant = loadOrThrow(cmd.tenantId());
        ensureVersion(tenant, cmd.expectedVersion());
        tenant.softDelete(clock);
        tenantRepository.update(tenant);
        audit.publish("tenant.soft_delete", tenant.id(), tenant.slug().value(),
                Map.of("version", Long.toString(tenant.version())));
        return toView(tenant);
    }

    private Tenant loadOrThrow(TenantId id) {
        return tenantRepository.findById(id).orElseThrow(
                () -> new TenantNotFoundException("tenant " + id + " not found"));
    }

    private void ensureVersion(Tenant tenant, long expectedVersion) {
        if (tenant.version() != expectedVersion) {
            throw new OptimisticConcurrencyException(
                    "expected version " + expectedVersion + " but tenant is at "
                            + tenant.version());
        }
    }

    private OutboxEvent buildOutboxEvent(Tenant tenant, String actorId) {
        Map<String, Object> payload = EventPayloads.tenantCreated(
                tenant.id().getValue(),
                tenant.slug().value(),
                tenant.displayName().value(),
                tenant.plan().name(),
                tenant.locale() == null ? null : tenant.locale().tag(),
                tenant.timezone() == null ? null : tenant.timezone().id(),
                tenant.calendar() == null ? null : tenant.calendar().name(),
                actorId,
                tenant.createdAt());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("etag", TenantRepository.etagFor(tenant.version()));
        return new OutboxEvent(
                tenant.id(),
                "tenant",
                tenant.id().getValue(),
                EventPayloads.EVENT_TYPE_TENANT_CREATED,
                EventPayloads.SCHEMA_TENANT_CREATED,
                EventPayloads.encode(payload),
                OutboxCorrelationContext.correlationId(),
                OutboxCorrelationContext.traceId(),
                metadata);
    }

    private static Results.TenantView toView(Tenant t) {
        return new Results.TenantView(
                t.id(),
                t.slug(),
                t.displayName(),
                t.plan(),
                t.status().name(),
                t.locale(),
                t.timezone(),
                t.calendar(),
                t.version(),
                TenantRepository.etagFor(t.version()),
                t.createdAt(),
                t.updatedAt(),
                t.suspendedAt(),
                t.deletedAt());
    }

    /** Slug uniqueness exception — mapped to {@code 409 Conflict} by the REST layer. */
    public static class SlugAlreadyExistsException extends RuntimeException {
        public SlugAlreadyExistsException(Slug slug) {
            super("slug already exists: " + slug.value());
        }
    }

    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }
}
