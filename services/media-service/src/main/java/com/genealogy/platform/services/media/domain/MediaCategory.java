package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of media categories. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.mediaCategories` (E7.1) + `requirements.md` R9.1.
 * The category drives the MIME allow-list and the worker
 * pipeline (E7.2 + E7.3).
 */
public enum MediaCategory {
    IMAGE,
    AUDIO,
    VIDEO,
    DOCUMENT,
    PDF,
    SVG,
    ARCHIVE,
    DNA_FASTQ;

    public static MediaCategory fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return MediaCategory.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown MediaCategory from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
