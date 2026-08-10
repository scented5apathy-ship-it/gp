package com.genealogy.platform.spring.web;

import com.genealogy.platform.spring.autoconfigure.PlatformProperties;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * {@code scripts/test-contracts.mjs}) and E3.5 adds a runtime
 * guard here so the negative case fails closed at the boundary.
 *
 * <p>E3.5 also rejects client-supplied {@code role} /
 * {@code actor_role} / {@code subject} / {@code actor_id}
 * parameters in the query string or path variables — these are
 * the Semgrep rule {@code no-client-supplied-tenant-id}'s
 * Java counterpart (the rule itself is TS-only; this is the
 * belt-and-braces server-side check).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TrustedContextFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TrustedContextFilter.class);

    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String CORRELATION_HEADER = "X-Correlation-Id";
    static final String MDC_TENANT = "tenant_id";
    static final String MDC_CORRELATION = "correlation_id";

    /**
     * Parameter / query / path-variable names that the client is
     * forbidden from supplying on the wire (E3.5 mirror of
     * {@code contracts/trusted-context/policy.yaml::refuseClientSupplied.rest}).
     */
    static final Set<String> FORBIDDEN_CLIENT_PARAMS = Set.of(
            "tenantId", "tenant_id",
            "role", "actor_role",
            "subject", "actor_id");

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

        // E3.5 — reject any client-supplied tenant_id / role /
        // subject / actor_id in the query string. The body is
        // checked later by the ABAC enforcer + JSON schema; this
        // filter catches the cheap path-level attack at the edge.
        String reject = rejectClientSuppliedIdentity(request);
        if (reject != null) {
            LOG.warn(
                    "trusted context violation reason=CLIENT_SUPPLIED_TENANT_ID param={} "
                            + "correlation_id={}",
                    reject, correlationId);
            writeProblem(response, HttpServletResponse.SC_BAD_REQUEST,
                    "https://genealogy/problems/client-supplied-identity",
                    "tenant_id / role / subject must come from the trusted context, not "
                            + "from the request (" + reject + ")");
            MDC.clear();
            return;
        }

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
                writeProblem(response, HttpServletResponse.SC_BAD_REQUEST,
                        "https://genealogy/problems/tenant-missing",
                        "X-Tenant-Id required");
                MDC.clear();
                return;
            }
        } else if (tenantId.length() > properties.getTenant().getMaxIdLength()) {
            writeProblem(response, HttpServletResponse.SC_BAD_REQUEST,
                    "https://genealogy/problems/tenant-invalid",
                    "X-Tenant-Id exceeds maximum length");
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

    /**
     * Returns the first forbidden query parameter name the client
     * supplied, or {@code null} when none of the forbidden keys are
     * present. Path variables are not visible at the filter layer
     * — the controller / service-side guard catches them via the
     * ABAC enforcer + cross-service RLS interceptor.
     */
    static String rejectClientSuppliedIdentity(HttpServletRequest request) {
        if (request.getQueryString() == null) {
            return null;
        }
        for (String param : FORBIDDEN_CLIENT_PARAMS) {
            String[] values = request.getParameterValues(param);
            if (values != null && values.length > 0) {
                // An empty string is allowed (it is the framework
                // marker, not a client-supplied identity); a
                // non-empty value is the violation.
                for (String value : values) {
                    if (value != null && !value.isBlank()) {
                        return param;
                    }
                }
            }
        }
        return null;
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
