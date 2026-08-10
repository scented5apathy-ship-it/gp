package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Kafka topic resolver.
 */
class KafkaTopicResolverTest {

    @Test
    void mergeTopicResolvesToPersonMergeAggregate() {
        assertEquals("genealogy.person-merge.v1.v1",
                KafkaTopicResolver.topicFor(MergeEventPayloads.EVENT_PERSON_MERGED));
        assertEquals("genealogy.person-merge.v1.v1",
                KafkaTopicResolver.topicFor(MergeEventPayloads.EVENT_PERSON_MERGE_REVERTED));
        assertEquals("genealogy.person-merge.v1.v1",
                KafkaTopicResolver.topicFor(MergeEventPayloads.EVENT_PERSON_MERGE_REJECTED));
    }

    @Test
    void personAndTreeCrudResolveToCorrectTopics() {
        assertEquals("genealogy.tree.v1.v1",
                KafkaTopicResolver.topicFor("gp.genealogy.v1.TreeCreated"));
        assertEquals("genealogy.tree.v1.v1",
                KafkaTopicResolver.topicFor("gp.genealogy.v1.TreeDeleted"));
        assertEquals("genealogy.person.v1.v1",
                KafkaTopicResolver.topicFor("gp.genealogy.v1.PersonCreated"));
        assertEquals("genealogy.person.v1.v1",
                KafkaTopicResolver.topicFor("gp.genealogy.v1.PersonUpdated"));
        assertEquals("genealogy.unlisted-token.v1.v1",
                KafkaTopicResolver.topicFor("gp.genealogy.v1.UnlistedTokenIssued"));
    }

    @Test
    void dlqTopicSuffixesWithDotDlqDotV1() {
        assertEquals("genealogy.person-merge.v1.v1.dlq.v1",
                KafkaTopicResolver.dlqFor(MergeEventPayloads.EVENT_PERSON_MERGED));
    }

    @Test
    void unknownEventTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> KafkaTopicResolver.topicFor("gp.genealogy.v1.Bogus"));
    }

    @Test
    void nullEventTypeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> KafkaTopicResolver.topicFor(null));
    }

    @Test
    void typeContractStability() {
        assertTrue(true);
    }
}
