package com.genealogy.platform.webbff.reconcile;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that the BFF installs in front of every route
 * that requires a tenant context. The guard calls
 * {@link MembershipReconciler} for the Keycloak subject + the
 * {@code X-Tenant-Id} header, populates the thread-local
 * {@link TrustedTenantContext} with the reconciled role, and
 * either forwards the request or returns a 404 problem+json
 * body (per E3.2d DoD — never 403, to avoid leaking the
 * existence of the foreign tenant).
 *
 * <p>The guard runs AFTER {@code platform-spring-boot-starter}'s
 * {@code TrustedContextFilter}; the upstream filter already
 * validated the {@code X-Tenant-Id} header format and refused
 * client-supplied identity parameters in the query string.
 * This filter adds the semantic check: the Keycloak subject
 * must actually belong to the selected tenant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class TenantSelectionGuard extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantSelectionGuard.class);

    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final MembershipReconciler reconciler;

    public TenantSelectionGuard(MembershipReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenantId = request.getHeader(TENANT_HEADER);
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(CORRELATION_HEADER, correlationId);
        MDC.put("correlation_id", correlationId);

        String subject = currentSubject();
        TenantReconciliationResult result = reconciler.reconcile(subject, tenantId, correlationId);

        if (!result.isAllowed()) {
            LOG.info(
                    "tenant reconciliation denied status={} subject={} tenant={} correlation_id={}",
                    result.status(), subject, tenantId, correlationId);
            writeProblem(response, HttpServletResponse.SC_NOT_FOUND,
                    "https://genealogy/problems/tenant-not-found",
                    "selected tenant is not accessible to the caller");
            MDC.clear();
            return;
        }

        TrustedTenantContext.set(
                TrustedTenantContext.of(result.tenantId(), result.actorId(),
                        result.actorRole(), correlationId, null));
        try {
            chain.doFilter(request, response);
        } finally {
            TrustedTenantContext.clear();
            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        // Probe + health routes don't need a tenant context.
        return path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.equals("/actuator/health/liveness")
                || path.equals("/actuator/health/readiness");
    }

    private static String currentSubject() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return auth.getName();
    }

    private static void writeProblem(
            HttpServletResponse response, int status, String type, String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter()
                .write("{\"type\":\"" + type + "\","
                        + "\"title\":\"" + title + "\","
                        + "\"status\":" + status + "}");
    }
}
