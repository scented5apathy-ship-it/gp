package com.genealogy.platform.libs.security.abac;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Purpose-scoped consent record (per
 * {@code privacy-and-legal-gate.md} §4 lawful-basis register +
 * {@code requirements.md} R13). The consent ledger is the source of
 * truth; OpenFGA does NOT store consent (architecture-decisions.md
 * §11).
 *
 * <p>{@link #status} is denormalised here so the ABAC engine can
 * decide without a second lookup. The engine still re-reads the
 * ledger before any mutating activity (privacy gate §D-06).
 */
public record ConsentRecord(
        String consentId,
        Purpose purpose,
        Action action,
        String subjectId,
        Status status,
        String policyVersion,
        Instant effectiveAt,
        Instant expiresAt,
        Instant revokedAt) {

    public enum Status {
        ACTIVE,
        EXPIRED,
        REVOKED,
        WITHDRAWN
    }

    public ConsentRecord {
        Objects.requireNonNull(consentId, "consentId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
    }

    public boolean isActiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (status != Status.ACTIVE) {
            return false;
        }
        if (instant.isBefore(effectiveAt)) {
            return false;
        }
        if (expiresAt != null && !instant.isBefore(expiresAt)) {
            return false;
        }
        return true;
    }

    public boolean isRevoked() {
        return status == Status.REVOKED || status == Status.WITHDRAWN
                || revokedAt != null;
    }

    public Optional<Instant> revocationTime() {
        return revokedAt == null ? Optional.empty() : Optional.of(revokedAt);
    }

    public ConsentRecord withStatus(Status newStatus) {
        return new ConsentRecord(consentId, purpose, action, subjectId, newStatus,
                policyVersion, effectiveAt, expiresAt, revokedAt);
    }

    public ConsentRecord withEffectiveAt(Instant newEffectiveAt) {
        return new ConsentRecord(consentId, purpose, action, subjectId, status,
                policyVersion, newEffectiveAt, expiresAt, revokedAt);
    }

    public ConsentRecord withExpiresAt(Instant newExpiresAt) {
        return new ConsentRecord(consentId, purpose, action, subjectId, status,
                policyVersion, effectiveAt, newExpiresAt, revokedAt);
    }

    public ConsentRecord withRevokedAt(Instant newRevokedAt) {
        return new ConsentRecord(consentId, purpose, action, subjectId,
                newRevokedAt == null ? status : Status.REVOKED,
                policyVersion, effectiveAt, expiresAt, newRevokedAt);
    }

    /** Closed set of purposes (R13 + privacy gate §4). */
    public enum Purpose {
        DNA_RAW_UPLOAD,
        DNA_MATCH,
        DNA_SHARE,
        DNA_RESEARCH,
        DNA_EXPORT,
        PUBLIC_PROJECTION,
        PUBLIC_DISCOVERY,
        SUPPORT_JIT;

        public boolean isGenetic() {
            switch (this) {
                case DNA_RAW_UPLOAD:
                case DNA_MATCH:
                case DNA_SHARE:
                case DNA_RESEARCH:
                case DNA_EXPORT:
                    return true;
                default:
                    return false;
            }
        }
    }

    /** Closed set of actions. */
    public enum Action {
        READ,
        WRITE,
        DELETE,
        EXPORT,
        MATCH,
        SHARE
    }
}
