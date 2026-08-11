package com.genealogy.platform.services.research.domain;

import java.util.Locale;

/**
 * Closed-set classification of a research Source. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.sourceKinds` (E6.1) and `requirements.md` R8.1
 * (repository, source, citation, transcript, page/locator,
 * URL, quality, attachment).
 *
 * <ul>
 *   <li>{@link #PRIMARY} — first-hand record produced by a
 *       participant or witness to the event (birth
 *       certificate, marriage register, household census).
 *   <li>{@link #SECONDARY} — second-hand record produced
 *       shortly after the event by someone with knowledge
 *       of the participant (newspaper obituary, memorial
 *       card, contemporary letter).
 *   <li>{@link #DERIVED} — derivative work that synthesises
 *       other sources (compiled family tree, genealogy
 *       website, magazine article).
 *   <li>{@link #ARCHIVE} — repository descriptor (a fonds,
 *       a collection, a digital archive URL) that points to
 *       but does not itself contain the record.
 *   <li>{@link #FINDING_AID} — index / catalogue entry
 *       (Namenregister, register of baptisms) used to
 *       locate the underlying primary record.
 *   <li>{@link #OTHER} — explicit escape hatch for
 *       vendor-neutral carry; the editor MUST add a
 *       {@code repositoryNote} so the platform can reason
 *       about the source later.
 * </ul>
 *
 * Adding a new kind requires an ADR supersession of the
 * policy; the lint-research-config script enforces the
 * closed-set vocabulary.
 */
public enum SourceKind {
    PRIMARY,
    SECONDARY,
    DERIVED,
    ARCHIVE,
    FINDING_AID,
    OTHER;

    public static SourceKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("sourceKind must not be null");
        }
        return SourceKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    /**
     * Returns {@code true} when the kind represents a source
     * that may itself anchor a citation (i.e. anything other
     * than {@link #ARCHIVE} and {@link #FINDING_AID}, which
     * only point to a record).
     */
    public boolean isAnchor() {
        return this == PRIMARY
                || this == SECONDARY
                || this == DERIVED
                || this == OTHER;
    }
}
