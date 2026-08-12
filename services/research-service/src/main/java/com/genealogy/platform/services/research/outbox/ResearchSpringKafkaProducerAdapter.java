package com.genealogy.platform.services.research.outbox;

import com.genealogy.platform.services.research.events.ResearchEventPayloads;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Production Kafka producer adapter. Wraps the
 * {@link KafkaTemplate} published by the {@code spring-kafka}
 * auto-configuration and converts the outbox row into a
 * {@link org.apache.kafka.clients.producer.ProducerRecord}; the
 * Apicurio schema-registry serialiser is configured at the
 * cluster level, so the producer only carries the JSON
 * intermediate (the relay's payloadJson is the canonical
 * authoritative shape).
 *
 * <p>The adapter is intentionally framework-thin: the
 * producer call is the only place that touches the platform
 * Kafka client, so a unit test can swap the
 * {@link ResearchKafkaProducerPort} without bringing up a
 * broker.
 */
@Component
public class ResearchSpringKafkaProducerAdapter implements ResearchKafkaProducerPort {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final AtomicReference<String> lastTopic = new AtomicReference<>();

    public ResearchSpringKafkaProducerAdapter(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
    }

    @Override
    public Result publish(ResearchOutboxEventRecord row, Instant now) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(now, "now");
        String topic = topicFor(row.eventType());
        lastTopic.set(topic);
        try {
            byte[] payload = row.payloadJson().getBytes(StandardCharsets.UTF_8);
            org.apache.kafka.common.header.internals.RecordHeaders headers =
                    new org.apache.kafka.common.header.internals.RecordHeaders();
            headers.add("x-schema-id", row.schemaId().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                    topic,
                    null,
                    row.partitionKey(),
                    payload,
                    headers)).get();
            return Result.published();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.transientFailure("interrupted: " + e.getMessage());
        } catch (Exception e) {
            return Result.transientFailure("kafka producer: " + e.getMessage());
        }
    }

    /** Visible for tests; the topic the previous publish hit. */
    public String lastTopic() {
        return lastTopic.get();
    }

    private static String topicFor(String eventType) {
        return ResearchOutboxRelayWiring.TopicNames.forEventType(eventType);
    }
}
