package com.genealogy.platform.services.audit.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 helper for the audit-service integrity hash chain. The
 * chain is <em>per tenant</em> so a tamper in tenant A cannot
 * invalidate the chain of tenant B. The genesis hash is 64 zeros
 * per <code>contracts/audit/policy.yaml::spec.integrity.genesisHash</code>.
 */
public final class HashChainComputer {

    public static final String GENESIS_HASH = "0".repeat(64);

    private HashChainComputer() {
    }

    public static String sha256(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String entryHash(AuditEntry entry) {
        return sha256(entry.canonicalBytes());
    }

    /**
     * Validates that {@code entry.entryHash()} matches the recomputed
     * hash and that {@code entry.previousHash()} equals the chain
     * head supplied by the caller. Returns the {@link IntegrityStatus}
     * for downstream reporting; never throws.
     */
    public static IntegrityStatus verify(AuditEntry entry, String expectedPreviousHash) {
        String recomputed = entryHash(entry);
        if (!recomputed.equals(entry.entryHash())) {
            return IntegrityStatus.tampered(
                    entry.eventId(),
                    "entry_hash mismatch (stored=" + entry.entryHash() + ", recomputed=" + recomputed + ")");
        }
        if (!entry.previousHash().equals(expectedPreviousHash)) {
            return IntegrityStatus.tampered(
                    entry.eventId(),
                    "previous_hash mismatch (expected=" + expectedPreviousHash
                            + ", got=" + entry.previousHash() + ")");
        }
        return IntegrityStatus.ok();
    }
}
