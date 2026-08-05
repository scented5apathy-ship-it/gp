package com.genealogy.platform.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kafka Testcontainers fixture. Uses the Confluent Platform image
 * pinned in ADR-E0.5-01 (Kafka 3.8) and exposes the bootstrap
 * servers through Spring dynamic properties. The Strimzi + Apicurio
 * schema registry container is NOT started here — it is
 * environment-specific and the E13 observability epic wires the
 * dedicated integration suite.
 */
public class KafkaFixture implements TestcontainersFixture {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1");

    private final KafkaContainer container;

    public KafkaFixture() {
        this(new KafkaContainer(IMAGE).withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true").withReuse(true));
    }

    public KafkaFixture(KafkaContainer container) {
        this.container = container;
    }

    @Override
    public void overrideProperties(DynamicPropertyRegistry registry) {
        if (!container.isRunning()) {
            container.start();
        }
        registry.add("spring.kafka.bootstrap-servers", container::getBootstrapServers);
        registry.add("platform.kafka.bootstrap-servers", container::getBootstrapServers);
    }

    public KafkaContainer container() {
        return container;
    }

    @Override
    public void stop() {
        // no-op; see PostgresFixture
    }
}
