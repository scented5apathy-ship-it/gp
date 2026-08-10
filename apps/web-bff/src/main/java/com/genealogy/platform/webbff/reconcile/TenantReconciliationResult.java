package com.genealogy.platform.webbff.reconcile;

import java.util.Objects;

/**
 * Read-only result of a single BFF tenant reconciliation (E3.5).
 * Returned by {@link MembershipReconciler#reconcile(String, String)}
 * so the {@link TenantSelectionGuard} can decide whether to
 * forward, reject with 404, or return 401.
 *
 * <p>Per {@code contracts/trusted-context/policy.yaml::reconciliation}
 * the reconciler must return {@link TenantReconciliationStatus#ALLOWED}
 * only when the membership is {@code ACTIVE}; {@code SUSPENDED} /
 * {@code REVOKED} / {@code INVITED} memberships authorise no
 * tenant selection.
 */
public record TenantReconciliationResult(
        TenantReconciliationStatus status,
        String tenantId,
        String actorId,
        String actorRole) {

    public TenantReconciliationResult {
        Objects.requireNonNull(status, "status");
    }

    public boolean isAllowed() {
        return status == TenantReconciliationStatus.ALLOWED;
    }

    public static TenantReconciliationResult allowed(
            String tenantId, String actorId, String actorRole) {
        return new TenantReconciliationResult(
                TenantReconciliationStatus.ALLOWED, tenantId, actorId, actorRole);
    }

    public static TenantReconciliationResult denied(
            TenantReconciliationStatus status, String actorId) {
        return new TenantReconciliationResult(status, null, actorId, null);
    }
}
