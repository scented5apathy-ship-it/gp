package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of processing tasks. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingTasks` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The processing pipeline only accepts these four tasks;
 * the Temporal workflow fans the asset out to one task per
 * asset category (image / document / video / scanned text).
 * Each task routes to a dedicated engine (libvips /
 * Gotenberg / FFmpeg / Tesseract) per the
 * {@link ProcessingEngine} closed-set.
 */
public enum ProcessingTask {
    IMAGE_TRANSCODE,
    DOCUMENT_RENDER,
    VIDEO_TRANSCODE,
    TEXT_OCR;

    public static ProcessingTask fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ProcessingTask.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ProcessingTask from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}