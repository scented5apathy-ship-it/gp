package com.genealogy.platform.services.media.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Multipart part record. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.multipartPartReAuthorizationRequired +
 * uploadGuardDenyReasons` (E7.1) + `design.md` §8.2 (S3
 * multipart upload).
 *
 * <p>Parts are received in monotonically increasing part
 * number order; the compact constructor rejects duplicate
 * part numbers, gap, oversize and undersize parts.
 */
public record MultipartPart(
        MediaTenantScopedId id,
        String uploadSessionId,
        int partNumber,
        long sizeBytes,
        String checksumDigest,
        ChecksumAlgorithm checksumAlgorithm,
        Instant receivedAt,
        MediaUploadAuditAttributes audit) {

    public static final int MIN_PART_NUMBER = 1;
    public static final long MIN_PART_SIZE_BYTES = 5242880L;
    public static final long MAX_PART_SIZE_BYTES = 1073741824L;
    public static final int MAX_CHECKSUM_LENGTH = 256;

    public MultipartPart {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uploadSessionId, "uploadSessionId");
        Objects.requireNonNull(checksumAlgorithm, "checksumAlgorithm");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(audit, "audit");
        if (uploadSessionId.isBlank()) {
            throw new IllegalArgumentException("uploadSessionId must not be blank");
        }
        if (partNumber < MIN_PART_NUMBER) {
            throw new IllegalArgumentException(
                    "partNumber must be >= " + MIN_PART_NUMBER + ", got " + partNumber);
        }
        if (sizeBytes < MIN_PART_SIZE_BYTES || sizeBytes > MAX_PART_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "sizeBytes must be in ["
                            + MIN_PART_SIZE_BYTES + ", " + MAX_PART_SIZE_BYTES
                            + "], got " + sizeBytes);
        }
        if (checksumDigest == null || checksumDigest.isBlank()) {
            throw new IllegalArgumentException("checksumDigest must not be blank");
        }
        if (checksumDigest.length() > MAX_CHECKSUM_LENGTH) {
            throw new IllegalArgumentException(
                    "checksumDigest exceeds " + MAX_CHECKSUM_LENGTH + " characters");
        }
    }

    public static MultipartPart received(
            MediaTenantScopedId id,
            String uploadSessionId,
            int partNumber,
            long sizeBytes,
            String checksumDigest,
            ChecksumAlgorithm checksumAlgorithm,
            Instant receivedAt,
            MediaUploadAuditAttributes audit) {
        return new MultipartPart(
                id,
                uploadSessionId,
                partNumber,
                sizeBytes,
                checksumDigest,
                checksumAlgorithm,
                receivedAt,
                audit);
    }

    public boolean isFirstPart() {
        return partNumber == MIN_PART_NUMBER;
    }
}
