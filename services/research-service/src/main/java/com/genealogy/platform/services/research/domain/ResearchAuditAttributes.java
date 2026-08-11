package com.genealogy.platform.services.research.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Audit metadata attached to every research aggregate. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.auditAttributes` (E6.1) + `requirements.md` R16.2
 * (audit log append-only) + NFR5 (no raw PII / DNA / token in
 * logs).
 *
 * <p>Mandatory key {@code actorPseudoId} — the platform
 * pseudonym derived from the Keycloak subject + Kong
 * boundary. Never the raw subject id, never the email.
 *
 * <p>Optional keys are restricted to the closed-set below;
 * adding a new key requires an ADR supersession.
 */
public record ResearchAuditAttributes(
        String actorPseudoId,
        String correlationId,
        String correlationReason,
        Map<String, String> extras) {

    public static final int MAX_EXTRAS = 16;

    public ResearchAuditAttributes {
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        if (actorPseudoId.isBlank()) {
            throw new IllegalArgumentException("actorPseudoId must not be blank");
        }
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (correlationReason != null && correlationReason.length() > 256) {
            throw new IllegalArgumentException(
                    "correlationReason exceeds 256 characters");
        }
        extras = extras == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extras));
        if (extras.size() > MAX_EXTRAS) {
            throw new IllegalArgumentException(
                    "extras exceeds " + MAX_EXTRAS + ": " + extras.size());
        }
        for (Map.Entry<String, String> e : extras.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("extras key must not be blank");
            }
            if (key.length() > 64) {
                throw new IllegalArgumentException(
                        "extras key exceeds 64 characters: " + key);
            }
            if (value != null && value.length() > 1024) {
                throw new IllegalArgumentException(
                        "extras value exceeds 1024 characters for key " + key);
            }
        }
    }

    public static ResearchAuditAttributes of(String actorPseudoId, String correlationId) {
        return new ResearchAuditAttributes(actorPseudoId, correlationId, null, Map.of());
    }

    public ResearchAuditAttributes withReason(String reason) {
        return new ResearchAuditAttributes(actorPseudoId, correlationId, reason, extras);
    }

    public ResearchAuditAttributes withExtra(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(extras);
        next.put(key, value);
        return new ResearchAuditAttributes(actorPseudoId, correlationId, correlationReason, next);
    }
}
