package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Activity feed filter. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.activityFeedAlwaysReproject +
 * activityFeedReAuthorizationRequiredOnRead` (E6.4),
 * `requirements.md` R10.5 + `design.md` §8.3 + R15.4.
 *
 * <p>The filter is a pure function that re-authorizes every
 * {@link ActivityFeedItem} read. The application layer
 * calls {@link CommentAuthorizationPort#authorize} with
 * {@link CommentAuthorizationPort.CommentAuthorizationAction#ACTIVITY_FEED_READ}
 * before exposing the item to the caller. A deny or abac_deny
 * outcome drops the item from the projection.
 */
public final class ActivityFeedFilter {

    private final CommentAuthorizationPort authorizationPort;

    public ActivityFeedFilter(CommentAuthorizationPort authorizationPort) {
        Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.authorizationPort = authorizationPort;
    }

    public boolean isReadable(ActivityFeedItem item, String actorPseudoId) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        if (actorPseudoId.isBlank()) {
            throw new IllegalArgumentException("actorPseudoId must not be blank");
        }
        var context = new CommentAuthorizationPort.CommentAuthorizationContext(
                item.itemId(),
                actorPseudoId,
                CommentAuthorizationPort.CommentAuthorizationAction.ACTIVITY_FEED_READ,
                scopeFor(item),
                item.itemId().resourceId());
        return authorizationPort.authorize(context).isAllowed();
    }

    private static WatchScope scopeFor(ActivityFeedItem item) {
        return switch (item.kind()) {
            case COMMENT_CREATED, COMMENT_EDITED, COMMENT_REDACTED, COMMENT_DELETED,
                    MENTION_NOTIFIED, MENTION_DROPPED -> WatchScope.COMMENT;
            case WATCH_SUBSCRIBED, WATCH_UNSUBSCRIBED -> WatchScope.COLLAB_THREAD;
            case ASSIGNMENT_OPENED, ASSIGNMENT_ACCEPTED, ASSIGNMENT_DECLINED,
                    ASSIGNMENT_REVOKED, ASSIGNMENT_EXPIRED -> WatchScope.COLLAB_THREAD;
            case NOTIFICATION_DELIVERED, NOTIFICATION_DROPPED -> WatchScope.COLLAB_THREAD;
        };
    }
}
