package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Mention record. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.mentionTargetKinds + mentionSensitiveFieldGuardRail`
 * (E6.4), `requirements.md` R10.5. A mention is a
 * reference to a {@link MentionTargetKind} (USER / ROLE /
 * TREE / BRANCH) embedded in a comment.
 *
 * <p>{@link #MAX_TARGET_ID_LENGTH} and the closed-set
 * {@link MentionTargetKind} guarantee the mention never
 * references a raw identifier; the application layer is
 * responsible for resolving the {@code targetId} into a
 * {@link TenantScopedId} at dispatch time.
 */
public record Mention(
        TenantScopedId commentId,
        MentionTargetKind targetKind,
        String targetId,
        CollaborationAuditAttributes audit) {

    public static final int MAX_TARGET_ID_LENGTH = 128;

    public Mention {
        Objects.requireNonNull(commentId, "commentId");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(audit, "audit");
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (targetId.length() > MAX_TARGET_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "targetId exceeds " + MAX_TARGET_ID_LENGTH + " characters");
        }
        if (!targetId.matches("[A-Za-z0-9._\\-]+")) {
            throw new IllegalArgumentException(
                    "targetId contains forbidden characters: " + targetId);
        }
    }
}
