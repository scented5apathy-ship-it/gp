package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Comment authorization decision. Mirrors the re-authorization
 * pattern from E6.2 (`ReAuthorizationDecision`) but pinned to
 * the comment / mention / watch / assignment flow. The
 * application layer re-authorizes the comment at construction,
 * edit, mention, watch-trigger, assignment-open and
 * notification-dispatch time (per
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.mentionReAuthorizationRequired +
 * watchReAuthorizationRequiredOnTrigger +
 * assignmentReAuthorizationRequiredOnAccept +
 * notificationHookReAuthorizationRequiredOnDispatch`).
 */
public record CommentAuthorizationDecision(
        CommentAuthorizationOutcome outcome,
        String reasonCode) {

    public CommentAuthorizationDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonCode.length() > 96) {
            throw new IllegalArgumentException(
                    "reasonCode exceeds 96 characters: " + reasonCode);
        }
        if (outcome != CommentAuthorizationOutcome.ALLOW
                && !reasonCode.startsWith("COLLAB_")) {
            throw new IllegalArgumentException(
                    "deny/abacDeny reasonCode must start with COLLAB_");
        }
    }

    public static CommentAuthorizationDecision allow(String reasonCode) {
        return new CommentAuthorizationDecision(
                CommentAuthorizationOutcome.ALLOW, reasonCode);
    }

    public boolean isAllowed() {
        return outcome == CommentAuthorizationOutcome.ALLOW;
    }
}
