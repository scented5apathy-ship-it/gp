package com.genealogy.platform.services.research.application.config;

import com.genealogy.platform.libs.security.abac.AbacDecisionCache;
import com.genealogy.platform.libs.security.abac.AbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.DefaultAbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.OpenFgaAbacGuard;
import com.genealogy.platform.libs.security.abac.OpenFgaAbacGuard.OpenfgaCheckSupplier;
import com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptor;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the OpenFGA + ABAC stack that the
 * {@code ResearchReAuthorizationPort} depends on. The
 * production deployment swaps the noop
 * {@link OpenfgaCheckSupplier} for a real OpenFGA client; the
 * unit tests override the bean with a deterministic supplier.
 */
@Configuration
public class ResearchAbacConfig {

    /**
     * Default ABAC policy engine. Mirrors the rules in
     * {@code design.md} §6.2 + {@code privacy-and-legal-gate.md}
     * §5/§7. The engine is purely functional — no I/O, no audit
     * emission.
     */
    @Bean
    public AbacPolicyEngine researchAbacPolicyEngine() {
        return new DefaultAbacPolicyEngine();
    }

    /**
     * In-process decision cache. The TTL is the same as the
     * default ({@code 5 minutes}) used by the platform
     * {@code TenantAbacEnforcer}; tenants with longer-living
     * caches override the bean explicitly.
     */
    @Bean
    public AbacDecisionCache researchAbacDecisionCache() {
        return new AbacDecisionCache();
    }

    /**
     * Noop OpenFGA supplier. The production wiring
     * ({@code e6.1e} + the OpenFGA-as-a-service roll-out)
     * replaces this with a wrapper around the
     * {@code openfga-sdk} that maps the resource id onto the
     * correct OpenFGA tuple. For E6.1d the supplier always
     * returns a present check id so the ABAC overlay runs
     * (the Semgrep
     * {@code no-openfga-allow-without-abac} gate is enforced).
     *
     * <p>When the deployment explicitly opts into the FGA
     * client (via the
     * {@code platform.security.openfga.enabled} flag) the
     * production bean overrides this one.
     */
    @Bean
    public OpenfgaCheckSupplier researchOpenfgaCheckSupplier() {
        return (tenantId, subjectId, resourceType, resourceId, action) ->
                Optional.of("noop-check-" + java.util.UUID.randomUUID());
    }

    @Bean
    public OpenFgaAbacGuard researchOpenFgaAbacGuard(
            AbacPolicyEngine policyEngine,
            AbacDecisionCache cache,
            OpenfgaCheckSupplier supplier) {
        return new OpenFgaAbacGuard(policyEngine, cache, supplier);
    }

    /**
     * Convenience: the
     * {@link com.genealogy.platform.services.research.authorization.ResearchReAuthorizationPort}
     * references the missing-tenant-context exception via the
     * RLS interceptor. We surface it here so a @PostConstruct
     * pre-flight can spot a missing wiring.
     */
    @Bean
    public ResearchAbacConfig.Marker researchAbacWiringMarker(ResearchRlsTxInterceptor rls) {
        return new ResearchAbacConfig.Marker(rls);
    }

    /** Marker record so Spring can prove the bean graph is closed. */
    public record Marker(ResearchRlsTxInterceptor rls) {
    }
}
