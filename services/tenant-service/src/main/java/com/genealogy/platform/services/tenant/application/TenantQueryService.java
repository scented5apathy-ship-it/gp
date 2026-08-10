package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.tenant.Tenant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only queries for the {@code Tenant} aggregate. Each method is
 * {@link Transactional} (read-only is enforced at the SQL layer by
 * RLS rather than by {@code @Transactional(readOnly = true)}) and
 * binds the runtime tenant via {@link TenantRlsTxInterceptor#bind()}
 * so the FORCE-ROW-LEVEL-SECURITY policy does not blank the result
 * set.
 *
 * <p>Read operations share the same RLS enforcement as the write
 * path; the production safety belt treats any unauthorised tenant
 * id the same as a missing tenant — both produce {@link Optional#empty()}
 * so the REST layer can answer a uniform {@code 404 Not Found}.
 */
@Service
public class TenantQueryService {

    private final TenantRepository tenantRepository;
    private final TenantRlsTxInterceptor rls;

    public TenantQueryService(
            TenantRepository tenantRepository,
            TenantRlsTxInterceptor rls) {
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.rls = Objects.requireNonNull(rls, "rls");
    }

    @Transactional
    public Optional<Results.TenantView> findById(TenantId tenantId) {
        rls.bind();
        return tenantRepository.findById(tenantId).map(this::toView);
    }

    @Transactional
    public TenantPage listForCurrentUser(int pageSize, String cursor) {
        rls.bind();
        int clamped = Math.max(1, Math.min(pageSize, 200));
        List<Tenant> rows = tenantRepository.findPage(clamped, cursor);
        String nextCursor = null;
        if (rows.size() > clamped) {
            Tenant last = rows.get(clamped - 1);
            nextCursor = TenantRepository.Cursor.encode(last.createdAt(), last.id().getValue());
            rows = rows.subList(0, clamped);
        }
        List<Results.TenantView> views = rows.stream().map(this::toView).toList();
        return new TenantPage(views, nextCursor);
    }

    private Results.TenantView toView(Tenant t) {
        return new Results.TenantView(
                t.id(),
                t.slug(),
                t.displayName(),
                t.plan(),
                t.status().name(),
                t.locale(),
                t.timezone(),
                t.calendar(),
                t.version(),
                TenantRepository.etagFor(t.version()),
                t.createdAt(),
                t.updatedAt(),
                t.suspendedAt(),
                t.deletedAt());
    }

    /** Page envelope — the controller maps this to the OpenAPI {@code TenantPage}. */
    public record TenantPage(List<Results.TenantView> items, String nextCursor) {
    }
}
