package com.genealogy.platform.services.research.grpc;

import com.genealogy.platform.research.v1.CreateHypothesisRequest;
import com.genealogy.platform.research.v1.GetHypothesisRequest;
import com.genealogy.platform.research.v1.Hypothesis;
import com.genealogy.platform.research.v1.HypothesisServiceGrpc;
import com.genealogy.platform.research.v1.TransitionHypothesisRequest;
import com.genealogy.platform.services.research.application.Commands;
import com.genealogy.platform.services.research.application.DraftDomainMapper;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.Results;
import com.genealogy.platform.services.research.domain.HypothesisStatus;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class ResearchHypothesisGrpcService
        extends HypothesisServiceGrpc.HypothesisServiceImplBase {

    private static void enforceTrustedContext(
            com.genealogy.platform.common.v1.Context context) {
        ResearchGrpcContextGuard.enforce(
                context.getTenantId(),
                context.getActorId(),
                context.getActorRole());
    }


    private final ResearchCommandService commandService;

    public ResearchHypothesisGrpcService(
            ResearchCommandService commandService) {
        this.commandService = Objects.requireNonNull(commandService, "commandService");
    }

    @Override
    public void createHypothesis(CreateHypothesisRequest request,
            StreamObserver<Hypothesis> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Commands.CreateHypothesis cmd = new Commands.CreateHypothesis(
                    request.getStatement(),
                    request.getSubjectReference(),
                    request.getSubjectKind(),
                    DraftDomainMapper.certaintyFromProto(request.getCertainty().name()),
                    request.getConfidence());
            Results.HypothesisView view = commandService.createHypothesis(cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (DraftDomainMapper.InvalidRequestException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getHypothesis(GetHypothesisRequest request,
            StreamObserver<Hypothesis> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Results.HypothesisView view = commandService.findHypothesis(request.getHypothesisId());
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.HypothesisNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void transitionHypothesis(TransitionHypothesisRequest request,
            StreamObserver<Hypothesis> observer) {
        enforceTrustedContext(request.getContext());
        try {
            HypothesisStatus next = DraftDomainMapper.hypothesisStatusFromProto(request.getToStatus().name());
            Results.HypothesisView view = commandService.transitionHypothesis(
                    request.getHypothesisId(), next);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.HypothesisNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (ResearchCommandService.InvalidTransitionException e) {
            observer.onError(Status.FAILED_PRECONDITION
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    private static Hypothesis toProto(Results.HypothesisView v) {
        Hypothesis.Builder b = Hypothesis.newBuilder()
                .setId(v.id())
                .setTenantId(v.tenantId())
                .setStatement(v.statement())
                .setSubjectReference(v.subjectReference())
                .setCertainty(com.genealogy.platform.research.v1.Certainty.valueOf(
                        certaintyToProto(v.certainty())))
                .setConfidence(v.confidence())
                .setStatus(com.genealogy.platform.research.v1.HypothesisStatus.valueOf(
                        hypothesisStatusToProto(v.status())))
                .setEtag(v.etag())
                .setVersion(v.version());
        if (v.subjectKind() != null) b.setSubjectKind(v.subjectKind());
        if (v.supersededByHypothesisId() != null) b.setSupersededByHypothesisId(v.supersededByHypothesisId());
        if (v.assignedTo() != null) b.setAssignedTo(v.assignedTo());
        if (v.createdAt() != null) b.setCreatedAt(v.createdAt().toString());
        if (v.updatedAt() != null) b.setUpdatedAt(v.updatedAt().toString());
        if (v.resolvedAt() != null) b.setResolvedAt(v.resolvedAt().toString());
        return b.build();
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

    private static String hypothesisStatusToProto(
            com.genealogy.platform.services.research.domain.HypothesisStatus s) {
        if (s == null) return "HYPOTHESIS_STATUS_UNSPECIFIED";
        return switch (s) {
            case DRAFT -> "HYPOTHESIS_PROPOSED";
            case ACTIVE -> "HYPOTHESIS_INVESTIGATING";
            case CORROBORATED -> "HYPOTHESIS_CORROBORATED";
            case REFUTED -> "HYPOTHESIS_REFUTED";
            case SUPERSEDED -> "HYPOTHESIS_SUPERSEDED";
        };
    }
}
