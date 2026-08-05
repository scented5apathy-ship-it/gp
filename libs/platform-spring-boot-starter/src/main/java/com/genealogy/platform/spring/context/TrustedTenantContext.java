package com.genealogy.platform.spring.context;

/**
 * Thread-local holder for the trusted tenant context. Populated by
 * the {@code TrustedContextFilter} on every inbound request and read
 * by gRPC services, audit hooks, repository guards and the OTel
 * resource attributes.
 *
 * <p>The holder is reset to {@link #empty()} when the request
 * finishes (or throws) to prevent leakage between threads. The
 * empty context is the safe default — services must refuse to act
 * when the holder is empty.
 */
public final class TrustedTenantContext {

    private static final ThreadLocal<TrustedTenantContext> CURRENT = new ThreadLocal<>();

    private final String tenantId;
    private final String actorId;
    private final String actorRole;
    private final String correlationId;
    private final String traceId;

    private TrustedTenantContext(
            String tenantId,
            String actorId,
            String actorRole,
            String correlationId,
            String traceId) {
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.correlationId = correlationId;
        this.traceId = traceId;
    }

    public static TrustedTenantContext empty() {
        return new TrustedTenantContext(null, null, null, null, null);
    }

    public static TrustedTenantContext of(
            String tenantId, String actorId, String actorRole, String correlationId, String traceId) {
        return new TrustedTenantContext(tenantId, actorId, actorRole, correlationId, traceId);
    }

    public static void set(TrustedTenantContext ctx) {
        if (ctx == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(ctx);
        }
    }

    public static TrustedTenantContext current() {
        TrustedTenantContext ctx = CURRENT.get();
        return ctx == null ? empty() : ctx;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public boolean isAuthenticated() {
        return tenantId != null && !tenantId.isBlank() && actorId != null && !actorId.isBlank();
    }
}
