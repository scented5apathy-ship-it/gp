package com.genealogy.platform.services.research.workspace;

import java.time.Instant;
import java.util.Objects;

/**
 * Workspace projection row aggregate. Mirrors the
 * {@code research_service.workspace_projection} table shipped
 * in V3__outbox_and_workspace.sql. The DTO is the only object
 * the consumer side and the editor UI deal with — the live
 * aggregate rows stay concealed behind the public REST/gRPC
 * surface per the cross-service contract.
 *
 * <p>The projection is re-built from the upstream events
 * (R8.4 + NFR1): {@code TreeVisibilityChanged} flips the
 * visibility column, {@code PersonRedacted} flips the
 * {@code redacted} flag and stamps the redaction reason.
 * The editor UI joins the projection back to the live
 * research rows only for the non-redacted columns.
 */
public record ResearchWorkspaceProjection(
        String tenantId,
        String treeId,
        String claimReference,
        String subjectReference,
        String subjectKind,
        Visibility visibility,
        boolean redacted,
        RedactionReason lastRedactionReason,
        Instant lastRedactedAt,
        Instant claimVerifiedAt,
        long projectionVersion,
        Instant createdAt,
        Instant updatedAt) {

    public enum Visibility {
        PRIVATE,
        UNLISTED,
        PUBLIC
    }

    public enum RedactionReason {
        LIVING,
        MINOR,
        CONSENT_REVOKED,
        JURISDICTION_BLOCKED
    }

    public ResearchWorkspaceProjection {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(claimReference, "claimReference");
        Objects.requireNonNull(subjectReference, "subjectReference");
        Objects.requireNonNull(subjectKind, "subjectKind");
        Objects.requireNonNull(visibility, "visibility");
        if (redacted) {
            Objects.requireNonNull(lastRedactionReason, "lastRedactionReason");
            Objects.requireNonNull(lastRedactedAt, "lastRedactedAt");
        }
        if (projectionVersion <= 0) {
            throw new IllegalArgumentException(
                    "projectionVersion must be > 0, got " + projectionVersion);
        }
    }

    public ResearchWorkspaceProjection withVisibility(Visibility next, Instant at) {
        return new ResearchWorkspaceProjection(
                tenantId, treeId, claimReference, subjectReference, subjectKind,
                next, redacted, lastRedactionReason, lastRedactedAt, claimVerifiedAt,
                projectionVersion + 1, createdAt, at);
    }

    public ResearchWorkspaceProjection withRedactionOverlay(
            RedactionReason reason, Instant at) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        return new ResearchWorkspaceProjection(
                tenantId, treeId, claimReference, subjectReference, subjectKind,
                visibility, true, reason, at, claimVerifiedAt,
                projectionVersion + 1, createdAt, at);
    }

    public ResearchWorkspaceProjection withClaimVerified(Instant at) {
        if (claimVerifiedAt != null) {
            return this;
        }
        return new ResearchWorkspaceProjection(
                tenantId, treeId, claimReference, subjectReference, subjectKind,
                visibility, redacted, lastRedactionReason, lastRedactedAt, at,
                projectionVersion + 1, createdAt, at);
    }
}
