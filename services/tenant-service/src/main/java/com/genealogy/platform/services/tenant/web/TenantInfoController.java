package com.genealogy.platform.services.tenant.web;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import com.genealogy.platform.spring.featureflags.SafeFeatureClient;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness + readiness + version probe for {@code tenant-service}.
 *
 * <p>Deliberately split from the E2.1 Helm chart probes: the
 * service exposes a {@code /api/v1/info} endpoint that returns the
 * running version, the trusted tenant context (only when the
 * request carries a valid token) and the OpenFeature safe-fallback
 * status. Kubernetes probes hit {@code /actuator/health/liveness}
 * and {@code /actuator/health/readiness} directly.
 */
@RestController
@RequestMapping("/api/v1")
public class TenantInfoController {

    private final String serviceVersion;
    private final SafeFeatureClient featureClient;

    public TenantInfoController(
            @Value("${spring.application.name}") String serviceName,
            @Value("${platform.otel.service-name:}") String otelServiceName,
            SafeFeatureClient featureClient) {
        this.serviceVersion = otelServiceName == null || otelServiceName.isBlank() ? serviceName : otelServiceName;
        this.featureClient = featureClient;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        return Map.of(
                "service", "tenant-service",
                "version", serviceVersion,
                "now", Instant.now().toString(),
                "feature_provider", featureClient.getString("platform.feature.provider", "noop"),
                "authenticated", ctx.isAuthenticated(),
                "tenant_id", ctx.getTenantId() == null ? "" : ctx.getTenantId());
    }
}
