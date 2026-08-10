package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.application.persistence.EntitlementRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.entitlement.Entitlement;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only query for the {@code Entitlement} entity. The
 * entitlement is a single-row-per-tenant entity, so the read shape
 * is exactly one {@link Results.EntitlementView}.
 */
@Service
public class EntitlementQueryService {

    private final EntitlementRepository entitlementRepository;
    private final TenantRlsTxInterceptor rls;

    public EntitlementQueryService(
            EntitlementRepository entitlementRepository,
            TenantRlsTxInterceptor rls) {
        this.entitlementRepository =
                Objects.requireNonNull(entitlementRepository, "entitlementRepository");
        this.rls = Objects.requireNonNull(rls, "rls");
    }

    @Transactional
    public Optional<Results.EntitlementView> findForTenant(TenantId tenantId) {
        rls.bind();
        return entitlementRepository.findByTenantId(tenantId).map(this::toView);
    }

    private Results.EntitlementView toView(Entitlement e) {
        return new Results.EntitlementView(
                e.tenantId(),
                e.plan(),
                e.memberLimit(),
                e.treeLimit(),
                e.storageLimitMb(),
                e.retentionDays(),
                e.billingExternalId(),
                e.updatedAt());
    }
}
