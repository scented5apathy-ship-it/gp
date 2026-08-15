package com.genealogy.platform.services.media.delivery;

import java.util.Objects;

/**
 * Verdict returned by {@link DeliveryOpenFgaPort#check}.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryDecisions` (E7.4) +
 * `requirements.md` R9.5 + ADR-E0.5-06.
 */
public record DeliveryOpenFgaVerdict(
        DeliveryOpenFgaOutcome outcome,
        DeliveryFailureReason failureReason,
        String reasonCode) {

    public DeliveryOpenFgaVerdict {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome == DeliveryOpenFgaOutcome.DENY
                && failureReason == null) {
            throw new IllegalArgumentException(
                    "DENY verdict MUST carry a failureReason");
        }
        if (outcome == DeliveryOpenFgaOutcome.ALLOW
                && failureReason != null) {
            throw new IllegalArgumentException(
                    "ALLOW verdict MUST NOT carry a failureReason");
        }
    }

    public static DeliveryOpenFgaVerdict allow() {
        return new DeliveryOpenFgaVerdict(
                DeliveryOpenFgaOutcome.ALLOW, null, "ok");
    }

    public static DeliveryOpenFgaVerdict deny(
            DeliveryFailureReason failureReason,
            String reasonCode) {
        return new DeliveryOpenFgaVerdict(
                DeliveryOpenFgaOutcome.DENY,
                failureReason,
                reasonCode);
    }
}