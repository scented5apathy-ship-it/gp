package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InMemoryInboxIdempotencyStore}.
 */
class InboxIdempotencyStoreTest {

    @Test
    void firstSeenReturnsTrueAndIsDeduped() {
        InMemoryInboxIdempotencyStore store = new InMemoryInboxIdempotencyStore();
        Instant now = Instant.now();
        assertTrue(store.tryClaim("evt-1", "t", "a", "s", "h",
                "EVENT_ID", "c", "trace", now));
        assertFalse(store.tryClaim("evt-1", "t", "a", "s", "h",
                "EVENT_ID", "c", "trace", now));
    }

    @Test
    void receivedAtReturnsRecordedTimestamp() {
        InMemoryInboxIdempotencyStore store = new InMemoryInboxIdempotencyStore();
        Instant now = Instant.now();
        store.tryClaim("evt-1", "t", "a", "s", "h", "EVENT_ID", "c", "tr", now);
        Optional<Instant> got = store.receivedAt("evt-1");
        assertTrue(got.isPresent());
        assertEquals(now, got.get());
    }

    @Test
    void receivedAtReturnsEmptyForUnknown() {
        InMemoryInboxIdempotencyStore store = new InMemoryInboxIdempotencyStore();
        assertTrue(store.receivedAt("evt-unknown").isEmpty());
    }

    @Test
    void expiredEntriesAreEvictedOnTryClaim() {
        InMemoryInboxIdempotencyStore store = new InMemoryInboxIdempotencyStore(1);
        Instant past = Instant.now().minusSeconds(2L * 86_400);
        store.tryClaim("evt-1", "t", "a", "s", "h", "EVENT_ID", "c", "tr", past);
        // After the TTL elapses, the store treats the event as new again.
        Instant now = Instant.now();
        assertTrue(store.tryClaim("evt-1", "t", "a", "s", "h",
                "EVENT_ID", "c", "tr", now));
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryInboxIdempotencyStore(0));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryInboxIdempotencyStore(-1));
    }

    @Test
    void ttlDaysAccessor() {
        assertEquals(7, new InMemoryInboxIdempotencyStore().ttlDays());
        assertEquals(1, new InMemoryInboxIdempotencyStore(1).ttlDays());
    }

    @Test
    void nullArgsAreRejected() {
        InMemoryInboxIdempotencyStore store = new InMemoryInboxIdempotencyStore();
        assertThrows(NullPointerException.class,
                () -> store.tryClaim(null, "t", "a", "s", "h", "EVENT_ID",
                        "c", "tr", Instant.now()));
        assertThrows(NullPointerException.class,
                () -> store.tryClaim("evt", "t", "a", "s", "h", "EVENT_ID",
                        "c", "tr", null));
    }
}
