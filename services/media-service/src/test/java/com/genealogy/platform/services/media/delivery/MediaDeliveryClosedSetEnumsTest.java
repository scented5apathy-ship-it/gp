package com.genealogy.platform.services.media.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Pins the closed-set enums for the media protected-delivery
 * policy. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliverySubjects + deliveryDecisions +
 * deliveryDispositions + deliveryContentTypes +
 * deliveryVisibilityScopes + deliveryFailureReasons +
 * deliveryAuditEvents + deliveryWatermarkModes +
 * deliveryAbacReasons + deliveryRevocationSources +
 * deliveryLinkableStatuses +
 * deliverySubjectVisibilityClass + dnaBucketPrefixes +
 * deliveryRangeUnit + signedUrlMethods` (E7.4).
 */
class MediaDeliveryClosedSetEnumsTest {

    @Test
    void deliverySubjectClosedSetMatchesContract() {
        assertEquals(DeliverySubject.DOWNLOAD,
                DeliverySubject.fromWire("DOWNLOAD"));
        assertEquals(DeliverySubject.THUMBNAIL,
                DeliverySubject.fromWire("thumbnail"));
        assertEquals(DeliverySubject.PREVIEW,
                DeliverySubject.fromWire("PREVIEW"));
        assertEquals(DeliverySubject.OCR_TEXT,
                DeliverySubject.fromWire("OCR_TEXT"));
        assertEquals(DeliverySubject.RANGE_PART,
                DeliverySubject.fromWire("RANGE_PART"));
        assertEquals(DeliverySubject.METADATA,
                DeliverySubject.fromWire("METADATA"));
        assertEquals(6, DeliverySubject.values().length);
        assertThrows(IllegalArgumentException.class,
                () -> DeliverySubject.fromWire("FULL_DOWNLOAD"));
        assertThrows(IllegalArgumentException.class,
                () -> DeliverySubject.fromWire(null));
    }

    @Test
    void deliveryDecisionKindClosedSetMatchesContract() {
        assertEquals(DeliveryDecisionKind.ALLOW,
                DeliveryDecisionKind.fromWire("ALLOW"));
        assertEquals(DeliveryDecisionKind.ALLOW_WATERMARKED,
                DeliveryDecisionKind.fromWire("allow_watermarked"));
        assertEquals(DeliveryDecisionKind.ALLOW_RANGE_ONLY,
                DeliveryDecisionKind.fromWire("ALLOW_RANGE_ONLY"));
        assertEquals(DeliveryDecisionKind.DENY,
                DeliveryDecisionKind.fromWire("DENY"));
        assertEquals(DeliveryDecisionKind.REDACT,
                DeliveryDecisionKind.fromWire("REDACT"));
        assertEquals(5, DeliveryDecisionKind.values().length);
    }

    @Test
    void deliveryDispositionClosedSetMatchesContract() {
        assertEquals(DeliveryDisposition.INLINE,
                DeliveryDisposition.fromWire("INLINE"));
        assertEquals(DeliveryDisposition.ATTACHMENT,
                DeliveryDisposition.fromWire("attachment"));
        assertEquals(DeliveryDisposition.REDACTED_PLACEHOLDER,
                DeliveryDisposition.fromWire("REDACTED_PLACEHOLDER"));
        assertEquals(3, DeliveryDisposition.values().length);
    }

    @Test
    void deliveryContentTypeClosedSetMatchesContract() {
        assertEquals(DeliveryContentType.IMAGE_WEBP,
                DeliveryContentType.fromWire("IMAGE_WEBP"));
        assertEquals(DeliveryContentType.IMAGE_AVIF,
                DeliveryContentType.fromWire("IMAGE_AVIF"));
        assertEquals(DeliveryContentType.IMAGE_JPEG,
                DeliveryContentType.fromWire("image_jpeg"));
        assertEquals(DeliveryContentType.APPLICATION_PDF,
                DeliveryContentType.fromWire("APPLICATION_PDF"));
        assertEquals(DeliveryContentType.VIDEO_MP4,
                DeliveryContentType.fromWire("VIDEO_MP4"));
        assertEquals(DeliveryContentType.TEXT_PLAIN,
                DeliveryContentType.fromWire("TEXT_PLAIN"));
        assertEquals(DeliveryContentType.APPLICATION_OCTET_STREAM,
                DeliveryContentType.fromWire("APPLICATION_OCTET_STREAM"));
        assertEquals(7, DeliveryContentType.values().length);
    }

    @Test
    void deliveryVisibilityScopeClosedSetMatchesContract() {
        assertEquals(DeliveryVisibilityScope.PRIVATE,
                DeliveryVisibilityScope.fromWire("PRIVATE"));
        assertEquals(DeliveryVisibilityScope.UNLISTED,
                DeliveryVisibilityScope.fromWire("UNLISTED"));
        assertEquals(DeliveryVisibilityScope.PUBLIC,
                DeliveryVisibilityScope.fromWire("public"));
        assertEquals(DeliveryVisibilityScope.INTERNAL_TENANT,
                DeliveryVisibilityScope.fromWire("INTERNAL_TENANT"));
        assertEquals(4, DeliveryVisibilityScope.values().length);
    }

    @Test
    void deliveryFailureReasonClosedSetMatchesContract() {
        assertEquals(DeliveryFailureReason.POLICY_DENIED,
                DeliveryFailureReason.fromWire("POLICY_DENIED"));
        assertEquals(DeliveryFailureReason.OPENFGA_DENY,
                DeliveryFailureReason.fromWire("OPENFGA_DENY"));
        assertEquals(DeliveryFailureReason.ABAC_DENY,
                DeliveryFailureReason.fromWire("ABAC_DENY"));
        assertEquals(DeliveryFailureReason.CONSENT_REVOKED,
                DeliveryFailureReason.fromWire("CONSENT_REVOKED"));
        assertEquals(DeliveryFailureReason.MEMBERSHIP_REVOKED,
                DeliveryFailureReason.fromWire("MEMBERSHIP_REVOKED"));
        assertEquals(DeliveryFailureReason.TENANT_DELETED,
                DeliveryFailureReason.fromWire("TENANT_DELETED"));
        assertEquals(DeliveryFailureReason.OBJECT_NOT_READY,
                DeliveryFailureReason.fromWire("OBJECT_NOT_READY"));
        assertEquals(DeliveryFailureReason.OBJECT_TAMPERED,
                DeliveryFailureReason.fromWire("OBJECT_TAMPERED"));
        assertEquals(DeliveryFailureReason.TTL_EXPIRED,
                DeliveryFailureReason.fromWire("TTL_EXPIRED"));
        assertEquals(DeliveryFailureReason.SIGNATURE_INVALID,
                DeliveryFailureReason.fromWire("SIGNATURE_INVALID"));
        assertEquals(10, DeliveryFailureReason.values().length);
    }

    @Test
    void deliveryAuditEventClosedSetMatchesContract() {
        assertEquals(DeliveryAuditEvent.DELIVERY_GRANTED,
                DeliveryAuditEvent.fromWire("DELIVERY_GRANTED"));
        assertEquals(DeliveryAuditEvent.DELIVERY_WATERMARKED,
                DeliveryAuditEvent.fromWire("DELIVERY_WATERMARKED"));
        assertEquals(DeliveryAuditEvent.DELIVERY_DENIED,
                DeliveryAuditEvent.fromWire("DELIVERY_DENIED"));
        assertEquals(DeliveryAuditEvent.DELIVERY_REVOKED,
                DeliveryAuditEvent.fromWire("DELIVERY_REVOKED"));
        assertEquals(DeliveryAuditEvent.DELIVERY_RANGE_SERVED,
                DeliveryAuditEvent.fromWire("DELIVERY_RANGE_SERVED"));
        assertEquals(5, DeliveryAuditEvent.values().length);
    }

    @Test
    void deliveryWatermarkModeClosedSetMatchesContract() {
        assertEquals(DeliveryWatermarkMode.NONE,
                DeliveryWatermarkMode.fromWire("NONE"));
        assertEquals(DeliveryWatermarkMode.TEXT_OVERLAY,
                DeliveryWatermarkMode.fromWire("text_overlay"));
        assertEquals(DeliveryWatermarkMode.DIAGONAL_REPEAT,
                DeliveryWatermarkMode.fromWire("DIAGONAL_REPEAT"));
        assertEquals(DeliveryWatermarkMode.VISIBLE_DOI,
                DeliveryWatermarkMode.fromWire("VISIBLE_DOI"));
        assertEquals(4, DeliveryWatermarkMode.values().length);
    }

    @Test
    void deliveryAbacReasonClosedSetMatchesContract() {
        assertEquals(DeliveryAbacReason.LIVING_MINOR_REDACT,
                DeliveryAbacReason.fromWire("LIVING_MINOR_REDACT"));
        assertEquals(DeliveryAbacReason.LIVING_RESTRICTED,
                DeliveryAbacReason.fromWire("LIVING_RESTRICTED"));
        assertEquals(DeliveryAbacReason.DNA_BUCKET_DENIED,
                DeliveryAbacReason.fromWire("DNA_BUCKET_DENIED"));
        assertEquals(DeliveryAbacReason.CONSENT_PURPOSE_MISSING,
                DeliveryAbacReason.fromWire("CONSENT_PURPOSE_MISSING"));
        assertEquals(DeliveryAbacReason.JURISDICTION_BLOCKED,
                DeliveryAbacReason.fromWire("JURISDICTION_BLOCKED"));
        assertEquals(DeliveryAbacReason.SCOPE_REVOKED,
                DeliveryAbacReason.fromWire("SCOPE_REVOKED"));
        assertEquals(6, DeliveryAbacReason.values().length);
    }

    @Test
    void deliveryRevocationSourceClosedSetMatchesContract() {
        assertEquals(DeliveryRevocationSource.MEMBERSHIP_REVOKED,
                DeliveryRevocationSource.fromWire("MEMBERSHIP_REVOKED"));
        assertEquals(DeliveryRevocationSource.TENANT_DELETED,
                DeliveryRevocationSource.fromWire("TENANT_DELETED"));
        assertEquals(DeliveryRevocationSource.CONSENT_REVOKED,
                DeliveryRevocationSource.fromWire("CONSENT_REVOKED"));
        assertEquals(DeliveryRevocationSource.POLICY_VERSION_BUMPED,
                DeliveryRevocationSource.fromWire("POLICY_VERSION_BUMPED"));
        assertEquals(4, DeliveryRevocationSource.values().length);
    }

    @Test
    void deliverySubjectVisibilityClassClosedSetMatchesContract() {
        assertEquals(DeliverySubjectVisibilityClass.LIVING,
                DeliverySubjectVisibilityClass.fromWire("LIVING"));
        assertEquals(DeliverySubjectVisibilityClass.MINOR,
                DeliverySubjectVisibilityClass.fromWire("minor"));
        assertEquals(DeliverySubjectVisibilityClass.HISTORICAL,
                DeliverySubjectVisibilityClass.fromWire("HISTORICAL"));
        assertEquals(3, DeliverySubjectVisibilityClass.values().length);
    }

    @Test
    void deliveryRangeUnitClosedSetMatchesContract() {
        assertEquals(DeliveryRangeUnit.BYTES,
                DeliveryRangeUnit.fromWire("BYTES"));
        assertEquals(DeliveryRangeUnit.NONE,
                DeliveryRangeUnit.fromWire("none"));
        assertEquals(2, DeliveryRangeUnit.values().length);
    }

    @Test
    void signedUrlMethodClosedSetMatchesContract() {
        assertEquals(SignedUrlMethod.GET,
                SignedUrlMethod.fromWire("GET"));
        assertEquals(SignedUrlMethod.HEAD,
                SignedUrlMethod.fromWire("head"));
        assertEquals(SignedUrlMethod.PUT,
                SignedUrlMethod.fromWire("PUT"));
        assertEquals(3, SignedUrlMethod.values().length);
    }

    @Test
    void deliveryOpenFgaOutcomeClosedSetMatchesContract() {
        assertEquals(DeliveryOpenFgaOutcome.ALLOW,
                DeliveryOpenFgaOutcome.fromWire("ALLOW"));
        assertEquals(DeliveryOpenFgaOutcome.DENY,
                DeliveryOpenFgaOutcome.fromWire("DENY"));
        assertEquals(2, DeliveryOpenFgaOutcome.values().length);
    }

    @Test
    void signedUrlTicketTtlBoundsAreEnforced() {
        Instant now = Instant.parse("2026-08-15T12:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new SignedUrlTicket(
                        "d-1",
                        "media/tenant-1/asset-abc/v1.0.0/image_transcode/v0",
                        "https://signed.example/u",
                        SignedUrlMethod.GET,
                        DeliveryDisposition.INLINE,
                        DeliveryContentType.IMAGE_WEBP,
                        null,
                        SignedUrlTicket.TTL_MINIMUM_SECONDS - 1,
                        now,
                        now.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class,
                () -> new SignedUrlTicket(
                        "d-1",
                        "media/tenant-1/asset-abc/v1.0.0/image_transcode/v0",
                        "https://signed.example/u",
                        SignedUrlMethod.GET,
                        DeliveryDisposition.INLINE,
                        DeliveryContentType.IMAGE_WEBP,
                        null,
                        SignedUrlTicket.TTL_CEILING_SECONDS + 1,
                        now,
                        now.plusSeconds(3600)));
        SignedUrlTicket ok = new SignedUrlTicket(
                "d-1",
                "media/tenant-1/asset-abc/v1.0.0/image_transcode/v0",
                "https://signed.example/u",
                SignedUrlMethod.GET,
                DeliveryDisposition.INLINE,
                DeliveryContentType.IMAGE_WEBP,
                null,
                300,
                now,
                now.plusSeconds(300));
        assertTrue(ok.remainingSeconds(now) == 300L);
    }

    @Test
    void watermarkOverlayRequiresActorPseudoIdEmbedding() {
        Instant now = Instant.parse("2026-08-15T12:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new WatermarkOverlay(
                        DeliveryWatermarkMode.TEXT_OVERLAY,
                        "no actor here",
                        "actor-1234",
                        now));
        WatermarkOverlay ok = new WatermarkOverlay(
                DeliveryWatermarkMode.TEXT_OVERLAY,
                "actor-1234 @2026-08-15T12:00:00Z",
                "actor-1234",
                now);
        assertTrue(ok.overlayText().contains("actor-1234"));
    }

    @Test
    void rangeRequestEnforcesSizeBounds() {
        Instant now = Instant.parse("2026-08-15T12:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new RangeRequest(
                        DeliveryRangeUnit.BYTES, 0L, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> new RangeRequest(
                        DeliveryRangeUnit.BYTES, 0L,
                        RangeRequest.MAX_RANGE_BYTES + 1L));
        RangeRequest ok = new RangeRequest(
                DeliveryRangeUnit.BYTES, 0L, 1023L);
        assertEquals(1024L, ok.span());
        assertEquals("bytes=0-1023", ok.wireHeader());
        assertThrows(IllegalArgumentException.class,
                () -> RangeRequest.parseFromHeader(
                        "bytes=0-100,200-300"));
    }

    @Test
    void isDnaBucketKeyMatchesAllClosedSetPrefixes() {
        assertTrue(MediaProtectedDelivery.isDnaBucketKey(
                "dna/raw/sample.fastq"));
        assertTrue(MediaProtectedDelivery.isDnaBucketKey(
                "dna/match/segments.bin"));
        assertTrue(MediaProtectedDelivery.isDnaBucketKey(
                "dna/consent/grant.json"));
        assertTrue(!MediaProtectedDelivery.isDnaBucketKey(
                "media/tenant-1/asset-abc/upload.jpg"));
    }
}