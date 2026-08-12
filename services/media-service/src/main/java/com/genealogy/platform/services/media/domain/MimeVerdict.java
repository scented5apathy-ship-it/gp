package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of MIME verdicts. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.mimeVerdicts` (E7.1) + `design.md` §8.2 + §11.
 *
 * <p>{@link #SANDBOX_REQUIRED} drives the {@code DESIGN.md}
 * §11 quarantine network policy for libvips / ImageMagick /
 * Tika / Tesseract / FFmpeg. {@link #DEEP_SCAN_REQUIRED}
 * forces the worker to run the Apache Tika deep metadata
 * pass before the asset is admitted to the {@code READY}
 * state.
 */
public enum MimeVerdict {
    ALLOW,
    DENY,
    SANDBOX_REQUIRED,
    DEEP_SCAN_REQUIRED;

    public static MimeVerdict fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return MimeVerdict.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown MimeVerdict from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
