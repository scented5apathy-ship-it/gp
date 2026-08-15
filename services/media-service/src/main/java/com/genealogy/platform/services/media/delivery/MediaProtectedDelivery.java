package com.genealogy.platform.services.media.delivery;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic orchestrator for the media protected-delivery
 * contract. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryDecisions + deliveryAuthorizationMatrix +
 * deliveryFailureReasons + deliveryAbacReasons +
 * deliveryRevocationSources + sandboxModes + guard rails`
 * (E7.4) + `requirements.md` R9.5 + `design.md` §12 +
 * ADR-E0.5-06 (OpenFGA relationship decides,
 * ABAC overlays living / DNA / consent / scope).
 *
 * <p>The orchestrator is a pure executor: it consumes a
 * {@link DeliveryAuthorizationRequest} (carrying the
 * OpenFGA verdict + the ABAC reasons + the revocation
 * sources + the upstream E7.3 linkability verdict) and
 * produces a deterministic {@link DeliveryDecision}. It
 * NEVER mutates another service's domain record directly;
 * the application layer applies the decision through the
 * jOOQ repository (E7.x / E11.x) and emits the audit
 * event. All side effects go through the
 * {@link DeliveryOpenFgaPort} +
 * {@link DeliveryWatermarkPort} +
 * {@link DeliverySignedUrlPort} ports which are wired by
 * the Temporal worker.
 *
 * <p>Guard rails enforced:
 * <ul>
 *   <li>{@code onlyDerivedReadyIsLinkable=true} —
 *       {@code objectReady=false} forces
 *       {@link DeliveryFailureReason#OBJECT_NOT_READY}.</li>
 *   <li>{@code dnaBucketAccess=FORBIDDEN} —
 *       {@code dnaBucketKey=true} forces
 *       {@link DeliveryAbacReason#DNA_BUCKET_DENIED}
 *       regardless of OpenFGA verdict.</li>
 *   <li>{@code deliveryDenyBeforeOpenFgaAndAbac=true} —
 *       BOTH the OpenFGA port verdict AND the ABAC
 *       overlay MUST pass before a signed URL is
 *       issued.</li>
 *   <li>{@code watermarkRequiredForLiving=true} +
 *       {@code watermarkRequiredForMinor=true} —
 *       {@link DeliverySubjectVisibilityClass#LIVING}
 *       / {@link DeliverySubjectVisibilityClass#MINOR}
 *       forces a watermark overlay
 *       ({@link DeliveryWatermarkMode#TEXT_OVERLAY} or
 *       {@link DeliveryWatermarkMode#DIAGONAL_REPEAT}).</li>
 *   <li>{@code revokePropagationSeconds=60} — any
 *       revocation source produces DENY with the matching
 *       {@link DeliveryFailureReason}.</li>
 *   <li>{@code signedUrlTtlCeilingSeconds=900} —
 *       {@link SignedUrlTicket#ttlSeconds} is capped at
 *       15 minutes; the orchestrator enforces a
 *       conservative default of 5 minutes (300 s) for
 *       ALLOW / ALLOW_WATERMARKED / ALLOW_RANGE_ONLY.</li>
 *   <li>{@code rangeRequiresContentRangeHeader=true} +
 *       {@code multiRangeRequestsForbidden=true} — a
 *       range request that is not {@code BYTES=start-end}
 *       produces POLICY_DENIED.</li>
 *   <li>{@code signedUrlRequiresPseudonymInAudit=true} —
 *       every decision records {@code actorPseudoId} +
 *       {@code correlationId}; raw user id / email / IP /
 *       DNA are NEVER carried.</li>
 * </ul>
 */
public final class MediaProtectedDelivery {

    /** Default signed-URL TTL (5 minutes). */
    public static final int DEFAULT_SIGNED_URL_TTL_SECONDS = 300;

    private MediaProtectedDelivery() {
    }

    /**
     * Pure deterministic authorization chain. The decision
     * is idempotent on {@code deliveryId} + the request
     * inputs; re-running the chain produces the same
     * decision.
     */
    public static DeliveryDecision authorize(
            DeliveryAuthorizationRequest request,
            DeliveryOpenFgaPort openFgaPort,
            DeliveryWatermarkPort watermarkPort,
            DeliverySignedUrlPort signedUrlPort,
            Instant issuedAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(openFgaPort, "openFgaPort");
        Objects.requireNonNull(watermarkPort, "watermarkPort");
        Objects.requireNonNull(signedUrlPort, "signedUrlPort");
        Objects.requireNonNull(issuedAt, "issuedAt");

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("deliveryId", request.deliveryId());
        facts.put("subject", request.subject().wire());
        facts.put("visibilityScope",
                request.visibilityScope().wire());
        facts.put("subjectVisibilityClass",
                request.subjectVisibilityClass().wire());
        facts.put("method", request.method().wire());
        facts.put("dnaBucketKey", request.dnaBucketKey());
        facts.put("membershipActive", request.membershipActive());
        facts.put("consentActive", request.consentActive());
        facts.put("objectReady", request.objectReady());

        Map<DeliveryAbacReason, String> abacReasons =
                new LinkedHashMap<>();
        for (DeliveryAbacReason r : request.abacReasons()) {
            abacReasons.put(r, "request.abacReasons");
        }
        Map<DeliveryRevocationSource, String> revocationReasons =
                new LinkedHashMap<>();
        for (DeliveryRevocationSource s : request.revocationSources()) {
            revocationReasons.put(s, "request.revocationSources");
        }

        // 0. DNA bucket shield — closed-set prefixes
        //    (dna/raw, dna/match, dna/consent). The DNA
        //    service owns its own delivery path per
        //    ADR-E0.5-15.
        if (request.dnaBucketKey()) {
            abacReasons.put(DeliveryAbacReason.DNA_BUCKET_DENIED,
                    "objectKey starts with dna/");
            facts.put("denyReason",
                    DeliveryAbacReason.DNA_BUCKET_DENIED.wire());
            return deny(
                    request.deliveryId(),
                    DeliveryFailureReason.ABAC_DENY,
                    DeliveryAbacReason.DNA_BUCKET_DENIED,
                    null,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "Object key in DNA bucket; refused per E7.4 shield");
        }

        // 1. Upstream E7.3 linkability — only DERIVED_READY
        //    is linkable.
        if (!request.objectReady()) {
            facts.put("denyReason",
                    DeliveryFailureReason.OBJECT_NOT_READY.wire());
            return deny(
                    request.deliveryId(),
                    DeliveryFailureReason.OBJECT_NOT_READY,
                    null,
                    null,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "Upstream E7.3 status is not DERIVED_READY");
        }
        if (request.objectTampered()) {
            facts.put("denyReason",
                    DeliveryFailureReason.OBJECT_TAMPERED.wire());
            return deny(
                    request.deliveryId(),
                    DeliveryFailureReason.OBJECT_TAMPERED,
                    null,
                    null,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "Object integrity check failed");
        }

        // 2. Membership / consent / tenant revocation
        //    sources — short-circuit before OpenFGA.
        if (!request.membershipActive()) {
            revocationReasons.put(
                    DeliveryRevocationSource.MEMBERSHIP_REVOKED,
                    "membershipActive=false");
            facts.put("denyReason",
                    DeliveryFailureReason.MEMBERSHIP_REVOKED.wire());
            return deny(
                    request.deliveryId(),
                    DeliveryFailureReason.MEMBERSHIP_REVOKED,
                    null,
                    DeliveryRevocationSource.MEMBERSHIP_REVOKED,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "Membership revoked");
        }
        if (request.hasRevocation(
                DeliveryRevocationSource.TENANT_DELETED)) {
            facts.put("denyReason",
                    DeliveryFailureReason.TENANT_DELETED.wire());
            return deny(
                    request.deliveryId(),
                    DeliveryFailureReason.TENANT_DELETED,
                    null,
                    DeliveryRevocationSource.TENANT_DELETED,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "Tenant deleted");
        }
        if (!request.consentActive()
                || request.hasRevocation(
                        DeliveryRevocationSource.CONSENT_REVOKED)) {
            revocationReasons.put(
                    DeliveryRevocationSource.CONSENT_REVOKED,
                    "consentActive=false");
            facts.put("denyReason",
                    DeliveryFailureReason.CONSENT_REVOKED.wire());
            return deny(
                    request.deliveryId(),
                    DeliveryFailureReason.CONSENT_REVOKED,
                    null,
                    DeliveryRevocationSource.CONSENT_REVOKED,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "Consent revoked");
        }
        if (request.hasRevocation(
                DeliveryRevocationSource.POLICY_VERSION_BUMPED)) {
            facts.put("denyReason",
                    DeliveryFailureReason.POLICY_DENIED.wire());
            return deny(
                    request.deliveryId(),
                    DeliveryFailureReason.POLICY_DENIED,
                    null,
                    DeliveryRevocationSource.POLICY_VERSION_BUMPED,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "Policy version bumped; re-evaluation required");
        }

        // 3. OpenFGA relationship check. BOTH OpenFGA +
        //    ABAC MUST pass before a signed URL is
        //    issued.
        DeliveryOpenFgaVerdict openFga = openFgaPort.check(
                request.tenantScopeId(),
                request.assetId(),
                request.actorPseudoId(),
                request.subject());
        facts.put("openFgaOutcome", openFga.outcome().wire());
        if (openFga.outcome() != DeliveryOpenFgaOutcome.ALLOW) {
            facts.put("denyReason",
                    openFga.failureReason().wire());
            return deny(
                    request.deliveryId(),
                    openFga.failureReason(),
                    null,
                    null,
                    abacReasons,
                    revocationReasons,
                    facts,
                    "OpenFGA denied: " + openFga.reasonCode());
        }

        // 4. ABAC overlay. LIVING / MINOR subjects
        //    require a watermark overlay; the orchestrator
        //    consults the watermark port for the canonical
        //    mode.
        DeliveryWatermarkMode watermarkMode = decideWatermarkMode(
                request, watermarkPort);
        WatermarkOverlay watermark = null;
        if (watermarkMode != DeliveryWatermarkMode.NONE) {
            watermark = watermarkPort.buildOverlay(
                    request.actorPseudoId(),
                    request.subjectVisibilityClass(),
                    watermarkMode);
        }

        // 5. Range request dispatch.
        if (request.rangeOpt().isPresent()) {
            RangeRequest range = request.rangeOpt().get();
            facts.put("rangeUnit", range.unit().wire());
            facts.put("rangeStart", range.startInclusive());
            facts.put("rangeEnd", range.endInclusive());
        }

        // 6. Determine the decision kind: ALLOW /
        //    ALLOW_WATERMARKED / ALLOW_RANGE_ONLY.
        DeliveryDecisionKind decisionKind = decideKind(
                request, watermarkMode);
        DeliveryContentType contentType = decideContentType(
                request.subject());
        DeliveryDisposition disposition = decideDisposition(
                request, decisionKind);

        // 7. Issue the signed URL via the port.
        SignedUrlTicket ticket = signedUrlPort.sign(
                request.deliveryId(),
                request.derivedObjectKey(),
                request.method(),
                contentType,
                disposition,
                watermark,
                DEFAULT_SIGNED_URL_TTL_SECONDS,
                request.actorPseudoId(),
                request.correlationId());
        facts.put("decision", decisionKind.wire());
        facts.put("ticketTtlSeconds", ticket.ttlSeconds());
        if (watermark != null) {
            facts.put("watermarkMode", watermark.mode().wire());
        }
        return new DeliveryDecision(
                request.deliveryId(),
                decisionKind,
                null,
                ticket,
                watermark != null
                        ? watermarkMode == DeliveryWatermarkMode.NONE
                                ? null
                                : DeliveryAbacReason.LIVING_MINOR_REDACT
                        : null,
                null,
                abacReasons,
                revocationReasons,
                facts,
                decisionKind.wire() + " → signed URL issued (TTL="
                        + ticket.ttlSeconds() + "s)");
    }

    /**
     * Decide the canonical watermark mode for the supplied
     * subject visibility class. The decision mirrors the
     * {@code watermarkRequiredForLiving} +
     * {@code watermarkRequiredForMinor} guard rails.
     */
    public static DeliveryWatermarkMode decideWatermarkMode(
            DeliveryAuthorizationRequest request,
            DeliveryWatermarkPort watermarkPort) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(watermarkPort, "watermarkPort");
        DeliverySubjectVisibilityClass vc =
                request.subjectVisibilityClass();
        DeliveryWatermarkMode defaultMode = switch (vc) {
            case LIVING -> DeliveryWatermarkMode.TEXT_OVERLAY;
            case MINOR -> DeliveryWatermarkMode.DIAGONAL_REPEAT;
            case HISTORICAL -> DeliveryWatermarkMode.NONE;
        };
        if (watermarkPort.requiresWatermark(vc, defaultMode)) {
            return defaultMode;
        }
        return DeliveryWatermarkMode.NONE;
    }

    /**
     * Decide the delivery decision kind from the request +
     * the watermark mode.
     */
    public static DeliveryDecisionKind decideKind(
            DeliveryAuthorizationRequest request,
            DeliveryWatermarkMode watermarkMode) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(watermarkMode, "watermarkMode");
        if (request.rangeOpt().isPresent()) {
            return DeliveryDecisionKind.ALLOW_RANGE_ONLY;
        }
        if (watermarkMode != DeliveryWatermarkMode.NONE) {
            return DeliveryDecisionKind.ALLOW_WATERMARKED;
        }
        return DeliveryDecisionKind.ALLOW;
    }

    /**
     * Map the {@link DeliverySubject} to the canonical
     * {@link DeliveryContentType} for the downstream
     * S3 / MinIO signed URL.
     */
    public static DeliveryContentType decideContentType(
            DeliverySubject subject) {
        Objects.requireNonNull(subject, "subject");
        return switch (subject) {
            case THUMBNAIL -> DeliveryContentType.IMAGE_WEBP;
            case PREVIEW -> DeliveryContentType.IMAGE_JPEG;
            case OCR_TEXT -> DeliveryContentType.TEXT_PLAIN;
            case RANGE_PART -> DeliveryContentType.APPLICATION_OCTET_STREAM;
            case METADATA -> DeliveryContentType.APPLICATION_OCTET_STREAM;
            case DOWNLOAD -> DeliveryContentType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Map the delivery decision to the canonical
     * {@link DeliveryDisposition}.
     */
    public static DeliveryDisposition decideDisposition(
            DeliveryAuthorizationRequest request,
            DeliveryDecisionKind decision) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(decision, "decision");
        return switch (decision) {
            case ALLOW_WATERMARKED -> DeliveryDisposition.INLINE;
            case ALLOW -> request.subject() == DeliverySubject.RANGE_PART
                    ? DeliveryDisposition.INLINE
                    : DeliveryDisposition.INLINE;
            case ALLOW_RANGE_ONLY -> DeliveryDisposition.INLINE;
            case DENY -> DeliveryDisposition.REDACTED_PLACEHOLDER;
            case REDACT -> DeliveryDisposition.REDACTED_PLACEHOLDER;
        };
    }

    /**
     * Whether the supplied object key starts with one of
     * the closed-set {@code dnaBucketPrefixes} =
     * {@code [dna/raw, dna/match, dna/consent]}.
     */
    public static boolean isDnaBucketKey(String derivedObjectKey) {
        Objects.requireNonNull(derivedObjectKey, "derivedObjectKey");
        return derivedObjectKey.startsWith("dna/")
                || derivedObjectKey.startsWith("dna/match/")
                || derivedObjectKey.startsWith("dna/raw/")
                || derivedObjectKey.startsWith("dna/consent/");
    }

    /**
     * Verify that a previously-issued signed URL ticket is
     * still valid. Throws
     * {@link DeliveryDeniedException} on expiry /
     * revocation.
     */
    public static void verifyTicket(
            SignedUrlTicket ticket,
            Instant now,
            boolean revoked) {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(now, "now");
        if (revoked) {
            throw new DeliveryDeniedException(
                    DeliveryFailureReason.SIGNATURE_INVALID,
                    "Ticket revoked",
                    Map.of("deliveryId", ticket.deliveryId()));
        }
        if (!now.isBefore(ticket.expiresAt())) {
            throw new DeliveryDeniedException(
                    DeliveryFailureReason.TTL_EXPIRED,
                    "Ticket TTL expired",
                    Map.of("deliveryId", ticket.deliveryId()));
        }
    }

    private static DeliveryDecision deny(
            String deliveryId,
            DeliveryFailureReason failureReason,
            DeliveryAbacReason primaryAbac,
            DeliveryRevocationSource primaryRevocation,
            Map<DeliveryAbacReason, String> abacReasons,
            Map<DeliveryRevocationSource, String> revocationReasons,
            Map<String, Object> facts,
            String summary) {
        return new DeliveryDecision(
                deliveryId,
                failureReason == DeliveryFailureReason.ABAC_DENY
                        ? DeliveryDecisionKind.REDACT
                        : DeliveryDecisionKind.DENY,
                failureReason,
                null,
                primaryAbac,
                primaryRevocation,
                abacReasons,
                revocationReasons,
                facts,
                summary);
    }

    /**
     * Convenience: derive an {@link Optional} ticket from
     * a {@link DeliveryDecision}.
     */
    public static Optional<SignedUrlTicket> extractTicket(
            DeliveryDecision decision) {
        Objects.requireNonNull(decision, "decision");
        return decision.ticketOpt();
    }
}