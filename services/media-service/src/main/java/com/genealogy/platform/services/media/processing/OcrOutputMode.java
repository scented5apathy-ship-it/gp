package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of OCR output modes. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.ocrOutputModes` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@link #TEXT} is the plain text output (used by the
 * search index). {@link #HOCR} is the hOCR XML output
 * (preserves bounding boxes for the searchable PDF).
 * {@link #PDF_SEARCHABLE} is the searchable PDF overlay
 * produced by Tesseract + a PDF post-processor.
 */
public enum OcrOutputMode {
    TEXT,
    HOCR,
    PDF_SEARCHABLE;

    public static OcrOutputMode fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return OcrOutputMode.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown OcrOutputMode from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}