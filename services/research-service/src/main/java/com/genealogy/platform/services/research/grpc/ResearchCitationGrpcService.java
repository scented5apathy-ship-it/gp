package com.genealogy.platform.services.research.grpc;

import com.genealogy.platform.research.v1.ApproveCitationRequest;
import com.genealogy.platform.research.v1.Citation;
import com.genealogy.platform.research.v1.CitationServiceGrpc;
import com.genealogy.platform.common.v1.Context;
import com.genealogy.platform.research.v1.CreateCitationRequest;
import com.genealogy.platform.research.v1.GetCitationRequest;
import com.genealogy.platform.research.v1.GetClaimProvenanceRequest;
import com.genealogy.platform.research.v1.ProvenanceChain;
import com.genealogy.platform.research.v1.SubmitCitationRequest;
import com.genealogy.platform.services.research.application.Commands;
import com.genealogy.platform.services.research.application.DraftDomainMapper;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.ResearchQueryService;
import com.genealogy.platform.services.research.application.Results;
import com.genealogy.platform.services.research.authorization.ResearchReAuthorizationPort;
import com.genealogy.platform.spring.context.TrustedTenantContext;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC adapter for the {@code CitationService} contract. The
 * {@link #submitCitation} + {@link #approveCitation} RPCs go
 * through the {@link ResearchReAuthorizationPort} so OpenFGA
 * + the ABAC overlay evaluate the call before the aggregate
 * mutation runs.
 */
@GrpcService
public class ResearchCitationGrpcService extends CitationServiceGrpc.CitationServiceImplBase {

    private static void enforceTrustedContext(
            com.genealogy.platform.common.v1.Context context) {
        ResearchGrpcContextGuard.enforce(
                context.getTenantId(),
                context.getActorId(),
                context.getActorRole());
    }


    private final ResearchCommandService commandService;
    private final ResearchQueryService queryService;
    private final ResearchReAuthorizationPort reAuthorization;

    public ResearchCitationGrpcService(
            ResearchCommandService commandService,
            ResearchQueryService queryService,
            ResearchReAuthorizationPort reAuthorization) {
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.reAuthorization = Objects.requireNonNull(reAuthorization, "reAuthorization");
    }

    @Override
    public void createCitation(CreateCitationRequest request,
            StreamObserver<Citation> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Commands.CreateCitation cmd = new Commands.CreateCitation(
                    null,
                    request.getClaimReference(),
                    request.getClaimKind(),
                    DraftDomainMapper.locator(
                            request.getLocatorRaw(),
                            request.getLocatorPage(),
                            request.getLocatorEntry(),
                            request.getLocatorVolume()),
                    DraftDomainMapper.citationQualityFromProto(request.getQuality().name()),
                    DraftDomainMapper.dispositionFromProto(request.getDisposition().name()),
                    DraftDomainMapper.certaintyFromProto(request.getCertainty().name()),
                    request.getConfidence(),
                    request.getQuotedText(),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of());
            Results.CitationView view = commandService.createCitation(cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (DraftDomainMapper.InvalidRequestException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getCitation(GetCitationRequest request,
            StreamObserver<Citation> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Results.CitationView view = commandService.findCitation(request.getCitationId());
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.CitationNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void submitCitation(SubmitCitationRequest request,
            StreamObserver<Citation> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Map<String, String> hints = new LinkedHashMap<>();
            hints.put("actorRole", request.getActorRole());
            reAuthorization.requireFromContext(
                    "citation",
                    request.getCitationId(),
                    ResearchReAuthorizationPort.Action.CITATION_SUBMIT,
                    hints);
            Results.CitationView view = commandService.findCitation(request.getCitationId());
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchReAuthorizationPort.ResearchReAuthorizationDeniedException e) {
            observer.onError(Status.PERMISSION_DENIED
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void approveCitation(ApproveCitationRequest request,
            StreamObserver<Citation> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Map<String, String> hints = new LinkedHashMap<>();
            hints.put("reviewerRole", request.getReviewerRole());
            hints.put("reasonCode", request.getReasonCode());
            reAuthorization.requireFromContext(
                    "citation",
                    request.getCitationId(),
                    ResearchReAuthorizationPort.Action.CITATION_APPROVE,
                    hints);
            Results.CitationView view = commandService.findCitation(request.getCitationId());
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchReAuthorizationPort.ResearchReAuthorizationDeniedException e) {
            observer.onError(Status.PERMISSION_DENIED
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getClaimProvenance(GetClaimProvenanceRequest request,
            StreamObserver<ProvenanceChain> observer) {
        enforceTrustedContext(request.getContext());
        Results.ProvenanceChainView view = queryService.traverseByClaim(
                request.getClaimReference());
        ProvenanceChain.Builder b = ProvenanceChain.newBuilder()
                .setTenantId(view.tenantId())
                .setClaimReference(view.claimReference());
        for (Results.ProvenanceHopView hop : view.hops()) {
            com.genealogy.platform.research.v1.ProvenanceHop.Builder hopBuilder =
                    com.genealogy.platform.research.v1.ProvenanceHop.newBuilder()
                            .setCitationId(hop.citationId())
                            .setSourceId(hop.sourceId())
                            .setSourceTitle(hop.sourceTitle())
                            .setRepositoryId(hop.repositoryId())
                            .setRepositoryName(hop.repositoryName())
                            .setQuality(com.genealogy.platform.research.v1.CitationQuality.valueOf(
                                    citationQualityToProto(hop.quality())))
                            .setDisposition(com.genealogy.platform.research.v1.CitationDisposition.valueOf(
                                    citationDispositionFromResultsToProto(hop.disposition())))
                            .setCertainty(com.genealogy.platform.research.v1.Certainty.valueOf(
                                    certaintyToProto(hop.certainty())))
                            .setConfidence(hop.confidence())
                            .setLocatorRaw(hop.locatorRaw())
                            .setQuotedText(hop.quotedText());
            if (hop.sourceKind() != null) {
                hopBuilder.setSourceKind(com.genealogy.platform.research.v1.SourceKind.valueOf(
                        sourceKindToProto(hop.sourceKind())));
            }
            b.addHops(hopBuilder.build());
        }
        observer.onNext(b.build());
        observer.onCompleted();
    }

    private static Citation toProto(Results.CitationView v) {
        return Citation.newBuilder()
                .setId(v.id())
                .setTenantId(v.tenantId())
                .setSourceId(v.sourceId())
                .setClaimReference(v.claimReference())
                .setClaimKind(v.claimKind() == null ? "" : v.claimKind())
                .setQuality(com.genealogy.platform.research.v1.CitationQuality.valueOf(
                        citationQualityToProto(v.quality())))
                .setDisposition(com.genealogy.platform.research.v1.CitationDisposition.valueOf(
                        citationDispositionFromResultsToProto(v.disposition())))
                .setCertainty(com.genealogy.platform.research.v1.Certainty.valueOf(
                        certaintyToProto(v.certainty())))
                .setConfidence(v.confidence())
                .setQuotedText(v.quotedText() == null ? "" : v.quotedText())
                .setEtag(v.etag())
                .setVersion(v.version())
                .build();
    }

    private static String citationQualityToProto(
            com.genealogy.platform.services.research.domain.CitationQuality q) {
        if (q == null) return "CITATION_QUALITY_UNSPECIFIED";
        return switch (q) {
            case ORIGINAL -> "CITATION_PRIMARY";
            case TRANSCRIPT -> "CITATION_AUTHORITATIVE";
            case ABSTRACT -> "CITATION_DERIVED";
            case IMAGE -> "CITATION_UNOFFICIAL";
            case COPY -> "CITATION_UNOFFICIAL";
            case UNKNOWN -> "CITATION_QUALITY_UNSPECIFIED";
        };
    }

    private static String citationDispositionToProto(
            com.genealogy.platform.services.research.domain.Citation.Disposition d) {
        if (d == null) return "CITATION_DISPOSITION_UNSPECIFIED";
        return switch (d) {
            case SUPPORTS -> "CITATION_SUPPORTING";
            case REFUTES -> "CITATION_CONTRADICTING";
            case MENTIONS -> "CITATION_MENTION";
            case UNCERTAIN -> "CITATION_DISPOSITION_UNSPECIFIED";
        };
    }

    private static String citationDispositionFromResultsToProto(
            Results.Disposition d) {
        if (d == null) return "CITATION_DISPOSITION_UNSPECIFIED";
        return switch (d) {
            case SUPPORTS -> "CITATION_SUPPORTING";
            case REFUTES -> "CITATION_CONTRADICTING";
            case MENTIONS -> "CITATION_MENTION";
            case UNCERTAIN -> "CITATION_DISPOSITION_UNSPECIFIED";
        };
    }

    private static String certaintyToProto(
            com.genealogy.platform.services.research.domain.Certainty c) {
        if (c == null) return "CERTAINTY_UNSPECIFIED";
        return switch (c) {
            case HYPOTHESIS -> "CERTAINTY_POSSIBLE";
            case ASSERTED -> "CERTAINTY_LIKELY";
            case VERIFIED -> "CERTAINTY_CERTAIN";
            case DISPUTED -> "CERTAINTY_UNSPECIFIED";
        };
    }

    private static String sourceKindToProto(
            com.genealogy.platform.services.research.domain.SourceKind k) {
        return DraftDomainMapper.sourceKindToProto(k);
    }

    private static String repositoryKindToProto(
            com.genealogy.platform.services.research.domain.RepositoryKind k) {
        return DraftDomainMapper.repositoryKindToProto(k);
    }

    @SuppressWarnings("unused")
    private static String safeTenant(String fallback) {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        return ctx.getTenantId() == null ? fallback : ctx.getTenantId();
    }
}
