package com.genealogy.platform.services.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.audit.ingest.AuditIngestService;
import com.genealogy.platform.spring.audit.AuditEventEnvelope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer for the audit topic. The MVP exposes a plain
 * {@link #onMessage(String)} method so the Spring Kafka wiring can
 * be applied via {@code @KafkaListener} in a follow-up epic that
 * adds the {@code spring-kafka} dependency to the version catalog.
 *
 * <p>The wire format is the JSON emitted by
 * {@link com.genealogy.platform.spring.audit.AuditEventEnvelope#toJson()}.
 * The deserialiser is owned here so the unit test path stays
 * framework-free.
 */
public class AuditKafkaListener {

    private static final Logger LOG = LoggerFactory.getLogger(AuditKafkaListener.class);

    private final AuditIngestService ingestService;
    private final ObjectMapper objectMapper;

    public AuditKafkaListener(AuditIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String payload) {
        try {
            AuditEventEnvelope envelope = deserialise(payload);
            ingestService.ingest(envelope);
        } catch (RuntimeException e) {
            LOG.error("failed to ingest audit event payload={}", payload, e);
            throw e;
        }
    }

    private AuditEventEnvelope deserialise(String payload) {
        try {
            Map<String, Object> raw = objectMapper.readValue(payload, Map.class);
            Map<String, String> metadata = new LinkedHashMap<>();
            Object metaRaw = raw.get("metadata");
            if (metaRaw instanceof Map<?, ?> meta) {
                for (Map.Entry<?, ?> entry : meta.entrySet()) {
                    metadata.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return new AuditEventEnvelope(
                    String.valueOf(raw.get("eventId")),
                    String.valueOf(raw.get("tenantId")),
                    String.valueOf(raw.get("actorId")),
                    String.valueOf(raw.get("auditClass")),
                    String.valueOf(raw.get("action")),
                    String.valueOf(raw.get("resourceType")),
                    String.valueOf(raw.get("resourceId")),
                    String.valueOf(raw.get("reasonCode")),
                    String.valueOf(raw.get("correlationId")),
                    java.time.Instant.parse(String.valueOf(raw.get("occurredAt"))),
                    metadata);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid audit envelope payload", e);
        }
    }
}
