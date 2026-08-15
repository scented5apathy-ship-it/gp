package com.genealogy.platform.services.media.delivery;

import java.util.Objects;
import java.util.Optional;

/**
 * Range request payload (HTTP {@code Range} header
 * mirror). Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryRangeUnit` (E7.4) +
 * `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>The range is {@code [startInclusive, endInclusive]}
 * with an explicit unit (only {@link DeliveryRangeUnit#BYTES}
 * is accepted today; {@link DeliveryRangeUnit#NONE} means
 * no-range). Multi-range requests are forbidden by the
 * orchestrator before this record is constructed.
 */
public record RangeRequest(
        DeliveryRangeUnit unit,
        long startInclusive,
        long endInclusive) {

    public static final long MAX_RANGE_BYTES = 67108864L;
    public static final long MIN_RANGE_BYTES = 1024L;

    public RangeRequest {
        Objects.requireNonNull(unit, "unit");
        if (unit == DeliveryRangeUnit.NONE) {
            throw new IllegalArgumentException(
                    "RangeRequest.unit NONE requires no range; "
                            + "use null instead");
        }
        if (startInclusive < 0L) {
            throw new IllegalArgumentException(
                    "startInclusive must be >= 0");
        }
        if (endInclusive < startInclusive) {
            throw new IllegalArgumentException(
                    "endInclusive must be >= startInclusive");
        }
        long span = endInclusive - startInclusive + 1L;
        if (span < MIN_RANGE_BYTES || span > MAX_RANGE_BYTES) {
            throw new IllegalArgumentException(
                    "range span out of bounds ["
                            + MIN_RANGE_BYTES + ", "
                            + MAX_RANGE_BYTES + "]");
        }
    }

    public long span() {
        return endInclusive - startInclusive + 1L;
    }

    public String wireHeader() {
        if (unit == DeliveryRangeUnit.NONE) {
            return "";
        }
        return "bytes=" + startInclusive + "-" + endInclusive;
    }

    public static Optional<RangeRequest> parseFromHeader(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String h = header.trim();
        if (!h.toLowerCase().startsWith("bytes=")) {
            return Optional.empty();
        }
        String body = h.substring("bytes=".length());
        if (body.contains(",")) {
            throw new IllegalArgumentException(
                    "multi-range requests forbidden");
        }
        int dash = body.indexOf('-');
        if (dash < 0) {
            throw new IllegalArgumentException(
                    "malformed Range header: " + header);
        }
        long start = Long.parseLong(body.substring(0, dash).trim());
        long end = Long.parseLong(body.substring(dash + 1).trim());
        return Optional.of(new RangeRequest(
                DeliveryRangeUnit.BYTES, start, end));
    }
}