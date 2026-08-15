package com.genealogy.platform.services.research.workspace;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;

/**
 * Kafka listener wiring for the upstream genealogy events.
 *
 * <p>E6.1d shipped the harness bean + the
 * {@link ResearchWorkspaceProjectionConsumer} entry point;
 * E6.1e adds:
 *
 * <ul>
 *   <li>The {@code researchKafkaListenerContainerFactory}
 *       bean that the {@link ResearchConsumerInboxListener}
 *       {@code @KafkaListener} annotations reference. The
 *       factory is configured for {@code MANUAL_IMMEDIATE}
 *       ack so the listener can defer the commit until the
 *       database transaction commits.</li>
 *   <li>The {@link ResearchJdbcConsumerInboxRepository} bean
 *       backing the durable consumer inbox.</li>
 * </ul>
 *
 * <p>Per ADR-E0.5-08 the consumer is
 * {@code enable.auto.commit=false} + {@code AckMode.MANUAL_IMMEDIATE};
 * the offset commits only after the projection row committed.
 */
@Configuration
public class ResearchKafkaConsumerConfig {

    @Bean
    public ResearchKafkaListenerHarness researchVisibilityChangeListener(
            ResearchWorkspaceProjectionConsumer consumer) {
        return new ResearchKafkaListenerHarness(consumer, "genealogy.tree-visibility.v1.v1");
    }

    @Bean
    public ResearchKafkaListenerHarness researchPersonRedactedListener(
            ResearchWorkspaceProjectionConsumer consumer) {
        return new ResearchKafkaListenerHarness(consumer, "genealogy.person-redacted.v1.v1");
    }

    /**
     * The {@code ConcurrentKafkaListenerContainerFactory} the
     * Spring {@code @KafkaListener} annotations on
     * {@link ResearchConsumerInboxListener} reference.
     */
    @Bean(name = "researchKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> researchKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(1);
        return factory;
    }

    /**
     * The production JdbcTemplate-backed inbox repository.
     * Unit-test {@code @TestConfiguration} can replace this
     * with an in-memory fake.
     */
    @Bean
    public ResearchJdbcConsumerInboxRepository researchJdbcConsumerInboxRepository(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new ResearchJdbcConsumerInboxRepository(jdbcTemplate);
    }

    /**
     * Tiny test seam: the consumer is registered via the
     * platform auto-configuration; this bean is the entry
     * point the integration tests use to invoke the
     * consumer directly.
     */
    public static final class ResearchKafkaListenerHarness {
        private final ResearchWorkspaceProjectionConsumer consumer;
        private final String topic;

        public ResearchKafkaListenerHarness(ResearchWorkspaceProjectionConsumer consumer, String topic) {
            this.consumer = consumer;
            this.topic = topic;
        }

        public void onPayload(String payload) {
            consumer.onPayload(payload);
        }

        public String topic() {
            return topic;
        }
    }
}
