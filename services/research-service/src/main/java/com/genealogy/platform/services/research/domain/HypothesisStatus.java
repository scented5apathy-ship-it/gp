package com.genealogy.platform.services.research.domain;

import java.util.Locale;

/**
 * Closed-set lifecycle of a {@code Hypothesis}. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.hypothesisStatuses` (E6.1) and `requirements.md` R8.1
 * (hypothesis + status proof).
 *
 * <ul>
 *   <li>{@link #DRAFT} — recorded but not yet shared with the
 *       research log; the editor is the only consumer.
 *   <li>{@link #ACTIVE} — visible to the rest of the research
 *       log; at least one assignment has been made.
 *   <li>{@link #CORROBORATED} — at least one citation
 *       supports the hypothesis; the
 *       {@code corroboratingCitations} list is mandatory.
 *   <li>{@link #REFUTED} — at least one citation refutes the
 *       hypothesis; the refuting citation is mandatory.
 *   <li>{@link #SUPERSEDED} — the hypothesis was replaced by a
 *       new one; the {@code supersededByHypothesisId} back
 *       reference is mandatory.
 * </ul>
 *
 * The state transitions are enforced by
 * {@link ResearchTaskStateMachine}. The wire vocabulary is
 * enforced by the lint-research-config script.
 */
public enum HypothesisStatus {
    DRAFT,
    ACTIVE,
    CORROBORATED,
    REFUTED,
    SUPERSEDED;

    public static HypothesisStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("hypothesisStatus must not be null");
        }
        return HypothesisStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isTerminal() {
        return this == REFUTED || this == SUPERSEDED;
    }
}
