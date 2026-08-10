package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Instant;

class UnlistedTokenTest {

    @Test
    void generateProduces32ByteUrlSafeToken() {
        UnlistedToken.GeneratedToken generated = UnlistedToken.generate(new SecureRandom());
        // 32 bytes base64url-no-padding → 43 chars
        assertEquals(43, generated.plaintext().length());
        assertEquals(64, generated.fingerprint().length()); // SHA-256 hex
        assertTrue(generated.fingerprint().matches("^[0-9a-f]{64}$"));
    }

    @Test
    void fingerprintIsDeterministic() {
        UnlistedToken.GeneratedToken a = new UnlistedToken.GeneratedToken(
                "abcdef", UnlistedToken.sha256HexLower("abcdef"));
        UnlistedToken.GeneratedToken b = new UnlistedToken.GeneratedToken(
                "abcdef", UnlistedToken.sha256HexLower("abcdef"));
        assertEquals(a.fingerprint(), b.fingerprint());
        assertNotEquals(a.fingerprint(), UnlistedToken.sha256HexLower("xyz"));
    }

    @Test
    void rejectsExpiresAtBeforeIssuedAt() {
        Instant issued = Instant.parse("2026-08-10T10:00:00Z");
        Instant expires = Instant.parse("2026-08-10T09:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new UnlistedToken(
                "t", "tree", "tenant", UnlistedToken.sha256HexLower("xyz"),
                UnlistedToken.Scope.FULL_TREE, null, issued, expires, null,
                "actor", null, null));
    }

    @Test
    void branchScopeRequiresBranchId() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new UnlistedToken(
                "t", "tree", "tenant", UnlistedToken.sha256HexLower("xyz"),
                UnlistedToken.Scope.BRANCH, null, now, now.plusSeconds(60), null,
                "actor", null, null));
    }

    @Test
    void revokedTokenIsInactive() {
        Instant now = Instant.parse("2026-08-10T10:00:00Z");
        UnlistedToken token = new UnlistedToken(
                "t", "tree", "tenant", UnlistedToken.sha256HexLower("xyz"),
                UnlistedToken.Scope.FULL_TREE, null, now, now.plusSeconds(3600),
                null, "actor", null, null);
        assertTrue(token.isActive(now.plusSeconds(60)));
        UnlistedToken revoked = token.revoked("actor2", "test", now.plusSeconds(30));
        assertEquals(false, revoked.isActive(now.plusSeconds(60)));
    }
}
