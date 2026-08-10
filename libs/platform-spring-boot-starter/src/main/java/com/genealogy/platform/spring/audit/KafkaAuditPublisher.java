package com.genealogy.platform.spring.audit;

import com.genealogy.platform.spring.autoconfigure.PlatformProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka-backed {@link AuditPublisher}. Replaces the
 * {@code MicrometerAuditPublisher} when a service injects an
 * {@link AuditEventSink} bean; otherwise the default no-op sink is
 * used and the publisher degrades to the Micrometer counter +
 * structured log behaviour shipped in E1.4.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link AuditEventValidator} rejects unknown actions / classes
 *       (publish never leaves the originating service).
 *   <li>{@link AuditRedactor} drops {@code denyKeys}, masks
 *       {@code maskKeys}, scrubs free-text {@code scrubPatterns}.
 *   <li>The {@link AuditEventSink} forwards the JSON envelope to the
 *       Kafka audit topic; the {@code audit-service} consumer is
 *       idempotent on {@code eventId}.
 * </ol>
 *
 * <p>Counter names (pseudonymous):
 * <ul>
 *   <li>{@code platform.audit.events.published}
 *   <li>{@code platform.audit.events.rejected}
 *   <li>{@code platform.audit.events.redacted}
 * </ul>
 */
public final class KafkaAuditPublisher implements AuditPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAuditPublisher.class);

    private final AuditEventSink sink;
    private final AuditEventValidator validator;
    private final AuditRedactor redactor;
    private final PlatformProperties properties;
    private final Counter publishedCounter;
    private final Counter rejectedCounter;
    private final Counter redactedCounter;
    private final AtomicLong droppedKeys = new AtomicLong();

    public KafkaAuditPublisher(
            AuditEventSink sink,
            AuditEventValidator validator,
            AuditRedactor redactor,
            MeterRegistry meterRegistry,
            PlatformProperties properties) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.publishedCounter = Counter.builder("platform.audit.events.published")
                .description("Number of audit events forwarded to the audit-service sink")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("platform.audit.events.rejected")
                .description("Number of audit events rejected by the validator")
                .register(meterRegistry);
        this.redactedCounter = Counter.builder("platform.audit.events.redacted")
                .description("Number of audit events that had at least one metadata field redacted")
                .register(meterRegistry);
    }

    @Override
    public void publish(AuditEvent event) {
        if (!properties.getAudit().isEnabled()) {
            return;
        }
        Objects.requireNonNull(event, "event");
        AuditValidationResult validation = validator.validate(event);
        if (!validation.isValid()) {
            rejectedCounter.increment();
            LOG.warn(
                    "audit publish rejected action={} resource={} violation={} detail={}",
                    event.getAction(),
                    event.getResource(),
                    validation.getViolation(),
                    validation.getDetail());
            return;
        }
        String auditClass = AuditEventValidator.deriveAuditClass(event);
        AuditEventEnvelope envelope = AuditEventEnvelope.from(event, auditClass, null);
        boolean redactedSomething = !envelope.getMetadata().isEmpty()
                && envelope.getMetadata().size() != countAfterRedaction(envelope);
        AuditEventEnvelope safeEnvelope = redactor.redact(envelope);
        if (redactedSomething) {
            redactedCounter.increment();
        }
        sink.send(safeEnvelope);
        publishedCounter.increment();
        LOG.info(
                "audit published event_id={} tenant_id={} actor_id={} audit_class={} action={}"
                        + " resource={} resource_id={} correlation_id={}",
                safeEnvelope.getEventId(),
                safeEnvelope.getTenantId(),
                safeEnvelope.getActorId(),
                safeEnvelope.getAuditClass(),
                safeEnvelope.getAction(),
                safeEnvelope.getResourceType(),
                safeEnvelope.getResourceId(),
                safeEnvelope.getCorrelationId());
    }

    private int countAfterRedaction(AuditEventEnvelope envelope) {
        return redactor.redact(envelope).getMetadata().size();
    }

    public long droppedKeys() {
        return droppedKeys.get();
    }
}
