package com.genealogy.platform.services.research.application;

import com.genealogy.platform.services.research.application.audit.ResearchAuditPublisher;
import com.genealogy.platform.services.research.application.persistence.CitationRepository;
import com.genealogy.platform.services.research.application.persistence.ConflictRepository;
import com.genealogy.platform.services.research.application.persistence.HypothesisRepository;
import com.genealogy.platform.services.research.application.persistence.RepositoryRepository;
import com.genealogy.platform.services.research.application.persistence.ResearchTaskRepository;
import com.genealogy.platform.services.research.application.persistence.SourceRepository;
import com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptor;
import com.genealogy.platform.services.research.domain.Citation;
import com.genealogy.platform.services.research.domain.Conflict;
import com.genealogy.platform.services.research.domain.Hypothesis;
import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
import com.genealogy.platform.services.research.domain.ResearchInvariants;
import com.genealogy.platform.services.research.domain.ResearchTask;
import com.genealogy.platform.services.research.domain.Repository;
import com.genealogy.platform.services.research.domain.Source;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import com.genealogy.platform.services.research.domain.ids.IdGenerator;
import com.genealogy.platform.services.research.domain.ids.TenantId;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Research aggregate command service. Every public method is
 * {@link Transactional} and the first statement is
 * {@code rls.bind()} so:
 *
 * <ol>
 *   <li>the aggregate, the optional bridge rows and the audit
 *       event commit together (per design.md §7.3 — outbox
 *       pattern);</li>
 *   <li>the {@code ResearchRlsTxInterceptor} binds the runtime
 *       role + {@code app.tenant_id} on the active JDBC
 *       connection so the RLS policy does not block legitimate
 *       reads.</li>
 * </ol>
 *
 * <p>Optimistic concurrency is enforced by the
 * {@code If-Match} header at the controller layer; the
 * repositories translate the {@code version} mismatch into
 * {@code RepositorySupport.OptimisticConcurrencyException}
 * which the REST surface (E6.1c) maps to {@code 412
 * Precondition Failed}.
 */
@Service
public class ResearchCommandService {

    private final RepositoryRepository repositoryRepository;
    private final SourceRepository sourceRepository;
    private final CitationRepository citationRepository;
    private final ResearchTaskRepository researchTaskRepository;
    private final HypothesisRepository hypothesisRepository;
    private final ConflictRepository conflictRepository;
    private final ResearchAuditPublisher audit;
    private final ResearchRlsTxInterceptor rls;
    private final IdGenerator idGenerator;
    private final java.time.Clock clock;

    public ResearchCommandService(
            RepositoryRepository repositoryRepository,
            SourceRepository sourceRepository,
            CitationRepository citationRepository,
            ResearchTaskRepository researchTaskRepository,
            HypothesisRepository hypothesisRepository,
            ConflictRepository conflictRepository,
            ResearchAuditPublisher audit,
            ResearchRlsTxInterceptor rls,
            IdGenerator idGenerator,
            java.time.Clock clock) {
        this.repositoryRepository =
                Objects.requireNonNull(repositoryRepository, "repositoryRepository");
        this.sourceRepository =
                Objects.requireNonNull(sourceRepository, "sourceRepository");
        this.citationRepository =
                Objects.requireNonNull(citationRepository, "citationRepository");
        this.researchTaskRepository =
                Objects.requireNonNull(researchTaskRepository, "researchTaskRepository");
        this.hypothesisRepository =
                Objects.requireNonNull(hypothesisRepository, "hypothesisRepository");
        this.conflictRepository =
                Objects.requireNonNull(conflictRepository, "conflictRepository");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.rls = Objects.requireNonNull(rls, "rls");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /* ---------------- Repository aggregate ---------------- */

    @Transactional
    public Results.RepositoryView createRepository(Commands.CreateRepository cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        ResearchAuditAttributes attrs = auditAttributes();
        Repository repository = null;
        try {
            repository = DraftDomainMapper.createRepository(
                    tenantId.getValue(), idGenerator.nextId(), cmd, attrs);
        } catch (DraftDomainMapper.InvalidRequestException e) {
            throw e;
        }
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(repository));
        repositoryRepository.insert(repository);
        audit.publish("repository.create", tenantId, "repository",
                repository.id().resourceId(), repository.version(),
                metadataFor(repository.name()));
        return toView(repository);
    }

