package com.genealogy.platform.services.media.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link MediaProtectedDelivery} + its value objects.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryDecisions + deliveryAuthorizationMatrix +
 * deliveryFailureReasons + deliveryAbacReasons +
 * deliveryRevocationSources + guard rails` (E7.4) +
 * `requirements.md` R9.5 + `design.md` §12 +
 * ADR-E0.5-06 (OpenFGA + ABAC).
 */
class MediaProtectedDeliveryTest {

    private static final Instant ISSUED_AT =
            Instant.parse("2026-08-15T12:00:00Z");

    private static final String ACTOR = "actor-1234";
    private static final String CORRELATION = "corr-abc-001";

    private static DeliveryAuthorizationRequest.Builder baseRequest() {
        return DeliveryAuthorizationRequest.builder()
                .deliveryId("d-1")
                .tenantScopeId("tenant-1")
                .assetId("asset-abc")
                .derivedObjectKey(
                        "media/tenant-1/asset-abc/v8.15.0/"
                                + "image_transcode/v0")
                .subject(DeliverySubject.DOWNLOAD)
                .visibilityScope(DeliveryVisibilityScope.PRIVATE)
                .subjectVisibilityClass(
                        DeliverySubjectVisibilityClass.HISTORICAL)
                .method(SignedUrlMethod.GET)
                .actorPseudoId(ACTOR)
                .correlationId(CORRELATION)
                .jurisdiction("EU")
                .abacReasons(List.of())
                .revocationSources(List.of())
                .dnaBucketKey(false)
                .membershipActive(true)
                .consentActive(true)
                .objectReady(true)
                .objectTampered(false);
    }

    @Test
    void historicalSubjectCleanRequestYieldsAllow() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest().build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.ALLOW, d.decision());
        assertNull(d.failureReason());
        assertNotNull(d.ticket());
        assertTrue(d.isGranted());
    }

    @Test
    void livingSubjectYieldsAllowWatermarkedWithOverlay() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .subjectVisibilityClass(
                                DeliverySubjectVisibilityClass.LIVING)
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.ALLOW_WATERMARKED, d.decision());
        assertNotNull(d.ticket());
        assertTrue(d.ticket().hasWatermark());
    }

    @Test
    void minorSubjectYieldsAllowWatermarkedDiagonal() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .subjectVisibilityClass(
                                DeliverySubjectVisibilityClass.MINOR)
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.ALLOW_WATERMARKED, d.decision());
        assertNotNull(d.ticket());
        assertTrue(d.ticket().hasWatermark());
    }

    @Test
    void rangeRequestYieldsAllowRangeOnly() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .subject(DeliverySubject.RANGE_PART)
                        .range(new RangeRequest(
                                DeliveryRangeUnit.BYTES, 0L, 1023L))
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.ALLOW_RANGE_ONLY, d.decision());
        assertNotNull(d.ticket());
    }

    @Test
    void dnaBucketKeyYieldsAbacDenyRedact() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .derivedObjectKey("dna/raw/sample.fastq")
                        .dnaBucketKey(true)
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.REDACT, d.decision());
        assertEquals(DeliveryFailureReason.ABAC_DENY, d.failureReason());
        assertNull(d.ticket());
        assertEquals(DeliveryAbacReason.DNA_BUCKET_DENIED,
                d.primaryAbacReason());
    }

    @Test
    void notReadyObjectYieldsObjectNotReady() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest().objectReady(false).build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.DENY, d.decision());
        assertEquals(DeliveryFailureReason.OBJECT_NOT_READY,
                d.failureReason());
    }

    @Test
    void tamperedObjectYieldsObjectTampered() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest().objectTampered(true).build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.DENY, d.decision());
        assertEquals(DeliveryFailureReason.OBJECT_TAMPERED,
                d.failureReason());
    }

    @Test
    void membershipInactiveYieldsMembershipRevoked() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest().membershipActive(false).build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.DENY, d.decision());
        assertEquals(DeliveryFailureReason.MEMBERSHIP_REVOKED,
                d.failureReason());
        assertEquals(DeliveryRevocationSource.MEMBERSHIP_REVOKED,
                d.primaryRevocationSource());
    }

    @Test
    void tenantDeletedRevocationYieldsTenantDeleted() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .revocationSources(List.of(
                                DeliveryRevocationSource.TENANT_DELETED))
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.DENY, d.decision());
        assertEquals(DeliveryFailureReason.TENANT_DELETED,
                d.failureReason());
    }

    @Test
    void consentInactiveYieldsConsentRevoked() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest().consentActive(false).build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.DENY, d.decision());
        assertEquals(DeliveryFailureReason.CONSENT_REVOKED,
                d.failureReason());
    }

    @Test
    void policyVersionBumpedRevocationYieldsPolicyDenied() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .revocationSources(List.of(
                                DeliveryRevocationSource
                                        .POLICY_VERSION_BUMPED))
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.DENY, d.decision());
        assertEquals(DeliveryFailureReason.POLICY_DENIED,
                d.failureReason());
    }

    @Test
    void openFgaDenyYieldsOpenFgaDeny() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest().build(),
                new DenyOpenFgaPort(
                        DeliveryFailureReason.OPENFGA_DENY,
                        "tuple missing"),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(DeliveryDecisionKind.DENY, d.decision());
        assertEquals(DeliveryFailureReason.OPENFGA_DENY,
                d.failureReason());
        assertNull(d.ticket());
    }

    @Test
    void abacReasonsOnRequestAreRecordedInDecision() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .abacReasons(List.of(
                                DeliveryAbacReason.JURISDICTION_BLOCKED))
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertTrue(d.abacReasons().containsKey(
                DeliveryAbacReason.JURISDICTION_BLOCKED));
    }

    @Test
    void signedUrlTtlDefaultsTo300Seconds() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest().build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertEquals(300, d.ticket().ttlSeconds());
        assertEquals(
                ISSUED_AT.plusSeconds(300), d.ticket().expiresAt());
    }

    @Test
    void verifyTicketAcceptsUnrevokedAndUnexpired() {
        SignedUrlTicket ticket = new SignedUrlTicket(
                "d-1",
                "media/tenant-1/asset-abc/v8.15.0/image_transcode/v0",
                "https://signed.example/u",
                SignedUrlMethod.GET,
                DeliveryDisposition.INLINE,
                DeliveryContentType.IMAGE_WEBP,
                null,
                300,
                ISSUED_AT,
                ISSUED_AT.plusSeconds(300));
        MediaProtectedDelivery.verifyTicket(ticket, ISSUED_AT.plusSeconds(60), false);
        assertThrows(DeliveryDeniedException.class,
                () -> MediaProtectedDelivery.verifyTicket(
                        ticket,
                        ISSUED_AT.plusSeconds(60),
                        true));
        assertThrows(DeliveryDeniedException.class,
                () -> MediaProtectedDelivery.verifyTicket(
                        ticket,
                        ISSUED_AT.plusSeconds(400),
                        false));
    }

    @Test
    void decideKindRoutesByWatermarkAndRange() {
        DeliveryWatermarkMode none = DeliveryWatermarkMode.NONE;
        DeliveryWatermarkMode text = DeliveryWatermarkMode.TEXT_OVERLAY;
        DeliveryAuthorizationRequest baseNoRange =
                baseRequest().build();
        DeliveryAuthorizationRequest rangeReq =
                baseRequest()
                        .subject(DeliverySubject.RANGE_PART)
                        .range(new RangeRequest(
                                DeliveryRangeUnit.BYTES, 0L, 1023L))
                        .build();
        assertEquals(DeliveryDecisionKind.ALLOW,
                MediaProtectedDelivery.decideKind(baseNoRange, none));
        assertEquals(DeliveryDecisionKind.ALLOW_WATERMARKED,
                MediaProtectedDelivery.decideKind(baseNoRange, text));
        assertEquals(DeliveryDecisionKind.ALLOW_RANGE_ONLY,
                MediaProtectedDelivery.decideKind(rangeReq, none));
    }

    @Test
    void decideContentTypeRoutesBySubject() {
        assertEquals(DeliveryContentType.IMAGE_WEBP,
                MediaProtectedDelivery.decideContentType(
                        DeliverySubject.THUMBNAIL));
        assertEquals(DeliveryContentType.IMAGE_JPEG,
                MediaProtectedDelivery.decideContentType(
                        DeliverySubject.PREVIEW));
        assertEquals(DeliveryContentType.TEXT_PLAIN,
                MediaProtectedDelivery.decideContentType(
                        DeliverySubject.OCR_TEXT));
        assertEquals(DeliveryContentType.APPLICATION_OCTET_STREAM,
                MediaProtectedDelivery.decideContentType(
                        DeliverySubject.DOWNLOAD));
    }

    @Test
    void decisionIsImmutableFacts() {
        DeliveryDecision d = MediaProtectedDelivery.authorize(
                baseRequest()
                        .abacReasons(List.of(
                                DeliveryAbacReason.JURISDICTION_BLOCKED))
                        .build(),
                new AllowOpenFgaPort(),
                new DefaultWatermarkPort(),
                new DefaultSignedUrlPort(),
                ISSUED_AT);
        assertThrows(UnsupportedOperationException.class,
                () -> d.facts().put("extra", "value"));
        assertThrows(UnsupportedOperationException.class,
                () -> d.abacReasons().clear());
        assertFalse(d.abacReasons().isEmpty());
    }

    @Test
    void deliveryDeniedExceptionCarriesFailureReason() {
        DeliveryDeniedException ex = new DeliveryDeniedException(
                DeliveryFailureReason.TTL_EXPIRED,
                "ttl expired",
                Map.of("k", "v"));
        assertEquals(DeliveryFailureReason.TTL_EXPIRED,
                ex.failureReason());
        assertEquals(1, ex.facts().size());
    }

    /**
     * Allow-stub for the OpenFGA port.
     */
    private static final class AllowOpenFgaPort
            implements DeliveryOpenFgaPort {
        @Override
        public DeliveryOpenFgaVerdict check(
                String tenantScopeId,
                String assetId,
                String actorPseudoId,
                DeliverySubject subject) {
            return DeliveryOpenFgaVerdict.allow();
        }
    }

    /**
     * Deny-stub for the OpenFGA port.
     */
    private static final class DenyOpenFgaPort
            implements DeliveryOpenFgaPort {
        private final DeliveryFailureReason reason;
        private final String code;

        DenyOpenFgaPort(DeliveryFailureReason reason, String code) {
            this.reason = reason;
            this.code = code;
        }

        @Override
        public DeliveryOpenFgaVerdict check(
                String tenantScopeId,
                String assetId,
                String actorPseudoId,
                DeliverySubject subject) {
            return DeliveryOpenFgaVerdict.deny(reason, code);
        }
    }

    /**
     * Default watermark port that always requires a watermark
     * for LIVING / MINOR subjects and produces a canonical
     * overlay embedding the {@code actorPseudoId}.
     */
    private static final class DefaultWatermarkPort
            implements DeliveryWatermarkPort {
        @Override
        public boolean requiresWatermark(
                DeliverySubjectVisibilityClass visibilityClass,
                DeliveryWatermarkMode mode) {
            return visibilityClass != DeliverySubjectVisibilityClass.HISTORICAL;
        }

        @Override
        public WatermarkOverlay buildOverlay(
                String actorPseudoId,
                DeliverySubjectVisibilityClass visibilityClass,
                DeliveryWatermarkMode mode) {
            Instant now = Instant.parse("2026-08-15T12:00:00Z");
            return new WatermarkOverlay(
                    mode,
                    actorPseudoId + " @2026-08-15T12:00:00Z",
                    actorPseudoId,
                    now);
        }
    }

    /**
     * Default signed-URL port that returns a deterministic
     * ticket with the canonical TTL.
     */
    private static final class DefaultSignedUrlPort
            implements DeliverySignedUrlPort {
        @Override
        public SignedUrlTicket sign(
                String deliveryId,
                String derivedObjectKey,
                SignedUrlMethod method,
                DeliveryContentType contentType,
                DeliveryDisposition disposition,
                WatermarkOverlay watermark,
                int ttlSeconds,
                String actorPseudoId,
                String correlationId) {
            Instant now = Instant.parse("2026-08-15T12:00:00Z");
            return new SignedUrlTicket(
                    deliveryId,
                    derivedObjectKey,
                    "https://signed.example/" + deliveryId,
                    method,
                    disposition,
                    contentType,
                    watermark,
                    ttlSeconds,
                    now,
                    now.plusSeconds(ttlSeconds));
        }
    }
}