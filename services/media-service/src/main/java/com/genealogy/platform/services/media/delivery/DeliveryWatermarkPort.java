package com.genealogy.platform.services.media.delivery;

/**
 * Pure port for the watermark overlay worker (libvips +
 * Skia / Chromium). The implementation lives in the
 * worker subproject (E7.x / E11.x); the E7.4 orchestrator
 * builds a {@link WatermarkOverlay} descriptor and the
 * adapter applies it to the artefact body before the
 * signed URL is issued.
 */
public interface DeliveryWatermarkPort {

    /**
     * Whether the supplied overlay mode + subject visibility
     * class require a watermark. The orchestrator consults
     * this before signing the URL.
     */
    boolean requiresWatermark(
            DeliverySubjectVisibilityClass visibilityClass,
            DeliveryWatermarkMode mode);

    /**
     * Build the canonical watermark overlay descriptor for
     * the supplied subject. The implementation MUST embed
     * the {@code actorPseudoId} + the request timestamp;
     * raw user id / email / IP / DNA are NEVER embedded
     * per the {@code DELIVERY_PSEUDONYM_IN_AUDIT}
     * invariant.
     */
    WatermarkOverlay buildOverlay(
            String actorPseudoId,
            DeliverySubjectVisibilityClass visibilityClass,
            DeliveryWatermarkMode mode);
}