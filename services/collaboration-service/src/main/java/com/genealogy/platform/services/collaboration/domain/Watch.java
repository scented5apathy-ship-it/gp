package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Watch subscription record. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.watchScopes + watchTriggers + watchReAuthorizationRequiredOnTrigger`
 * (E6.4), `requirements.md` R10.5.
 *
 * <p>A watch subscription binds a {@link WatchScope} to a
 * trigger + a subscriber. The application layer must
 * re-authorize the subscription before every notification
 * dispatch (per the
 * {@code watchReAuthorizationRequiredOnTrigger} toggle).
 */
public record Watch(
        TenantScopedId subscriptionId,
        WatchScope scope,
        String scopeId,
        WatchTrigger trigger,
        Set<WatchTrigger> triggers,
        CollaborationAuditAttributes audit,
        Instant subscribedAt) {

    public static final int MAX_SCOPE_ID_LENGTH = 128;
    public static final int MAX_TRIGGERS = 8;

    public Watch {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(subscribedAt, "subscribedAt");
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId must not be blank");
        }
        if (scopeId.length() > MAX_SCOPE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "scopeId exceeds " + MAX_SCOPE_ID_LENGTH + " characters");
        }
        if (triggers == null || triggers.isEmpty()) {
            throw new IllegalArgumentException("triggers must not be empty");
        }
        if (triggers.size() > MAX_TRIGGERS) {
            throw new IllegalArgumentException(
                    "triggers exceeds " + MAX_TRIGGERS + " entries");
        }
        if (!triggers.contains(trigger)) {
            throw new IllegalArgumentException(
                    "trigger " + trigger + " must be present in triggers");
        }
    }
}