    @Transactional
    public Results.RepositoryView findRepository(String id) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        Repository repository = repositoryRepository.findById(tenantId.getValue(), id)
                .orElseThrow(() -> new RepositoryNotFoundException(
                        "repository " + id + " not found"));
        return toView(repository);
    }

    @Transactional
    public Results.RepositoryView archiveRepository(String id, long expectedVersion) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        Repository repository = repositoryRepository.findById(tenantId.getValue(), id)
                .orElseThrow(() -> new RepositoryNotFoundException(
                        "repository " + id + " not found"));
        ensureVersion(repository.version(), expectedVersion);
        Repository archived = repository.archive(java.time.Instant.now(clock),
                auditAttributes());
        repositoryRepository.update(archived);
        audit.publish("repository.archive", tenantId, "repository",
                repository.id().resourceId(), archived.version(),
                metadataFor(repository.name()));
        return toView(archived);
    }

    /* ---------------- Source aggregate ---------------- */

    @Transactional
    public Results.SourceView createSource(Commands.CreateSource cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        ResearchAuditAttributes attrs = auditAttributes();
        Source source = DraftDomainMapper.createSource(
                tenantId.getValue(), idGenerator.nextId(), cmd, attrs);
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(source));
        sourceRepository.insert(source);
        audit.publish("source.create", tenantId, "source",
                source.id().resourceId(), source.version(),
                metadataFor(source.title()));
        return toView(source);
    }

    @Transactional
    public Results.SourceView findSource(String id) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        Source source = sourceRepository.findById(tenantId.getValue(), id)
                .orElseThrow(() -> new SourceNotFoundException(
                        "source " + id + " not found"));
        return toView(source);
    }

    /* ---------------- Citation aggregate ---------------- */

    @Transactional
    public Results.CitationView createCitation(Commands.CreateCitation cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        ResearchAuditAttributes attrs = auditAttributes();
        Citation citation = DraftDomainMapper.createCitation(
                tenantId.getValue(), idGenerator.nextId(), cmd, attrs);
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(citation));
        citationRepository.insert(citation);
        audit.publish("citation.create", tenantId, "citation",
                citation.id().resourceId(), citation.version(),
                metadataFor(citation.claimReference()));
        return toView(citation);
    }

    @Transactional
    public Results.CitationView findCitation(String id) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        Citation citation = citationRepository.findById(tenantId.getValue(), id)
                .orElseThrow(() -> new CitationNotFoundException(
                        "citation " + id + " not found"));
        return toView(citation);
    }

    /* ---------------- ResearchTask aggregate ---------------- */

    @Transactional
    public Results.ResearchTaskView createResearchTask(Commands.CreateResearchTask cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        ResearchAuditAttributes attrs = auditAttributes();
        ResearchTask task = DraftDomainMapper.createResearchTask(
                tenantId.getValue(), idGenerator.nextId(), cmd, attrs);
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(task));
        researchTaskRepository.insert(task);
        audit.publish("researchTask.create", tenantId, "researchTask",
                task.id().resourceId(), task.version(),
                metadataFor(task.title()));
        return toView(task);
    }

    @Transactional
    public Results.ResearchTaskView transitionResearchTask(
            String id, Commands.TransitionResearchTask cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        ResearchTask task = researchTaskRepository.findById(tenantId.getValue(), id)
                .orElseThrow(() -> new ResearchTaskNotFoundException(
                        "researchTask " + id + " not found"));
        ResearchTask next;
        try {
            next = DraftDomainMapper.transitionResearchTask(task, cmd);
        } catch (IllegalStateException e) {
            throw new InvalidTransitionException(e.getMessage());
        }
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(next));
        researchTaskRepository.update(next);
        audit.publish("researchTask.transition", tenantId, "researchTask",
                task.id().resourceId(), next.version(),
                metadataFor(task.title(), "toStatus", cmd.next().name()));
        return toView(next);
    }

    /* ---------------- Hypothesis aggregate ---------------- */

    @Transactional
    public Results.HypothesisView createHypothesis(Commands.CreateHypothesis cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        ResearchAuditAttributes attrs = auditAttributes();
        Hypothesis hypothesis = DraftDomainMapper.createHypothesis(
                tenantId.getValue(), idGenerator.nextId(), cmd, attrs);
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(hypothesis));
        hypothesisRepository.insert(hypothesis);
        audit.publish("hypothesis.create", tenantId, "hypothesis",
                hypothesis.id().resourceId(), hypothesis.version(),
                metadataFor(hypothesis.statement()));
        return toView(hypothesis);
    }

    @Transactional
    public Results.HypothesisView transitionHypothesis(String id,
            com.genealogy.platform.services.research.domain.HypothesisStatus next) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        Hypothesis hypothesis = hypothesisRepository.findById(tenantId.getValue(), id)
                .orElseThrow(() -> new HypothesisNotFoundException(
                        "hypothesis " + id + " not found"));
        Hypothesis updated;
        try {
            updated = DraftDomainMapper.transitionHypothesis(hypothesis, next);
        } catch (IllegalStateException e) {
            throw new InvalidTransitionException(e.getMessage());
        }
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(updated));
        hypothesisRepository.update(updated);
        audit.publish("hypothesis.transition", tenantId, "hypothesis",
                hypothesis.id().resourceId(), updated.version(),
                metadataFor(hypothesis.statement(), "toStatus", next.name()));
        return toView(updated);
    }

    /* ---------------- Conflict aggregate ---------------- */

    @Transactional
    public Results.ConflictView createConflict(Commands.CreateConflict cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        ResearchAuditAttributes attrs = auditAttributes();
        Conflict conflict = DraftDomainMapper.createConflict(
                tenantId.getValue(), idGenerator.nextId(), cmd, attrs);
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(conflict));
        conflictRepository.insert(conflict);
        audit.publish("conflict.create", tenantId, "conflict",
                conflict.id().resourceId(), conflict.version(),
                metadataFor(conflict.summary()));
        return toView(conflict);
    }

    @Transactional
    public Results.ConflictView transitionConflict(String id, Commands.TransitionConflict cmd) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        Conflict conflict = conflictRepository.findById(tenantId.getValue(), id)
                .orElseThrow(() -> new ConflictNotFoundException(
                        "conflict " + id + " not found"));
        Conflict.ConflictStatus next = cmd.next();
        if (next == Conflict.ConflictStatus.RESOLVED) {
            if (cmd.resolutionProof() == null || cmd.resolutionProof().isBlank()) {
                throw new InvalidTransitionException(
                        "conflict.status=RESOLVED requires a non-blank resolutionProof");
            }
        }
        Conflict updated = new Conflict(
                conflict.id(),
                conflict.summary(),
                conflict.kind(),
                conflict.kindNote(),
                conflict.participants(),
                conflict.linkedCitationIds(),
                next,
                cmd.resolution(),
                cmd.resolutionProof(),
                conflict.createdAt(),
                java.time.Instant.now(clock),
                next.isTerminal() ? java.time.Instant.now(clock) : null,
                conflict.version() + 1,
                conflict.audit());
        DraftDomainMapper.assertNoDeny(ResearchInvariants.check(updated));
        conflictRepository.update(updated);
        audit.publish("conflict.transition", tenantId, "conflict",
                conflict.id().resourceId(), updated.version(),
                metadataFor(conflict.summary(), "toStatus", next.name()));
        return toView(updated);
    }

    /* ---------------- Helpers ---------------- */

    private TenantId currentTenantId() {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        String tenantId = ctx.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResearchRlsTxInterceptor.MissingTenantContextException(
                    "trusted tenant context is required");
        }
        return new TenantId(tenantId);
    }

    private ResearchAuditAttributes auditAttributes() {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        String actorId = ctx.getActorId() == null ? "anonymous" : ctx.getActorId();
        String correlationId = ctx.getCorrelationId() == null
                ? "n/a" : ctx.getCorrelationId();
        return ResearchAuditAttributes.of(actorId, correlationId);
    }

    private static Map<String, String> metadataFor(String... values) {
        Map<String, String> meta = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            meta.put(values[i], values[i + 1]);
        }
        return meta;
    }

    private static void ensureVersion(long current, long expected) {
        if (current != expected) {
            throw new OptimisticConcurrencyException(
                    "expected version " + expected + " but aggregate is at " + current);
        }
    }

    private static Results.RepositoryView toView(Repository repository) {
        return new Results.RepositoryView(
                repository.id().resourceId(),
                repository.id().tenantId(),
                repository.name(),
                repository.kind(),
                repository.locationLabel(),
                repository.websiteUrl(),
                repository.description(),
                repository.privateHolding(),
                repository.createdAt(),
                repository.updatedAt(),
                repository.archivedAt(),
                repository.version(),
                RepositoryRepository.etagFor(repository.version()),
                repository.metadata());
    }

    private static Results.SourceView toView(Source source) {
        return new Results.SourceView(
                source.id().resourceId(),
                source.id().tenantId(),
                source.repositoryId().resourceId(),
                source.title(),
                source.sourceKind(),
                source.author(),
                source.publisher(),
                source.publicationYear(),
                source.publisherLocation(),
                source.locator() == null
                        ? null
                        : new Results.LocatorView(
                                source.locator().raw(),
                                source.locator().page(),
                                source.locator().entry(),
                                source.locator().volume()),
                source.attachments().stream()
                        .map(ResearchCommandService::toAttachmentView)
                        .toList(),
                source.description(),
                source.createdAt(),
                source.updatedAt(),
                source.archivedAt(),
                source.version(),
                SourceRepository.etagFor(source.version()));
    }

    private static Results.CitationView toView(Citation citation) {
        return new Results.CitationView(
                citation.id().resourceId(),
                citation.id().tenantId(),
                citation.sourceId().resourceId(),
                citation.claimReference(),
                citation.claimKind(),
                citation.locator() == null
                        ? null
                        : new Results.LocatorView(
                                citation.locator().raw(),
                                citation.locator().page(),
                                citation.locator().entry(),
                                citation.locator().volume()),
                citation.quality(),
                toWireDisposition(citation.disposition()),
                citation.certainty(),
                citation.confidence(),
                citation.quotedText(),
                citation.transcriptSegments().stream()
                        .map(ResearchCommandService::toTranscriptSegmentView)
                        .toList(),
                citation.attachments().stream()
                        .map(ResearchCommandService::toAttachmentView)
                        .toList(),
                citation.externalUrls(),
                citation.createdAt(),
                citation.updatedAt(),
                citation.version(),
                CitationRepository.etagFor(citation.version()));
    }

    private static Results.ResearchTaskView toView(ResearchTask task) {
        return new Results.ResearchTaskView(
                task.id().resourceId(),
                task.id().tenantId(),
                task.title(),
                task.description(),
                task.subjectReference(),
                task.subjectKind(),
                task.status(),
                task.assignments().stream()
                        .map(ResearchCommandService::toAssignmentView)
                        .toList(),
                task.linkedCitationIds().stream()
                        .map(TenantScopedId::resourceId)
                        .toList(),
                task.blockedReason(),
                task.resolvedProof(),
                task.createdAt(),
                task.updatedAt(),
                task.resolvedAt(),
                task.version(),
                ResearchTaskRepository.etagFor(task.version()));
    }

    private static Results.HypothesisView toView(Hypothesis hypothesis) {
        return new Results.HypothesisView(
                hypothesis.id().resourceId(),
                hypothesis.id().tenantId(),
                hypothesis.statement(),
                hypothesis.subjectReference(),
                hypothesis.subjectKind(),
                hypothesis.certainty(),
                hypothesis.confidence(),
                hypothesis.status(),
                hypothesis.corroboratingCitations().stream()
                        .map(TenantScopedId::resourceId)
                        .toList(),
                hypothesis.refutingCitations().stream()
                        .map(TenantScopedId::resourceId)
                        .toList(),
                hypothesis.supersededByHypothesisId(),
                hypothesis.assignedTo(),
                hypothesis.createdAt(),
                hypothesis.updatedAt(),
                hypothesis.resolvedAt(),
                hypothesis.version(),
                HypothesisRepository.etagFor(hypothesis.version()));
    }

    private static Results.ConflictView toView(Conflict conflict) {
        return new Results.ConflictView(
                conflict.id().resourceId(),
                conflict.id().tenantId(),
                conflict.summary(),
                conflict.kind(),
                conflict.kindNote(),
                conflict.participants().stream()
                        .map(ResearchCommandService::toParticipantView)
                        .toList(),
                conflict.linkedCitationIds().stream()
                        .map(TenantScopedId::resourceId)
                        .toList(),
                conflict.status(),
                conflict.resolution(),
                conflict.resolutionProof(),
                conflict.createdAt(),
                conflict.updatedAt(),
                conflict.resolvedAt(),
                conflict.version(),
                ConflictRepository.etagFor(conflict.version()));
    }

    private static Results.Disposition toWireDisposition(Citation.Disposition d) {
        return Results.Disposition.valueOf(d.name());
    }

    private static Results.AttachmentView toAttachmentView(
            com.genealogy.platform.services.research.domain.AttachmentRef a) {
        return new Results.AttachmentView(a.kind(), a.mediaObjectId(),
                a.canonicalUrl(), a.caption(), a.locale());
    }

    private static Results.TranscriptSegmentView toTranscriptSegmentView(
            com.genealogy.platform.services.research.domain.TranscriptSegment s) {
        return new Results.TranscriptSegmentView(s.lineNumber(), s.text(),
                s.originalScript(), s.translationLocale(), s.speaker());
    }

    private static Results.AssignmentView toAssignmentView(
            com.genealogy.platform.services.research.domain.ResearchTask.Assignment a) {
        return new Results.AssignmentView(a.assigneePseudoId(), a.assigneeRole(),
                a.assignedAt(), a.releasedAt(), a.note());
    }

    private static Results.ParticipantView toParticipantView(
            com.genealogy.platform.services.research.domain.Conflict.Participant p) {
        return new Results.ParticipantView(p.reference(), p.referenceKind(),
                p.interpretation(),
                p.supportingCitations().stream()
                        .map(TenantScopedId::resourceId)
                        .toList());
    }

    /* ---------------- Domain exceptions ---------------- */

    public static class RepositoryNotFoundException extends RuntimeException {
        public RepositoryNotFoundException(String message) {
            super(message);
        }
    }

    public static class SourceNotFoundException extends RuntimeException {
        public SourceNotFoundException(String message) {
            super(message);
        }
    }

    public static class CitationNotFoundException extends RuntimeException {
        public CitationNotFoundException(String message) {
            super(message);
        }
    }

    public static class ResearchTaskNotFoundException extends RuntimeException {
        public ResearchTaskNotFoundException(String message) {
            super(message);
        }
    }

    public static class HypothesisNotFoundException extends RuntimeException {
        public HypothesisNotFoundException(String message) {
            super(message);
        }
    }

    public static class ConflictNotFoundException extends RuntimeException {
        public ConflictNotFoundException(String message) {
            super(message);
        }
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }

    public static class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String message) {
            super(message);
        }
    }
}
