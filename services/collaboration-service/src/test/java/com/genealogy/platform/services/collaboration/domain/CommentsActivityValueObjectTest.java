package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * E6.4 value-object + executor tests. Pins every
 * compact-constructor rejection + every executor branch
 * for the comment / mention / watch / assignment / activity
 * feed / notification hook flow.
 */
class CommentsActivityValueObjectTest {

    private static final TenantScopedId TENANT = new TenantScopedId(
            "t", TenantScopedId.ResourceKind.COMMENT, "c1");
    private static final TenantScopedId PROPOSAL = new TenantScopedId(
            "t", TenantScopedId.ResourceKind.PROPOSAL, "p1");
    private static final CollaborationAuditAttributes AUDIT =
            CollaborationAuditAttributes.of("actor1", "corr1");

    @Test
    void commentRejectsBlankBody() {
        assertThrows(IllegalArgumentException.class, () -> new Comment(
                TENANT, PROPOSAL, WatchScope.PROPOSAL, "p1",
                CommentStatus.ACTIVE, "", AUDIT, Instant.now(), null, null));
    }

    @Test
    void commentRejectsOversizedBody() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Comment.MAX_BODY_LENGTH + 1; i += 1) sb.append('a');
        String body = sb.toString();
        assertThrows(IllegalArgumentException.class, () -> new Comment(
                TENANT, PROPOSAL, WatchScope.PROPOSAL, "p1",
                CommentStatus.ACTIVE, body, AUDIT, Instant.now(), null, null));
    }

    @Test
    void commentRejectsBlankScopeId() {
        assertThrows(IllegalArgumentException.class, () -> new Comment(
                TENANT, PROPOSAL, WatchScope.PROPOSAL, "",
                CommentStatus.ACTIVE, "ok", AUDIT, Instant.now(), null, null));
    }

    @Test
    void commentRejectsSensitiveFieldIfStatusActive() {
        assertThrows(IllegalArgumentException.class, () -> new Comment(
                TENANT, PROPOSAL, WatchScope.PROPOSAL, "p1",
                CommentStatus.ACTIVE, "see dnaRawData", AUDIT, Instant.now(), null, null));
    }

    @Test
    void commentAllowsSensitiveFieldIfStatusHidden() {
        Comment c = new Comment(
                TENANT, PROPOSAL, WatchScope.PROPOSAL, "p1",
                CommentStatus.HIDDEN, "see dnaRawData", AUDIT, Instant.now(), null, null);
        assertEquals(CommentStatus.HIDDEN, c.status());
    }

    @Test
    void commentRedactedForcesRedactedStatus() {
        Comment c = new Comment(
                TENANT, PROPOSAL, WatchScope.PROPOSAL, "p1",
                CommentStatus.ACTIVE, "ok", AUDIT, Instant.now(), null, null);
        Comment redacted = c.redacted("redact test", RedactionReason.LIVING_MINOR);
        assertEquals(CommentStatus.REDACTED, redacted.status());
        assertEquals(Comment.sanitizeBody("ok"), redacted.body());
    }

    @Test
    void redactedRejectsBlankReason() {
        Comment c = new Comment(
                TENANT, PROPOSAL, WatchScope.PROPOSAL, "p1",
                CommentStatus.ACTIVE, "ok", AUDIT, Instant.now(), null, null);
        assertThrows(IllegalArgumentException.class,
                () -> c.redacted("", RedactionReason.LIVING_MINOR));
    }

    @Test
    void mentionRejectsBlankTargetId() {
        assertThrows(IllegalArgumentException.class, () -> new Mention(
                TENANT, MentionTargetKind.USER, "", AUDIT));
    }

    @Test
    void mentionRejectsForbiddenCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new Mention(
                TENANT, MentionTargetKind.USER, "bad/id", AUDIT));
    }

    @Test
    void watchRejectsEmptyTriggers() {
        assertThrows(IllegalArgumentException.class, () -> new Watch(
                TENANT, WatchScope.PROPOSAL, "p1",
                WatchTrigger.ANY_CHANGE, Set.of(), AUDIT, Instant.now()));
    }

    @Test
    void watchRejectsTriggerNotInSet() {
        assertThrows(IllegalArgumentException.class, () -> new Watch(
                TENANT, WatchScope.PROPOSAL, "p1",
                WatchTrigger.ANY_CHANGE, Set.of(WatchTrigger.MENTION), AUDIT, Instant.now()));
    }

    @Test
    void assignmentRejectsBlankTargetPseudoId() {
        assertThrows(IllegalArgumentException.class, () -> new Assignment(
                TENANT, AssignmentRole.REVIEWER, AssignmentStatus.PENDING,
                "", PROPOSAL, Instant.now(), null, null, AUDIT));
    }

    @Test
    void assignmentAcceptedRequiresClosedAt() {
        assertThrows(IllegalArgumentException.class, () -> new Assignment(
                TENANT, AssignmentRole.REVIEWER, AssignmentStatus.ACCEPTED,
                "target1", PROPOSAL, Instant.now(), null, null, AUDIT));
    }

    @Test
    void assignmentAcceptStampsClosedAt() {
        Assignment a = new Assignment(
                TENANT, AssignmentRole.REVIEWER, AssignmentStatus.PENDING,
                "target1", PROPOSAL, Instant.now(), null, null, AUDIT);
        Assignment accepted = a.accept(Instant.now().plusSeconds(60));
        assertEquals(AssignmentStatus.ACCEPTED, accepted.status());
        assertEquals(accepted.closedAt(), accepted.closedAt());
    }

    @Test
    void activityFeedItemRejectsBlankSummary() {
        assertThrows(IllegalArgumentException.class, () -> new ActivityFeedItem(
                TENANT, ActivityKind.COMMENT_CREATED, ActivityVisibility.PRIVATE,
                "actor1", "target1", "", null, null, Instant.now(), null));
    }

    @Test
    void activityFeedItemRedactedForcesMarker() {
        ActivityFeedItem item = ActivityFeedItem.redacted(
                TENANT, ActivityKind.COMMENT_REDACTED, ActivityVisibility.PRIVATE,
                "actor1", "target1", RedactionReason.LIVING_MINOR, Instant.now());
        assertEquals(ActivityFeedItem.REDACTED_FIELD_MARKER, item.summary());
        assertEquals(RedactionReason.LIVING_MINOR, item.redactionReason());
    }

    @Test
    void activityFeedCollectorDropsForbiddenVisibility() {
        ActivityFeedCollector collector = new ActivityFeedCollector(
                Set.of("PRIVATE", "TREE"));
        ActivityFeedItem item = new ActivityFeedItem(
                TENANT, ActivityKind.COMMENT_CREATED, ActivityVisibility.PUBLIC,
                "actor1", "target1", "ok", null, null, Instant.now(), null);
        ActivityFeed feed = collector.collect(
                TENANT, "actor1", List.of(item), Instant.now(), false);
        assertTrue(feed.items().isEmpty());
    }

    @Test
    void activityFeedCollectorRedactsSensitiveItems() {
        ActivityFeedCollector collector = new ActivityFeedCollector(
                Set.of("PRIVATE"));
        ActivityFeedItem item = new ActivityFeedItem(
                TENANT, ActivityKind.COMMENT_REDACTED, ActivityVisibility.PRIVATE,
                "actor1", "target1", "ok", RedactionReason.LIVING_MINOR,
                Set.of("LIVING_MINOR"), Instant.now(), null);
        ActivityFeed feed = collector.collect(
                TENANT, "actor1", List.of(item), Instant.now(), false);
        assertEquals(1, feed.items().size());
        ActivityFeedItem redacted = feed.items().values().iterator().next();
        assertEquals(ActivityFeedItem.REDACTED_FIELD_MARKER, redacted.summary());
    }

    @Test
    void notificationHookRejectsBlankTemplateKey() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationHook(
                TENANT, NotificationHookKind.COMMENT_CREATED, NotificationChannel.IN_APP,
                "target1", "", NotificationOutcome.DELIVERED, null, null,
                CollaborationAuditAttributes.of("actor1", "corr1"), Instant.now()));
    }

    @Test
    void notificationHookRejectsDeliveredWithRedactionReason() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationHook(
                TENANT, NotificationHookKind.COMMENT_CREATED, NotificationChannel.IN_APP,
                "target1", "tmpl", NotificationOutcome.DELIVERED, null,
                RedactionReason.LIVING_MINOR,
                CollaborationAuditAttributes.of("actor1", "corr1"), Instant.now()));
    }

    @Test
    void notificationHookRejectsOversizedPayload() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NotificationHook.MAX_PAYLOAD_BYTES + 1; i += 1) sb.append('a');
        assertThrows(IllegalArgumentException.class, () -> new NotificationHook(
                TENANT, NotificationHookKind.COMMENT_CREATED, NotificationChannel.IN_APP,
                "target1", "tmpl", NotificationOutcome.DELIVERED,
                java.util.Map.of("k", sb.toString()), null,
                CollaborationAuditAttributes.of("actor1", "corr1"), Instant.now()));
    }

    @Test
    void notificationHookDispatcherAllowDelivers() {
        NotificationHookDispatcher dispatcher = new NotificationHookDispatcher();
        NotificationHook hook = dispatcher.dispatch(
                TENANT, NotificationHookKind.COMMENT_CREATED, NotificationChannel.IN_APP,
                "target1", "tmpl", java.util.Map.of("k", "v"),
                CommentAuthorizationDecision.allow("COLLAB_OK"), Instant.now());
        assertEquals(NotificationOutcome.DELIVERED, hook.outcome());
    }

    @Test
    void notificationHookDispatcherDenyDrops() {
        NotificationHookDispatcher dispatcher = new NotificationHookDispatcher();
        NotificationHook hook = dispatcher.dispatch(
                TENANT, NotificationHookKind.COMMENT_CREATED, NotificationChannel.IN_APP,
                "target1", "tmpl", java.util.Map.of(),
                new CommentAuthorizationDecision(
                        CommentAuthorizationOutcome.DENY, "COLLAB_DENY"),
                Instant.now());
        assertEquals(NotificationOutcome.DROPPED, hook.outcome());
    }

    @Test
    void notificationHookDispatcherAbacDenyRedacts() {
        NotificationHookDispatcher dispatcher = new NotificationHookDispatcher();
        NotificationHook hook = dispatcher.dispatch(
                TENANT, NotificationHookKind.COMMENT_CREATED, NotificationChannel.IN_APP,
                "target1", "tmpl", java.util.Map.of(),
                new CommentAuthorizationDecision(
                        CommentAuthorizationOutcome.ABAC_DENY, "COLLAB_ABAC_DENY"),
                Instant.now());
        assertEquals(NotificationOutcome.REDACTED, hook.outcome());
        assertEquals(RedactionReason.RAW_PII_DETECTED, hook.redactionReason());
    }

    @Test
    void commentAuthorizerDeniesForbiddenScope() {
        CommentAuthorizer auth = new CommentAuthorizer(Set.of("PROPOSAL"));
        CommentAuthorizationDecision d = auth.authorizeCreate(
                "ok", WatchScope.COMMENT, "c1");
        assertFalse(d.isAllowed());
        assertEquals(CommentAuthorizationOutcome.DENY, d.outcome());
    }

    @Test
    void commentAuthorizerAbacDeniesSensitiveField() {
        CommentAuthorizer auth = new CommentAuthorizer(Set.of("PROPOSAL"));
        CommentAuthorizationDecision d = auth.authorizeCreate(
                "see dnaRawData", WatchScope.PROPOSAL, "p1");
        assertFalse(d.isAllowed());
        assertEquals(CommentAuthorizationOutcome.ABAC_DENY, d.outcome());
    }

    @Test
    void commentAuthorizerAllowHappyPath() {
        CommentAuthorizer auth = new CommentAuthorizer(Set.of("PROPOSAL"));
        CommentAuthorizationDecision d = auth.authorizeCreate(
                "ok", WatchScope.PROPOSAL, "p1");
        assertTrue(d.isAllowed());
    }

    @Test
    void activityRedactionFilterDetectsSensitiveField() {
        assertTrue(ActivityRedactionFilter.mentionsSensitiveField("see dnaRawData"));
        assertFalse(ActivityRedactionFilter.mentionsSensitiveField("ok"));
        assertEquals(1, ActivityRedactionFilter.redactedKeyCount("see dnaRawData"));
    }

    @Test
    void activityFeedFilterAuthorizesViaPort() {
        ActivityFeedItem item = new ActivityFeedItem(
                TENANT, ActivityKind.COMMENT_CREATED, ActivityVisibility.PRIVATE,
                "actor1", "target1", "ok", null, null, Instant.now(), null);
        CommentAuthorizationPort allowPort = ctx -> CommentAuthorizationDecision.allow("OK");
        ActivityFeedFilter filter = new ActivityFeedFilter(allowPort);
        assertTrue(filter.isReadable(item, "actor1"));
        CommentAuthorizationPort denyPort = ctx -> new CommentAuthorizationDecision(
                CommentAuthorizationOutcome.DENY, "COLLAB_DENY");
        ActivityFeedFilter denyFilter = new ActivityFeedFilter(denyPort);
        assertFalse(denyFilter.isReadable(item, "actor1"));
    }
}
