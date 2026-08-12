package com.genealogy.platform.services.research.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.research.application.Commands;
import com.genealogy.platform.services.research.application.DraftDomainMapper;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.ResearchQueryService;
import com.genealogy.platform.services.research.application.Results;
import com.genealogy.platform.services.research.domain.AttachmentRef;
import com.genealogy.platform.services.research.domain.Locator;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import com.genealogy.platform.services.research.domain.TranscriptSegment;
import com.genealogy.platform.services.research.web.dto.ResearchDtos;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for the research-service aggregates. Mounted at
 * {@code /api/v1/} per the OpenAPI contract
 * ({@code contracts/openapi/public-api/v1/research.yaml}).
 *
 * <p>Honours every header documented in
 * {@code contracts/openapi/common/headers.yaml}:
 *
 * <ul>
 *   <li>{@code Idempotency-Key} — replayed via
 *       {@link IdempotencyCache}.</li>
 *   <li>{@code If-Match} — optimistic concurrency, returns
 *       {@code 412 Precondition Failed} on mismatch.</li>
 *   <li>{@code X-Correlation-Id} — echoed on every response
 *       and stamped on every audit event.</li>
 *   <li>{@code X-Tenant-Id} — populated by the
 *       {@code TrustedContextFilter} into
 *       {@link TrustedTenantContext}.</li>
 * </ul>
 *
 * <p>Cross-tenant attempts return {@code 404 Not Found} rather
 * than {@code 403 Forbidden} so the wire does not leak the
 * existence of a tenant the caller cannot reach.
 */
@RestController
@RequestMapping("/api/v1")
public class ResearchController {

    private final ResearchCommandService commandService;
    private final ResearchQueryService queryService;
    private final IdempotencyCache idempotencyCache;
    private final ObjectMapper objectMapper;

