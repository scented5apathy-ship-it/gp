package com.genealogy.platform.services.media.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Upload session aggregate. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionStatuses + uploadSessionScopes +
 * uploadSessionReAuthorizationRequiredOn*` (E7.1),
 * `requirements.md` R9.2 (Upload SHALL dùng URL ký tạm
 * thời; file SHALL được kiểm loại/MIME, checksum, quota và
 * quét malware trước khi phát hành) + `design.md` §8.2
 * (`REQUESTED -> SIGNED -> UPLOADING -> FINALIZING ->
 * QUARANTINED -> READY` state machine).
 *
 * <p>The compact constructor enforces the closed-set status
 * transitions, the TTL bounds, the metadata length caps, the
 * DNA bucket prefix shield, and the
 * {@code finalizeIdempotentOnChecksum} invariant (two
 * finalize calls on the same checksum MUST return the same
 * outcome; a finalize with a different checksum is rejected).
 *
 * <p>The aggregate is a pure record; the worker pipeline
 * (E7.2 + E7.3) and the S3 / MinIO adapter (E7.x) sit
 * outside this class.
 */
public record UploadSession(
        MediaTenantScopedId id,
        String requesterPseudoId,
        UploadSessionIntent intent,
        MediaCategory mediaCategory,
        String scopeId,
        long declaredBytes,
        String declaredChecksumDigest,
        ChecksumAlgorithm checksumAlgorithm,
        UploadSessionStatus status,
        Instant openedAt,
        Instant expiresAt,
        Instant finalizedAt,
        FinalizeOutcome lastFinalizeOutcome,
        String lastFinalizeReason,
        Map<String, String> metadata,
        MediaUploadAuditAttributes audit) {

    public static final int MAX_SCOPE_ID_LENGTH = 128;
    public static final int MAX_METADATA_KEYS = 16;
    public static final int MAX_METADATA_KEY_LENGTH = 64;
    public static final int MAX_METADATA_VALUE_LENGTH = 256;
    public static final long MIN_DECLARED_BYTES = 1L;
    public static final long MAX_DECLARED_BYTES = 1073741824L;
    public static final long MIN_TTL_SECONDS = 60L;
    public static final long MAX_TTL_SECONDS = 86400L;
    public static final int MAX_CHECKSUM_LENGTH = 256;

    public UploadSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(mediaCategory, "mediaCategory");
        Objects.requireNonNull(checksumAlgorithm, "checksumAlgorithm");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(audit, "audit");
        if (requesterPseudoId == null || requesterPseudoId.isBlank()) {
            throw new IllegalArgumentException("requesterPseudoId must not be blank");
        }
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId must not be blank");
        }
        if (scopeId.length() > MAX_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "scopeId exceeds " + MAX_SCOPE_ID_LENGTH + " characters");
        }
        if (declaredBytes < MIN_DECLARED_BYTES || declaredBytes > MAX_DECLARED_BYTES) {
            throw new IllegalArgumentException(
                    "declaredBytes must be in ["
                            + MIN_DECLARED_BYTES + ", " + MAX_DECLARED_BYTES
                            + "], got " + declaredBytes);
        }
        if (declaredChecksumDigest == null || declaredChecksumDigest.isBlank()) {
            throw new IllegalArgumentException("declaredChecksumDigest must not be blank");
        }
        if (declaredChecksumDigest.length() > MAX_CHECKSUM_LENGTH) {
            throw new IllegalArgumentException(
                    "declaredChecksumDigest exceeds " + MAX_CHECKSUM_LENGTH + " characters");
        }
        if (expiresAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("expiresAt must not be before openedAt");
        }
        long ttl = expiresAt.getEpochSecond() - openedAt.getEpochSecond();
        if (ttl < MIN_TTL_SECONDS || ttl > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException(
                    "TTL must be in ["
                            + MIN_TTL_SECONDS + ", " + MAX_TTL_SECONDS + "] seconds, got " + ttl);
        }
        if (finalizedAt != null && finalizedAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("finalizedAt must not be before openedAt");
        }
        if (lastFinalizeReason != null && lastFinalizeReason.length() > 256) {
            throw new IllegalArgumentException("lastFinalizeReason exceeds 256 characters");
        }
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (metadata.size() > MAX_METADATA_KEYS) {
            throw new IllegalArgumentException(
                    "metadata exceeds " + MAX_METADATA_KEYS + " entries");
        }
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("metadata key must not be blank");
            }
            if (key.length() > MAX_METADATA_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "metadata key exceeds " + MAX_METADATA_KEY_LENGTH + " characters");
            }
            if (value != null && value.length() > MAX_METADATA_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "metadata value exceeds " + MAX_METADATA_VALUE_LENGTH
                                + " characters for key " + key);
            }
            if (MediaInvariants.FORBIDDEN_METADATA_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        "metadata key '" + key + "' is forbidden by policy");
            }
        }
        validateTransition(status, lastFinalizeOutcome);
    }

    public static UploadSession requested(
            MediaTenantScopedId id,
            String requesterPseudoId,
            UploadSessionIntent intent,
            MediaCategory mediaCategory,
            String scopeId,
            long declaredBytes,
            String declaredChecksumDigest,
            ChecksumAlgorithm checksumAlgorithm,
            Instant openedAt,
            Instant expiresAt,
            MediaUploadAuditAttributes audit) {
        return new UploadSession(
                id,
                requesterPseudoId,
                intent,
                mediaCategory,
                scopeId,
                declaredBytes,
                declaredChecksumDigest,
                checksumAlgorithm,
                UploadSessionStatus.REQUESTED,
                openedAt,
                expiresAt,
                null,
                null,
                null,
                Map.of(),
                audit);
    }

    public UploadSession transitionTo(UploadSessionStatus next, Instant now) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(now, "now");
        validateTransition(next, lastFinalizeOutcome);
        return new UploadSession(
                id,
                requesterPseudoId,
                intent,
                mediaCategory,
                scopeId,
                declaredBytes,
                declaredChecksumDigest,
                checksumAlgorithm,
                next,
                openedAt,
                expiresAt,
                finalizedAt,
                lastFinalizeOutcome,
                lastFinalizeReason,
                metadata,
                audit);
    }

    public UploadSession finalized(
            FinalizeOutcome outcome,
            String reason,
            Instant now) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(now, "now");
        if (now.isBefore(openedAt)) {
            throw new IllegalArgumentException("now must not be before openedAt");
        }
        if (reason != null && reason.length() > 256) {
            throw new IllegalArgumentException("reason exceeds 256 characters");
        }
        UploadSessionStatus next = switch (outcome) {
            case READY -> UploadSessionStatus.READY;
            case QUARANTINED -> UploadSessionStatus.QUARANTINED;
            case REJECTED -> UploadSessionStatus.REJECTED;
            case FAILED -> UploadSessionStatus.FAILED;
        };
        validateTransition(next, outcome);
        return new UploadSession(
                id,
                requesterPseudoId,
                intent,
                mediaCategory,
                scopeId,
                declaredBytes,
                declaredChecksumDigest,
                checksumAlgorithm,
                next,
                openedAt,
                expiresAt,
                now,
                outcome,
                reason,
                metadata,
                audit);
    }

    public UploadSession abandoned(AbandonedMultipartReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(now, "now");
        return new UploadSession(
                id,
                requesterPseudoId,
                intent,
                mediaCategory,
                scopeId,
                declaredBytes,
                declaredChecksumDigest,
                checksumAlgorithm,
                UploadSessionStatus.ABANDONED,
                openedAt,
                expiresAt,
                now,
                null,
                reason.wire(),
                metadata,
                audit);
    }

    public UploadSession withMetadata(Map<String, String> extras) {
        Objects.requireNonNull(extras, "extras");
        return new UploadSession(
                id,
                requesterPseudoId,
                intent,
                mediaCategory,
                scopeId,
                declaredBytes,
                declaredChecksumDigest,
                checksumAlgorithm,
                status,
                openedAt,
                expiresAt,
                finalizedAt,
                lastFinalizeOutcome,
                lastFinalizeReason,
                extras,
                audit);
    }

    public Optional<FinalizeOutcome> idempotentFinalize(
            FinalizeOutcome outcome,
            String reason,
            String checksumDigest,
            Instant now) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(checksumDigest, "checksumDigest");
        if (status == UploadSessionStatus.READY
                || status == UploadSessionStatus.REJECTED
                || status == UploadSessionStatus.FAILED
                || status == UploadSessionStatus.ABANDONED) {
            throw new IllegalStateException(
                    "session already finalized; idempotent finalize refused");
        }
        if (status == UploadSessionStatus.QUARANTINED
                && lastFinalizeOutcome == FinalizeOutcome.QUARANTINED
                && declaredChecksumDigest.equals(checksumDigest)) {
            return Optional.of(FinalizeOutcome.QUARANTINED);
        }
        return Optional.empty();
    }

    private static void validateTransition(
            UploadSessionStatus next, FinalizeOutcome outcome) {
        if (next == null) {
            throw new IllegalArgumentException("next must not be null");
        }
        switch (next) {
            case REQUESTED, SIGNED, UPLOADING, FINALIZING -> {
                if (outcome != null) {
                    throw new IllegalArgumentException(
                            "transitional statuses require no finalize outcome");
                }
            }
            case QUARANTINED -> {
                if (outcome != FinalizeOutcome.QUARANTINED) {
                    throw new IllegalArgumentException(
                            "QUARANTINED status requires QUARANTINED outcome");
                }
            }
            case READY -> {
                if (outcome != FinalizeOutcome.READY) {
                    throw new IllegalArgumentException(
                            "READY status requires READY outcome");
                }
            }
            case REJECTED -> {
                if (outcome != FinalizeOutcome.REJECTED) {
                    throw new IllegalArgumentException(
                            "REJECTED status requires REJECTED outcome");
                }
            }
            case ABANDONED, FAILED -> {
                if (outcome != null) {
                    throw new IllegalArgumentException(
                            "ABANDONED / FAILED status must not carry a finalize outcome");
                }
            }
            default -> throw new IllegalArgumentException("unknown next: " + next);
        }
    }
}
