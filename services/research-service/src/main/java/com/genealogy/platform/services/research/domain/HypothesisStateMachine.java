package com.genealogy.platform.services.research.domain;

import java.util.Objects;

/**
 * Pure state-transition matrix for a {@link Hypothesis}.
 * Mirrors `contracts/research/research-policy.yaml::
 * spec.hypothesisStatusMatrix` (E6.1) + `requirements.md`
 * R8.1 (hypothesis + status proof) + `design.md` §5.5.
 *
 * <p>The matrix is intentionally restricted:
 *
 * <ul>
 *   <li>{@code DRAFT} → {@code ACTIVE}, {@code REFUTED}.
 *   <li>{@code ACTIVE} → {@code CORROBORATED},
 *       {@code REFUTED}, {@code SUPERSEDED}.
 *   <li>{@code CORROBORATED} → {@code REFUTED},
 *       {@code SUPERSEDED}.
 *   <li>{@code REFUTED} / {@code SUPERSEDED} are terminal.
 * </ul>
 */
public final class HypothesisStateMachine {

    private HypothesisStateMachine() {
    }

    public static boolean canTransition(HypothesisStatus from, HypothesisStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return false;
        }
        return switch (from) {
            case DRAFT -> to == HypothesisStatus.ACTIVE
                    || to == HypothesisStatus.REFUTED;
            case ACTIVE -> to == HypothesisStatus.CORROBORATED
                    || to == HypothesisStatus.REFUTED
                    || to == HypothesisStatus.SUPERSEDED;
            case CORROBORATED -> to == HypothesisStatus.REFUTED
                    || to == HypothesisStatus.SUPERSEDED;
            case REFUTED, SUPERSEDED -> false;
        };
    }

    public static void assertTransition(HypothesisStatus from, HypothesisStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "illegal hypothesisStatus transition: " + from + " -> " + to);
        }
    }

    public static Hypothesis transition(
            Hypothesis hypothesis,
            HypothesisStatus to) {
        Objects.requireNonNull(hypothesis, "hypothesis");
        Objects.requireNonNull(to, "to");
        assertTransition(hypothesis.status(), to);
        return hypothesis.withStatus(to);
    }
}
