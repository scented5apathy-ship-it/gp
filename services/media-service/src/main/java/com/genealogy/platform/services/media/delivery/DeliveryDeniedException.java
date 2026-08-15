package com.genealogy.platform.services.media.delivery;

import java.util.Map;
import java.util.Objects;

/**
 * Thrown when the protected-delivery orchestrator refuses
 * an authorization request with {@code REDACT} or
 * {@code DENY}. Mirrors the
 * {@code DELIVERY_OPENFGA_AND_ABAC_REQUIRED} /
 * {@code DELIVERY_DNA_BUCKET_FORBIDDEN} invariants in
 * `contracts/media/media-protected-delivery-policy.yaml`
 * (E7.4) + `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>The exception is intentionally a {@link RuntimeException}
 * so the application layer can short-circuit the
 * authorization chain and produce a {@code 403 Forbidden}
 * (DENY) or {@code 410 Gone + REDACTED_PLACEHOLDER} (REDACT)
 * response without leaking the underlying reason code to
 * the requester.
 */
public class DeliveryDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final DeliveryFailureReason failureReason;
    private final Map<String, Object> facts;

    public DeliveryDeniedException(
            DeliveryFailureReason failureReason,
            String message,
            Map<String, Object> facts) {
        super(message);
        Objects.requireNonNull(failureReason, "failureReason");
        this.failureReason = failureReason;
        this.facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    public DeliveryFailureReason failureReason() {
        return failureReason;
    }

    public Map<String, Object> facts() {
        return facts;
    }
}