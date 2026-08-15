package com.genealogy.platform.services.media.delivery;

/**
 * Pure port for the OpenFGA relationship-graph check.
 * Mirrors `design.md` §4.2 (OpenFGA decides
 * relationships) + ADR-E0.5-06 (store-per-tenant with
 * shared model, p95 ≤ 500 ms) +
 * `ownership-catalog.md` §3 (OpenFGA platform owner:
 * platform-primary + identity team).
 *
 * <p>The implementation lives in the worker subproject
 * (E7.x / E11.x); the E7.4 orchestrator calls the port
 * with the {@code tenantScopeId} + the {@code assetId} +
 * the {@code actorPseudoId} and expects a closed-set
 * ALLOW / DENY verdict plus a reason code. The adapter is
 * expected to short-circuit when the relationship is
 * cached.
 */
public interface DeliveryOpenFgaPort {

    /**
     * Check the OpenFGA relationship tuple(s) for the
     * requested delivery. Returns
     * {@link DeliveryOpenFgaVerdict#ALLOW} when the tuple
     * grants the requested access; otherwise
     * {@link DeliveryOpenFgaVerdict#DENY} with a reason.
     */
    DeliveryOpenFgaVerdict check(
            String tenantScopeId,
            String assetId,
            String actorPseudoId,
            DeliverySubject subject);
}