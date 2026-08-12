package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Comment authorizer. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.commentScopes + sensitiveFieldGuardRail +
 * mentionSensitiveFieldGuardRail` (E6.4), `requirements.md`
 * R10.5 + R15.4 + `design.md` §8.3.
 *
 * <p>The authorizer checks the comment body for sensitive
 * field references (closed-set pinned to the YAML contract)
 * and returns a {@link CommentAuthorizationDecision} with a
 * deterministic reason code. The application layer wraps the
 * OpenFGA + ABAC check before the comment is persisted.
 *
 * <p>The authorizer is a pure function; it does not touch
 * the persistence layer.
 */
public final class CommentAuthorizer {

    private final Set<String> commentScopes;

    public CommentAuthorizer(Set<String> commentScopes) {
        Objects.requireNonNull(commentScopes, "commentScopes");
        if (commentScopes.isEmpty()) {
            throw new IllegalArgumentException("commentScopes must not be empty");
        }
        this.commentScopes = Set.copyOf(commentScopes);
    }

    public CommentAuthorizationDecision authorizeCreate(
            String body, WatchScope scope, String scopeId) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(scopeId, "scopeId");
        if (!commentScopes.contains(scope.name())) {
            return new CommentAuthorizationDecision(
                    CommentAuthorizationOutcome.DENY,
                    "COLLAB_COMMENT_FORBIDDEN_SCOPE");
        }
        if (ActivityRedactionFilter.mentionsSensitiveField(body)) {
            return new CommentAuthorizationDecision(
                    CommentAuthorizationOutcome.ABAC_DENY,
                    "COLLAB_COMMENT_SENSITIVE_FIELD_MENTIONED");
        }
        return CommentAuthorizationDecision.allow("COLLAB_COMMENT_AUTHORIZED");
    }

    public CommentAuthorizationDecision authorizeMention(
            Mention mention, WatchScope scope) {
        Objects.requireNonNull(mention, "mention");
        Objects.requireNonNull(scope, "scope");
        if (!commentScopes.contains(scope.name())) {
            return new CommentAuthorizationDecision(
                    CommentAuthorizationOutcome.DENY,
                    "COLLAB_MENTION_FORBIDDEN_SCOPE");
        }
        return CommentAuthorizationDecision.allow("COLLAB_MENTION_AUTHORIZED");
    }

    public CommentAuthorizationDecision authorizeWatchTrigger(
            Watch watch, NotificationOutcome lastOutcome) {
        Objects.requireNonNull(watch, "watch");
        if (lastOutcome == NotificationOutcome.REDACTED) {
            return new CommentAuthorizationDecision(
                    CommentAuthorizationOutcome.ABAC_DENY,
                    "COLLAB_WATCH_LAST_EVENT_REDACTED");
        }
        return CommentAuthorizationDecision.allow("COLLAB_WATCH_TRIGGER_AUTHORIZED");
    }

    public CommentAuthorizationDecision authorizeAssignmentOpen(
            AssignmentRole role, WatchScope scope) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(scope, "scope");
        if (!commentScopes.contains(scope.name())) {
            return new CommentAuthorizationDecision(
                    CommentAuthorizationOutcome.DENY,
                    "COLLAB_ASSIGNMENT_FORBIDDEN_SCOPE");
        }
        return CommentAuthorizationDecision.allow("COLLAB_ASSIGNMENT_AUTHORIZED");
    }
}
