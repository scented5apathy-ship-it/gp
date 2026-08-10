package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.UnlistedToken;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link UnlistedTokenRepository}.
 * Test-only — production uses the JDBC implementation.
 */
public final class InMemoryUnlistedTokenRepository implements UnlistedTokenRepository {

    private final Map<String, UnlistedToken> byFingerprint = new ConcurrentHashMap<>();

    private static String key(String tenantId, String fingerprint) {
        return tenantId + "|" + fingerprint;
    }

    @Override
    public void insert(UnlistedToken token) {
        byFingerprint.put(key(token.tenantId(), token.fingerprint()), token);
    }

    @Override
    public Optional<UnlistedToken> findByFingerprint(String tenantId, String fingerprint) {
        UnlistedToken token = byFingerprint.get(key(tenantId, fingerprint));
        return Optional.ofNullable(token);
    }

    @Override
    public List<UnlistedToken> listActive(String tenantId, String treeId, Instant now) {
        List<UnlistedToken> out = new ArrayList<>();
        for (UnlistedToken token : byFingerprint.values()) {
            if (token.tenantId().equals(tenantId)
                    && token.treeId().equals(treeId)
                    && token.isActive(now)) {
                out.add(token);
            }
        }
        return out;
    }

    @Override
    public List<UnlistedToken> findExpired(Instant now, int limit) {
        List<UnlistedToken> out = new ArrayList<>();
        for (UnlistedToken token : byFingerprint.values()) {
            if (token.revokedAt() == null && !now.isBefore(token.expiresAt())) {
                out.add(token);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public void revoke(String tenantId, String fingerprint, String revokedBy, String reason, Instant at) {
        UnlistedToken token = byFingerprint.get(key(tenantId, fingerprint));
        if (token == null || token.revokedAt() != null) {
            return;
        }
        byFingerprint.put(key(tenantId, fingerprint), token.revoked(revokedBy, reason, at));
    }
}
