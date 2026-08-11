package com.genealogy.platform.services.research.domain;

import java.util.Objects;

/**
 * Pure state-transition matrix for a {@link ResearchTask}.
 * Mirrors `contracts/research/research-policy.yaml::
 * spec.researchTaskStatusMatrix` (E6.1) + `requirements.md`
 * R8.1 (research log task + status proof) + `design.md` §5.5.
 *
 * <p>The matrix is intentionally restricted:
 *
 * <ul>
 *   <li>{@code OPEN} → {@code IN_PROGRESS}, {@code BLOCKED},
 *       {@code ABANDONED}.
 *   <li>{@code IN_PROGRESS} → {@code BLOCKED},
 *       {@code RESOLVED}, {@code ABANDONED}.
 *   <li>{@code BLOCKED} → {@code IN_PROGRESS},
 *       {@code ABANDONED}.
 *   <li>{@code RESOLVED} / {@code ABANDONED} are terminal.
 * </ul>
 *
 * The same schema is mirrored for {@link HypothesisStatus}
 * in {@link HypothesisStateMachine}.
 */
public final class ResearchTaskStateMachine {

    private ResearchTaskStateMachine() {
    }

    public static boolean canTransition(ResearchTaskStatus from, ResearchTaskStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return false;
        }
        return switch (from) {
            case OPEN -> to == ResearchTaskStatus.IN_PROGRESS
                    || to == ResearchTaskStatus.BLOCKED
                    || to == ResearchTaskStatus.ABANDONED;
            case IN_PROGRESS -> to == ResearchTaskStatus.BLOCKED
                    || to == ResearchTaskStatus.RESOLVED
                    || to == ResearchTaskStatus.ABANDONED;
            case BLOCKED -> to == ResearchTaskStatus.IN_PROGRESS
                    || to == ResearchTaskStatus.ABANDONED;
            case RESOLVED, ABANDONED -> false;
        };
    }

    public static void assertTransition(ResearchTaskStatus from, ResearchTaskStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "illegal researchTaskStatus transition: " + from + " -> " + to);
        }
    }

    public static ResearchTask transition(
            ResearchTask task,
            ResearchTaskStatus to,
            String blockedReason,
            String resolvedProof) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(to, "to");
        assertTransition(task.status(), to);
        return task.withStatus(to, blockedReason, resolvedProof);
    }
}
