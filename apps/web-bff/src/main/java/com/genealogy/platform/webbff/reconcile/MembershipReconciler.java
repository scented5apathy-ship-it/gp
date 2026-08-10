package com.genealogy.platform.webbff.reconcile;

import com.genealogy.platform.webbff.client.MembershipView;
import com.genealogy.platform.webbff.client.TenantServiceClient;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BFF-side tenant reconciliation (E3.5). Given the Keycloak
 * subject and the requested tenant selection, the reconciler
 * confirms that the subject holds an {@code ACTIVE} membership
 * for the selected tenant. The contract is the only place the
 * BFF consults to authorise a tenant selection; the result is
 * the source of the {@code actorRole} that lands in
 * {@code TrustedTenantContext}.
 *
 * <p>Per {@code contracts/trusted-context/policy.yaml::reconciliation}:
 * <ul>
 *   <li>{@code ACTIVE} membership → {@link TenantReconciliationStatus#ALLOWED}
 *       with the role from the membership row.</li>
 *   <li>{@code INVITED} / {@code SUSPENDED} / {@code REVOKED}
 *       membership → {@link TenantReconciliationStatus#MEMBERSHIP_NOT_ACTIVE}
 *       (rejected with 404 — never 403, to avoid leaking the
 *       existence of the foreign tenant per E3.2d DoD).</li>
 *   <li>No membership for the requested tenant →
 *       {@link TenantReconciliationStatus#TENANT_NOT_FOUND}
 *       (rejected with 404).</li>
 * </ul>
 *
 * <p>The reconciler is intentionally framework-free apart from
 * the {@link TenantServiceClient} seam so it can be unit-tested
 * with a stub client.
 */
@Component
public class MembershipReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(MembershipReconciler.class);
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final TenantServiceClient client;

    public MembershipReconciler(TenantServiceClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * Reconcile the requested tenant selection against the
     * Keycloak subject's memberships.
     *
     * @param subject the validated Keycloak subject
     * @param tenantSelection the tenant id the user is asking to act under
     * @param correlationId propagated for log correlation
     */
    public TenantReconciliationResult reconcile(
            String subject, String tenantSelection, String correlationId) {
        if (subject == null || subject.isBlank()) {
            return TenantReconciliationResult.denied(
                    TenantReconciliationStatus.SUBJECT_MISSING, null);
        }
        if (tenantSelection == null || tenantSelection.isBlank()) {
            return TenantReconciliationResult.denied(
                    TenantReconciliationStatus.TENANT_NOT_FOUND, subject);
        }

        MembershipView.Page page;
        try {
            page = client.listMemberships(tenantSelection, subject, correlationId);
        } catch (RuntimeException ex) {
            // tenant-service unreachable / 5xx — refuse closed.
            LOG.warn("tenant-service reconciliation failed tenant={} correlation_id={}",
                    tenantSelection, correlationId, ex);
            return TenantReconciliationResult.denied(
                    TenantReconciliationStatus.TENANT_NOT_FOUND, subject);
        }

        if (page == null || page.items == null || page.items.isEmpty()) {
            return TenantReconciliationResult.denied(
                    TenantReconciliationStatus.TENANT_NOT_FOUND, subject);
        }

        for (MembershipView membership : page.items) {
            if (tenantSelection.equals(membership.tenantId)
                    && subject.equals(membership.userId)) {
                if (ACTIVE_STATUS.equals(membership.status)) {
                    return TenantReconciliationResult.allowed(
                            tenantSelection, subject, membership.role);
                }
                return TenantReconciliationResult.denied(
                        TenantReconciliationStatus.MEMBERSHIP_NOT_ACTIVE, subject);
            }
        }
        return TenantReconciliationResult.denied(
                TenantReconciliationStatus.TENANT_NOT_FOUND, subject);
    }
}
