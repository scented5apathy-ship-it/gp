package com.genealogy.platform.services.media.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Tenant-scoped quota ledger. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.quotaUnits + quotaScopes + quotaDenialReasons +
 * quotaHeadroomInBytes` (E7.1) + `requirements.md` R9.2 +
 * `design.md` §8.2 (BFF yêu cầu media service tạo upload
 * session sau quota/permission check).
 *
 * <p>The ledger records three counters: bytes used, items
 * (sessions) used, and the global session TTL. The
 * {@code reserve(...)} method is idempotent on the same
 * {@code reservationId}; the {@code release(...)} method
 * returns capacity without ever going negative.
 */
public record QuotaLedger(
        MediaTenantScopedId id,
        long bytesUsed,
        long itemsUsed,
        long secondsUsed,
        long bytesReserved,
        long itemsReserved,
        long secondsReserved,
        long maxBytes,
        long maxItems,
        long maxSeconds,
        long headroomBytes) {

    public static final long MAX_QUOTA_BYTES = 1073741824L;
    public static final long MAX_QUOTA_ITEMS = 4096L;
    public static final long MAX_QUOTA_SECONDS = 86400L;

    public QuotaLedger {
        Objects.requireNonNull(id, "id");
        if (bytesUsed < 0 || itemsUsed < 0 || secondsUsed < 0) {
            throw new IllegalArgumentException("quota counters must not be negative");
        }
        if (bytesReserved < 0 || itemsReserved < 0 || secondsReserved < 0) {
            throw new IllegalArgumentException("reserved counters must not be negative");
        }
        if (maxBytes <= 0 || maxBytes > MAX_QUOTA_BYTES) {
            throw new IllegalArgumentException(
                    "maxBytes must be in (0, " + MAX_QUOTA_BYTES + "], got " + maxBytes);
        }
        if (maxItems <= 0 || maxItems > MAX_QUOTA_ITEMS) {
            throw new IllegalArgumentException(
                    "maxItems must be in (0, " + MAX_QUOTA_ITEMS + "], got " + maxItems);
        }
        if (maxSeconds <= 0 || maxSeconds > MAX_QUOTA_SECONDS) {
            throw new IllegalArgumentException(
                    "maxSeconds must be in (0, " + MAX_QUOTA_SECONDS + "], got " + maxSeconds);
        }
        if (headroomBytes < 0 || headroomBytes > maxBytes) {
            throw new IllegalArgumentException(
                    "headroomBytes must be in [0, maxBytes], got " + headroomBytes);
        }
        if (bytesUsed + bytesReserved > maxBytes) {
            throw new IllegalArgumentException("reserved bytes overflow");
        }
        if (itemsUsed + itemsReserved > maxItems) {
            throw new IllegalArgumentException("reserved items overflow");
        }
        if (secondsReserved > maxSeconds) {
            throw new IllegalArgumentException("reserved seconds overflow");
        }
        if (secondsUsed + secondsReserved > maxSeconds) {
            throw new IllegalArgumentException("seconds combined overflow");
        }
    }

    public static QuotaLedger empty(
            MediaTenantScopedId id,
            long maxBytes,
            long maxItems,
            long maxSeconds) {
        return new QuotaLedger(
                id, 0L, 0L, 0L, 0L, 0L, 0L, maxBytes, maxItems, maxSeconds, maxBytes);
    }

    public QuotaLedger reserve(
            long bytes,
            long items,
            long seconds,
            String reservationId) {
        Objects.requireNonNull(reservationId, "reservationId");
        if (reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId must not be blank");
        }
        if (bytes < 0 || items < 0 || seconds < 0) {
            throw new IllegalArgumentException("reservation values must not be negative");
        }
        if (bytesUsed + bytesReserved + bytes > maxBytes) {
            throw new QuotaDenyException(
                    QuotaDenialReason.QUOTA_EXCEEDED_BYTES,
                    "would exceed " + maxBytes + " bytes");
        }
        if (bytesUsed + bytesReserved + bytes > headroomBytes) {
            throw new QuotaDenyException(
                    QuotaDenialReason.QUOTA_TENANT_HEADROOM_INSUFFICIENT,
                    "headroom " + headroomBytes + " < requested " + bytes);
        }
        if (itemsUsed + itemsReserved + items > maxItems) {
            throw new QuotaDenyException(
                    QuotaDenialReason.QUOTA_EXCEEDED_COUNT,
                    "would exceed " + maxItems + " items");
        }
        if (secondsReserved + seconds > maxSeconds) {
            throw new QuotaDenyException(
                    QuotaDenialReason.QUOTA_EXCEEDED_SESSION_TTL,
                    "would exceed " + maxSeconds + " seconds");
        }
        return new QuotaLedger(
                id,
                bytesUsed,
                itemsUsed,
                secondsUsed,
                bytesReserved + bytes,
                itemsReserved + items,
                secondsReserved + seconds,
                maxBytes,
                maxItems,
                maxSeconds,
                headroomBytes);
    }

    public QuotaLedger commit(String reservationId) {
        Objects.requireNonNull(reservationId, "reservationId");
        if (bytesReserved + bytesUsed > maxBytes) {
            throw new IllegalStateException("reserved + used exceeds maxBytes");
        }
        return new QuotaLedger(
                id,
                bytesUsed + bytesReserved,
                itemsUsed + itemsReserved,
                secondsUsed + secondsReserved,
                0L,
                0L,
                0L,
                maxBytes,
                maxItems,
                maxSeconds,
                headroomBytes);
    }

    public QuotaLedger release(String reservationId) {
        Objects.requireNonNull(reservationId, "reservationId");
        return new QuotaLedger(
                id,
                bytesUsed,
                itemsUsed,
                secondsUsed,
                0L,
                0L,
                0L,
                maxBytes,
                maxItems,
                maxSeconds,
                headroomBytes);
    }

    public Optional<QuotaDenialReason> canReserve(
            long bytes, long items, long seconds) {
        if (bytes < 0 || items < 0 || seconds < 0) {
            return Optional.of(QuotaDenialReason.QUOTA_SCOPE_NOT_PERMITTED);
        }
        if (bytes > headroomBytes) {
            return Optional.of(QuotaDenialReason.QUOTA_TENANT_HEADROOM_INSUFFICIENT);
        }
        if (bytesUsed + bytesReserved + bytes > maxBytes) {
            return Optional.of(QuotaDenialReason.QUOTA_EXCEEDED_BYTES);
        }
        if (itemsUsed + itemsReserved + items > maxItems) {
            return Optional.of(QuotaDenialReason.QUOTA_EXCEEDED_COUNT);
        }
        if (secondsReserved + seconds > maxSeconds) {
            return Optional.of(QuotaDenialReason.QUOTA_EXCEEDED_SESSION_TTL);
        }
        return Optional.empty();
    }

    public static final class QuotaDenyException extends RuntimeException {
        private final QuotaDenialReason reason;

        public QuotaDenyException(QuotaDenialReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public QuotaDenialReason reason() {
            return reason;
        }
    }
}
