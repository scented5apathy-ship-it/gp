package com.genealogy.platform.services.research.workspace;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Tiny utility that binds the trusted tenant context for the
 * duration of a supplier call. The Kafka consumer side runs
 * off-spring (no HTTP request thread), so the
 * {@link TrustedTenantContext} thread-local is empty by
 * default; the binder materialises a context from the event
 * payload so the {@link ResearchWorkspaceProjectionService}
 * methods can read the tenant id + actor id the same way the
 * REST + gRPC paths do.
 *
 * <p>The supplier must NOT be null. The supplier is allowed to
 * throw; the binder clears the thread-local before the
 * exception propagates so a subsequent event is not poisoned.
 */
@Component
public class ResearchTenantContextBinder {

    public <T> T runWith(String tenantId, String actorId, String correlationId, Supplier<T> body) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(body, "body");
        TrustedTenantContext ctx = TrustedTenantContext.of(
                tenantId,
                actorId == null ? "anonymous" : actorId,
                "service",
                correlationId == null ? java.util.UUID.randomUUID().toString() : correlationId,
                null);
        TrustedTenantContext.set(ctx);
        try {
            return body.get();
        } finally {
            TrustedTenantContext.clear();
        }
    }
}
