package com.genealogy.platform.services.research.grpc;

import com.genealogy.platform.research.v1.ConflictServiceGrpc;
import com.genealogy.platform.research.v1.CreateConflictRequest;
import com.genealogy.platform.research.v1.GetConflictRequest;
import com.genealogy.platform.research.v1.PartialMergeConflictRequest;
import com.genealogy.platform.research.v1.TransitionConflictRequest;
import com.genealogy.platform.services.research.application.Commands;
import com.genealogy.platform.services.research.application.DraftDomainMapper;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.Results;
import com.genealogy.platform.services.research.authorization.ResearchReAuthorizationPort;
import com.genealogy.platform.services.research.domain.Conflict;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class ResearchConflictGrpcService
        extends ConflictServiceGrpc.ConflictServiceImplBase {

    private static void enforceTrustedContext(
            com.genealogy.platform.common.v1.Context context) {
        ResearchGrpcContextGuard.enforce(
                context.getTenantId(),
                context.getActorId(),
                context.getActorRole());
    }


    private final ResearchCommandService commandService;
    private final ResearchReAuthorizationPort reAuthorization;

    public ResearchConflictGrpcService(
            ResearchCommandService commandService,
            ResearchReAuthorizationPort reAuthorization) {
        this.commandService = Objects.requireNonNull(commandService, "commandService");
        this.reAuthorization = Objects.requireNonNull(reAuthorization, "reAuthorization");
    }

    @Override
    public void createConflict(CreateConflictRequest request,
            StreamObserver<com.genealogy.platform.research.v1.Conflict> observer) {
        enforceTrustedContext(request.getContext());
        try {
            java.util.List<com.genealogy.platform.services.research.domain.Conflict.Participant> participants =
                    new ArrayList<>();
            for (com.genealogy.platform.research.v1.ConflictParticipant p
                    : request.getParticipantsList()) {
                participants.add(new com.genealogy.platform.services.research.domain.Conflict.Participant(
                        p.getReference(),
                        p.getReferenceKind(),
                        p.getInterpretation(),
                        new ArrayList<>()));
            }
            Commands.CreateConflict cmd = new Commands.CreateConflict(
                    request.getSummary(),
                    DraftDomainMapper.conflictKindFromProto(request.getKind().name()),
                    request.getKindNote(),
                    participants);
            Results.ConflictView view = commandService.createConflict(cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (DraftDomainMapper.InvalidRequestException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getConflict(GetConflictRequest request,
            StreamObserver<com.genealogy.platform.research.v1.Conflict> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Results.ConflictView view = commandService.findConflict(request.getConflictId());
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.ConflictNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void transitionConflict(TransitionConflictRequest request,
            StreamObserver<com.genealogy.platform.research.v1.Conflict> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Conflict.ConflictStatus next = DraftDomainMapper.conflictStatusFromProto(request.getToStatus().name());
            Commands.TransitionConflict cmd = new Commands.TransitionConflict(
                    next,
                    request.getResolution(),
                    request.getResolutionProof());
            Results.ConflictView view = commandService.transitionConflict(
                    request.getConflictId(), cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.ConflictNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (ResearchCommandService.InvalidTransitionException e) {
            observer.onError(Status.FAILED_PRECONDITION
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void partialMergeConflict(PartialMergeConflictRequest request,
            StreamObserver<com.genealogy.platform.research.v1.Conflict> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Map<String, String> hints = new LinkedHashMap<>();
            hints.put("approvingCitationCount",
                    Integer.toString(request.getApprovingCitationIdsCount()));
            reAuthorization.requireFromContext(
                    "conflict",
                    request.getConflictId(),
                    ResearchReAuthorizationPort.Action.CONFLICT_PARTIAL_MERGE,
                    hints);
            Conflict.ConflictStatus resolved = Conflict.ConflictStatus.RESOLVED;
            Commands.TransitionConflict cmd = new Commands.TransitionConflict(
                    resolved,
                    request.getResolution(),
                    request.getResolutionProof());
            Results.ConflictView view = commandService.transitionConflict(
                    request.getConflictId(), cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchReAuthorizationPort.ResearchReAuthorizationDeniedException e) {
            observer.onError(Status.PERMISSION_DENIED
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (ResearchCommandService.ConflictNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (ResearchCommandService.InvalidTransitionException e) {
            observer.onError(Status.FAILED_PRECONDITION
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    private static com.genealogy.platform.research.v1.Conflict toProto(
            Results.ConflictView v) {
        com.genealogy.platform.research.v1.Conflict.Builder b = com.genealogy.platform.research.v1.Conflict.newBuilder()
                .setId(v.id())
                .setTenantId(v.tenantId())
                .setSummary(v.summary())
                .setKind(com.genealogy.platform.research.v1.ConflictKind.valueOf(
                        conflictKindToProto(v.kind())))
                .setStatus(com.genealogy.platform.research.v1.ConflictStatus.valueOf(
                        conflictStatusToProto(v.status())))
                .setEtag(v.etag())
                .setVersion(v.version());
        if (v.kindNote() != null) b.setKindNote(v.kindNote());
        if (v.resolution() != null) b.setResolution(v.resolution());
        if (v.resolutionProof() != null) b.setResolutionProof(v.resolutionProof());
        if (v.createdAt() != null) b.setCreatedAt(v.createdAt().toString());
        if (v.updatedAt() != null) b.setUpdatedAt(v.updatedAt().toString());
        if (v.resolvedAt() != null) b.setResolvedAt(v.resolvedAt().toString());
        return b.build();
    }

    private static String conflictKindToProto(
            com.genealogy.platform.services.research.domain.ConflictKind k) {
        if (k == null) return "CONFLICT_KIND_UNSPECIFIED";
        return switch (k) {
            case SOURCE_DISAGREES -> "CONFLICT_FACTUAL";
            case CITATION_DISAGREES -> "CONFLICT_FACTUAL";
            case CLAIM_CONTRADICTS_SOURCE -> "CONFLICT_SOURCE_CONFLICT";
            case HYPOTHESIS_COLLIDES -> "CONFLICT_INTERPRETATION";
            case OTHER -> "CONFLICT_IDENTIFICATION";
        };
    }

    private static String conflictStatusToProto(
            com.genealogy.platform.services.research.domain.Conflict.ConflictStatus s) {
        if (s == null) return "CONFLICT_STATUS_UNSPECIFIED";
        return switch (s) {
            case OPEN -> "CONFLICT_DETECTED";
            case INVESTIGATING -> "CONFLICT_IN_REVIEW";
            case RESOLVED -> "CONFLICT_RESOLVED";
            case ABANDONED -> "CONFLICT_REJECTED";
        };
    }
}
