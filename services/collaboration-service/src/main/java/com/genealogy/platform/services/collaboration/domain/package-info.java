/**
 * E6.2 + E6.3 + E6.4 collaboration-service domain aggregates + value objects.
 *
 * <p>Mirrors `requirements.md` R10.1 (proposal diff + source +
 * reason + scope), R10.2 (approve / reject / request change /
 * partial merge), R10.3 (optimistic concurrency conflict +
 * comparison model), R10.4 (tenant / tree admin SHALL configure
 * direct edit OR approval per role / branch / resource type),
 * R10.5 (system SHALL have comment + mention + watch +
 * assignment + notification + activity feed), R10.6 (approved
 * change traces back to proposal + reviewer), `design.md` §8.3
 * (base version + normalized patch/command, no arbitrary JSON
 * patch on forbidden fields, OpenFGA + ABAC re-check at approve
 * time, merge produces a new domain command; activity feed is
 * re-projected through the current permission state, no
 * sensitive snapshot in a notification) and ADR-E0.5-08
 * (BACKWARD evolution of closed-set vocabularies).
 *
 * <p>Cross-cutting invariants and the immutable state-transition
 * matrices live in the same package: {@code CollaborationInvariants}
 * for the DENY / WARN / INFO findings,
 * {@code ChangeProposalStateMachine} for the proposal lifecycle,
 * {@code ReviewStateMachine} for the review decision lifecycle,
 * {@code PartialMergeExecutor} for materialising a
 * decision-3 (partial merge) into a new domain command list,
 * {@code DirectEditMatrix} + {@code RoutingExecutor} for the
 * mixed-collaboration routing policy (E6.3),
 * {@code MergeCommandFactory} + {@code PatchValidator} for the
 * conflict comparison + merge command materialisation (E6.3),
 * {@code FlagsmithRolloutSync} for the Flagsmith safe-default
 * rollout sync (E6.3), and {@code Comment} +
 * {@code Mention} + {@code Watch} + {@code Assignment} +
 * {@code ActivityFeedItem} + {@code ActivityFeed} +
 * {@code NotificationHook} + {@code CommentAuthorizer} +
 * {@code ActivityFeedCollector} + {@code ActivityFeedFilter} +
 * {@code NotificationHookDispatcher} +
 * {@code ActivityRedactionFilter} for the comments / activity
 * feed / notification hook flow (E6.4).
 *
 * <p>Pure Java 21 records; no Spring beans, no Flyway, no jOOQ —
 * the executor stays framework-free so the policy linter can
 * reason about the closed-set vocabulary without dragging the
 * persistence layer along.
 */
@NonNullApi
package com.genealogy.platform.services.collaboration.domain;

import org.springframework.lang.NonNullApi;