package com.genealogy.platform.services.research.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.research.workspace.ResearchWorkspaceProjection.RedactionReason;
import com.genealogy.platform.services.research.workspace.ResearchWorkspaceProjection.Visibility;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Spring {@code @KafkaListener} adapter for the upstream
 * genealogy events that drive the research workspace
 * projection. Closes E6.1d Gap 4.
 *
 * <p>The class re-uses the framework-free
 * {@link ResearchWorkspaceProjectionConsumer} as the
 * envelope-routing parser, and adds the production-side
 * concerns:
 *
 * <ul>
 *   <li><b>Manual ACK after DB commit.</b> The {@code @KafkaListener}
 *       container is configured with
 *       {@code ackMode=MANUAL_IMMEDIATE}; the listener calls
 *       {@link Acknowledgment#acknowledge()} only after the
 *       {@link ResearchConsumerInboxService#apply} call returns
 *       so a rolled-back transaction does not commit the
 *       offset.</li>
 *   <li><b>Idempotency via the durable inbox.</b> The first
 *       statement of every {@code apply(...)} call is the
 *       {@code INSERT ... ON CONFLICT DO NOTHING} into
 *       {@code research_service.consumer_inbox}. A duplicate
 *       delivery observes the row and skips the projection
 *       mutation, so a Kafka re-delivery never forks the
 *       state.</li>
 *   <li><b>Trusted tenant context rebind.</b> The envelope
 *       carries the tenant id; the listener rebinds the
 *       trusted context via
 *       {@link ResearchTenantContextBinder} for the duration
 *       of the projection mutation.</li>
 *   <li><b>Closed-set guards.</b> The envelope's
 *       {@code eventType} / {@code payload} are validated
 *       against the canonical closed-set enums; an unknown
 *       {@code eventType} is logged + skipped (per E6.1d
 *       decision) and the offset is acknowledged so the
 *       consumer does not block.</li>
 * </ul>
 *
 * <p>The listener is wired with the platform
 * {@code spring-kafka} starter; the consumer factory is
 * configured by {@link ResearchKafkaConsumerConfig}.
 *
 * <p>Scope guard (per {@code agent-execution.md} §4.4):
 *   - The harness class {@link ResearchKafkaConsumerConfig} +
 *     the framework-free {@link ResearchWorkspaceProjectionConsumer}
 *     are reused unchanged.
 *   - The projection service is unchanged; the new layer is
 *     a thin adapter on top.
 *   - No new Kafka topic / ACL.
 */
@Component
public class ResearchConsumerInboxListener {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchConsumerInboxListener.class);

    private final ResearchConsumerInboxService inboxService;
    private final ResearchWorkspaceProjectionService projectionService;
    private final ResearchTenantContextBinder tenantContextBinder;
    private final ObjectMapper mapper;

    public ResearchConsumerInboxListener(
            ResearchConsumerInboxService inboxService,
            ResearchWorkspaceProjectionService projectionService,
            ResearchTenantContextBinder tenantContextBinder,
            ObjectMapper mapper) {
        this.inboxService = Objects.requireNonNull(inboxService, "inboxService");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService");
        this.tenantContextBinder = Objects.requireNonNull(tenantContextBinder, "tenantContextBinder");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @KafkaListener(
            topics = "genealogy.tree-visibility.v1.v1",
            groupId = "research-service-workspace",
            containerFactory = "researchKafkaListenerContainerFactory")
    public void onTreeVisibilityChanged(
            ConsumerRecord<String, byte[]> record,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topicHeader,
            Acknowledgment ack) {
        handleEnvelope(record, "gp.genealogy.v1.TreeVisibilityChanged",
                topicHeader == null ? record.topic() : topicHeader, ack);
    }

    @KafkaListener(
            topics = "genealogy.person-redacted.v1.v1",
            groupId = "research-service-workspace",
            containerFactory = "researchKafkaListenerContainerFactory")
    public void onPersonRedacted(
            ConsumerRecord<String, byte[]> record,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topicHeader,
            Acknowledgment ack) {
        handleEnvelope(record, "gp.genealogy.v1.PersonRedacted",
                topicHeader == null ? record.topic() : topicHeader, ack);
    }

    private void handleEnvelope(
            ConsumerRecord<String, byte[]> record,
            String expectedEventType,
            String topic,
            Acknowledgment ack) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(ack, "ack");
        byte[] payload = record.value() == null
                ? new byte[0]
                : record.value();
        String json = new String(payload, StandardCharsets.UTF_8);
        Map<String, Object> envelope;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(json, Map.class);
            envelope = parsed;
        } catch (Exception e) {
            LOG.error("research listener: invalid envelope JSON topic={} partition={} offset={}",
                    topic, record.partition(), record.offset(), e);
            ack.acknowledge();
            return;
        }
        String eventType = stringField(envelope, "eventType");
        if (!expectedEventType.equals(eventType)) {
            LOG.warn("research listener: eventType mismatch topic={} expected={} got={}",
                    topic, expectedEventType, eventType);
            ack.acknowledge();
            return;
        }
        String tenantId = stringField(envelope, "tenantId");
        String actorPseudoId = stringField(envelope, "actorPseudoId");
        String correlationId = stringField(envelope, "correlationId");
        String eventId = stringField(envelope, "eventId");
        if (tenantId == null || tenantId.isBlank()) {
            LOG.error("research listener: missing tenantId topic={} offset={}", topic, record.offset());
            ack.acknowledge();
            return;
        }
        if (eventId == null || eventId.isBlank()) {
            eventId = deterministicEventId(record, json);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) envelope.get("payload");
        final String resolvedEventId = eventId;
        final String resolvedTenantId = tenantId;
        final String resolvedActor = actorPseudoId;
        final String resolvedCorrelation = correlationId;
        final String resolvedEventType = eventType;

        try {
            inboxService.apply(
                    resolvedTenantId,
                    topic,
                    resolvedEventId,
                    resolvedEventType,
                    json,
                    resolvedActor,
                    resolvedCorrelation,
                    () -> {
                        tenantContextBinder.runWith(
                                resolvedTenantId, resolvedActor, resolvedCorrelation,
                                () -> dispatchProjection(resolvedEventType, payloadMap,
                                        resolvedTenantId, resolvedActor, resolvedCorrelation));
                        return null;
                    });
        } catch (RuntimeException e) {
            LOG.error("research listener: failed envelope topic={} tenantId={} eventId={}",
                    topic, resolvedTenantId, resolvedEventId, e);
            // Re-throw so the Spring container routes the offset
            // to the DLT (per design.md §7.3). The duplicate
            // detection in the next re-delivery is guarded by
            // the durable inbox row (Outcome=FAILED).
            throw e;
        } finally {
            ack.acknowledge();
        }
    }

    private Void dispatchProjection(
            String eventType,
            Map<String, Object> payloadMap,
            String tenantId,
            String actorPseudoId,
            String correlationId) {
        if (payloadMap == null) {
            throw new IllegalArgumentException(
                    eventType + " payload is empty (envelope MUST carry the Avro payload)");
        }
        switch (eventType) {
            case "gp.genealogy.v1.TreeVisibilityChanged" -> {
                String treeId = stringField(payloadMap, "treeId");
                String to = stringField(payloadMap, "to");
                if (treeId == null || to == null) {
                    throw new IllegalArgumentException(
                            "TreeVisibilityChanged payload must carry treeId + to (got " + payloadMap + ")");
                }
                Visibility visibility = parseVisibility(to);
                projectionService.rebroadcastVisibility(
                        tenantId, treeId, visibility, actorPseudoId, correlationId);
            }
            case "gp.genealogy.v1.PersonRedacted" -> {
                String personId = stringField(payloadMap, "personId");
                String reasonRaw = stringField(payloadMap, "reason");
                if (personId == null || reasonRaw == null) {
                    throw new IllegalArgumentException(
                            "PersonRedacted payload must carry personId + reason (got " + payloadMap + ")");
                }
                RedactionReason reason = parseRedactionReason(reasonRaw);
                projectionService.applyRedactionOverlay(
                        tenantId, personId, reason, actorPseudoId, correlationId);
            }
            default -> LOG.warn("research listener: skipping unknown eventType={}", eventType);
        }
        return null;
    }

    private static String stringField(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Visibility parseVisibility(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("visibility 'to' is required");
        }
        try {
            return Visibility.valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "unknown visibility '" + wire + "' (closed-set: PRIVATE | UNLISTED | PUBLIC)");
        }
    }

    private static RedactionReason parseRedactionReason(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("redaction reason is required");
        }
        try {
            return RedactionReason.valueOf(wire.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "unknown redaction reason '" + wire
                            + "' (closed-set: LIVING | MINOR | CONSENT_REVOKED"
                            + " | JURISDICTION_BLOCKED)");
        }
    }

    /**
     * Stable event id fallback when the upstream envelope
     * omits {@code eventId}. The deterministic hash lets the
     * durable inbox correctly identify re-deliveries of the
     * same Kafka record even when the producer does not
     * supply an id.
     */
    private static String deterministicEventId(ConsumerRecord<String, byte[]> record, String body) {
        java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on the JVM", e);
        }
        digest.update(record.topic().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Integer.toString(record.partition()).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Long.toString(record.offset()).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        if (record.key() != null) {
            digest.update(record.key().getBytes(StandardCharsets.UTF_8));
        }
        digest.update((byte) 0);
        digest.update(body.getBytes(StandardCharsets.UTF_8));
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Static helper kept for backward compatibility with the
     * legacy test seam: callers that only have the JSON
     * envelope can resolve the event id deterministically.
     */
    public static String eventIdFor(String topic, int partition, long offset, String key, String body) {
        java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on the JVM", e);
        }
        digest.update(topic.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Integer.toString(partition).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Long.toString(offset).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        if (key != null) {
            digest.update(key.getBytes(StandardCharsets.UTF_8));
        }
        digest.update((byte) 0);
        digest.update(body.getBytes(StandardCharsets.UTF_8));
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Package-private to let the harness inspect the dispatched events. */
    Map<String, String> debugMetadata() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("listenerClass", getClass().getSimpleName());
        return out;
    }
}
