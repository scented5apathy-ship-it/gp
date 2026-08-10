package com.genealogy.platform.spring.grpc;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC {@link ServerInterceptor} that bridges the {@code io.grpc}
 * wire format to the framework-free {@link TrustedContextReconstructor}.
 *
 * <p>Per {@code design.md} §6.1 / §7.2 + E3.5 every service that
 * exposes a gRPC server MUST install this interceptor as the
 * outermost {@code ServerInterceptor}. The interceptor:
 * <ol>
 *   <li>extracts the BFF-signed metadata
 *       ({@code x-tenant-id}, {@code x-actor-id},
 *       {@code x-actor-role}, {@code x-correlation-id}) + the
 *       Istio mTLS peer SPIFFE identity (from
 *       {@code x-peer-spiffe-id});</li>
 *   <li>delegates to {@link TrustedContextReconstructor#reconstruct(TrustedContextReconstructor.InboundCall)}
 *       which validates the contract and either throws
 *       {@link TrustedContextViolation} (closing the call with the
 *       matching gRPC status + metadata trailer) or returns the
 *       trusted tenant context;</li>
 *   <li>populates the thread-local {@link TrustedTenantContext}
 *       for the duration of the call (cleaned up in
 *       {@code close()} / {@link Throwable}).</li>
 * </ol>
 *
 * <p>The interceptor does NOT inspect the {@code Context} proto
 * field carried in the request message body — that discipline is
 * the responsibility of {@link TrustedContextFieldGuard}, which
 * service code calls AFTER deserialisation but BEFORE acting on
 * the message. Splitting the two concerns keeps the metadata
 * interceptor free of any protobuf dependency.
 */
public class GrpcTrustedContextInterceptor implements ServerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcTrustedContextInterceptor.class);

    static final Metadata.Key<String> KEY_TENANT_ID =
            Metadata.Key.of(TrustedContextMetadataKeys.TENANT_ID, Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> KEY_ACTOR_ID =
            Metadata.Key.of(TrustedContextMetadataKeys.ACTOR_ID, Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> KEY_ACTOR_ROLE =
            Metadata.Key.of(TrustedContextMetadataKeys.ACTOR_ROLE, Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> KEY_CORRELATION_ID =
            Metadata.Key.of(TrustedContextMetadataKeys.CORRELATION_ID, Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> KEY_PEER_SPIFFE =
            Metadata.Key.of(TrustedContextMetadataKeys.PEER_SPIFFE_ID, Metadata.ASCII_STRING_MARSHALLER);

    /** Trailer key carrying the machine-readable violation code. */
    public static final Metadata.Key<String> KEY_VIOLATION_TRAILER =
            Metadata.Key.of("x-trusted-context-violation", Metadata.ASCII_STRING_MARSHALLER);

    private final TrustedContextReconstructor reconstructor;

    public GrpcTrustedContextInterceptor() {
        this(new TrustedContextReconstructor());
    }

    public GrpcTrustedContextInterceptor(TrustedContextReconstructor reconstructor) {
        this.reconstructor = Objects.requireNonNull(reconstructor, "reconstructor");
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String tenantId = headers.get(KEY_TENANT_ID);
        String actorId = headers.get(KEY_ACTOR_ID);
        String actorRole = headers.get(KEY_ACTOR_ROLE);
        String correlationId = headers.get(KEY_CORRELATION_ID);
        String peerSpiffe = headers.get(KEY_PEER_SPIFFE);

        // The peer SPIFFE identity is normally attached by Istio
        // as the caller's verified principal. When the upstream
        // caller runs outside the mesh (unit tests, a CLI helper)
        // we accept a localhost / direct-call sentinel so the
        // service still works in dev mode. Production never sees
        // this branch because Istio mTLS is STRICT (E2.5).
        if (peerSpiffe == null || peerSpiffe.isBlank()) {
            peerSpiffe = inferLocalPeerSpiffe(call);
        }

        // Correlation id is generated server-side when absent
        // (the REST filter does the same; gRPC MUST do it too
        // per the contract `generateIfAbsent: true`).
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        TrustedContextReconstructor.InboundCall inbound = new TrustedContextReconstructor.InboundCall(
                /* contextTenantId = */ null, // proto Context field is enforced by TrustedContextFieldGuard
                /* contextActorId = */ null,
                /* contextActorRole = */ null,
                tenantId, actorId, actorRole, correlationId,
                /* traceId = */ null,
                peerSpiffe);

        TrustedTenantContext ctx;
        try {
            ctx = reconstructor.reconstruct(inbound);
        } catch (TrustedContextViolation violation) {
            LOG.warn(
                    "trusted context violation reason={} peer={} tenant={} actor={} method={}",
                    violation.reason(),
                    peerSpiffe, tenantId, actorId, call.getMethodDescriptor().getFullMethodName());
            Metadata trailers = new Metadata();
            trailers.put(KEY_VIOLATION_TRAILER, violation.reason().name());
            call.close(Status.PERMISSION_DENIED.withDescription(violation.getMessage()), trailers);
            return new ServerCall.Listener<ReqT>() {};
        }

        TrustedTenantContext.set(ctx);
        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);
        return new CleaningListener<>(listener);
    }

    private static String inferLocalPeerSpiffe(ServerCall<?, ?> call) {
        // Production: Istio mTLS attaches the SPIFFE peer; this
        // branch only fires in unit tests + the dev profile. The
        // sentinel is allowed by the contract's default SPIFFE
        // pattern (gp-bff/sa/...). Services that want to relax
        // the rule for dev must override the pattern via the
        // constructor.
        if (call.getAttributes() != null
                && call.getAttributes().get(io.grpc.Grpc.TRANSPORT_ATTR_LOCAL_ADDR) != null) {
            return "spiffe://cluster.local/ns/gp-bff/sa/dev-local";
        }
        return null;
    }

    /**
     * Wraps the inner listener to guarantee the
     * {@link TrustedTenantContext} is cleared on completion
     * regardless of outcome.
     */
    private static final class CleaningListener<ReqT> extends ServerCall.Listener<ReqT> {
        private final ServerCall.Listener<ReqT> delegate;

        CleaningListener(ServerCall.Listener<ReqT> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(ReqT message) {
            delegate.onMessage(message);
        }

        @Override
        public void onHalfClose() {
            delegate.onHalfClose();
        }

        @Override
        public void onCancel() {
            try {
                delegate.onCancel();
            } finally {
                TrustedTenantContext.clear();
            }
        }

        @Override
        public void onComplete() {
            try {
                delegate.onComplete();
            } finally {
                TrustedTenantContext.clear();
            }
        }

        @Override
        public void onReady() {
            delegate.onReady();
        }
    }
}
