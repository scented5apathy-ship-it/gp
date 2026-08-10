package com.genealogy.platform.services.genealogy.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * UNLISTED visibility token. The plaintext token is generated
 * server-side with {@link SecureRandom}, returned ONCE to the
 * caller at issuance, and never persisted in plaintext. Only the
 * SHA-256 fingerprint (hex lower-case) is stored and emitted on
 * the event bus per {@code contracts/genealogy/unlisted-token.yaml}.
 *
 * <p>Per {@code design.md} §6.3, the HTTP layer MUST return
 * {@code X-Robots-Tag: noindex} and the {@code robots} meta
 * equivalent for every UNLISTED page. Per
 * {@code requirements.md} R3.5, tokens may be revoked at any time.
 */
public record UnlistedToken(
        String tokenId,
        String treeId,
        String tenantId,
        String fingerprint,
        Scope scope,
        String branchId,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String issuedBy,
        String revokedBy,
        String revocationReason) {

    /** 32 bytes — encoded as URL-safe base64url → 43 chars (no padding). */
    public static final int TOKEN_BYTE_LENGTH = 32;

    public enum Scope {
        FULL_TREE,
        BRANCH;

        public static Scope fromWire(String wire) {
            if (wire == null) {
                return Scope.FULL_TREE;
            }
            return Scope.valueOf(wire.trim().toUpperCase(Locale.ROOT));
        }

        public String wire() {
            return name();
        }
    }

    public UnlistedToken {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(issuedBy, "issuedBy");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        if (scope == Scope.BRANCH && (branchId == null || branchId.isBlank())) {
            throw new IllegalArgumentException("branchId is required for BRANCH scope");
        }
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public UnlistedToken revoked(String nextRevokedBy, String reason, Instant at) {
        return new UnlistedToken(
                tokenId,
                treeId,
                tenantId,
                fingerprint,
                scope,
                branchId,
                issuedAt,
                expiresAt,
                at,
                issuedBy,
                nextRevokedBy,
                reason);
    }

    /**
     * Generate the plaintext token + its SHA-256 fingerprint.
     * Caller MUST return the plaintext to the user exactly once;
     * only the {@code fingerprint} is persisted.
     */
    public static GeneratedToken generate(SecureRandom random) {
        byte[] buf = new byte[TOKEN_BYTE_LENGTH];
        random.nextBytes(buf);
        String plaintext = Base64UrlEncoder.encode(buf);
        String fingerprint = sha256HexLower(plaintext);
        return new GeneratedToken(plaintext, fingerprint);
    }

    public static String sha256HexLower(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Pair of plaintext (return once to caller) and fingerprint (persist + audit). */
    public record GeneratedToken(String plaintext, String fingerprint) {
        public GeneratedToken {
            Objects.requireNonNull(plaintext, "plaintext");
            Objects.requireNonNull(fingerprint, "fingerprint");
        }
    }
}
