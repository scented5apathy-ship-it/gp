package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of checksum algorithms. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.checksumAlgorithms` (E7.1) + `requirements.md` R9.2
 * (file SHALL được kiểm loại/MIME, checksum, quota và quét
 * malware trước khi phát hành). The finalizer MUST verify
 * the declared checksum against the upload stream before
 * promoting the asset to {@code QUARANTINED}.
 */
public enum ChecksumAlgorithm {
    SHA256,
    SHA512,
    BLAKE3;

    public static ChecksumAlgorithm fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ChecksumAlgorithm.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ChecksumAlgorithm from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
