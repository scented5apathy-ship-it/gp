package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Comment authorization port. Mirrors the in-memory
 * {@link ReAuthorizationPort} from E6.2 but pinned to the
 * comment / activity / notification flow. The application
 * layer injects a Spring adapter that calls OpenFGA + ABAC
 * at comment construction, edit, mention, watch-trigger,
 * assignment-open and notification-dispatch time.
 *
 * <p>The port is a pure function; the executor MUST call
 * {@link #authorize(CommentAuthorizationContext)} before
 * dispatching any comment / mention / watch / assignment /
 * notification hook.
 */
public interface CommentAuthorizationPort {

    CommentAuthorizationDecision authorize(CommentAuthorizationContext context);

    record CommentAuthorizationContext(
            TenantScopedId subjectId,
            String actorPseudoId,
            CommentAuthorizationAction action,
            WatchScope scope,
            String scopeId) {

        public CommentAuthorizationContext {
            Objects.requireNonNull(subjectId, "subjectId");
            Objects.requireNonNull(actorPseudoId, "actorPseudoId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(scope, "scope");
            if (scopeId == null || scopeId.isBlank()) {
                throw new IllegalArgumentException("scopeId must not be blank");
            }
        }
    }

    enum CommentAuthorizationAction {
        COMMENT_CREATE,
        COMMENT_EDIT,
        COMMENT_REDACT,
        COMMENT_DELETE,
        MENTION_DISPATCH,
        WATCH_SUBSCRIBE,
        WATCH_TRIGGER,
        ASSIGNMENT_OPEN,
        ASSIGNMENT_ACCEPT,
        NOTIFICATION_DISPATCH,
        ACTIVITY_FEED_READ
    }
}
