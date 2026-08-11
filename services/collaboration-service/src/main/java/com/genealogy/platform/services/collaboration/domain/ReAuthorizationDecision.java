package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of the {@code ReAuthorizationPort} check the
 * executor performs at submit + approve + partial-merge time.
 * Mirrors `requirements.md` R10.6 (approved change traces to
 * a reviewer that still had permission at review time) +
 * `design.md` §8.3 (OpenFGA + ABAC re-check at approve time).
 *
 * <p>{@code evaluatedAt} MUST be ≤ {@code Instant.now()} on
 * capture. The collaboration-service never persists the
 * OpenFGA + ABAC raw verdict — only the closed-set
 * {@link ReAuthorizationOutcome} + the correlationId that
 * allows SRE to stitch the trace.
 */
public record ReAuthorizationDecision(
        ReAuthorizationOutcome outcome,
        String actorPseudoId,
        String correlationId,
        String reasonCode,
        Instant evaluatedAt) {

    public ReAuthorizationDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (actorPseudoId.isBlank()) {
            throw new IllegalArgumentException("actorPseudoId must not be blank");
        }
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (actorPseudoId.length() > 128) {
            throw new IllegalArgumentException(
                    "actorPseudoId exceeds 128 characters");
        }
        if (correlationId.length() > 128) {
            throw new IllegalArgumentException(
                    "correlationId exceeds 128 characters");
        }
        if (reasonCode != null && reasonCode.length() > 64) {
            throw new IllegalArgumentException(
                    "reasonCode exceeds 64 characters");
        }
        if (reasonCode != null && reasonCode.isBlank()) {
            reasonCode = null;
        }
    }

    public static ReAuthorizationDecision allow(
            String actorPseudoId,
            String correlationId,
            Instant evaluatedAt) {
        return new ReAuthorizationDecision(
                ReAuthorizationOutcome.ALLOW,
                actorPseudoId, correlationId, null, evaluatedAt);
    }

    public static ReAuthorizationDecision deny(
            String actorPseudoId,
            String correlationId,
            String reasonCode,
            Instant evaluatedAt) {
        return new ReAuthorizationDecision(
                ReAuthorizationOutcome.DENY,
                actorPseudoId, correlationId, reasonCode, evaluatedAt);
    }

    public static ReAuthorizationDecision abacDeny(
            String actorPseudoId,
            String correlationId,
            String reasonCode,
            Instant evaluatedAt) {
        return new ReAuthorizationDecision(
                ReAuthorizationOutcome.ABAC_DENY,
                actorPseudoId, correlationId, reasonCode, evaluatedAt);
    }

    public boolean isAllow() {
        return outcome == ReAuthorizationOutcome.ALLOW;
    }
}