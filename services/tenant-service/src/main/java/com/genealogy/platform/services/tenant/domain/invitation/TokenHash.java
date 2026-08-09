package com.genealogy.platform.services.tenant.domain.invitation;

import java.util.Objects;

/**
 * Salted hash of the invite token. The raw token only ever appears
 * in the invite email body; what we persist is the salted hash so a
 * database compromise cannot be replayed as an active invite.
 *
 * <p>The application layer (E3.2c) is responsible for choosing the
 * hash function (HMAC-SHA-256 with a server-side salt from Vault per
 * ADR-E0.5-06). This value object only enforces non-empty and a
 * reasonable length cap so the V2 migration CHECK holds.
 */
public record TokenHash(String value) {

    /** Hex-encoded HMAC-SHA-256 = 64 chars; allow a small slack. */
    private static final int MAX_LEN = 128;

    public TokenHash {
        Objects.requireNonNull(value, "tokenHash");
        if (value.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }
        if (value.length() > MAX_LEN) {
            throw new IllegalArgumentException(
                    "tokenHash length must be <= " + MAX_LEN + " (got " + value.length() + ")");
        }
    }
}