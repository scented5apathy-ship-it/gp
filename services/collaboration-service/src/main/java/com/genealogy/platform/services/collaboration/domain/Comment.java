package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Comment aggregate. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.commentStatuses + commentScopes + sensitiveFieldGuardRail`
 * (E6.4), `requirements.md` R10.5 (system SHALL have
 * comment + mention + watch + assignment + notification +
 * activity feed) and `design.md` §8.3 (every comment
 * carries explicit scope + an authorization decision at
 * construction time).
 *
 * <p>The comment body is sanitised at construction time:
 * any reference to a {@link #MENTION_SENSITIVE_FIELDS}
 * field forces the comment into {@link CommentStatus#HIDDEN}
 * + {@link CommentStatus#REDACTED} + the
 * {@code COMMENT_SENSITIVE_FIELD_MENTIONED} invariant code.
 *
 * <p>Audit attributes ({@link CollaborationAuditAttributes})
 * carry the {@code actorPseudoId} + {@code correlationId}.
 */
public record Comment(
        TenantScopedId id,
        TenantScopedId proposalId,
        WatchScope scope,
        String scopeId,
        CommentStatus status,
        String body,
        CollaborationAuditAttributes audit,
        Instant createdAt,
        Instant editedAt,
        Map<String, String> metadata) {

    public static final int MAX_BODY_LENGTH = 8192;
    public static final int MAX_SCOPE_ID_LENGTH = 128;
    public static final int MAX_METADATA_KEYS = 16;

    public static final Set<String> MENTION_SENSITIVE_FIELDS = Set.of(
            "dnaRawData",
            "dnaMatchId",
            "consentReceipt",
            "livingMarker",
            "visibility",
            "redactedFields",
            "rawEmail",
            "rawPhone",
            "rawSsn",
            "rawPassport");

    public Comment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(createdAt, "createdAt");
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId must not be blank");
        }
        if (scopeId.length() > MAX_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "scopeId exceeds " + MAX_SCOPE_ID_LENGTH + " characters");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "body exceeds " + MAX_BODY_LENGTH + " characters");
        }
        if (editedAt != null && editedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("editedAt must not be before createdAt");
        }
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (metadata.size() > MAX_METADATA_KEYS) {
            throw new IllegalArgumentException(
                    "metadata exceeds " + MAX_METADATA_KEYS + " entries");
        }
        if (mentionsSensitiveField(body) && status != CommentStatus.HIDDEN
                && status != CommentStatus.REDACTED) {
            throw new IllegalArgumentException(
                    "body references a sensitive field; status must be HIDDEN or REDACTED");
        }
    }

    public static boolean mentionsSensitiveField(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase();
        for (String field : MENTION_SENSITIVE_FIELDS) {
            if (lower.contains(field.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public Comment redacted(String reason, RedactionReason redactionReason) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(redactionReason, "redactionReason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        String sanitized = sanitizeBody(body);
        return new Comment(
                id, proposalId, scope, scopeId,
                CommentStatus.REDACTED,
                sanitized,
                audit,
                createdAt,
                Instant.now(),
                metadata);
    }

    public static String sanitizeBody(String body) {
        if (body == null) return "";
        return "[REDACTED]";
    }
}
