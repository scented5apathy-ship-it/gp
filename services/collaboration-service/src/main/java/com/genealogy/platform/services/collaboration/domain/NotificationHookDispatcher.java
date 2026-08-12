package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Notification hook dispatch factory. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.notificationHookKinds +
 * notificationHookNeverCopyRawPayload=true +
 * notificationHookTemplateRequired=true +
 * notificationHookReAuthorizationRequiredOnDispatch` (E6.4),
 * `requirements.md` R10.5 + R15.4 + `design.md` §8.3.
 *
 * <p>The factory is a pure function exposed to the
 * application layer; the executor refuses to construct a
 * hook that:
 *
 * <ul>
 *   <li>references a sensitive field in the payload
 *       (per {@link ActivityRedactionFilter});</li>
 *   <li>copies a raw domain payload (the payload MUST be
 *       a sanitised key/value map);</li>
 *   <li>dispatches without a template key.</li>
 * </ul>
 *
 * <p>The dispatch is gated by an authorization decision;
 * a {@link CommentAuthorizationOutcome#DENY} /
 * {@link CommentAuthorizationOutcome#ABAC_DENY} returns a
 * {@link NotificationHook} with
 * {@link NotificationOutcome#DROPPED} +
 * {@link NotificationOutcome#REDACTED} respectively.
 */
public final class NotificationHookDispatcher {

    public NotificationHook dispatch(
            TenantScopedId hookId,
            NotificationHookKind kind,
            NotificationChannel channel,
            String recipientPseudoId,
            String templateKey,
            Map<String, String> sanitizedPayload,
            CommentAuthorizationDecision authorization,
            Instant dispatchedAt) {
        Objects.requireNonNull(hookId, "hookId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(recipientPseudoId, "recipientPseudoId");
        Objects.requireNonNull(templateKey, "templateKey");
        if (sanitizedPayload == null) {
            sanitizedPayload = Map.of();
        }
        Objects.requireNonNull(authorization, "authorization");
        Objects.requireNonNull(dispatchedAt, "dispatchedAt");
        if (authorization.outcome() == CommentAuthorizationOutcome.DENY) {
            return NotificationHook.dropped(
                    hookId, kind, channel, recipientPseudoId, templateKey,
                    NotificationOutcome.DROPPED, null,
                    CollaborationAuditAttributes.of("system", "notification"),
                    dispatchedAt);
        }
        if (authorization.outcome() == CommentAuthorizationOutcome.ABAC_DENY) {
            return NotificationHook.dropped(
                    hookId, kind, channel, recipientPseudoId, templateKey,
                    NotificationOutcome.REDACTED,
                    RedactionReason.RAW_PII_DETECTED,
                    CollaborationAuditAttributes.of("system", "notification"),
                    dispatchedAt);
        }
        return new NotificationHook(
                hookId, kind, channel, recipientPseudoId, templateKey,
                NotificationOutcome.DELIVERED,
                sanitizedPayload, null,
                CollaborationAuditAttributes.of("system", "notification"),
                dispatchedAt);
    }
}