    public ResearchController(
            ResearchCommandService commandService,
            ResearchQueryService queryService,
            IdempotencyCache idempotencyCache,
            ObjectMapper objectMapper) {
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.idempotencyCache = Objects.requireNonNull(idempotencyCache, "idempotencyCache");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /* ---------------- Repository ---------------- */

    @PostMapping("/repositories")
    public ResponseEntity<ResearchDtos.RepositoryResponse> createRepository(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody ResearchDtos.CreateRepositoryRequest body,
            HttpServletRequest request) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.RepositoryResponse.class);
        }
        if (body == null || body.name() == null || body.kind() == null) {
            throw new IllegalArgumentException("name and kind are required");
        }
        Commands.CreateRepository cmd = new Commands.CreateRepository(
                body.name(),
                DraftDomainMapper.repositoryKind(body.kind()),
                body.locationLabel(),
                body.websiteUrl(),
                body.description(),
                body.privateHolding() != null && body.privateHolding());
        Results.RepositoryView view = commandService.createRepository(cmd);
        ResearchDtos.RepositoryResponse response = toRepositoryResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.CREATED, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/repositories/{id}")
    public ResponseEntity<ResearchDtos.RepositoryResponse> getRepository(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Results.RepositoryView view = commandService.findRepository(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRepositoryResponse(view));
    }

    /* ---------------- Source ---------------- */

    @PostMapping("/sources")
    public ResponseEntity<ResearchDtos.SourceResponse> createSource(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody ResearchDtos.CreateSourceRequest body,
            HttpServletRequest request) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.SourceResponse.class);
        }
        if (body == null || body.title() == null || body.sourceKind() == null
                || body.repositoryId() == null || body.locator() == null) {
            throw new IllegalArgumentException(
                    "title, sourceKind, repositoryId and locator.raw are required");
        }
        String tenantId = assertTrustedTenantId();
        Commands.CreateSource cmd = new Commands.CreateSource(
                TenantScopedId.of(tenantId, TenantScopedId.ResourceKind.REPOSITORY,
                        body.repositoryId()),
                body.title(),
                DraftDomainMapper.sourceKind(body.sourceKind()),
                body.author(),
                body.publisher(),
                body.publicationYear(),
                body.publisherLocation(),
                DraftDomainMapper.locator(body.locator().raw(), body.locator().page(),
                        body.locator().entry(), body.locator().volume()),
                mapAttachments(body.attachments()),
                body.description());
        Results.SourceView view = commandService.createSource(cmd);
        ResearchDtos.SourceResponse response = toSourceResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.CREATED, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/sources/{id}")
    public ResponseEntity<ResearchDtos.SourceResponse> getSource(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Results.SourceView view = commandService.findSource(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(toSourceResponse(view));
    }

    /* ---------------- Citation ---------------- */

    @PostMapping("/citations")
    public ResponseEntity<ResearchDtos.CitationResponse> createCitation(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody ResearchDtos.CreateCitationRequest body,
            HttpServletRequest request) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.CitationResponse.class);
        }
        if (body == null || body.claimReference() == null || body.sourceId() == null
                || body.quality() == null || body.disposition() == null
                || body.certainty() == null || body.locator() == null) {
            throw new IllegalArgumentException(
                    "claimReference, sourceId, quality, disposition, certainty and locator.raw"
                            + " are required");
        }
        String tenantId = assertTrustedTenantId();
        Commands.CreateCitation cmd = new Commands.CreateCitation(
                TenantScopedId.of(tenantId, TenantScopedId.ResourceKind.SOURCE,
                        body.sourceId()),
                body.claimReference(),
                body.claimKind(),
                DraftDomainMapper.locator(body.locator().raw(), body.locator().page(),
                        body.locator().entry(), body.locator().volume()),
                DraftDomainMapper.citationQuality(body.quality()),
                DraftDomainMapper.disposition(body.disposition()),
                DraftDomainMapper.certainty(body.certainty()),
                body.confidence(),
                body.quotedText(),
                mapTranscriptSegments(body.transcriptSegments()),
                mapAttachments(body.attachments()),
                body.externalUrls() == null ? new ArrayList<>() : new ArrayList<>(body.externalUrls()));
        Results.CitationView view = commandService.createCitation(cmd);
        ResearchDtos.CitationResponse response = toCitationResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.CREATED, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/citations/{id}")
    public ResponseEntity<ResearchDtos.CitationResponse> getCitation(
            @PathVariable("id") String id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Results.CitationView view = commandService.findCitation(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(toCitationResponse(view));
    }

    /* ---------------- Claims — provenance ---------------- */

    @GetMapping("/claims/{claimId}/provenance")
    public ResponseEntity<ResearchDtos.ProvenanceChainResponse> getClaimProvenance(
            @PathVariable("claimId") String claimId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("claimId must not be blank");
        }
        Results.ProvenanceChainView view = queryService.traverseByClaim(claimId);
        List<ResearchDtos.ProvenanceHopResponse> hops = new ArrayList<>();
        for (Results.ProvenanceHopView hop : view.hops()) {
            hops.add(new ResearchDtos.ProvenanceHopResponse(
                    hop.citationId(),
                    hop.sourceId(),
                    hop.sourceTitle(),
                    hop.sourceKind() == null ? null : hop.sourceKind().name(),
                    hop.repositoryId(),
                    hop.repositoryName(),
                    hop.repositoryKind() == null ? null : hop.repositoryKind().name(),
                    hop.quality() == null ? null : hop.quality().name(),
                    hop.disposition() == null ? null : hop.disposition().name(),
                    hop.certainty() == null ? null : hop.certainty().name(),
                    hop.confidence(),
                    hop.locatorRaw(),
                    hop.quotedText()));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResearchDtos.ProvenanceChainResponse(
                        view.tenantId(), view.claimReference(), hops));
    }

    /* ---------------- ResearchTask ---------------- */

    @PostMapping("/research-tasks")
    public ResponseEntity<ResearchDtos.ResearchTaskResponse> createResearchTask(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody ResearchDtos.CreateResearchTaskRequest body,
            HttpServletRequest request) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.ResearchTaskResponse.class);
        }
        if (body == null || body.title() == null || body.subjectReference() == null) {
            throw new IllegalArgumentException("title and subjectReference are required");
        }
        Commands.CreateResearchTask cmd = new Commands.CreateResearchTask(
                body.title(),
                body.description(),
                body.subjectReference(),
                body.subjectKind());
        Results.ResearchTaskView view = commandService.createResearchTask(cmd);
        ResearchDtos.ResearchTaskResponse response = toResearchTaskResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.CREATED, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @PostMapping("/research-tasks/{id}/transitions")
    public ResponseEntity<ResearchDtos.ResearchTaskResponse> transitionResearchTask(
            @PathVariable("id") String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ResearchDtos.TransitionResearchTaskRequest body) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.ResearchTaskResponse.class);
        }
        if (body == null || body.toStatus() == null) {
            throw new IllegalArgumentException("toStatus is required");
        }
        Commands.TransitionResearchTask cmd = new Commands.TransitionResearchTask(
                DraftDomainMapper.researchTaskStatus(body.toStatus()),
                body.blockedReason(),
                body.resolvedProof());
        Results.ResearchTaskView view = commandService.transitionResearchTask(id, cmd);
        ResearchDtos.ResearchTaskResponse response = toResearchTaskResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.OK, response, view.etag());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /* ---------------- Hypothesis ---------------- */

    @PostMapping("/hypotheses")
    public ResponseEntity<ResearchDtos.HypothesisResponse> createHypothesis(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody ResearchDtos.CreateHypothesisRequest body,
            HttpServletRequest request) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.HypothesisResponse.class);
        }
        if (body == null || body.statement() == null || body.subjectReference() == null
                || body.certainty() == null) {
            throw new IllegalArgumentException(
                    "statement, subjectReference and certainty are required");
        }
        Commands.CreateHypothesis cmd = new Commands.CreateHypothesis(
                body.statement(),
                body.subjectReference(),
                body.subjectKind(),
                DraftDomainMapper.certainty(body.certainty()),
                body.confidence());
        Results.HypothesisView view = commandService.createHypothesis(cmd);
        ResearchDtos.HypothesisResponse response = toHypothesisResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.CREATED, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @PostMapping("/hypotheses/{id}/transitions")
    public ResponseEntity<ResearchDtos.HypothesisResponse> transitionHypothesis(
            @PathVariable("id") String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ResearchDtos.TransitionHypothesisRequest body) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.HypothesisResponse.class);
        }
        if (body == null || body.toStatus() == null) {
            throw new IllegalArgumentException("toStatus is required");
        }
        com.genealogy.platform.services.research.domain.HypothesisStatus next =
                DraftDomainMapper.hypothesisStatus(body.toStatus());
        Results.HypothesisView view = commandService.transitionHypothesis(id, next);
        ResearchDtos.HypothesisResponse response = toHypothesisResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.OK, response, view.etag());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /* ---------------- Conflict ---------------- */

    @PostMapping("/conflicts")
    public ResponseEntity<ResearchDtos.ConflictResponse> createConflict(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody ResearchDtos.CreateConflictRequest body,
            HttpServletRequest request) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.ConflictResponse.class);
        }
        if (body == null || body.summary() == null || body.kind() == null
                || body.participants() == null || body.participants().size() < 2) {
            throw new IllegalArgumentException(
                    "summary, kind and at least two participants are required");
        }
        List<com.genealogy.platform.services.research.domain.Conflict.Participant> participants =
                new ArrayList<>();
        for (ResearchDtos.ParticipantDto dto : body.participants()) {
            participants.add(new com.genealogy.platform.services.research.domain.Conflict.Participant(
                    dto.reference(),
                    dto.referenceKind(),
                    dto.interpretation(),
                    new ArrayList<>()));
        }
        Commands.CreateConflict cmd = new Commands.CreateConflict(
                body.summary(),
                DraftDomainMapper.conflictKind(body.kind()),
                body.kindNote(),
                participants);
        Results.ConflictView view = commandService.createConflict(cmd);
        ResearchDtos.ConflictResponse response = toConflictResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.CREATED, response, view.etag());
        }
        URI location = URI.create(request.getRequestURI() + "/" + view.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @PostMapping("/conflicts/{id}/transitions")
    public ResponseEntity<ResearchDtos.ConflictResponse> transitionConflict(
            @PathVariable("id") String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ResearchDtos.TransitionConflictRequest body) {
        Optional<IdempotencyCache.CachedResponse> replay = idempotencyCache.get(idempotencyKey);
        if (replay.isPresent()) {
            return replay(replay.get(), ResearchDtos.ConflictResponse.class);
        }
        if (body == null || body.toStatus() == null) {
            throw new IllegalArgumentException("toStatus is required");
        }
        Commands.TransitionConflict cmd = new Commands.TransitionConflict(
                DraftDomainMapper.conflictStatus(body.toStatus()),
                body.resolution(),
                body.resolutionProof());
        Results.ConflictView view = commandService.transitionConflict(id, cmd);
        ResearchDtos.ConflictResponse response = toConflictResponse(view);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheResponse(idempotencyKey, HttpStatus.OK, response, view.etag());
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, view.etag())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /* ---------------- Common helpers ---------------- */

    private static String assertTrustedTenantId() {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        String tenantId = ctx.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResearchCommandService.RepositoryNotFoundException(
                    "trusted tenant context is missing");
        }
        return tenantId;
    }

    private static List<AttachmentRef> mapAttachments(List<ResearchDtos.AttachmentDto> body) {
        if (body == null) {
            return new ArrayList<>();
        }
        List<AttachmentRef> out = new ArrayList<>();
        for (ResearchDtos.AttachmentDto dto : body) {
            out.add(DraftDomainMapper.attachment(dto.kind(), dto.mediaObjectId(),
                    dto.canonicalUrl(), dto.caption(), dto.locale()));
        }
        return out;
    }

    private static List<TranscriptSegment> mapTranscriptSegments(
            List<ResearchDtos.TranscriptSegmentDto> body) {
        if (body == null) {
            return new ArrayList<>();
        }
        List<TranscriptSegment> out = new ArrayList<>();
        for (ResearchDtos.TranscriptSegmentDto dto : body) {
            out.add(DraftDomainMapper.transcriptSegment(dto.lineNumber(), dto.text(),
                    dto.originalScript(), dto.translationLocale(), dto.speaker()));
        }
        return out;
    }

    private static ResearchDtos.RepositoryResponse toRepositoryResponse(Results.RepositoryView v) {
        return new ResearchDtos.RepositoryResponse(
                v.id(), v.tenantId(), v.name(), v.kind().name(),
                v.locationLabel(), v.websiteUrl(), v.description(),
                v.privateHolding(),
                v.createdAt() == null ? null : v.createdAt().toString(),
                v.updatedAt() == null ? null : v.updatedAt().toString(),
                v.archivedAt() == null ? null : v.archivedAt().toString(),
                v.version(), v.etag(), v.metadata());
    }

    private static ResearchDtos.SourceResponse toSourceResponse(Results.SourceView v) {
        ResearchDtos.LocatorDto locator = v.locator() == null
                ? null
                : new ResearchDtos.LocatorDto(v.locator().raw(), v.locator().page(),
                        v.locator().entry(), v.locator().volume());
        List<ResearchDtos.AttachmentDto> attachments = new ArrayList<>();
        for (Results.AttachmentView a : v.attachments()) {
            attachments.add(new ResearchDtos.AttachmentDto(a.kind().name(), a.mediaObjectId(),
                    a.canonicalUrl(), a.caption(), a.locale()));
        }
        return new ResearchDtos.SourceResponse(
                v.id(), v.tenantId(), v.repositoryId(), v.title(),
                v.sourceKind().name(),
                v.author(), v.publisher(), v.publicationYear(), v.publisherLocation(),
                locator, attachments, v.description(),
                v.createdAt() == null ? null : v.createdAt().toString(),
                v.updatedAt() == null ? null : v.updatedAt().toString(),
                v.archivedAt() == null ? null : v.archivedAt().toString(),
                v.version(), v.etag());
    }

    private static ResearchDtos.CitationResponse toCitationResponse(Results.CitationView v) {
        ResearchDtos.LocatorDto locator = v.locator() == null
                ? null
                : new ResearchDtos.LocatorDto(v.locator().raw(), v.locator().page(),
                        v.locator().entry(), v.locator().volume());
        List<ResearchDtos.TranscriptSegmentDto> segments = new ArrayList<>();
        for (Results.TranscriptSegmentView s : v.transcriptSegments()) {
            segments.add(new ResearchDtos.TranscriptSegmentDto(s.lineNumber(), s.text(),
                    s.originalScript(), s.translationLocale(), s.speaker()));
        }
        List<ResearchDtos.AttachmentDto> attachments = new ArrayList<>();
        for (Results.AttachmentView a : v.attachments()) {
            attachments.add(new ResearchDtos.AttachmentDto(a.kind().name(), a.mediaObjectId(),
                    a.canonicalUrl(), a.caption(), a.locale()));
        }
        return new ResearchDtos.CitationResponse(
                v.id(), v.tenantId(), v.sourceId(), v.claimReference(), v.claimKind(),
                locator,
                v.quality().name(),
                v.disposition().name(),
                v.certainty().name(),
                v.confidence(),
                v.quotedText(),
                segments,
                attachments,
                v.externalUrls(),
                v.createdAt() == null ? null : v.createdAt().toString(),
                v.updatedAt() == null ? null : v.updatedAt().toString(),
                v.version(), v.etag());
    }

    private static ResearchDtos.ResearchTaskResponse toResearchTaskResponse(
            Results.ResearchTaskView v) {
        List<ResearchDtos.AssignmentResponse> assignments = new ArrayList<>();
        for (Results.AssignmentView a : v.assignments()) {
            assignments.add(new ResearchDtos.AssignmentResponse(
                    a.assigneePseudoId(), a.assigneeRole(),
                    a.assignedAt() == null ? null : a.assignedAt().toString(),
                    a.releasedAt() == null ? null : a.releasedAt().toString(),
                    a.note()));
        }
        return new ResearchDtos.ResearchTaskResponse(
                v.id(), v.tenantId(), v.title(), v.description(),
                v.subjectReference(), v.subjectKind(),
                v.status().name(),
                assignments,
                v.linkedCitationIds(),
                v.blockedReason(), v.resolvedProof(),
                v.createdAt() == null ? null : v.createdAt().toString(),
                v.updatedAt() == null ? null : v.updatedAt().toString(),
                v.resolvedAt() == null ? null : v.resolvedAt().toString(),
                v.version(), v.etag());
    }

    private static ResearchDtos.HypothesisResponse toHypothesisResponse(
            Results.HypothesisView v) {
        return new ResearchDtos.HypothesisResponse(
                v.id(), v.tenantId(), v.statement(), v.subjectReference(), v.subjectKind(),
                v.certainty().name(), v.confidence(), v.status().name(),
                v.corroboratingCitations(), v.refutingCitations(),
                v.supersededByHypothesisId(), v.assignedTo(),
                v.createdAt() == null ? null : v.createdAt().toString(),
                v.updatedAt() == null ? null : v.updatedAt().toString(),
                v.resolvedAt() == null ? null : v.resolvedAt().toString(),
                v.version(), v.etag());
    }

    private static ResearchDtos.ConflictResponse toConflictResponse(Results.ConflictView v) {
        List<ResearchDtos.ParticipantResponse> participants = new ArrayList<>();
        for (Results.ParticipantView p : v.participants()) {
            participants.add(new ResearchDtos.ParticipantResponse(
                    p.reference(), p.referenceKind(), p.interpretation(),
                    p.supportingCitations()));
        }
        return new ResearchDtos.ConflictResponse(
                v.id(), v.tenantId(), v.summary(), v.kind().name(), v.kindNote(),
                participants, v.linkedCitationIds(),
                v.status().name(),
                v.resolution(), v.resolutionProof(),
                v.createdAt() == null ? null : v.createdAt().toString(),
                v.updatedAt() == null ? null : v.updatedAt().toString(),
                v.resolvedAt() == null ? null : v.resolvedAt().toString(),
                v.version(), v.etag());
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> replay(IdempotencyCache.CachedResponse cached, Class<T> type) {
        try {
            T body = cached.body() == null || cached.body().isEmpty()
                    ? null
                    : objectMapper.readValue(cached.body(), type);
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(
                    HttpStatus.valueOf(cached.status()));
            if (cached.etag() != null) {
                builder.header(HttpHeaders.ETAG, cached.etag());
            }
            return builder
                    .header("X-Idempotent-Replay", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to replay cached response", e);
        }
    }

    private void cacheResponse(String key, HttpStatus status, Object body, String etag) {
        try {
            String json = body == null ? "" : objectMapper.writeValueAsString(body);
            idempotencyCache.store(key, new IdempotencyCache.CachedResponse(
                    status.value(),
                    MediaType.APPLICATION_JSON_VALUE,
                    json,
                    etag));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Idempotency cache is best-effort; a serialisation error
            // never fails the original request.
        }
    }

    @SuppressWarnings("unused")
    private static Map<String, String> emptyMetadata() {
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unused")
    private static URI buildLocation(String parent, String id) {
        return URI.create(parent + "/" + id);
    }
}
