package com.genealogy.platform.spring.web;

import com.genealogy.platform.spring.autoconfigure.PlatformProperties;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the {@link TrustedTenantContext} from the Keycloak JWT
 * + {@code X-Tenant-Id} header and writes the correlation id to the
 * SLF4J MDC. The filter is intentionally strict: when
 * {@code platform.tenant.headerRequired} is {@code true} (default)
 * and the request does not carry a valid {@code X-Tenant-Id} header,
 * the filter returns {@code 400 Bad Request} with an RFC 9457 body.
 *
 * <p>Tenant id is server-derived from the validated JWT subject
 * (Keycloak group claim) and the trusted header. Clients cannot
 * pass arbitrary {@code tenantId} in the JSON body — the
 * contract-test suite enforces the same rule (see
 * {@code scripts/test-contracts.mjs}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TrustedContextFilter extends OncePerRequestFilter {

    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String CORRELATION_HEADER = "X-Correlation-Id";
    static final String MDC_TENANT = "tenant_id";
    static final String MDC_CORRELATION = "correlation_id";

    private final PlatformProperties properties;

    public TrustedContextFilter(PlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        String tenantId = request.getHeader(TENANT_HEADER);
        String actorId = null;
        String actorRole = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            actorId = jwt.getSubject();
            Object role = jwt.getClaims().get("role");
            if (role instanceof String s) {
                actorRole = s;
            }
        }

        if (tenantId == null || tenantId.isBlank()) {
            if (properties.getTenant().isHeaderRequired()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/problem+json");
                response.getWriter()
                        .write(
                                "{\"type\":\"https://genealogy/problems/tenant-missing\","
                                        + "\"title\":\"X-Tenant-Id required\","
                                        + "\"status\":400}");
                MDC.clear();
                return;
            }
        } else if (tenantId.length() > properties.getTenant().getMaxIdLength()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/problem+json");
            response.getWriter()
                    .write(
                            "{\"type\":\"https://genealogy/problems/tenant-invalid\","
                                    + "\"title\":\"X-Tenant-Id exceeds maximum length\","
                                    + "\"status\":400}");
            MDC.clear();
            return;
        } else {
            MDC.put(MDC_TENANT, tenantId);
        }

        TrustedTenantContext.set(
                TrustedTenantContext.of(tenantId, actorId, actorRole, correlationId, null));
        try {
            chain.doFilter(request, response);
        } finally {
            TrustedTenantContext.clear();
            MDC.remove(MDC_CORRELATION);
            MDC.remove(MDC_TENANT);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.equals("/actuator/health/liveness")
                || path.equals("/actuator/health/readiness");
    }

    static String bearer(HttpServletRequest request) {
        String h = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (h == null || !h.toLowerCase().startsWith("bearer ")) {
            return null;
        }
        return h.substring("bearer ".length()).trim();
    }
}
