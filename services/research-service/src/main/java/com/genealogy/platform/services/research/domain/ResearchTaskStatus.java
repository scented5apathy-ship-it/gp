package com.genealogy.platform.services.research.domain;

import java.util.Locale;

/**
 * Closed-set lifecycle of a {@code ResearchTask}. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.researchTaskStatuses` (E6.1) and `requirements.md` R8.1
 * (research log task + status proof).
 *
 * <ul>
 *   <li>{@link #OPEN} — freshly created, no assignee yet.
 *   <li>{@link #IN_PROGRESS} — at least one editor has
 *       accepted the task; the assignee is mandatory.
 *   <li>{@link #BLOCKED} — the editor needs help from another
 *       role (e.g. access to a restricted archive); the
 *       {@code blockedReason} field is mandatory.
 *   <li>{@link #RESOLVED} — terminal success state; the
 *       edit carries a {@code resolvedProof} citation.
 *   <li>{@link #ABANDONED} — terminal failure state; the
 *       editor decided the line is not worth pursuing.
 * </ul>
 *
 * Transitions are restricted by
 * {@link ResearchTaskStateMachine}. The wire vocabulary is
 * enforced by the lint-research-config script.
 */
public enum ResearchTaskStatus {
    OPEN,
    IN_PROGRESS,
    BLOCKED,
    RESOLVED,
    ABANDONED;

    public static ResearchTaskStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("researchTaskStatus must not be null");
        }
        return ResearchTaskStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == ABANDONED;
    }
}
