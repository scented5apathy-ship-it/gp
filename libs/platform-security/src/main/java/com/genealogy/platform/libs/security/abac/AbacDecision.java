package com.genealogy.platform.libs.security.abac;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Final ABAC decision returned by the engine. Per
 * {@code design.md} §6.2 the decision has {@code allow/deny},
 * obligations and a reason code. The {@link #obligations} map
 * keys are {@link AbacObligation.Kind}.
 *
 * <p>{@code decisionId} is a stable opaque identifier — services
 * emit it on the audit entry alongside the {@code reasonCode} so
 * the on-call team can trace a denial back to the policy.
 */
public final class AbacDecision {

    public enum Effect {
        ALLOW,
        DENY
    }

    private final String decisionId;
    private final Effect effect;
    private final ReasonCode reasonCode;
    private final AbacObligation obligations;
    private final Instant evaluatedAt;
    private final Map<String, String> attributes;
    private final String openfgaCheckId;

    private AbacDecision(
            String decisionId,
            Effect effect,
            ReasonCode reasonCode,
            AbacObligation obligations,
            Instant evaluatedAt,
            Map<String, String> attributes,
            String openfgaCheckId) {
        this.decisionId = Objects.requireNonNull(decisionId, "decisionId");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.obligations = Objects.requireNonNull(obligations, "obligations");
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.openfgaCheckId = openfgaCheckId;
    }

    public static AbacDecision allow(String decisionId, AbacObligation obligations) {
        if (obligations == null) {
            obligations = AbacObligation.none();
        }
        return new AbacDecision(
                decisionId, Effect.ALLOW,
                ReasonCode.OBLIGATION_REDACT, obligations,
                Instant.now(), Map.of(), null);
    }

    public static AbacDecision allowWithReason(
            String decisionId, ReasonCode reason, AbacObligation obligations) {
        return new AbacDecision(
                decisionId, Effect.ALLOW,
                Objects.requireNonNull(reason, "reason"),
                obligations == null ? AbacObligation.none() : obligations,
                Instant.now(), Map.of(), null);
    }

    public static AbacDecision deny(String decisionId, ReasonCode reason) {
        return new AbacDecision(
                decisionId, Effect.DENY,
                Objects.requireNonNull(reason, "reason"),
                AbacObligation.none(),
                Instant.now(), Map.of(), null);
    }

    public static AbacDecision denyWithObligations(
            String decisionId,
            ReasonCode reason,
            AbacObligation obligations,
            String openfgaCheckId) {
        return new AbacDecision(
                decisionId, Effect.DENY,
                Objects.requireNonNull(reason, "reason"),
                obligations == null ? AbacObligation.none() : obligations,
                Instant.now(), Map.of(), openfgaCheckId);
    }

    public String decisionId() {
        return decisionId;
    }

    public Effect effect() {
        return effect;
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }

    public AbacObligation obligations() {
        return obligations;
    }

    public Instant evaluatedAt() {
        return evaluatedAt;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public Optional<String> openfgaCheckId() {
        return openfgaCheckId == null ? Optional.empty() : Optional.of(openfgaCheckId);
    }

    public boolean isAllow() {
        return effect == Effect.ALLOW;
    }

    public boolean isDeny() {
        return effect == Effect.DENY;
    }

    public AbacDecision withAttribute(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, String> merged = new LinkedHashMap<>(attributes);
        merged.put(key, value);
        return new AbacDecision(decisionId, effect, reasonCode, obligations,
                evaluatedAt, merged, openfgaCheckId);
    }

    public AbacDecision withOpenfgaCheckId(String checkId) {
        if (checkId == null) {
            return this;
        }
        return new AbacDecision(decisionId, effect, reasonCode, obligations,
                evaluatedAt, attributes, checkId);
    }
}
