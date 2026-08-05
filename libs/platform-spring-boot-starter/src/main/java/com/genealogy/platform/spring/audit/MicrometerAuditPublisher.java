package com.genealogy.platform.spring.audit;

import com.genealogy.platform.spring.autoconfigure.PlatformProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link AuditPublisher} implementation: increments a
 * Micrometer counter and emits a structured log line. The dedicated
 * {@code audit-service} ingestion (Kafka topic with idempotent
 * inbox + append-only ledger) replaces this in E3.6.
 *
 * <p>No raw DNA / file content / access tokens ever end up in the
 * log line because {@link AuditEvent} only carries opaque ids.
 */
public final class MicrometerAuditPublisher implements AuditPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerAuditPublisher.class);

    private final Counter counter;
    private final PlatformProperties properties;

    public MicrometerAuditPublisher(MeterRegistry meterRegistry, PlatformProperties properties) {
        this.properties = properties;
        this.counter = Counter.builder(properties.getAudit().getMetricName())
                .description("Number of audit events emitted by this service")
                .tag("service", "unknown")
                .register(meterRegistry);
    }

    @Override
    public void publish(AuditEvent event) {
        if (!properties.getAudit().isEnabled()) {
            return;
        }
        counter.increment();
        LOG.info(
                "audit event_id={} tenant_id={} actor_id={} action={} resource={} resource_id={} correlation_id={}",
                event.getEventId(),
                event.getTenantId(),
                event.getActorId(),
                event.getAction(),
                event.getResource(),
                event.getResourceId(),
                event.getCorrelationId());
    }
}
