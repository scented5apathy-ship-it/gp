package com.genealogy.platform.services.research.domain;

import java.util.Locale;

/**
 * Closed-set quality grade attached to a {@link Citation}.
 * Mirrors `contracts/research/research-policy.yaml::
 * spec.citationQualities` (E6.1) and `requirements.md` R8.1
 * (quality field on every citation).
 *
 * <ul>
 *   <li>{@link #ORIGINAL} — citation points at the original
 *       record (digitised register page, archival scan).
 *   <li>{@link #TRANSCRIPT} — citation points at a verbatim
 *       transcription produced by the editor or a community
 *       volunteer; the {@code transcript} field is mandatory.
 *   <li>{@link #ABSTRACT} — citation points at a derived
 *       summary (index card, family-tree software export).
 *   <li>{@link #IMAGE} — citation points at a digital image
 *       of the original; quality is encoded in the asset
 *       (deferred to E7.2).
 *   <li>{@link #COPY} — citation points at a cited copy the
 *       editor has not personally inspected.
 *   <li>{@link #UNKNOWN} — explicit escape hatch for
 *       vendor-neutral carry; triggers a WARN finding from
 *       {@link ResearchInvariants}.
 * </ul>
 *
 * The CURRENT state of the wire vocabulary is enforced by the
 * lint-research-config script.
 */
public enum CitationQuality {
    ORIGINAL,
    TRANSCRIPT,
    ABSTRACT,
    IMAGE,
    COPY,
    UNKNOWN;

    public static CitationQuality fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("citationQuality must not be null");
        }
        return CitationQuality.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
