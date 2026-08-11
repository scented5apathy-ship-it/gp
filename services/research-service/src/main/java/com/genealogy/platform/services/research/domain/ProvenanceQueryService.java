package com.genealogy.platform.services.research.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory provenance query. A provenance query traces a
 * claim back to the source that supports it: claim →
 * citation → source → repository + attachments. Mirrors
 * `requirements.md` R8.4 (provenance from fact to source) +
 * `design.md` §5.5.
 *
 * <p>E6.1 ships the in-memory executor only. The relational
 * schema (Flyway migration + jOOQ) lands in E6.x; the
 * executor is intentionally free of SQL so the policy can
 * evolve with the contract.
 *
 * <p>Two traversal modes are supported:
 *
 * <ul>
 *   <li>{@link #traverse(String, String)} — by tenant + claim
 *       reference. Returns every citation, with the source it
 *       points at and the repository that owns the source.
 *   <li>{@link #traverseByCitation(String, String)} — by
 *       tenant + citation id. Returns a single citation chain.
 * </ul>
 *
 * <p>The result is a flat, JSON-friendly record so the
 * presentation layer can render provenance without
 * interpreting nested aggregates.
 */
public final class ProvenanceQueryService {

    private final Map<String, Map<String, Citation>> citationsByTenant;
    private final Map<String, Map<String, Source>> sourcesByTenant;
    private final Map<String, Map<String, Repository>> repositoriesByTenant;

    public ProvenanceQueryService(
            Map<String, Map<String, Citation>> citationsByTenant,
            Map<String, Map<String, Source>> sourcesByTenant,
            Map<String, Map<String, Repository>> repositoriesByTenant) {
        this.citationsByTenant = citationsByTenant == null
                ? Map.of() : defensiveCopy(citationsByTenant);
        this.sourcesByTenant = sourcesByTenant == null
                ? Map.of() : defensiveCopy(sourcesByTenant);
        this.repositoriesByTenant = repositoriesByTenant == null
                ? Map.of() : defensiveCopy(repositoriesByTenant);
    }

    public ProvenanceChain traverse(String tenantId, String claimReference) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(claimReference, "claimReference");
        if (claimReference.isBlank()) {
            throw new IllegalArgumentException("claimReference must not be blank");
        }
        Map<String, Citation> citations = citationsByTenant.get(tenantId);
        if (citations == null) {
            return new ProvenanceChain(tenantId, claimReference, List.of());
        }
        List<ProvenanceHop> hops = new ArrayList<>();
        for (Citation citation : citations.values()) {
            if (!citation.claimReference().equals(claimReference)) {
                continue;
            }
            hops.add(buildHop(citation, sourcesByTenant.get(tenantId),
                    repositoriesByTenant.get(tenantId)));
        }
        Collections.sort(hops, (a, b) -> {
            int c = a.certainty().compareTo(b.certainty());
            if (c != 0) {
                return c;
            }
            return a.citationId().compareTo(b.citationId());
        });
        return new ProvenanceChain(tenantId, claimReference, List.copyOf(hops));
    }

    public ProvenanceChain traverseByCitation(String tenantId, String citationId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(citationId, "citationId");
        Map<String, Citation> citations = citationsByTenant.get(tenantId);
        if (citations == null) {
            return new ProvenanceChain(tenantId, citationId, List.of());
        }
        Citation citation = citations.get(citationId);
        if (citation == null) {
            return new ProvenanceChain(tenantId, citationId, List.of());
        }
        return new ProvenanceChain(tenantId, citation.claimReference(),
                List.of(buildHop(citation, sourcesByTenant.get(tenantId),
                        repositoriesByTenant.get(tenantId))));
    }

    public int citationCount(String tenantId, String claimReference) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(claimReference, "claimReference");
        Map<String, Citation> citations = citationsByTenant.get(tenantId);
        if (citations == null) {
            return 0;
        }
        int count = 0;
        for (Citation citation : citations.values()) {
            if (citation.claimReference().equals(claimReference)) {
                count += 1;
            }
        }
        return count;
    }

    private ProvenanceHop buildHop(
            Citation citation,
            Map<String, Source> sources,
            Map<String, Repository> repositories) {
        Source source = sources == null ? null : sources.get(citation.sourceId().resourceId());
        Repository repository = null;
        if (source != null && repositories != null) {
            repository = repositories.get(source.repositoryId().resourceId());
        }
        return new ProvenanceHop(
                citation.id().resourceId(),
                citation.sourceId().resourceId(),
                source == null ? null : source.title(),
                source == null ? null : source.sourceKind(),
                source == null ? null : source.repositoryId().resourceId(),
                repository == null ? null : repository.name(),
                repository == null ? null : repository.kind(),
                citation.quality(),
                citation.disposition(),
                citation.certainty(),
                citation.confidence(),
                citation.locator() == null ? null : citation.locator().raw(),
                citation.quotedText());
    }

    private static <K, V> Map<K, Map<K, V>> defensiveCopy(Map<K, Map<K, V>> input) {
        Map<K, Map<K, V>> out = new LinkedHashMap<>();
        for (Map.Entry<K, Map<K, V>> e : input.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Flat provenance chain for a single claim. Mirrors
     * `contracts/research/research-policy.yaml::
     * spec.provenanceChainSchema`.
     */
    public record ProvenanceChain(
            String tenantId,
            String claimReference,
            List<ProvenanceHop> hops) {
        public ProvenanceChain {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(claimReference, "claimReference");
            hops = hops == null ? List.of() : List.copyOf(hops);
        }

        public boolean isEmpty() {
            return hops.isEmpty();
        }

        public int size() {
            return hops.size();
        }
    }

    /**
     * One hop in the chain: citation → source → repository.
     */
    public record ProvenanceHop(
            String citationId,
            String sourceId,
            String sourceTitle,
            SourceKind sourceKind,
            String repositoryId,
            String repositoryName,
            RepositoryKind repositoryKind,
            CitationQuality quality,
            Citation.Disposition disposition,
            Certainty certainty,
            Double confidence,
            String locatorRaw,
            String quotedText) {
        public ProvenanceHop {
            Objects.requireNonNull(citationId, "citationId");
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(quality, "quality");
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(certainty, "certainty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Convenience builder for tests + production wiring. */
    public static final class Builder {
        private final Map<String, Map<String, Citation>> citations = new LinkedHashMap<>();
        private final Map<String, Map<String, Source>> sources = new LinkedHashMap<>();
        private final Map<String, Map<String, Repository>> repositories = new LinkedHashMap<>();

        public Builder addCitation(Citation citation) {
            Objects.requireNonNull(citation, "citation");
            citations.computeIfAbsent(citation.id().tenantId(), k -> new LinkedHashMap<>())
                    .put(citation.id().resourceId(), citation);
            return this;
        }

        public Builder addSource(Source source) {
            Objects.requireNonNull(source, "source");
            sources.computeIfAbsent(source.id().tenantId(), k -> new LinkedHashMap<>())
                    .put(source.id().resourceId(), source);
            return this;
        }

        public Builder addRepository(Repository repository) {
            Objects.requireNonNull(repository, "repository");
            repositories.computeIfAbsent(repository.id().tenantId(), k -> new LinkedHashMap<>())
                    .put(repository.id().resourceId(), repository);
            return this;
        }

        public ProvenanceQueryService build() {
            return new ProvenanceQueryService(citations, sources, repositories);
        }
    }
}
