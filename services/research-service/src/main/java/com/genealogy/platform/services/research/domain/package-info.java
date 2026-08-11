/**
 * E6.1 research-service domain aggregates + value objects.
 *
 * <p>Mirrors `requirements.md` R8 (repositories, sources,
 * citations, transcripts, page/locator, URLs, quality grades,
 * research log, research tasks, hypotheses, conflicts, status
 * proof), R4.4 (every claim must trace back to a citation) and
 * `design.md` §5.3 + §5.5. Pure Java 21 records; no Spring
 * beans, no Flyway, no jOOQ — the executor stays
 * framework-free so the policy linter can reason about the
 * closed-set vocabulary without dragging the persistence layer
 * along.
 *
 * <p>Cross-cutting invariants and the immutable state-transition
 * matrix live in the same package:
 * {@code ResearchInvariants} for the DENY/WARN/INFO findings,
 * {@code ResearchTaskStateMachine} for the research task
 * lifecycle and {@code ProvenanceQueryService} for the
 * claim → citation → source → file walk.
 */
@NonNullApi
package com.genealogy.platform.services.research.domain;

import org.springframework.lang.NonNullApi;
