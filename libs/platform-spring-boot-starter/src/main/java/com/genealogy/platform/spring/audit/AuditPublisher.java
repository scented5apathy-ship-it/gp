package com.genealogy.platform.spring.audit;

/**
 * Hook for emitting {@link AuditEvent}s. Default implementation in
 * the starter is the {@code MicrometerAuditPublisher} (counts +
 * structured log). Dedicated {@code audit-service} integration
 * (Kafka topic + idempotent inbox) lands in E3.6 — services must
 * inject this interface rather than a concrete publisher so the
 * swap is a one-line change.
 */
public interface AuditPublisher {

    void publish(AuditEvent event);
}
