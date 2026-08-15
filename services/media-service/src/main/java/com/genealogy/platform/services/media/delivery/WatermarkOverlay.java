package com.genealogy.platform.services.media.delivery;

import java.time.Instant;
import java.util.Objects;

/**
 * Watermark overlay descriptor. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryWatermarkModes + watermarkMaxOverlayChars`
 * (E7.4) + `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>The overlay carries the actor's
 * {@code actorPseudoId} + the request timestamp; raw user
 * id / email / IP / DNA are NEVER embedded per the
 * {@code DELIVERY_PSEUDONYM_IN_AUDIT} invariant. The
 * overlay text is capped at
 * {@code watermarkMaxOverlayChars=64}.
 */
public record WatermarkOverlay(
        DeliveryWatermarkMode mode,
        String overlayText,
        String actorPseudoId,
        Instant requestedAt) {

    public static final int MAX_OVERLAY_CHARS = 64;

    public WatermarkOverlay {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(overlayText, "overlayText");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (mode == DeliveryWatermarkMode.NONE) {
            throw new IllegalArgumentException(
                    "WatermarkOverlay.mode NONE requires no overlay");
        }
        if (overlayText.isBlank()
                || overlayText.length() > MAX_OVERLAY_CHARS) {
            throw new IllegalArgumentException(
                    "overlayText length out of bounds (max "
                            + MAX_OVERLAY_CHARS + ")");
        }
        if (actorPseudoId.isBlank()) {
            throw new IllegalArgumentException(
                    "actorPseudoId must not be blank");
        }
        if (!overlayText.toLowerCase()
                .contains(actorPseudoId.toLowerCase())) {
            throw new IllegalArgumentException(
                    "overlayText MUST embed actorPseudoId");
        }
    }
}