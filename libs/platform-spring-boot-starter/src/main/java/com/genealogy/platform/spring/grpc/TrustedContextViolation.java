package com.genealogy.platform.spring.grpc;

import java.util.Objects;

/**
 * Thrown when an inbound gRPC call violates the trusted tenant
 * context contract (E3.5). The {@link Reason} enum is the
 * machine-readable code surfaced in the gRPC trailer as a
 * {@code x-trusted-context-violation} metadata entry and in
 * the {@code AuditEvent} payload.
 *
 * <p>Per {@code design.md} §6.1 the service MUST self-validate;
 * the violation is never silently dropped.
 */
public class TrustedContextViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public TrustedContextViolation(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    /**
     * Closed-set violation codes. Additions require an ADR; the
     * catalogue is the audit contract.
     */
    public enum Reason {
        /** Proto {@code Context.tenant_id} was set by the client. */
        CLIENT_SUPPLIED_TENANT_ID,
        /** Proto {@code Context.actor_id} was set by the client. */
        CLIENT_SUPPLIED_ACTOR_ID,
        /** Proto {@code Context.actor_role} was set by the client. */
        CLIENT_SUPPLIED_ACTOR_ROLE,
        /** Inbound gRPC call has no SPIFFE peer identity (Istio mTLS missing). */
        MISSING_SPIFFE_PEER,
        /** SPIFFE peer identity is outside the sanctioned trust zone. */
        UNTRUSTED_SPIFFE_PEER,
        /** Missing {@code x-tenant-id} metadata. */
        MISSING_TENANT_ID,
        /** Missing {@code x-actor-id} metadata. */
        MISSING_ACTOR_ID,
        /** Missing {@code x-actor-role} metadata. */
        MISSING_ACTOR_ROLE,
        /** Missing {@code x-correlation-id} metadata (interceptor failed to generate). */
        MISSING_CORRELATION_ID,
    }
}
