package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set source-reference kinds. Mirrors
 * {@code contracts/genealogy/event-claim-policy.yaml::
 * spec.sourceReferenceKinds} (E4.5). A claim MUST carry at
 * least one source reference (R4.4 / R8); the kind tells
 * the renderer how to render a citation chip.
 */
public enum SourceReferenceKind {
    REPOSITORY_CITATION,
    DOCUMENT_CITATION,
    TRANSCRIPT_CITATION,
    PAGE_LOCATOR,
    URL,
    MEDIA_ATTACHMENT,
    INTERVIEW_NOTE;

    public static SourceReferenceKind fromWire(String wire) {
        if (wire == null) {
            return URL;
        }
        return SourceReferenceKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
