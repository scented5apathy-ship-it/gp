package com.genealogy.platform.spring.grpc;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.Objects;
import java.util.UUID;

/**
 * gRPC {@link ClientInterceptor} used by the BFF to propagate the
 * REST-authenticated trusted tenant context to downstream services
 * over the mesh. Per {@code contracts/trusted-context/policy.yaml}
 * (E3.5) the only sanctioned source for {@code tenant_id} /
 * {@code actor_id} / {@code actor_role} on the gRPC wire is the
 * BFF-issued metadata; the proto {@code Context} fields MUST be
 * empty on the wire (enforced server-side by
 * {@link TrustedContextFieldGuard}).
 *
 * <p>Install this interceptor on every outbound channel the BFF
 * opens. The interceptor reads the thread-local
 * {@link TrustedTenantContext} (populated by the REST filter on
 * the inbound hop) and writes it as gRPC metadata so the
 * downstream {@link GrpcTrustedContextInterceptor} can
 * reconstruct it.
 */
public class GrpcTrustedContextClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        TrustedTenantContext ctx = TrustedTenantContext.current();
        // The BFF MUST have a populated context before opening a
        // gRPC call; if not, the call is refused locally to avoid
        // leaking a request into the mesh with no tenant identity.
        if (!ctx.isAuthenticated()) {
            throw new IllegalStateException(
                    "GrpcTrustedContextClientInterceptor invoked with no trusted tenant context; "
                            + "the inbound REST request must populate TrustedTenantContext first "
                            + "(E3.5 contracts/trusted-context/policy.yaml).");
        }

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(GrpcTrustedContextInterceptor.KEY_TENANT_ID, ctx.getTenantId());
                headers.put(GrpcTrustedContextInterceptor.KEY_ACTOR_ID, ctx.getActorId());
                if (ctx.getActorRole() != null) {
                    headers.put(GrpcTrustedContextInterceptor.KEY_ACTOR_ROLE, ctx.getActorRole());
                }
                String correlationId = ctx.getCorrelationId();
                if (correlationId == null || correlationId.isBlank()) {
                    correlationId = UUID.randomUUID().toString();
                }
                headers.put(GrpcTrustedContextInterceptor.KEY_CORRELATION_ID, correlationId);
                super.start(responseListener, headers);
            }
        };
    }

    /** Factory helper for programmatic {@code NettyChannelBuilder#intercept(...)}. */
    public static GrpcTrustedContextClientInterceptor create() {
        return new GrpcTrustedContextClientInterceptor();
    }
}
