package com.genealogy.platform.spring.grpc;

import java.util.Objects;

/**
 * Defensive validator that every gRPC service MUST call after
 * deserialising an inbound request message but BEFORE acting on
 * it. The guard enforces the E3.5 contract:
 * "Clients MUST NOT set the {@code Context.tenant_id} /
 * {@code Context.actor_id} / {@code Context.actor_role} fields
 * directly — the server reconstructs the trusted context from
 * the BFF-signed gRPC metadata."
 *
 * <p>Per {@code contracts/protobuf/common/v1/context.proto} the
 * {@code Context} message is the FIRST field of every request.
 * When the field is present and populated, the guard throws
 * {@link TrustedContextViolation} with the matching reason code;
 * the caller closes the gRPC call with {@code PERMISSION_DENIED}
 * + the {@code x-trusted-context-violation} trailer.
 *
 * <p>Why a separate guard rather than putting the check inside
 * the metadata interceptor: the interceptor only sees gRPC
 * metadata; the {@code Context} field lives inside the message
 * body which is only available AFTER deserialisation. Splitting
 * the two concerns also keeps the interceptor free of any
 * dependency on the generated protobuf classes.
 *
 * <p>Usage from a generated gRPC service base class:
 * <pre>{@code
 *   public void createTenant(CreateTenantRequest req, StreamObserver<Tenant> obs) {
 *       TrustedContextFieldGuard.enforce(req.getContext());
 *       // ... domain logic
 *   }
 * }</pre>
 */
public final class TrustedContextFieldGuard {

    private TrustedContextFieldGuard() {
        throw new AssertionError("static utility — not instantiable");
    }

    /**
     * Enforce the contract on the deserialised {@code Context}
     * field. The {@code context} argument is intentionally typed
     * as a {@link ContextView} so this class does not depend on
     * the generated protobuf types — services adapt their
     * generated {@code com.genealogy.platform.common.v1.Context}
     * instance into a {@link ContextView} via a lambda.
     */
    public static void enforce(ContextView context) {
        Objects.requireNonNull(context, "context");
        if (context.tenantId() != null && !context.tenantId().isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.CLIENT_SUPPLIED_TENANT_ID,
                    "Context.tenant_id must be empty on inbound gRPC; clients cannot set it "
                            + "(contracts/trusted-context/policy.yaml E3.5).");
        }
        if (context.actorId() != null && !context.actorId().isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ID,
                    "Context.actor_id must be empty on inbound gRPC; clients cannot set it "
                            + "(contracts/trusted-context/policy.yaml E3.5).");
        }
        if (context.actorRole() != null && !context.actorRole().isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ROLE,
                    "Context.actor_role must be empty on inbound gRPC; clients cannot set it "
                            + "(contracts/trusted-context/policy.yaml E3.5).");
        }
    }

    /**
     * Read-only view of the deserialised {@code Context} message.
     * Services adapt their generated proto instance to this view
     * via a lambda; the guard itself stays framework-free so it
     * can be unit-tested without protobuf.
     */
    public interface ContextView {
        String tenantId();

        String actorId();

        String actorRole();

        static ContextView of(String tenantId, String actorId, String actorRole) {
            return new SimpleContextView(tenantId, actorId, actorRole);
        }
    }

    private record SimpleContextView(String tenantId, String actorId, String actorRole)
            implements ContextView {
    }
}
