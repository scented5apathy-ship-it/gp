package com.genealogy.platform.services.genealogy.outbox;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves an event type to its Kafka topic name. Mirrors
 * ADR-E0.5-08: topic naming = {@code
 * <domain>.<aggregate>.<version>.v{n}}, e.g.
 * {@code genealogy.person.v1.v1}.
 *
 * <p>The mapping is intentionally hard-pinned; adding a
 * new event requires an ADR supersession + a closed-set
 * entry in {@link #TOPIC_BY_EVENT_TYPE}. The relay
 * refuses to publish a row whose event type is unknown
 * (it lands in DEAD_LETTERED with
 * {@link DlqReason#UNKNOWN_TOPIC}).
 */
public final class KafkaTopicResolver {

    private static final String DOMAIN = "genealogy";
    private static final String VERSION = "v1";

    private static final Map<String, String> TOPIC_BY_EVENT_TYPE = Map.ofEntries(
            Map.entry("gp.genealogy.v1.TreeCreated", topic("tree")),
            Map.entry("gp.genealogy.v1.TreeVisibilityChanged", topic("tree")),
            Map.entry("gp.genealogy.v1.TreeArchived", topic("tree")),
            Map.entry("gp.genealogy.v1.TreeRestored", topic("tree")),
            Map.entry("gp.genealogy.v1.TreeTransferred", topic("tree")),
            Map.entry("gp.genealogy.v1.TreeDeleted", topic("tree")),
            Map.entry("gp.genealogy.v1.PersonCreated", topic("person")),
            Map.entry("gp.genealogy.v1.PersonUpdated", topic("person")),
            Map.entry("gp.genealogy.v1.PersonPrivacyChanged", topic("person")),
            Map.entry("gp.genealogy.v1.PersonLivingStatusChanged", topic("person")),
            Map.entry("gp.genealogy.v1.PersonDeleted", topic("person")),
            Map.entry("gp.genealogy.v1.UnlistedTokenIssued", topic("unlisted-token")),
            Map.entry("gp.genealogy.v1.UnlistedTokenRevoked", topic("unlisted-token")),
            Map.entry("gp.genealogy.v1.PersonMerged", topic("person-merge")),
            Map.entry("gp.genealogy.v1.PersonMergeReverted", topic("person-merge")),
            Map.entry("gp.genealogy.v1.PersonMergeRejected", topic("person-merge"))
    );

    private KafkaTopicResolver() {
    }

    private static String topic(String aggregate) {
        return DOMAIN + "." + aggregate + "." + VERSION + ".v1";
    }

    /** Returns the topic name for the given event type. */
    public static String topicFor(String eventType) {
        Objects.requireNonNull(eventType, "eventType");
        String topic = TOPIC_BY_EVENT_TYPE.get(eventType);
        if (topic == null) {
            throw new IllegalArgumentException(
                    "no topic mapping for eventType '" + eventType + "'");
        }
        return topic;
    }

    /** Returns the dead-letter topic name (suffixed with {@code .dlq.v1}). */
    public static String dlqFor(String eventType) {
        return topicFor(eventType) + ".dlq.v1";
    }
}
