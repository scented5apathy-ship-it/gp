/**
 * E6.2 collaboration-service domain aggregates + value objects.
 *
 * <p>Mirrors `requirements.md` R10.1 (proposal diff + source +
 * reason + scope), R10.2 (approve / reject / request change /
 * partial merge), R10.3 (optimistic concurrency conflict +
 * comparison model), R10.6 (approved change traces back to
 * proposal + reviewer), `design.md` §8.3 (base version +
 * normalized patch/command, no arbitrary JSON patch on
 * forbidden fields, OpenFGA + ABAC re-check at approve time,
 * merge produces a new domain command) and ADR-E0.5-08
 * (BACKWARD evolution of closed-set vocabularies).
 *
 * <p>Cross-cutting invariants and the immutable state-transition
 * matrices live in the same package: {@code CollaborationInvariants}
 * for the DENY / WARN / INFO findings,
 * {@code ChangeProposalStateMachine} for the proposal lifecycle,
 * {@code ReviewStateMachine} for the review decision lifecycle
 * and {@code PartialMergeExecutor} for materialising a
 * decision-3 (partial merge) into a new domain command list.
 *
 * <p>Pure Java 21 records; no Spring beans, no Flyway, no jOOQ —
 * the executor stays framework-free so the policy linter can
 * reason about the closed-set vocabulary without dragging the
 * persistence layer along.
 */
@NonNullApi
package com.genealogy.platform.services.collaboration.domain;

import org.springframework.lang.NonNullApi;