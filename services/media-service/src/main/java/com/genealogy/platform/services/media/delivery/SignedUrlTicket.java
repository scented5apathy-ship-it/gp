package com.genealogy.platform.services.media.delivery;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Signed-URL ticket issued by the protected-delivery
 * orchestrator. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.signedUrlTtlCeilingSeconds + signedUrlTtlMinimumSeconds`
 * (E7.4) + `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>The ticket carries:
 * <ul>
 *   <li>{@code url} — the S3 / MinIO signed URL the
 *       application layer forwards to the requester.</li>
 *   <li>{@code ttlSeconds} — the time-to-live; capped at
 *       {@code signedUrlTtlCeilingSeconds=900} per the
 *       {@code DELIVERY_TTL_CEILING_ENFORCED} invariant.</li>
 *   <li>{@code method} — the HTTP method (default
 *       {@link SignedUrlMethod#GET}).</li>
 *   <li>{@code disposition} — the content disposition.</li>
 *   <li>{@code watermark} — the optional watermark overlay
 *       (null when no overlay is required).</li>
 *   <li>{@code contentType} — the canonical content type
 *       of the underlying artefact.</li>
 *   <li>{@code issuedAt} + {@code expiresAt} — RFC-3339
 *       timestamps for audit + revoke.</li>
 * </ul>
 */
public record SignedUrlTicket(
        String deliveryId,
        String derivedObjectKey,
        String url,
        SignedUrlMethod method,
        DeliveryDisposition disposition,
        DeliveryContentType contentType,
        WatermarkOverlay watermark,
        int ttlSeconds,
        Instant issuedAt,
        Instant expiresAt) {

    public static final int TTL_CEILING_SECONDS = 900;
    public static final int TTL_MINIMUM_SECONDS = 15;

    public SignedUrlTicket {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(derivedObjectKey, "derivedObjectKey");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (ttlSeconds < TTL_MINIMUM_SECONDS
                || ttlSeconds > TTL_CEILING_SECONDS) {
            throw new IllegalArgumentException(
                    "ttlSeconds out of bounds ["
                            + TTL_MINIMUM_SECONDS + ", "
                            + TTL_CEILING_SECONDS + "]");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be > issuedAt");
        }
    }

    public Optional<WatermarkOverlay> watermarkOpt() {
        return Optional.ofNullable(watermark);
    }

    public boolean hasWatermark() {
        return watermark != null;
    }

    public long remainingSeconds(Instant now) {
        Objects.requireNonNull(now, "now");
        long s = expiresAt.getEpochSecond() - now.getEpochSecond();
        return Math.max(0L, s);
    }
}