package com.genealogy.platform.services.research.grpc;

import com.genealogy.platform.research.v1.ArchiveRepositoryRequest;
import com.genealogy.platform.research.v1.CreateRepositoryRequest;
import com.genealogy.platform.research.v1.GetRepositoryRequest;
import com.genealogy.platform.common.v1.Context;
import com.genealogy.platform.research.v1.Repository;
import com.genealogy.platform.research.v1.RepositoryServiceGrpc;
import com.genealogy.platform.services.research.application.Commands;
import com.genealogy.platform.services.research.application.DraftDomainMapper;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.Results;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC adapter for the {@code RepositoryService} contract.
 * Mirrors the REST surface from E6.1c; the {@link
 * DraftDomainMapper} keeps the wire types / domain types
 * decoupled.
 */
@GrpcService
public class ResearchRepositoryGrpcService extends RepositoryServiceGrpc.RepositoryServiceImplBase {

    private static void enforceTrustedContext(
            com.genealogy.platform.common.v1.Context context) {
        ResearchGrpcContextGuard.enforce(
                context.getTenantId(),
                context.getActorId(),
                context.getActorRole());
    }


    private final ResearchCommandService commandService;

    public ResearchRepositoryGrpcService(
            ResearchCommandService commandService) {
        this.commandService = Objects.requireNonNull(commandService, "commandService");
    }

    @Override
    public void createRepository(CreateRepositoryRequest request,
            StreamObserver<Repository> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Commands.CreateRepository cmd = new Commands.CreateRepository(
                    request.getName(),
                    DraftDomainMapper.repositoryKindFromProto(request.getKind().name()),
                    request.getLocationLabel(),
                    request.getWebsiteUrl(),
                    request.getDescription(),
                    request.getPrivateHolding());
            Results.RepositoryView view = commandService.createRepository(cmd);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (DraftDomainMapper.InvalidRequestException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void getRepository(GetRepositoryRequest request,
            StreamObserver<Repository> observer) {
        enforceTrustedContext(request.getContext());
        try {
            Results.RepositoryView view = commandService.findRepository(request.getRepositoryId());
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.RepositoryNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void archiveRepository(ArchiveRepositoryRequest request,
            StreamObserver<Repository> observer) {
        enforceTrustedContext(request.getContext());
        try {
            long expected = request.getEtag().isBlank() ? 0L
                    : Long.parseLong(request.getEtag().replaceAll("[^0-9]", ""));
            Results.RepositoryView view = commandService.archiveRepository(
                    request.getRepositoryId(), expected);
            observer.onNext(toProto(view));
            observer.onCompleted();
        } catch (ResearchCommandService.RepositoryNotFoundException e) {
            observer.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (ResearchCommandService.OptimisticConcurrencyException e) {
            observer.onError(Status.FAILED_PRECONDITION
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    private static Repository toProto(Results.RepositoryView v) {
        Repository.Builder b = Repository.newBuilder()
                .setId(v.id())
                .setTenantId(v.tenantId())
                .setName(v.name())
                .setKind(com.genealogy.platform.research.v1.RepositoryKind.valueOf(
                        DraftDomainMapper.repositoryKindToProto(v.kind())))
                .setPrivateHolding(v.privateHolding())
                .setEtag(v.etag())
                .setVersion(v.version());
        if (v.locationLabel() != null) b.setLocationLabel(v.locationLabel());
        if (v.websiteUrl() != null) b.setWebsiteUrl(v.websiteUrl());
        if (v.description() != null) b.setDescription(v.description());
        if (v.createdAt() != null) b.setCreatedAt(v.createdAt().toString());
        if (v.updatedAt() != null) b.setUpdatedAt(v.updatedAt().toString());
        if (v.archivedAt() != null) b.setArchivedAt(v.archivedAt().toString());
        return b.build();
    }
}
