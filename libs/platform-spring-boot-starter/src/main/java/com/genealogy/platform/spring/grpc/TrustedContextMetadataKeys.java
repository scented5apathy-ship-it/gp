package com.genealogy.platform.spring.grpc;

import java.util.Objects;

/**
 * Closed-set constants for the gRPC metadata keys that the BFF
 * → service trusted context contract uses (E3.5). Mirrors
 * `contracts/trusted-context/policy.yaml::grpcMetadataKeys`.
 *
 * <p>These keys are the canonical wire format — every BFF client
 * interceptor and every service server interceptor MUST agree on
 * them. The linter {@code scripts/lint-trusted-context.mjs}
 * refuses any drift.
 *
 * <p>Per {@code design.md} §7.2 gRPC metadata keys are lowercase;
 * the Spring {@code Metadata.Key} class normalises to ASCII
 * lowercase on lookup, so the constants are declared lowercase.
 */
public final class TrustedContextMetadataKeys {

    /** Server-derived tenant id (BFF → service). */
    public static final String TENANT_ID = "x-tenant-id";

    /** Server-derived actor id (= Keycloak subject). */
    public static final String ACTOR_ID = "x-actor-id";

    /** Server-derived actor role within the tenant (from membership row). */
    public static final String ACTOR_ROLE = "x-actor-role";

    /** Correlation id propagated from the REST hop (`X-Correlation-Id`). */
    public static final String CORRELATION_ID = "x-correlation-id";

    /** Idempotency key for non-idempotent RPCs. */
    public static final String IDEMPOTENCY_KEY = "x-idempotency-key";

    /** User-Agent forwarded for audit. */
    public static final String USER_AGENT = "x-user-agent";

    /**
     * SPIFFE peer identity of the calling workload. Filled by the
     * Istio mTLS layer; the interceptor validates it against the
     * expected pattern declared in {@code contracts/trusted-context/policy.yaml}.
     */
    public static final String PEER_SPIFFE_ID = "x-peer-spiffe-id";

    private TrustedContextMetadataKeys() {
        throw new AssertionError("constants — not instantiable");
    }

    /** All metadata keys carried from REST → gRPC by the BFF client. */
    public static final java.util.List<String> FORWARDED_HEADERS = java.util.List.of(
            TENANT_ID, ACTOR_ID, ACTOR_ROLE, CORRELATION_ID, IDEMPOTENCY_KEY, USER_AGENT);

    /** Defensive null/blank helper for metadata values. */
    public static String orEmpty(String value) {
        Objects.requireNonNull(value, "value");
        return value;
    }
}
