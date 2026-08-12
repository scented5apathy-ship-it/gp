package com.genealogy.platform.services.research.workspace;

import com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptor;
import com.genealogy.platform.services.research.outbox.ResearchJdbcOutboxWriter;
import com.genealogy.platform.services.research.events.ResearchEventPayloads;
import com.genealogy.platform.spring.audit.AuditEvent;
import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side projection service. Reacts to upstream
 * {@code TreeVisibilityChanged} + {@code PersonRedacted} events
 * and mutates the workspace projection so the editor UI never
 * reaches the redacted fields.
 *
 * <p>Public methods are {@link Transactional} and the first
 * statement is {@code rls.bind()} so the {@code UPDATE
 * workspace_projection} statement inherits the RLS posture
 * from {@link ResearchRlsTxInterceptor}.
 */
@Service
public class ResearchWorkspaceProjectionService {

    private final ResearchWorkspaceProjectionRepository repository;
    private final ResearchJdbcOutboxWriter outbox;
    private final ResearchRlsTxInterceptor rls;
    private final AuditPublisher audit;
    private final java.time.Clock clock;

    public ResearchWorkspaceProjectionService(
            ResearchWorkspaceProjectionRepository repository,
            ResearchJdbcOutboxWriter outbox,
            ResearchRlsTxInterceptor rls,
            AuditPublisher audit,
            java.time.Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.rls = Objects.requireNonNull(rls, "rls");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Re-projection workspace op: re-broadcast the new
     * visibility onto every projection row for the tree.
     * Called by the {@code TreeVisibilityChanged} consumer.
     */
    @Transactional
    public int rebroadcastVisibility(
            String tenantId,
            String treeId,
            ResearchWorkspaceProjection.Visibility visibility,
            String actorPseudoId,
            String correlationId) {
        rls.bind();
        Instant now = Instant.now(clock);
        int touched = 0;
        for (ResearchWorkspaceProjection row : repository.findByTree(tenantId, treeId)) {
            ResearchWorkspaceProjection next = row.withVisibility(visibility, now);
            repository.upsert(next);
            touched += 1;
        }
        publishAudit("workspace.visibilityRebroadcast",
                tenantId, treeId, actorPseudoId, correlationId,
                Map.of("visibility", visibility.name(), "touched", Integer.toString(touched)));
        return touched;
    }

    /**
     * Redaction overlay: flips {@code redacted=true} on every
     * projection row that references the subject. Called by the
     * {@code PersonRedacted} consumer.
     */
    @Transactional
    public int applyRedactionOverlay(
            String tenantId,
            String subjectReference,
            ResearchWorkspaceProjection.RedactionReason reason,
            String actorPseudoId,
            String correlationId) {
        rls.bind();
        Instant now = Instant.now(clock);
        int touched = repository.applyRedactionOverlay(tenantId, subjectReference, reason, now);
        publishAudit("workspace.redactionOverlay",
                tenantId, subjectReference, actorPseudoId, correlationId,
                Map.of("reason", reason.name(), "touched", Integer.toString(touched)));
        return touched;
    }

    /**
     * Records the first verification timestamp for a claim.
     * Called by the {@code ClaimVerified} consumer (or
     * directly by the REST surface in E6.1e).
     */
    @Transactional
    public void recordClaimVerified(
            String tenantId,
            String treeId,
            String claimReference,
            String subjectReference,
            String subjectKind,
            String actorPseudoId,
            String correlationId) {
        rls.bind();
        Instant now = Instant.now(clock);
        ResearchWorkspaceProjection existing = repository
                .find(tenantId, treeId, claimReference).orElse(null);
        ResearchWorkspaceProjection next;
        if (existing == null) {
            next = new ResearchWorkspaceProjection(
                    tenantId, treeId, claimReference, subjectReference, subjectKind,
                    ResearchWorkspaceProjection.Visibility.PRIVATE,
                    false, null, null, now, 1L, now, now);
        } else {
            next = existing.withClaimVerified(now);
        }
        repository.upsert(next);
        publishAudit("workspace.claimVerified",
                tenantId, claimReference, actorPseudoId, correlationId,
                Map.of("subjectReference", subjectReference));
    }

    /**
     * Writes the {@link ResearchEventPayloads#EVENT_CLAIM_VERIFIED}
     * event to the outbox from the same transaction the
     * projection row committed in. The caller (= the consumer
     * side of {@code CitationCreated}) supplies the verified
     * citation so the relay publishes exactly the contract
     * payload.
     */
    @Transactional
    public void enqueueClaimVerified(
            String tenantId,
            String claimReference,
            String verifyingCitationId,
            String correlationId) {
        rls.bind();
        TrustedTenantContext ctx = TrustedTenantContext.current();
        String actorPseudoId = ctx.getActorId() == null ? "anonymous" : ctx.getActorId();
        outbox.enqueue(
                claimReference,
                tenantId,
                ResearchEventPayloads.EVENT_CLAIM_VERIFIED,
                new ResearchEventPayloads.ClaimVerifiedEvent(
                        claimReference,
                        tenantId,
                        verifyingCitationId,
                        Instant.now(clock),
                        actorPseudoId,
                        correlationId),
                actorPseudoId,
                correlationId,
                ctx.getCorrelationId(),
                Instant.now(clock));
    }

    private void publishAudit(String action, String tenantId, String resourceId,
            String actorPseudoId, String correlationId, Map<String, String> metadata) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("actorPseudoId", actorPseudoId == null ? "anonymous" : actorPseudoId);
        if (correlationId != null) {
            meta.put("correlationId", correlationId);
        }
        meta.putAll(metadata);
        audit.publish(new AuditEvent(
                tenantId,
                actorPseudoId,
                action,
                "workspaceProjection",
                resourceId,
                correlationId,
                meta));
    }

    /**
     * @return opaque event id for the test path that asserts
     *     the outbox enqueue fires.
     */
    public String newEventId() {
        return UUID.randomUUID().toString();
    }
}
