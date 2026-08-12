package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Notification hook value object. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.notificationHookKinds + notificationChannels + notificationOutcomes`
 * (E6.4), `requirements.md` R10.5 + `design.md` §8.3 + R15.4
 * (analytics / notifications may not collect raw DNA or
 * sensitive content).
 *
 * <p>The hook carries a {@link NotificationHookKind}, a
 * recipient (channel + pseudoId), a template key, a
 * sanitised payload (size-capped at
 * {@link #MAX_PAYLOAD_BYTES} UTF-8 bytes) and a
 * {@link NotificationOutcome}. The raw payload is never
 * copied into the request (per
 * {@code notificationHookNeverCopyRawPayload=true}); the
 * template key is mandatory (per
 * {@code notificationHookTemplateRequired=true}).
 */
public record NotificationHook(
        TenantScopedId hookId,
        NotificationHookKind kind,
        NotificationChannel channel,
        String recipientPseudoId,
        String templateKey,
        NotificationOutcome outcome,
        Map<String, String> sanitizedPayload,
        RedactionReason redactionReason,
        CollaborationAuditAttributes audit,
        Instant dispatchedAt) {

    public static final int MAX_PAYLOAD_BYTES = 4096;
    public static final int MAX_TEMPLATE_KEY_LENGTH = 64;
    public static final int MAX_PSEUDO_ID_LENGTH = 128;
    public static final int MAX_PAYLOAD_ENTRIES = 16;

    public NotificationHook {
        Objects.requireNonNull(hookId, "hookId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(dispatchedAt, "dispatchedAt");
        if (recipientPseudoId == null || recipientPseudoId.isBlank()) {
            throw new IllegalArgumentException("recipientPseudoId must not be blank");
        }
        if (recipientPseudoId.length() > MAX_PSEUDO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "recipientPseudoId exceeds " + MAX_PSEUDO_ID_LENGTH + " characters");
        }
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey must not be blank");
        }
        if (templateKey.length() > MAX_TEMPLATE_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "templateKey exceeds " + MAX_TEMPLATE_KEY_LENGTH + " characters");
        }
        if (outcome == NotificationOutcome.DELIVERED && redactionReason != null) {
            throw new IllegalArgumentException(
                    "DELIVERED hook cannot carry a redactionReason");
        }
        sanitizedPayload = sanitizedPayload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(sanitizedPayload));
        if (sanitizedPayload.size() > MAX_PAYLOAD_ENTRIES) {
            throw new IllegalArgumentException(
                    "sanitizedPayload exceeds " + MAX_PAYLOAD_ENTRIES + " entries");
        }
        int totalBytes = 0;
        for (Map.Entry<String, String> e : sanitizedPayload.entrySet()) {
            String v = e.getValue();
            if (v != null) {
                totalBytes += v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
        }
        if (totalBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "sanitizedPayload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
        }
    }

    public static NotificationHook dropped(
            TenantScopedId hookId,
            NotificationHookKind kind,
            NotificationChannel channel,
            String recipientPseudoId,
            String templateKey,
            NotificationOutcome outcome,
            RedactionReason reason,
            CollaborationAuditAttributes audit,
            Instant dispatchedAt) {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome == NotificationOutcome.DELIVERED) {
            throw new IllegalArgumentException(
                    "dropped(...) requires a non-DELIVERED outcome");
        }
        return new NotificationHook(
                hookId, kind, channel, recipientPseudoId, templateKey,
                outcome, Map.of(), reason, audit, dispatchedAt);
    }
}
