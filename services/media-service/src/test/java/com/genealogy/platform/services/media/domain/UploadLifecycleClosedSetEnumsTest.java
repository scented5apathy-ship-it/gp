package com.genealogy.platform.services.media.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the closed-set enums for the upload-lifecycle
 * policy. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionStatuses + uploadSessionIntents +
 * mediaCategories + mimeVerdicts + checksumAlgorithms +
 * finalizeOutcomes + quotaDenialReasons +
 * uploadGuardDenyReasons + abandonedMultipartReasons +
 * quotaUnits + uploadAuthorizationOutcome` (E7.1).
 */
class UploadLifecycleClosedSetEnumsTest {

    @Test
    void uploadSessionStatusClosedSetMatchesContract() {
        assertEquals(UploadSessionStatus.REQUESTED, UploadSessionStatus.fromWire("REQUESTED"));
        assertEquals(UploadSessionStatus.SIGNED, UploadSessionStatus.fromWire("signed"));
        assertEquals(UploadSessionStatus.UPLOADING, UploadSessionStatus.fromWire("Uploading"));
        assertEquals(UploadSessionStatus.FINALIZING, UploadSessionStatus.fromWire("finalizing"));
        assertEquals(UploadSessionStatus.QUARANTINED, UploadSessionStatus.fromWire("QUARANTINED"));
        assertEquals(UploadSessionStatus.READY, UploadSessionStatus.fromWire("READY"));
        assertEquals(UploadSessionStatus.REJECTED, UploadSessionStatus.fromWire("REJECTED"));
        assertEquals(UploadSessionStatus.ABANDONED, UploadSessionStatus.fromWire("ABANDONED"));
        assertEquals(UploadSessionStatus.FAILED, UploadSessionStatus.fromWire("FAILED"));
        assertEquals("READY", UploadSessionStatus.READY.wire());
    }

    @Test
    void uploadSessionStatusRejectsUnknownAndNull() {
        assertThrows(IllegalArgumentException.class,
                () -> UploadSessionStatus.fromWire("DONE"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadSessionStatus.fromWire(null));
    }

    @Test
    void uploadSessionIntentClosedSetMatchesContract() {
        for (String intent : new String[]{
                "ATTACHMENT", "ALBUM", "PROFILE", "TREE_MEDIA",
                "DOCUMENT_THUMBNAIL", "OCR_INPUT", "DELIVERY_THUMBNAIL"}) {
            assertNotNull(UploadSessionIntent.fromWire(intent));
        }
        assertEquals(UploadSessionIntent.ATTACHMENT, UploadSessionIntent.fromWire("attachment"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadSessionIntent.fromWire("PHOTO"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadSessionIntent.fromWire(null));
    }

    @Test
    void mediaCategoryClosedSetMatchesContract() {
        for (String cat : new String[]{
                "IMAGE", "AUDIO", "VIDEO", "DOCUMENT", "PDF",
                "SVG", "ARCHIVE", "DNA_FASTQ"}) {
            assertNotNull(MediaCategory.fromWire(cat));
        }
        assertEquals(MediaCategory.IMAGE, MediaCategory.fromWire("image"));
        assertThrows(IllegalArgumentException.class,
                () -> MediaCategory.fromWire("BITMAP"));
        assertThrows(IllegalArgumentException.class,
                () -> MediaCategory.fromWire(null));
    }

    @Test
    void mimeVerdictClosedSetMatchesContract() {
        for (String v : new String[]{"ALLOW", "DENY", "SANDBOX_REQUIRED", "DEEP_SCAN_REQUIRED"}) {
            assertNotNull(MimeVerdict.fromWire(v));
        }
        assertEquals(MimeVerdict.SANDBOX_REQUIRED, MimeVerdict.fromWire("sandbox_required"));
        assertThrows(IllegalArgumentException.class,
                () -> MimeVerdict.fromWire("QUARANTINED"));
        assertThrows(IllegalArgumentException.class,
                () -> MimeVerdict.fromWire(null));
    }

    @Test
    void checksumAlgorithmClosedSetMatchesContract() {
        for (String a : new String[]{"SHA256", "SHA512", "BLAKE3"}) {
            assertNotNull(ChecksumAlgorithm.fromWire(a));
        }
        assertEquals(ChecksumAlgorithm.SHA256, ChecksumAlgorithm.fromWire("sha256"));
        assertThrows(IllegalArgumentException.class,
                () -> ChecksumAlgorithm.fromWire("MD5"));
        assertThrows(IllegalArgumentException.class,
                () -> ChecksumAlgorithm.fromWire(null));
    }

    @Test
    void finalizeOutcomeClosedSetMatchesContract() {
        for (String o : new String[]{"READY", "REJECTED", "QUARANTINED", "FAILED"}) {
            assertNotNull(FinalizeOutcome.fromWire(o));
        }
        assertEquals(FinalizeOutcome.READY, FinalizeOutcome.fromWire("ready"));
        assertThrows(IllegalArgumentException.class,
                () -> FinalizeOutcome.fromWire("UNDEFINED"));
        assertThrows(IllegalArgumentException.class,
                () -> FinalizeOutcome.fromWire(null));
    }

    @Test
    void quotaDenialReasonClosedSetMatchesContract() {
        for (String r : new String[]{
                "QUOTA_EXCEEDED_BYTES",
                "QUOTA_EXCEEDED_COUNT",
                "QUOTA_EXCEEDED_SESSION_TTL",
                "QUOTA_SCOPE_NOT_PERMITTED",
                "QUOTA_TENANT_HEADROOM_INSUFFICIENT"}) {
            assertNotNull(QuotaDenialReason.fromWire(r));
        }
        assertEquals(QuotaDenialReason.QUOTA_EXCEEDED_BYTES,
                QuotaDenialReason.fromWire("quota_exceeded_bytes"));
        assertThrows(IllegalArgumentException.class,
                () -> QuotaDenialReason.fromWire("EXCEEDED"));
        assertThrows(IllegalArgumentException.class,
                () -> QuotaDenialReason.fromWire(null));
    }

    @Test
    void uploadGuardDenyReasonClosedSetMatchesContract() {
        for (String r : new String[]{
                "MIME_NOT_PERMITTED",
                "CHECKSUM_MISMATCH",
                "DECLARED_SIZE_MISMATCH",
                "MULTIPART_PART_NUMBER_INVALID",
                "MULTIPART_PART_COUNT_OVERFLOW",
                "MULTIPART_PART_SEQUENCE_GAP",
                "SESSION_NOT_OWNED_BY_CALLER",
                "SESSION_ABANDONED",
                "SESSION_ALREADY_FINALIZED",
                "RATE_LIMITED",
                "PAYLOAD_DNA_BUCKET_FORBIDDEN"}) {
            assertNotNull(UploadGuardDenyReason.fromWire(r));
        }
        assertEquals(UploadGuardDenyReason.CHECKSUM_MISMATCH,
                UploadGuardDenyReason.fromWire("checksum_mismatch"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadGuardDenyReason.fromWire("INVALID"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadGuardDenyReason.fromWire(null));
    }

    @Test
    void abandonedMultipartReasonClosedSetMatchesContract() {
        for (String r : new String[]{
                "SESSION_TTL_EXPIRED",
                "CALLER_ABORTED_FINALIZE",
                "NO_PART_RECEIVED_IN_TTL",
                "CHECKSUM_FINALIZE_TIMEOUT",
                "QUOTA_REVOKED_MID_FLIGHT"}) {
            assertNotNull(AbandonedMultipartReason.fromWire(r));
        }
        assertEquals(AbandonedMultipartReason.SESSION_TTL_EXPIRED,
                AbandonedMultipartReason.fromWire("session_ttl_expired"));
        assertThrows(IllegalArgumentException.class,
                () -> AbandonedMultipartReason.fromWire("ORPHAN"));
        assertThrows(IllegalArgumentException.class,
                () -> AbandonedMultipartReason.fromWire(null));
    }

    @Test
    void quotaUnitClosedSetMatchesContract() {
        for (String u : new String[]{"BYTES", "ITEMS", "SECONDS"}) {
            assertNotNull(QuotaUnit.fromWire(u));
        }
        assertEquals(QuotaUnit.BYTES, QuotaUnit.fromWire("bytes"));
        assertThrows(IllegalArgumentException.class,
                () -> QuotaUnit.fromWire("REQUESTS"));
        assertThrows(IllegalArgumentException.class,
                () -> QuotaUnit.fromWire(null));
    }

    @Test
    void uploadAuthorizationOutcomeClosedSetMatchesContract() {
        for (String o : new String[]{"ALLOW", "DENY", "ABAC_DENY"}) {
            assertNotNull(UploadAuthorizationOutcome.fromWire(o));
        }
        assertEquals(UploadAuthorizationOutcome.ABAC_DENY,
                UploadAuthorizationOutcome.fromWire("abac_deny"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadAuthorizationOutcome.fromWire("UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> UploadAuthorizationOutcome.fromWire(null));
    }

    @Test
    void closedSetCoverageMatchesContract() {
        assertEquals(9, UploadSessionStatus.values().length);
        assertEquals(7, UploadSessionIntent.values().length);
        assertEquals(8, MediaCategory.values().length);
        assertEquals(4, MimeVerdict.values().length);
        assertEquals(3, ChecksumAlgorithm.values().length);
        assertEquals(4, FinalizeOutcome.values().length);
        assertEquals(5, QuotaDenialReason.values().length);
        assertEquals(11, UploadGuardDenyReason.values().length);
        assertEquals(5, AbandonedMultipartReason.values().length);
        assertEquals(3, QuotaUnit.values().length);
        assertEquals(3, UploadAuthorizationOutcome.values().length);
        for (UploadSessionStatus s : UploadSessionStatus.values()) {
            assertTrue(s.wire().matches("[A-Z_]+"));
        }
    }
}
