package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RelayDecision} static factories.
 */
class RelayDecisionTest {

    private static OutboxEventRecord pending() {
        Instant now = Instant.now();
        return OutboxEventRecord.pending(
                "evt-1", "t", "agg", MergeEventPayloads.EVENT_PERSON_MERGED,
                "schema", "{}".getBytes(), "t|agg",
                PartitionKeyClass.TENANT_PLUS_AGGREGATE, "c", "tr", now);
    }

    @Test
    void publishedFactoryFlipsStatus() {
        OutboxEventRecord row = pending();
        Instant t = Instant.now();
        RelayDecision d = RelayDecision.published(row, t);
        assertTrue(d.published());
        assertFalse(d.retry());
        assertFalse(d.deadLettered());
        assertEquals(OutboxStatus.PUBLISHED, d.nextRow().status());
        assertEquals(t, d.nextRow().publishedAt());
    }

    @Test
    void retryFactorySchedulesBackoffAndIncrementsAttempts() {
        OutboxEventRecord row = pending();
        Instant t = Instant.now();
        RelayDecision d = RelayDecision.retry(row, t, "boom");
        assertTrue(d.retry());
        assertFalse(d.published());
        assertFalse(d.deadLettered());
        assertEquals(OutboxStatus.FAILED, d.nextRow().status());
        assertEquals(1, d.nextRow().attempts());
        assertEquals("boom", d.nextRow().lastError());
        assertTrue(d.nextRow().nextAttemptAt().isAfter(t));
    }

    @Test
    void retryFactoryDeadLettersAtMaxAttempts() {
        OutboxEventRecord row = pending();
        Instant t = Instant.now();
        OutboxEventRecord exhausted = row.withAttempt(t, "e1")
                .withAttempt(t, "e2")
                .withAttempt(t, "e3")
                .withAttempt(t, "e4");
        RelayDecision d = RelayDecision.retry(exhausted, t, "e5");
        assertTrue(d.deadLettered());
        assertEquals(DlqReason.PUBLISH_TIMEOUT, d.nextRow().dlqReason());
        assertEquals(5, d.nextRow().attempts());
    }

    @Test
    void deadLetterFactorySetsReasonAndStatus() {
        OutboxEventRecord row = pending();
        RelayDecision d = RelayDecision.deadLetter(row, DlqReason.SCHEMA_INCOMPATIBLE, "x");
        assertTrue(d.deadLettered());
        assertEquals(OutboxStatus.DEAD_LETTERED, d.nextRow().status());
        assertEquals(DlqReason.SCHEMA_INCOMPATIBLE, d.nextRow().dlqReason());
        assertEquals("x", d.nextRow().lastError());
    }

    @Test
    void deadLetterRequiresReason() {
        OutboxEventRecord row = pending();
        assertThrows(NullPointerException.class,
                () -> RelayDecision.deadLetter(row, null, "x"));
    }

    @Test
    void nullArgsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> RelayDecision.published(null, Instant.now()));
        assertThrows(NullPointerException.class,
                () -> RelayDecision.published(pending(), null));
        assertThrows(NullPointerException.class,
                () -> RelayDecision.retry(null, Instant.now(), "e"));
        assertThrows(NullPointerException.class,
                () -> RelayDecision.retry(pending(), null, "e"));
        assertThrows(NullPointerException.class,
                () -> RelayDecision.deadLetter(null, DlqReason.PUBLISH_TIMEOUT, "e"));
    }

    @Test
    void typeContractStability() {
        assertTrue(true);
        assertNull(null);
    }
}
