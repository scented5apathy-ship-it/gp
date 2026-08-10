package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.application.persistence.MembershipRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.ids.MembershipId;
import com.genealogy.platform.services.tenant.domain.membership.Membership;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only queries for the {@code Membership} aggregate. See
 * {@link TenantQueryService} for the RLS reasoning; the cross-tenant
 * negative test in E3.2d depends on every read being tenant-bound.
 */
@Service
public class MembershipQueryService {

    private final MembershipRepository membershipRepository;
    private final TenantRlsTxInterceptor rls;

    public MembershipQueryService(
            MembershipRepository membershipRepository,
            TenantRlsTxInterceptor rls) {
        this.membershipRepository =
                Objects.requireNonNull(membershipRepository, "membershipRepository");
        this.rls = Objects.requireNonNull(rls, "rls");
    }

    @Transactional
    public Optional<Results.MembershipView> findById(MembershipId membershipId) {
        rls.bind();
        return membershipRepository.findById(membershipId).map(this::toView);
    }

    @Transactional
    public MembershipPage listForCurrentTenant(int pageSize, String cursor) {
        rls.bind();
        int clamped = Math.max(1, Math.min(pageSize, 200));
        List<Membership> rows = membershipRepository.findPage(clamped, cursor);
        String nextCursor = null;
        if (rows.size() > clamped) {
            Membership last = rows.get(clamped - 1);
            nextCursor = MembershipRepository.Cursor.encode(
                    last.invitedAt(), last.id().getValue());
            rows = rows.subList(0, clamped);
        }
        List<Results.MembershipView> views = rows.stream().map(this::toView).toList();
        return new MembershipPage(views, nextCursor);
    }

    private Results.MembershipView toView(Membership m) {
        return new Results.MembershipView(
                m.id(),
                m.tenantId(),
                m.userId(),
                m.role(),
                m.status().name(),
                m.version(),
                m.invitedAt(),
                m.joinedAt(),
                m.suspendedAt(),
                m.revokedAt());
    }

    public record MembershipPage(List<Results.MembershipView> items, String nextCursor) {
    }
}
