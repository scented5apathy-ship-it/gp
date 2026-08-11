package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Application port the {@code ChangeProposal} + {@code Review}
 * executors use to re-check OpenFGA + ABAC at submit /
 * approve / partial-merge time. Mirrors
 * `requirements.md` R10.6 (approved change traces back to
 * a reviewer that still had permission at review time) +
 * `design.md` §8.3 (review re-checks OpenFGA + ABAC at
 * approve time).
 *
 * <p>The port is intentionally narrow: it returns a
 * {@link ReAuthorizationDecision} carrying only the
 * closed-set outcome + correlation id + reason code. The
 * collaboration-service MUST NOT log the raw OpenFGA / ABAC
 * verdict anywhere.
 */
@FunctionalInterface
public interface ReAuthorizationPort {

    ReAuthorizationDecision evaluate(
            String tenantId,
            String actorPseudoId,
            String correlationId,
            ChangeProposal proposal,
            Review review);

    /**
     * Convenience that re-checks OpenFGA + ABAC when the
     * proposal is being submitted. The {@code review} is
     * {@code null} on submit; non-null on approve /
     * partial-merge.
     */
    default ReAuthorizationDecision evaluateOnSubmit(
            String tenantId,
            String actorPseudoId,
            String correlationId,
            ChangeProposal proposal) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(proposal, "proposal");
        return evaluate(tenantId, actorPseudoId, correlationId, proposal, null);
    }
}