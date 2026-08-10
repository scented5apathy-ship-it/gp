package com.genealogy.platform.spring.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.spring.autoconfigure.PlatformProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KafkaAuditPublisherTest {

    @Test
    void rejectedEventIsNotForwarded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        List<AuditEventEnvelope> sent = new ArrayList<>();
        AuditEventSink sink = sent::add;
        AuditEventValidator validator = new AuditEventValidator();
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        KafkaAuditPublisher publisher = new KafkaAuditPublisher(
                sink, validator, redactor, registry, props);
        AuditEvent event = new AuditEvent(
                "", "actor-1", "tenant.created", "tenant", "smith",
                "corr-1", Map.of());
        publisher.publish(event);
        assertThat(sent).isEmpty();
        assertThat(registry.find("platform.audit.events.rejected").counter().count()).isEqualTo(1d);
        assertThat(registry.find("platform.audit.events.published").counter().count()).isEqualTo(0d);
    }

    @Test
    void knownEventIsForwardedAndRedacted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        List<AuditEventEnvelope> sent = new ArrayList<>();
        AuditEventSink sink = sent::add;
        AuditEventValidator validator = new AuditEventValidator();
        AuditRedactor redactor = AuditRedactor.defaultRedactor();
        KafkaAuditPublisher publisher = new KafkaAuditPublisher(
                sink, validator, redactor, registry, props);
        AuditEvent event = new AuditEvent(
                "tenant-1",
                "actor-1",
                "tenant.created",
                "tenant",
                "smith",
                "corr-1",
                Map.of("email", "alice@example.com", "rawDna", "ACGTACGTACGTACGT"));
        publisher.publish(event);
        assertThat(sent).hasSize(1);
        AuditEventEnvelope envelope = sent.get(0);
        assertThat(envelope.getAuditClass()).isEqualTo("authorization");
        assertThat(envelope.getMetadata()).doesNotContainKey("rawDna");
        assertThat(envelope.getMetadata()).containsEntry("email", "[REDACTED:email]");
        assertThat(registry.find("platform.audit.events.published").counter().count()).isEqualTo(1d);
        assertThat(registry.find("platform.audit.events.redacted").counter().count()).isEqualTo(1d);
    }

    @Test
    void disabledByPlatformProperty() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        props.getAudit().setEnabled(false);
        List<AuditEventEnvelope> sent = new ArrayList<>();
        AuditEventSink sink = sent::add;
        KafkaAuditPublisher publisher = new KafkaAuditPublisher(
                sink, new AuditEventValidator(), AuditRedactor.defaultRedactor(), registry, props);
        publisher.publish(new AuditEvent(
                "tenant-1", "actor-1", "tenant.created", "tenant", "smith",
                "corr-1", Map.of()));
        assertThat(sent).isEmpty();
        assertThat(registry.find("platform.audit.events.published").counter().count()).isEqualTo(0d);
    }

    @Test
    void authEventMapsToAuthClass() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        List<AuditEventEnvelope> sent = new ArrayList<>();
        AuditEventSink sink = sent::add;
        KafkaAuditPublisher publisher = new KafkaAuditPublisher(
                sink, new AuditEventValidator(), AuditRedactor.defaultRedactor(), registry, props);
        publisher.publish(new AuditEvent(
                "tenant-1", "actor-1", "auth.login.succeeded", "session", "sess-1",
                "corr-1", Map.of()));
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getAuditClass()).isEqualTo("auth");
    }

    @Test
    void unknownActionIsRejectedAndNotForwarded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        List<AuditEventEnvelope> sent = new ArrayList<>();
        AuditEventSink sink = sent::add;
        KafkaAuditPublisher publisher = new KafkaAuditPublisher(
                sink, new AuditEventValidator(), AuditRedactor.defaultRedactor(), registry, props);
        publisher.publish(new AuditEvent(
                "tenant-1", "actor-1", "made_up.event", "tenant", "smith",
                "corr-1", Map.of()));
        assertThat(sent).isEmpty();
        assertThat(registry.find("platform.audit.events.rejected").counter().count()).isEqualTo(1d);
    }

    @Test
    void envelopeOccurrenceMatchesEvent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformProperties props = new PlatformProperties();
        List<AuditEventEnvelope> sent = new ArrayList<>();
        AuditEventSink sink = sent::add;
        KafkaAuditPublisher publisher = new KafkaAuditPublisher(
                sink, new AuditEventValidator(), AuditRedactor.defaultRedactor(), registry, props);
        Instant now = Instant.now();
        AuditEvent event = new AuditEvent(
                "tenant-1", "actor-1", "consent.revoked", "consent", "c-1",
                "corr-1", Map.of());
        publisher.publish(event);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getOccurredAt()).isNotNull();
        assertThat(sent.get(0).getOccurredAt().getEpochSecond()).isGreaterThanOrEqualTo(now.getEpochSecond());
    }
}
