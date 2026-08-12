package com.genealogy.platform.services.research.grpc;

import com.genealogy.platform.research.v1.CreateResearchTaskRequest;
import com.genealogy.platform.research.v1.GetResearchTaskRequest;
import com.genealogy.platform.research.v1.ResearchTask;
import com.genealogy.platform.research.v1.ResearchTaskServiceGrpc;
import com.genealogy.platform.research.v1.TransitionResearchTaskRequest;
import com.genealogy.platform.services.research.application.Commands;
import com.genealogy.platform.services.research.application.DraftDomainMapper;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.Results;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class ResearchTaskGrpcService extends ResearchTaskServiceGrpc.ResearchTaskServiceImplBase {

    private static void enforceTrustedContext(
            com.genealogy.platform.common.v1.Context context) {
        ResearchGrpcContextGuard.enforce(
                context.getTenantId(),
                context.getActorId(),
                context.getActorRole());
    }


    private final ResearchCommandService commandService;

    public ResearchTaskGrpcService(
            ResearchCommandService commandService) {
        this.commandService = Objects.requireNonNull(commandService, "commandService");
    }

    @Override
    public void createResearchTask(CreateResearchTaskRequest request,
            StreamObserver<ResearchTask> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Commands.CreateResearchTask cmd = new Commands.CreateResearchTask(
                    request.getTitle(),
                    request.getDescription(),
                    request.getSubjectReference(),
                    request.getSubjectKind());
            Results.ResearchTaskView view = commandService.createResearchTask(cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (DraftDomainMapper.InvalidRequestException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getResearchTask(GetResearchTaskRequest request,
            StreamObserver<ResearchTask> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Results.ResearchTaskView view = commandService.findResearchTask(request.getTaskId());
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.ResearchTaskNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void transitionResearchTask(TransitionResearchTaskRequest request,
            StreamObserver<ResearchTask> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Commands.TransitionResearchTask cmd = new Commands.TransitionResearchTask(
                    DraftDomainMapper.researchTaskStatusFromProto(request.getToStatus().name()),
                    request.getBlockedReason(),
                    request.getResolvedProof());
            Results.ResearchTaskView view = commandService.transitionResearchTask(
                    request.getTaskId(), cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.ResearchTaskNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (ResearchCommandService.InvalidTransitionException e) {
            observer.onError(Status.FAILED_PRECONDITION
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    private static ResearchTask toProto(Results.ResearchTaskView v) {
        ResearchTask.Builder b = ResearchTask.newBuilder()
                .setId(v.id())
                .setTenantId(v.tenantId())
                .setTitle(v.title())
                .setSubjectReference(v.subjectReference())
                .setStatus(com.genealogy.platform.research.v1.ResearchTaskStatus.valueOf(
                        researchTaskStatusToProto(v.status())))
                .setEtag(v.etag())
                .setVersion(v.version());
        if (v.description() != null) b.setDescription(v.description());
        if (v.subjectKind() != null) b.setSubjectKind(v.subjectKind());
        if (v.blockedReason() != null) b.setBlockedReason(v.blockedReason());
        if (v.resolvedProof() != null) b.setResolvedProof(v.resolvedProof());
        if (v.createdAt() != null) b.setCreatedAt(v.createdAt().toString());
        if (v.updatedAt() != null) b.setUpdatedAt(v.updatedAt().toString());
        if (v.resolvedAt() != null) b.setResolvedAt(v.resolvedAt().toString());
        return b.build();
    }

    private static String researchTaskStatusToProto(
            com.genealogy.platform.services.research.domain.ResearchTaskStatus s) {
        if (s == null) return "RESEARCH_TASK_STATUS_UNSPECIFIED";
        return switch (s) {
            case OPEN -> "RESEARCH_TASK_OPEN";
            case IN_PROGRESS -> "RESEARCH_TASK_IN_PROGRESS";
            case BLOCKED -> "RESEARCH_TASK_BLOCKED";
            case RESOLVED -> "RESEARCH_TASK_RESOLVED";
            case ABANDONED -> "RESEARCH_TASK_ABANDONED";
        };
    }
}
