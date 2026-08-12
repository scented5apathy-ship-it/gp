package com.genealogy.platform.services.media.domain;

import java.util.Objects;

/**
 * Pure checksum verifier. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.checksumAlgorithms + maxChecksumDigestLength +
 * uploadGuardDenyReasons` (E7.1) + `requirements.md` R9.2 +
 * `design.md` §8.2 (client finalize bằng checksum).
 *
 * <p>The verifier accepts an algorithm from the
 * {@code checksumAlgorithms} closed-set, validates the
 * digest length, and compares the expected vs. observed
 * digest without copying either value into the return
 * object. The executor NEVER logs the raw digest.
 */
public final class ChecksumVerifier {

    public static final int MAX_DIGEST_LENGTH = 256;

    private final java.util.Set<ChecksumAlgorithm> allowed;

    public ChecksumVerifier(java.util.Set<ChecksumAlgorithm> allowed) {
        Objects.requireNonNull(allowed, "allowed");
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("allowed must not be empty");
        }
        this.allowed = java.util.Set.copyOf(allowed);
    }

    public boolean verify(
            ChecksumAlgorithm algorithm,
            String expectedDigest,
            String observedDigest) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(expectedDigest, "expectedDigest");
        Objects.requireNonNull(observedDigest, "observedDigest");
        if (!allowed.contains(algorithm)) {
            throw new IllegalArgumentException(
                    "algorithm not permitted: " + algorithm.wire());
        }
        if (expectedDigest.length() > MAX_DIGEST_LENGTH
                || observedDigest.length() > MAX_DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "digest exceeds " + MAX_DIGEST_LENGTH + " characters");
        }
        return constantTimeEquals(expectedDigest, observedDigest);
    }

    public boolean verifyDeclared(
            UploadSession session,
            ChecksumAlgorithm algorithm,
            String observedDigest) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(observedDigest, "observedDigest");
        if (session.checksumAlgorithm() != algorithm) {
            return false;
        }
        return verify(algorithm, session.declaredChecksumDigest(), observedDigest);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i += 1) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
