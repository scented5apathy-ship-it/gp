package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.UnlistedToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link UnlistedToken}. The plaintext token is
 * never persisted; only its SHA-256 fingerprint, scope and
 * lifecycle timestamps are stored. Expired tokens are returned by
 * {@link #findExpired(Instant, int)} for the sweeper.
 */
public interface UnlistedTokenRepository {

    void insert(UnlistedToken token);

    Optional<UnlistedToken> findByFingerprint(String tenantId, String fingerprint);

    List<UnlistedToken> listActive(String tenantId, String treeId, Instant now);

    /** Tokens past {@code expiresAt} that have NOT been revoked yet. */
    List<UnlistedToken> findExpired(Instant now, int limit);

    void revoke(String tenantId, String fingerprint, String revokedBy, String reason, Instant at);
}
