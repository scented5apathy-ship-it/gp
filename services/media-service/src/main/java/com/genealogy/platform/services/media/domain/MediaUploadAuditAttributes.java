package com.genealogy.platform.services.media.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Audit metadata attached to every upload-lifecycle aggregate.
 * Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.auditRequiredKeys` (E7.1) + `requirements.md` R16.2
 * (audit log append-only) + NFR5 (no raw PII / DNA / token in
 * logs).
 *
 * <p>Mandatory keys: {@code actorPseudoId} +
 * {@code correlationId}. Optional keys are restricted to the
 * closed-set; adding a new key requires an ADR supersession.
 */
public record MediaUploadAuditAttributes(
        String actorPseudoId,
        String correlationId,
        String correlationReason,
        Map<String, String> extras) {

    public static final int MAX_EXTRAS = 16;
    public static final int MAX_EXTRAS_KEY_LENGTH = 64;
    public static final int MAX_EXTRAS_VALUE_LENGTH = 1024;
    public static final int MAX_REASON_LENGTH = 256;

    public MediaUploadAuditAttributes {
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        if (actorPseudoId.isBlank()) {
            throw new IllegalArgumentException("actorPseudoId must not be blank");
        }
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        if (correlationReason != null && correlationReason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "correlationReason exceeds " + MAX_REASON_LENGTH + " characters");
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
            if (key.length() > MAX_EXTRAS_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "extras key exceeds " + MAX_EXTRAS_KEY_LENGTH + " characters: " + key);
            }
            if (value != null && value.length() > MAX_EXTRAS_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "extras value exceeds " + MAX_EXTRAS_VALUE_LENGTH
                                + " characters for key " + key);
            }
        }
    }

    public static MediaUploadAuditAttributes of(
            String actorPseudoId, String correlationId) {
        return new MediaUploadAuditAttributes(actorPseudoId, correlationId, null, Map.of());
    }

    public MediaUploadAuditAttributes withReason(String reason) {
        return new MediaUploadAuditAttributes(
                actorPseudoId, correlationId, reason, extras);
    }

    public MediaUploadAuditAttributes withExtra(String key, String value) {
        Map<String, String> next = new LinkedHashMap<>(extras);
        next.put(key, value);
        return new MediaUploadAuditAttributes(
                actorPseudoId, correlationId, correlationReason, next);
    }
}
