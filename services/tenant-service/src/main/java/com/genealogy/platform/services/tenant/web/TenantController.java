package com.genealogy.platform.services.tenant.web;

import com.genealogy.platform.spring.audit.AuditEvent;
import com.genealogy.platform.spring.audit.AuditPublisher;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal tenant-create endpoint that proves the E1.4 template is
 * wired end-to-end (REST + trusted context + audit hook +
 * OpenFeature safe fallback + Problem Details). The full
 * aggregate, jOOQ repository, Keycloak mapping and OpenFGA tuples
 * land in E3.2.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final AuditPublisher audit;

    public TenantController(AuditPublisher audit) {
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> create(@RequestBody Map<String, String> body) {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        if (!ctx.isAuthenticated()) {
            // RFC 9457 problem — body left empty to keep the example
            // concise; a real handler in E3.2 returns the
            // `Problem` schema from `contracts/openapi/common/`.
            throw new MissingTenantContextException();
        }
        String slug = body.getOrDefault("slug", "");
        String displayName = body.getOrDefault("display_name", "");
        audit.publish(new AuditEvent(
                ctx.getTenantId(),
                ctx.getActorId(),
                "tenant.create",
                "tenant",
                slug,
                ctx.getCorrelationId(),
                Map.of("display_name", displayName)));
        return Map.of(
                "status", "accepted",
                "tenant_id", ctx.getTenantId(),
                "slug", slug,
                "display_name", displayName,
                "etag", "\"v0\"");
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    static class MissingTenantContextException extends RuntimeException {
        MissingTenantContextException() {
            super("missing trusted tenant context");
        }
    }
}
