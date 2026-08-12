package com.genealogy.platform.services.research.grpc;

import com.genealogy.platform.spring.grpc.TrustedContextFieldGuard;
import com.genealogy.platform.spring.grpc.TrustedContextFieldGuard.ContextView;
import com.genealogy.platform.spring.grpc.TrustedContextViolation;

/**
 * Helper that adapts the generated
 * {@code com.genealogy.platform.common.v1.Context} proto field
 * to the framework-free {@link ContextView} so the static
 * {@link TrustedContextFieldGuard#enforce} call can run
 * without dragging the protobuf dependency into the
 * platform-starter.
 */
final class ResearchGrpcContextGuard {

    private ResearchGrpcContextGuard() {
    }

    /**
     * Enforce the contract on the deserialised
     * {@code Context} field of the supplied request.
     * The caller MUST map the supplied proto field to the
     * three-id Record; the helper stays out of the generated
     * protobuf classes.
     */
    static void enforce(String tenantId, String actorId, String actorRole) {
        try {
            TrustedContextFieldGuard.enforce(
                    ContextView.of(tenantId, actorId, actorRole));
        } catch (TrustedContextViolation violation) {
            // Re-throw as a runtime so the Spring gRPC
            // handler maps it to a PERMISSION_DENIED status.
            throw new RuntimeException(violation.getMessage(), violation);
        }
    }
}
