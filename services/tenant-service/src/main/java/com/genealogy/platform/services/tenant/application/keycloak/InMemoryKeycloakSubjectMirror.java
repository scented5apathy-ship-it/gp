package com.genealogy.platform.services.tenant.application.keycloak;

import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Keycloak mirror used in E3.2c (unit tests + Testcontainers IT).
 *
 * <p>The mirror keeps two indexes:
 *
 * <ul>
 *   <li>{@code email → userId} — populated by
 *       {@link #register(String, UserId)} or {@link #ensureForEmail(String)}.</li>
 *   <li>{@code tokenHash → email} — populated by
 *       {@link #rememberInviteToken(String, String)} and queried by
 *       {@link #findEmailByRawToken(String)}.</li>
 * </ul>
 *
 * <p>E3.5 swaps this bean for the {@code KeycloakAdminClient}-backed
 * implementation; the domain / application code never references this
 * concrete type.
 *
 * <p><strong>Security:</strong> the mirror only stores the SHA-256 of the
 * raw invite token (not the raw token itself) so a heap dump cannot
 * replay the invite. The salted-HMAC hashing mandated by ADR-E0.5-06
 * lands with the Vault-backed implementation; for E3.2c the SHA-256
 * is sufficient because the mirror never persists the value to disk.
 */
public final class InMemoryKeycloakSubjectMirror implements KeycloakSubjectMirror {

    private final Map<String, UserId> emailIndex = new ConcurrentHashMap<>();
    private final Map<String, String> tokenHashIndex = new ConcurrentHashMap<>();
    private final IdGenerator idGenerator;

    public InMemoryKeycloakSubjectMirror(IdGenerator idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    /** Register an explicit user under an email (used in tests + future admin flows). */
    public void register(String email, UserId userId) {
        emailIndex.put(normalizeEmail(email), userId);
    }

    @Override
    public UserId ensureForEmail(String email) {
        String key = normalizeEmail(email);
        return emailIndex.computeIfAbsent(key, unused -> new UserId(idGenerator.nextId()));
    }

    /** Record an invite token → email mapping. The token is hashed before storage. */
    public void rememberInviteToken(String email, String rawToken) {
        tokenHashIndex.put(sha256Hex(rawToken), normalizeEmail(email));
    }

    @Override
    public UserId resolveByEmail(String email) {
        return emailIndex.get(normalizeEmail(email));
    }

    @Override
    public String findEmailByRawToken(String rawToken) {
        return tokenHashIndex.get(sha256Hex(rawToken));
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
