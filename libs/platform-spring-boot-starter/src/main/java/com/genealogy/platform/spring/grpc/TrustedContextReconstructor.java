package com.genealogy.platform.spring.grpc;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pure POJO that reconstructs the {@link TrustedTenantContext}
 * for a single inbound gRPC call from the BFF-signed metadata +
 * the SPIFFE peer identity + (optionally) a Keycloak JWT subject.
 *
 * <p>This class is the seam every service depends on; the actual
 * {@code io.grpc.ServerInterceptor} wrapper is a thin shell that
 * adapts the gRPC {@code Metadata} into the {@link InboundCall}
 * record below, calls {@link #reconstruct(InboundCall)} and writes
 * the result back to the thread-local {@link TrustedTenantContext}.
 *
 * <p>Per {@code contracts/trusted-context/policy.yaml} (E3.5) the
 * logic is:
 * <ol>
 *   <li>If the {@code Context.tenant_id} / {@code Context.actor_id}
 *       / {@code Context.actor_role} proto fields are set by the
 *       client (i.e. not server-derived), the call is rejected
 *       with {@link TrustedContextViolation#CLIENT_SUPPLIED_TENANT_ID}
 *       (or the matching code). The proto fields are advisory only
 *       and are overwritten server-side.</li>
 *   <li>The {@code x-tenant-id} / {@code x-actor-id} /
 *       {@code x-actor-role} metadata keys are the trusted source —
 *       they were placed there by the BFF client interceptor (or
 *       by an authorised internal workload over Istio mTLS).</li>
 *   <li>The SPIFFE peer identity MUST match the expected pattern
 *       declared in the contract (default: BFF service accounts
 *       in {@code gp-bff}). Calls from workloads outside the
 *       sanctioned trust zone are rejected.</li>
 * </ol>
 *
 * <p>The class is framework-free (no {@code io.grpc} import) so
 * it can be unit-tested without bringing up a gRPC server.
 */
public final class TrustedContextReconstructor {

    /** Default SPIFFE pattern for sanctioned BFF callers. */
    public static final String DEFAULT_BFF_SPIFFE_PATTERN =
            "^spiffe://cluster\\.local/ns/gp-bff/sa/.+$";

    private final Pattern spiffePattern;

    public TrustedContextReconstructor() {
        this(Pattern.compile(DEFAULT_BFF_SPIFFE_PATTERN));
    }

    public TrustedContextReconstructor(Pattern spiffePattern) {
        this.spiffePattern = Objects.requireNonNull(spiffePattern, "spiffePattern");
    }

    /**
     * Reconstruct the trusted tenant context for an inbound gRPC
     * call. Throws {@link TrustedContextViolation} when the call
     * violates the E3.5 contract (client supplied proto fields,
     * missing SPIFFE peer, SPIFFE peer outside the sanctioned
     * trust zone, or missing required metadata).
     *
     * @param call the inbound call metadata + proto context
     * @return the server-derived trusted tenant context
     */
    public TrustedTenantContext reconstruct(InboundCall call) {
        Objects.requireNonNull(call, "call");

        // 1) Client-supplied proto fields are forbidden.
        if (call.contextTenantId() != null && !call.contextTenantId().isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.CLIENT_SUPPLIED_TENANT_ID,
                    "Context.tenant_id must be empty on inbound gRPC; clients cannot set it "
                            + "(contracts/trusted-context/policy.yaml E3.5).");
        }
        if (call.contextActorId() != null && !call.contextActorId().isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ID,
                    "Context.actor_id must be empty on inbound gRPC; clients cannot set it "
                            + "(contracts/trusted-context/policy.yaml E3.5).");
        }
        if (call.contextActorRole() != null && !call.contextActorRole().isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ROLE,
                    "Context.actor_role must be empty on inbound gRPC; clients cannot set it "
                            + "(contracts/trusted-context/policy.yaml E3.5).");
        }

        // 2) SPIFFE peer identity is required + must match the
        //    sanctioned trust zone.
        String peerSpiffe = call.peerSpiffeId();
        if (peerSpiffe == null || peerSpiffe.isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.MISSING_SPIFFE_PEER,
                    "Inbound gRPC call has no SPIFFE peer identity; Istio mTLS must attach it.");
        }
        if (!spiffePattern.matcher(peerSpiffe).matches()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.UNTRUSTED_SPIFFE_PEER,
                    "Inbound gRPC call SPIFFE peer '" + peerSpiffe
                            + "' is outside the sanctioned trust zone ("
                            + spiffePattern.pattern() + ").");
        }

        // 3) BFF-issued metadata is the trusted source.
        String tenantId = call.metadataTenantId();
        String actorId = call.metadataActorId();
        String actorRole = call.metadataActorRole();
        String correlationId = call.metadataCorrelationId();
        String traceId = call.metadataTraceId();

        if (tenantId == null || tenantId.isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.MISSING_TENANT_ID,
                    "Inbound gRPC call has no x-tenant-id metadata; BFF must populate it.");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.MISSING_ACTOR_ID,
                    "Inbound gRPC call has no x-actor-id metadata; BFF must populate it.");
        }
        if (actorRole == null || actorRole.isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.MISSING_ACTOR_ROLE,
                    "Inbound gRPC call has no x-actor-role metadata; BFF must populate it.");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new TrustedContextViolation(
                    TrustedContextViolation.Reason.MISSING_CORRELATION_ID,
                    "Inbound gRPC call has no x-correlation-id metadata; interceptor must "
                            + "generate one when absent (per contracts/trusted-context/policy.yaml).");
        }

        return TrustedTenantContext.of(tenantId, actorId, actorRole, correlationId, traceId);
    }

    /**
     * Snapshot of an inbound gRPC call. Built by the gRPC
     * {@code ServerInterceptor} shell from
     * {@code io.grpc.Metadata} + the deserialised
     * {@code com.genealogy.platform.common.v1.Context} proto
     * field (or any other request message carrying the
     * trusted context envelope).
     *
     * <p>The record is intentionally a plain Java record so it
     * stays out of the gRPC API surface; the interceptor is the
     * single place that translates gRPC types into this record.
     */
    public record InboundCall(
            String contextTenantId,
            String contextActorId,
            String contextActorRole,
            String metadataTenantId,
            String metadataActorId,
            String metadataActorRole,
            String metadataCorrelationId,
            String metadataTraceId,
            String peerSpiffeId) {
    }
}
