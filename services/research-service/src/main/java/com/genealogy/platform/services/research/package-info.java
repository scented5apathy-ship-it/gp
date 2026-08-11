/**
 * E6.1 `research-service` research-log + citations + provenance
 * domain layer.
 *
 * Owns the {@code Repository}, {@code Source}, {@code Citation},
 * {@code ResearchTask}, {@code Hypothesis} and {@code Conflict}
 * aggregates, the closed-set of {@code SourceKind},
 * {@code CitationQuality}, {@code ResearchTaskStatus},
 * {@code HypothesisStatus}, {@code ConflictKind} and
 * {@code RepositoryKind}, the locator + transcript value
 * objects, the {@code ResearchInvariants} self-check service and
 * the in-memory {@code ProvenanceQueryService}. Mirrors
 * `contracts/research/research-policy.yaml` +
 * `requirements.md` R8 (sources, citations, research log,
 * provenance), R4.4 (certainty + provenance on every claim) and
 * `design.md` §5.3 (research log + claim lifecycle) + §5.5.
 *
 * The aggregate is intentionally framework-free: the command
 * service hydrates it, jOOQ (deferred) maps it, and the event
 * publisher emits the corresponding Avro events under
 * `contracts/events/research/v1/` (deferred in E6.x).
 *
 * Per the research-service ownership entry in
 * `ownership-catalog.md` the closed-set enums and audit
 * invariants are the platform's source of truth for the
 * research vocabulary; the linter enforced at
 * `scripts/lint-research-config.mjs` blocks drift between the
 * YAML contract and the Java enum constants.
 */
@NonNullApi
package com.genealogy.platform.services.research;

import org.springframework.lang.NonNullApi;
