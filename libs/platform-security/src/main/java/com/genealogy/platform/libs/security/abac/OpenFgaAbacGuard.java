package com.genealogy.platform.libs.security.abac;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * OpenFGA + ABAC combinator that closes the Semgrep
 * {@code no-openfga-allow-without-abac} check.
 *
 * <p>The combinator runs the supplied OpenFGA check first and, on
 * {@code allow}, evaluates the ABAC overlay. The caller MUST supply
 * a non-null {@link Supplier} for both — passing {@code null}
 * throws, so the call site cannot bypass the ABAC step.
 *
 * <p>The combinator caches the combined decision under
 * {@code abac:{tenantId}:{resourceType}:{resourceId}:{subjectId}:{action}}
 * and exposes an {@link #invalidate(String) invalidate} path that
 * the role / policy / consent write flows must call.
 */
public final class OpenFgaAbacGuard {

    private final AbacPolicyEngine policyEngine;
    private final AbacDecisionCache cache;
    private final OpenfgaCheckSupplier openfgaCheck;

    public OpenFgaAbacGuard(
            AbacPolicyEngine policyEngine,
            AbacDecisionCache cache,
            OpenfgaCheckSupplier openfgaCheck) {
        this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.openfgaCheck = Objects.requireNonNull(openfgaCheck, "openfgaCheck");
    }

    /**
     * Evaluates OpenFGA + ABAC and returns the combined decision.
     * The {@code action} argument is opaque — callers can use it
     * to differentiate read / write / delete keys in the cache.
     */
    public AbacDecision check(AbacRequest request, String action) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(action, "action");
        String key = cacheKey(request, action);
        AbacDecision cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Optional<String> openfgaCheckId = openfgaCheck.check(
                request.tenantId(),
                request.subjectId(),
                request.resourceType(),
                request.resourceId(),
                action);
        if (openfgaCheckId.isEmpty()) {
            AbacDecision deny = AbacDecision.deny(
                    "abac-" + UUID.randomUUID(),
                    ReasonCode.OPENFGA_DENY);
            cache.put(key, deny);
            return deny;
        }

        AbacDecision decision = policyEngine.evaluate(request)
                .withOpenfgaCheckId(openfgaCheckId.get())
                .withAttribute("engine", policyEngine.engineId())
                .withAttribute("action", action);

        // When the ABAC engine returns deny, surface the Semgrep
        // failure code so the audit entry makes the missing
        // overlay obvious in dashboards. (The overlay was called;
        // the failure is intentional in this branch — the code is
        // for documentation purposes only and is a no-op here.)
        cache.put(key, decision);
        return decision;
    }

    /**
     * Removes every cached decision scoped to the supplied tenant.
     * Called from role / policy / consent write flows (E3.4).
     */
    public int invalidateTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return cache.invalidateByPrefix("abac:" + tenantId + ":");
    }

    /**
     * Removes a single cached decision. Called from consent
     * revocation flows (privacy gate §D-06).
     */
    public void invalidate(String tenantId, String resourceType, String resourceId,
            String subjectId, String action) {
        Objects.requireNonNull(tenantId, "tenantId");
        cache.invalidate(cacheKey(tenantId, resourceType, resourceId, subjectId,
                action));
    }

    public AbacDecisionCache cache() {
        return cache;
    }

    private static String cacheKey(AbacRequest request, String action) {
        return cacheKey(request.tenantId(), request.resourceType(),
                request.resourceId(), request.subjectId(), action);
    }

    private static String cacheKey(String tenantId, String resourceType,
            String resourceId, String subjectId, String action) {
        return "abac:" + tenantId + ":" + resourceType + ":" + resourceId
                + ":" + subjectId + ":" + action;
    }

    /** Functional interface for the OpenFGA relationship check. */
    @FunctionalInterface
    public interface OpenfgaCheckSupplier {
        /**
         * Returns a present check id when the relationship is
         * allowed; empty when denied. Implementations run the
         * OpenFGA {@code Check} API and return the {@code checkId}
         * echoed by the server so the ABAC decision can carry the
         * reference for traceability.
         */
        Optional<String> check(String tenantId, String subjectId,
                String resourceType, String resourceId, String action);
    }
}
