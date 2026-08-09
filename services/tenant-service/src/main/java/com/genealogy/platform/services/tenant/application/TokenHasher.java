package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.services.tenant.domain.invitation.TokenHash;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hashes the raw invite token before it is persisted. The raw
 * token only appears in the email body; the database stores the
 * salted-hash so a database leak cannot replay the invite.
 *
 * <p>E3.2c ships a deterministic SHA-256 implementation. E3.5
 * swaps it for a Vault-backed HMAC implementation (the salt is
 * rotated by Vault, the hashing is performed inside Vault so the
 * service never holds the secret key).
 */
public interface TokenHasher {

    TokenHash hash(String rawToken);

    /** SHA-256 reference implementation used in E3.2c. */
    final class Sha256 implements TokenHasher {

        @Override
        public TokenHash hash(String rawToken) {
            Objects.requireNonNull(rawToken, "rawToken");
            if (rawToken.isBlank()) {
                throw new IllegalArgumentException("rawToken must not be blank");
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(
                        rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return new TokenHash(HexFormat.of().formatHex(hash));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available on this JVM", e);
            }
        }
    }
}
