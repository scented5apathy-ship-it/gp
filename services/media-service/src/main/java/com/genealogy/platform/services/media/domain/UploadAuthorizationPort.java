package com.genealogy.platform.services.media.domain;

import java.util.Set;

/**
 * Port for the upload-lifecycle authorization decision
 * (OpenFGA + ABAC). Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionReAuthorizationRequiredOn* +
 * quotaReAuthorizationRequiredOnFinalize +
 * quarantineGateReAuthorizationRequiredOnAdmit +
 * abandonedMultipartReAuthorizationRequiredOnReap` (E7.1) +
 * `requirements.md` R9.2 + `design.md` §8.2 + ADR-E0.5-06.
 *
 * <p>The application layer wraps the OpenFGA + ABAC call
 * behind this port; the executor never calls OpenFGA
 * directly. Implementations MUST be deterministic and
 * tenant-safe.
 */
public interface UploadAuthorizationPort {

    UploadAuthorizationDecision authorize(
            UploadAuthorizationContext context, UploadAuthorizationAction action);

    enum UploadAuthorizationAction {
        UPLOAD_SESSION_CREATE,
        UPLOAD_SESSION_SIGNED_URL_ISSUE,
        UPLOAD_SESSION_MULTIPART_PART_RECEIPT,
        UPLOAD_SESSION_FINALIZE,
        QUOTA_FINALIZE,
        QUARANTINE_GATE_ADMIT,
        ABANDONED_MULTIPART_REAP,
        SIGNED_URL_RE_ISSUE
    }

    record UploadAuthorizationContext(
            String tenantId,
            String requesterPseudoId,
            String targetPseudoId,
            String sessionId,
            String scopeId,
            UploadSessionIntent intent,
            MediaCategory mediaCategory,
            Set<String> requestedScopes) {

        public UploadAuthorizationContext {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            if (requesterPseudoId == null || requesterPseudoId.isBlank()) {
                throw new IllegalArgumentException("requesterPseudoId must not be blank");
            }
            if (targetPseudoId == null || targetPseudoId.isBlank()) {
                throw new IllegalArgumentException("targetPseudoId must not be blank");
            }
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("sessionId must not be blank");
            }
            if (scopeId == null || scopeId.isBlank()) {
                throw new IllegalArgumentException("scopeId must not be blank");
            }
            requestedScopes = requestedScopes == null ? Set.of() : Set.copyOf(requestedScopes);
        }
    }
}
