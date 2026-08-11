package com.genealogy.platform.services.research.domain;

import java.util.Locale;

/**
 * Closed-set attachment kind for a {@code Source} or
 * {@code Citation}. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.attachmentKinds` (E6.1) and `requirements.md` R8.1
 * (attachment reference).
 *
 * <p>Attachments are pointers to objects living in the
 * media-service (E7) — they are NEVER raw blobs and NEVER
 * DNA. The {@code PointerStrategy} is intentionally separate
 * from the media-service bucket policy so the research
 * domain stays free of object-store identifiers.
 *
 * <ul>
 *   <li>{@link #DIGITAL_IMAGE} — scanned page / photograph
 *       (jpg, png, tiff).
 *   <li>{@link #PDF} — digitised PDF (or text-layer PDF).
 *   <li>{@link #AUDIO} — oral history / interview recording.
 *   <li>{@link #VIDEO} — video recording.
 *   <li>{@link #TRANSCRIPT} — verbatim textual transcript.
 *   <li>{@link #EXTERNAL_URL} — external link (requires
 *       {@code canonicalUrl} + privacy redaction per
 *       R7.4 / R10).
 *   <li>{@link #OTHER} — explicit escape hatch.
 * </ul>
 */
public enum AttachmentKind {
    DIGITAL_IMAGE,
    PDF,
    AUDIO,
    VIDEO,
    TRANSCRIPT,
    EXTERNAL_URL,
    OTHER;

    public static AttachmentKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("attachmentKind must not be null");
        }
        return AttachmentKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
