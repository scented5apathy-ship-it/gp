package com.genealogy.platform.spring.audit;

/**
 * Pluggable transport for {@link AuditEventEnvelope}s. The default
 * no-op implementation is bound by {@code AuditAutoConfiguration};
 * production services wire a Kafka-backed sink (see
 * {@code services/audit-service/.../KafkaAuditEventSink}) without
 * touching the application code that calls
 * {@code AuditPublisher.publish(...)}.
 *
 * <p>The interface stays framework-free so service tests can supply
 * an in-memory sink without standing up Kafka. The wire format is
 * {@link AuditEventEnvelope#toJson()} so producer + consumer agree
 * on the contract regardless of transport.
 */
@FunctionalInterface
public interface AuditEventSink {

    void send(AuditEventEnvelope envelope);

    /**
     * No-op sink used when {@code platform.audit.enabled=false} or
     * when the service has not registered a transport yet. Keeps
     * DI consumers compiling.
     */
    AuditEventSink NOOP = envelope -> {
        // intentionally empty
    };
}
