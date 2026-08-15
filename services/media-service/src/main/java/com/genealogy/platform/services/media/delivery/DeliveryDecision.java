package com.genealogy.platform.services.media.delivery;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Terminal delivery decision returned by
 * {@link MediaProtectedDelivery#authorize(DeliveryAuthorizationRequest)}.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryDecisions + deliveryFailureReasons +
 * deliveryAbacReasons + deliveryRevocationSources` (E7.4)
 * + `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>The decision carries:
 * <ul>
 *   <li>{@code decision} — the closed-set delivery
 *       decision.</li>
 *   <li>{@code failureReason} — null on ALLOW /
 *       ALLOW_WATERMARKED / ALLOW_RANGE_ONLY; the
 *       closed-set {@link DeliveryFailureReason} on DENY /
 *       REDACT.</li>
 *   <li>{@code ticket} — null unless {@code decision} is
 *       ALLOW / ALLOW_WATERMARKED / ALLOW_RANGE_ONLY.</li>
 *   <li>{@code abacReasons} + {@code revocationSources} —
 *       the evidence the orchestrator applied.</li>
 *   <li>{@code facts} — audit-evidence map the
 *       application layer forwards to the audit-service.</li>
 * </ul>
 */
public record DeliveryDecision(
        String deliveryId,
        DeliveryDecisionKind decision,
        DeliveryFailureReason failureReason,
        SignedUrlTicket ticket,
        DeliveryAbacReason primaryAbacReason,
        DeliveryRevocationSource primaryRevocationSource,
        Map<DeliveryAbacReason, String> abacReasons,
        Map<DeliveryRevocationSource, String> revocationSources,
        Map<String, Object> facts,
        String summary) {

    public DeliveryDecision {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(abacReasons, "abacReasons");
        Objects.requireNonNull(revocationSources, "revocationSources");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(summary, "summary");
        if (deliveryId.isBlank()) {
            throw new IllegalArgumentException(
                    "deliveryId must not be blank");
        }
        switch (decision) {
            case ALLOW, ALLOW_WATERMARKED, ALLOW_RANGE_ONLY -> {
                if (failureReason != null) {
                    throw new IllegalArgumentException(
                            "decision " + decision.wire()
                                    + " MUST NOT carry a failureReason");
                }
                if (ticket == null) {
                    throw new IllegalArgumentException(
                            "decision " + decision.wire()
                                    + " MUST carry a SignedUrlTicket");
                }
            }
            case DENY, REDACT -> {
                if (failureReason == null) {
                    throw new IllegalArgumentException(
                            "decision " + decision.wire()
                                    + " MUST carry a DeliveryFailureReason");
                }
                if (ticket != null) {
                    throw new IllegalArgumentException(
                            "decision " + decision.wire()
                                    + " MUST NOT carry a SignedUrlTicket");
                }
            }
            default -> throw new IllegalArgumentException(
                    "unhandled DeliveryDecisionKind: " + decision.wire());
        }
        abacReasons = Collections.unmodifiableMap(
                new LinkedHashMap<>(abacReasons));
        revocationSources = Collections.unmodifiableMap(
                new LinkedHashMap<>(revocationSources));
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    public boolean isGranted() {
        return decision == DeliveryDecisionKind.ALLOW
                || decision == DeliveryDecisionKind.ALLOW_WATERMARKED
                || decision == DeliveryDecisionKind.ALLOW_RANGE_ONLY;
    }

    public Optional<SignedUrlTicket> ticketOpt() {
        return Optional.ofNullable(ticket);
    }
}