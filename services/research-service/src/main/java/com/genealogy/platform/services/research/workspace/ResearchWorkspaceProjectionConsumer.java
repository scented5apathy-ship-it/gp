package com.genealogy.platform.services.research.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.research.workspace.ResearchWorkspaceProjection.RedactionReason;
import com.genealogy.platform.services.research.workspace.ResearchWorkspaceProjection.Visibility;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Consumer-side entry point for the upstream genealogy events.
 *
 * <p>The E6.1d commit keeps this class at the test-harness
 * layer: the production Spring {@code @KafkaListener} wiring
 * lands in E6.1e once the canonical {@code TreeVisibilityChanged}
 * + {@code PersonRedacted} Avro deserializers are generated
 * via buf (the consumer relies on the canonical
 * {@code EventEnvelope} envelope so the Spring Kafka adapter
 * is the only piece that needs to be added).
 *
 * <p>Per R8.4 + NFR1:
 *   - {@code TreeVisibilityChanged} re-broadcasts the new
 *     visibility onto every workspace projection row for the
 *     affected tree.
 *   - {@code PersonRedacted} applies the redaction overlay to
 *     every projection row that references the redacted
 *     subject.
 *
 * <p>The {@link ResearchTenantContextBinder} binds the trusted
 * tenant context for the duration of the call so the
 * {@link ResearchWorkspaceProjectionService} methods can read
 * the tenant id + actor id the same way the REST + gRPC paths
 * do.
 *
 * <p>Residual gap (E6.1e): the {@code @KafkaListener} bean
 * wraps this entry point once the Spring Kafka auto-config +
 * Apicurio deserializer are wired in the next Epic.
 */
@Component
public class ResearchWorkspaceProjectionConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ResearchWorkspaceProjectionConsumer.class);

    private final ResearchWorkspaceProjectionService service;
    private final ResearchTenantContextBinder tenantContextBinder;
    private final ObjectMapper mapper;

    public ResearchWorkspaceProjectionConsumer(
            ResearchWorkspaceProjectionService service,
            ResearchTenantContextBinder tenantContextBinder,
            ObjectMapper mapper) {
        this.service = Objects.requireNonNull(service, "service");
        this.tenantContextBinder = Objects.requireNonNull(tenantContextBinder, "tenantContextBinder");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Test seam: the entry point accepts the JSON-intermediate
     * envelope (the production relay converts envelope →
     * Avro payload at publish time; the consumer side
     * decodes the envelope first and then the Avro payload
     * in the next Epic). Tests skip the Kafka adapter and
     * call this method directly.
     */
    public void onEnvelope(String envelopeJson) {
        Objects.requireNonNull(envelopeJson, "envelopeJson");
        try {
            java.util.Map<String, Object> envelope;
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> parsed = mapper.readValue(envelopeJson, java.util.Map.class);
                envelope = parsed;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new RuntimeException("invalid envelope JSON", e);
            }
            String eventType = stringField(envelope, "eventType");
            String tenantId = stringField(envelope, "tenantId");
            String actorPseudoId = stringField(envelope, "actorPseudoId");
            String correlationId = stringField(envelope, "correlationId");
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required (envelope MUST carry it)");
            }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> payload =
                    (java.util.Map<String, Object>) envelope.get("payload");
            switch (eventType) {
                case "gp.genealogy.v1.TreeVisibilityChanged" ->
                    handleVisibilityChanged(tenantId, payload, actorPseudoId, correlationId);
                case "gp.genealogy.v1.PersonRedacted" ->
                    handlePersonRedacted(tenantId, payload, actorPseudoId, correlationId);
                default -> LOG.warn(
                        "research workspace consumer skipping unknown eventType={}",
                        eventType);
            }
        } catch (RuntimeException e) {
            LOG.error("research workspace consumer failed envelope={}", envelopeJson, e);
            throw e;
        }
    }

    /** Backward-compatible overload for the legacy test harness. */
    public void onPayload(String payload) {
        onEnvelope(payload);
    }

    private void handleVisibilityChanged(String tenantId, java.util.Map<String, Object> payload,
            String actorPseudoId, String correlationId) {
        if (payload == null) {
            throw new IllegalArgumentException(
                    "TreeVisibilityChanged payload is empty (envelope MUST carry the Avro payload)");
        }
        String treeId = stringField(payload, "treeId");
        String to = stringField(payload, "to");
        if (treeId == null || to == null) {
            throw new IllegalArgumentException(
                    "TreeVisibilityChanged payload must carry treeId + to (got " + payload + ")");
        }
        Visibility visibility = parseVisibility(to);
        tenantContextBinder.runWith(tenantId, actorPseudoId, correlationId, () -> {
            service.rebroadcastVisibility(
                    tenantId, treeId, visibility, actorPseudoId, correlationId);
            return null;
        });
    }

    private void handlePersonRedacted(String tenantId, java.util.Map<String, Object> payload,
            String actorPseudoId, String correlationId) {
        if (payload == null) {
            throw new IllegalArgumentException(
                    "PersonRedacted payload is empty (envelope MUST carry the Avro payload)");
        }
        String personId = stringField(payload, "personId");
        String reasonRaw = stringField(payload, "reason");
        if (personId == null || reasonRaw == null) {
            throw new IllegalArgumentException(
                    "PersonRedacted payload must carry personId + reason (got " + payload + ")");
        }
        RedactionReason reason = parseRedactionReason(reasonRaw);
        tenantContextBinder.runWith(tenantId, actorPseudoId, correlationId, () -> {
            service.applyRedactionOverlay(
                    tenantId, personId, reason, actorPseudoId, correlationId);
            return null;
        });
    }

    private static String stringField(java.util.Map<String, Object> body, String key) {
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
}
