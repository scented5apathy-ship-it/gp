package com.genealogy.platform.services.research.workspace;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka listener wiring for the upstream genealogy events.
 * The platform {@code spring-kafka} auto-configuration creates
 * the consumer factory; this class only declares the listener
 * endpoints so the {@link ResearchWorkspaceProjectionConsumer}
 * is invoked on the right topics.
 *
 * <p>The two upstream topics are owned by the genealogy
 * service; the consumer factory reads them through the
 * same Strimzi partition that the producer uses so the
 * cross-service re-projection is consistent.
 *
 * <p>Per ADR-E0.5-08 the consumer is
 * {@code enable.auto.commit=false} — the consumer side
 * commits offsets only after the projection row committed.
 * The {@code @KafkaListener} annotation is layered on top
 * of the harness bean a later epic (E6.1e + the E2.3
 * roll-out) wires up; E6.1d ships the harness so the
 * Spring Kafka factory can pick the consumer up
 * automatically once the cluster is rolled out.
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
