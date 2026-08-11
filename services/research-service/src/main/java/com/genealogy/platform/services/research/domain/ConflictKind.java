package com.genealogy.platform.services.research.domain;

import java.util.Locale;

/**
 * Closed-set classification of a research conflict. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.conflictKinds` (E6.1) and `requirements.md` R8.1
 * (reseach log conflict).
 *
 * <ul>
 *   <li>{@link #SOURCE_DISAGREES} — two sources disagree on
 *       the same fact (e.g. one birth date vs. another).
 *   <li>{@link #CITATION_DISAGREES} — two citations of the
 *       same source disagree on the same field (often a
 *       transcription error).
 *   <li>{@link #CLAIM_CONTRADICTS_SOURCE} — a hypothesis is
 *       no longer supported by the source that originally
 *       corroborated it.
 *   <li>{@link #HYPOTHESIS_COLLIDES} — two active hypotheses
 *       cannot simultaneously be true.
 *   <li>{@link #OTHER} — explicit escape hatch for
 *       unmodelled conflicts; the editor MUST attach a
 *       {@code kindNote} so the platform can reason about
 *       the conflict later.
 * </ul>
 *
 * The wire vocabulary is enforced by the lint-research-config
 * script.
 */
public enum ConflictKind {
    SOURCE_DISAGREES,
    CITATION_DISAGREES,
    CLAIM_CONTRADICTS_SOURCE,
    HYPOTHESIS_COLLIDES,
    OTHER;

    public static ConflictKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("conflictKind must not be null");
        }
        return ConflictKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
