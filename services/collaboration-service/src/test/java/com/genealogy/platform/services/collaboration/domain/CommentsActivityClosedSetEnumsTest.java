package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * E6.4 closed-set enum + wire-codec tests. Mirrors
 * {@link MixedClosedSetEnumsTest} (E6.3).
 */
class CommentsActivityClosedSetEnumsTest {

    @Test
    void commentStatusPinsClosedSet() {
        for (String value : new String[] {
                "ACTIVE", "EDITED", "DELETED", "REDACTED", "HIDDEN"
        }) {
            assertEquals(value, CommentStatus.fromWire(value).wire());
        }
    }

    @Test
    void commentStatusRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> CommentStatus.fromWire(null));
    }

    @Test
    void commentStatusRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> CommentStatus.fromWire("UNKNOWN"));
    }

    @Test
    void mentionTargetKindPinsClosedSet() {
        for (String value : new String[] {"USER", "ROLE", "TREE", "BRANCH"}) {
            assertEquals(value, MentionTargetKind.fromWire(value).wire());
        }
    }

    @Test
    void watchScopePinsClosedSet() {
        for (String value : new String[] {
                "PROPOSAL", "REVIEW", "COMMENT", "PERSON",
                "RELATIONSHIP", "TREE_VISIBILITY", "COLLAB_THREAD"
        }) {
            assertEquals(value, WatchScope.fromWire(value).wire());
        }
    }

    @Test
    void watchTriggerPinsClosedSet() {
        for (String value : new String[] {
                "ANY_CHANGE", "MENTION", "STATUS_CHANGE",
                "DIRECT_EDIT", "APPROVAL_REQUIRED", "DENY"
        }) {
            assertEquals(value, WatchTrigger.fromWire(value).wire());
        }
    }

    @Test
    void assignmentRolePinsClosedSet() {
        for (String value : new String[] {
                "WATCHER", "REVIEWER", "APPROVER", "GATEKEEPER", "MENTIONED"
        }) {
            assertEquals(value, AssignmentRole.fromWire(value).wire());
        }
    }

    @Test
    void assignmentStatusPinsClosedSet() {
        for (String value : new String[] {
                "PENDING", "ACCEPTED", "DECLINED", "EXPIRED", "REVOKED"
        }) {
            assertEquals(value, AssignmentStatus.fromWire(value).wire());
        }
    }

    @Test
    void activityKindPinsClosedSet() {
        for (String value : new String[] {
                "COMMENT_CREATED", "COMMENT_EDITED", "COMMENT_REDACTED",
                "COMMENT_DELETED", "MENTION_NOTIFIED", "MENTION_DROPPED",
                "WATCH_SUBSCRIBED", "WATCH_UNSUBSCRIBED",
                "ASSIGNMENT_OPENED", "ASSIGNMENT_ACCEPTED", "ASSIGNMENT_DECLINED",
                "ASSIGNMENT_REVOKED", "ASSIGNMENT_EXPIRED",
                "NOTIFICATION_DELIVERED", "NOTIFICATION_DROPPED"
        }) {
            assertEquals(value, ActivityKind.fromWire(value).wire());
        }
    }

    @Test
    void activityVisibilityPinsClosedSet() {
        for (String value : new String[] {"PUBLIC", "TREE", "BRANCH", "PRIVATE"}) {
            assertEquals(value, ActivityVisibility.fromWire(value).wire());
        }
    }

    @Test
    void notificationChannelPinsClosedSet() {
        for (String value : new String[] {"IN_APP", "EMAIL", "PUSH", "WEBHOOK"}) {
            assertEquals(value, NotificationChannel.fromWire(value).wire());
        }
    }

    @Test
    void notificationOutcomePinsClosedSet() {
        for (String value : new String[] {
                "DELIVERED", "DROPPED", "RATE_LIMITED", "REDACTED",
                "TEMPLATE_MISSING", "CHANNEL_DISABLED", "RECIPIENT_OPTED_OUT"
        }) {
            assertEquals(value, NotificationOutcome.fromWire(value).wire());
        }
    }

    @Test
    void notificationHookKindPinsClosedSet() {
        for (String value : new String[] {
                "COMMENT_CREATED", "MENTION", "WATCH_TRIGGER", "ASSIGNMENT_DUE"
        }) {
            assertEquals(value, NotificationHookKind.fromWire(value).wire());
        }
    }

    @Test
    void redactionReasonPinsClosedSet() {
        for (String value : new String[] {
                "LIVING_MINOR", "DNA_CONSENT_REVOKED", "RAW_PII_DETECTED",
                "VISIBILITY_DEMOTED", "SUBJECT_REMOVED", "CORRECTION_APPLIED"
        }) {
            assertEquals(value, RedactionReason.fromWire(value).wire());
        }
    }

    @Test
    void commentAuthorizationOutcomePinsClosedSet() {
        for (String value : new String[] {"ALLOW", "DENY", "ABAC_DENY"}) {
            assertEquals(value, CommentAuthorizationOutcome.fromWire(value).wire());
        }
    }

    @Test
    void fromWireIsCaseNormalising() {
        assertEquals(CommentStatus.ACTIVE, CommentStatus.fromWire("active"));
        assertTrue(CommentStatus.fromWire("Hidden") == CommentStatus.HIDDEN);
    }
}
