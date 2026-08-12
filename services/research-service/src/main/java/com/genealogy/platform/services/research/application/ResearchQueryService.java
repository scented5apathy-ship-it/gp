package com.genealogy.platform.services.research.application;

import com.genealogy.platform.services.research.application.persistence.ProvenanceJdbcRepository;
import com.genealogy.platform.services.research.domain.Citation;
import com.genealogy.platform.services.research.domain.ids.TenantId;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Research query service. Read-only counterpart to
 * {@link ResearchCommandService}; every public method is
 * {@link Transactional} and the first statement is
 * {@code rls.bind()} so the SELECT inherits the same RLS
 * posture as the writes.
 */
@Service
public class ResearchQueryService {

    private final ProvenanceJdbcRepository provenanceJdbcRepository;
    private final com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptor rls;

    public ResearchQueryService(
            ProvenanceJdbcRepository provenanceJdbcRepository,
            com.genealogy.platform.services.research.application.rls.ResearchRlsTxInterceptor rls) {
        this.provenanceJdbcRepository =
                Objects.requireNonNull(provenanceJdbcRepository, "provenanceJdbcRepository");
        this.rls = Objects.requireNonNull(rls, "rls");
    }

    @Transactional
    public Results.ProvenanceChainView traverseByClaim(String claimReference) {
        rls.bind();
        TenantId tenantId = currentTenantId();
        List<ProvenanceJdbcRepository.ProvenanceHop> hops =
                provenanceJdbcRepository.traverseByClaim(tenantId.getValue(), claimReference);
        List<Results.ProvenanceHopView> viewHops = new ArrayList<>();
        for (ProvenanceJdbcRepository.ProvenanceHop hop : hops) {
            viewHops.add(new Results.ProvenanceHopView(
                    hop.citationId(),
                    hop.sourceId(),
                    hop.sourceTitle(),
                    hop.sourceKind(),
                    hop.repositoryId(),
                    hop.repositoryName(),
                    hop.repositoryKind(),
                    hop.quality(),
                    toWireDisposition(hop.disposition()),
                    hop.certainty(),
                    hop.confidence(),
                    hop.locatorRaw(),
                    hop.quotedText()));
        }
        return new Results.ProvenanceChainView(tenantId.getValue(), claimReference, viewHops);
    }

    private static Results.Disposition toWireDisposition(Citation.Disposition d) {
        return Results.Disposition.valueOf(d.name());
    }

    private TenantId currentTenantId() {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        String tenantId = ctx.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new com.genealogy.platform.services.research.application.rls
                    .ResearchRlsTxInterceptor.MissingTenantContextException(
                    "trusted tenant context is required");
        }
        return new TenantId(tenantId);
    }
}
