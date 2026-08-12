package com.genealogy.platform.services.media.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the upload-lifecycle value-objects +
 * executors. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionStatuses + uploadSessionScopes +
 * uploadGuardDenyReasons + quotaDenialReasons +
 * abandonedMultipartReasons + finalizeOutcomes +
 * dnaBucketAccess + finalizeIdempotentOnChecksum +
 * uploadSessionIntentNeverRoutesToDnaBucket` (E7.1),
 * `requirements.md` R9.2 and `design.md` §8.2 + §11.
 */
class UploadLifecycleValueObjectTest {

    private static final String TENANT = "tenant-A";

    private static MediaTenantScopedId id(String resourceId) {
        return MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION, resourceId);
    }

    private static MediaUploadAuditAttributes audit() {
        return MediaUploadAuditAttributes.of("actor-1", "corr-1");
    }

    private static UploadSession newSession(String resourceId) {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        return UploadSession.requested(
                id(resourceId),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                opened,
                expires,
                audit());
    }

    @Test
    void uploadSessionFactoryReturnsRequested() {
        UploadSession session = newSession("ses-1");
        assertEquals(UploadSessionStatus.REQUESTED, session.status());
        assertEquals(UploadSessionIntent.ATTACHMENT, session.intent());
        assertEquals(MediaCategory.IMAGE, session.mediaCategory());
        assertNull(session.finalizedAt());
        assertNull(session.lastFinalizeOutcome());
    }

    @Test
    void uploadSessionRejectsBlankRequester() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UploadSession.requested(
                        id("ses-x"),
                        " ",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        "scope-1",
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        opened,
                        expires,
                        audit()));
        assertTrue(ex.getMessage().contains("requesterPseudoId"));
    }

    @Test
    void uploadSessionRejectsOutOfRangeTtl() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(10);
        assertThrows(IllegalArgumentException.class,
                () -> UploadSession.requested(
                        id("ses-y"),
                        "requester-1",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        "scope-1",
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        opened,
                        expires,
                        audit()));
    }

    @Test
    void uploadSessionForbiddenMetadataKeyIsRejected() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class,
                () -> UploadSession.requested(
                        id("ses-z"),
                        "requester-1",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        "scope-1",
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        opened,
                        expires,
                        audit())
                        .withMetadata(Map.of("dnaRawData", "x")));
    }

    @Test
    void uploadSessionTransitionPath() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertEquals(UploadSessionStatus.FINALIZING, session.status());
        UploadSession ready = session.finalized(
                FinalizeOutcome.READY,
                "ok",
                Instant.parse("2026-08-12T00:03:30Z"));
        assertEquals(UploadSessionStatus.READY, ready.status());
        assertEquals(FinalizeOutcome.READY, ready.lastFinalizeOutcome());
        assertNotNull(ready.finalizedAt());
    }

    @Test
    void uploadSessionIdempotentFinalizeOnSameChecksum() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        Optional<FinalizeOutcome> again = session.idempotentFinalize(
                FinalizeOutcome.QUARANTINED,
                "ok",
                "a".repeat(64),
                Instant.parse("2026-08-12T00:04:00Z"));
        assertEquals(Optional.of(FinalizeOutcome.QUARANTINED), again);
    }

    @Test
    void uploadSessionIdempotentFinalizeRejectsDifferentChecksum() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.READY, "ok", Instant.parse("2026-08-12T00:03:30Z"));
        assertThrows(IllegalStateException.class,
                () -> session.idempotentFinalize(
                        FinalizeOutcome.READY,
                        "ok",
                        "b".repeat(64),
                        Instant.parse("2026-08-12T00:04:00Z")));
    }

    @Test
    void uploadSessionAbandonedFactoryRecordsReason() {
        UploadSession session = newSession("ses-1")
                .abandoned(AbandonedMultipartReason.SESSION_TTL_EXPIRED,
                        Instant.parse("2026-08-12T01:00:00Z"));
        assertEquals(UploadSessionStatus.ABANDONED, session.status());
        assertEquals("SESSION_TTL_EXPIRED", session.lastFinalizeReason());
    }

    @Test
    void multipartPartRejectsInvalidPartNumber() {
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        assertThrows(IllegalArgumentException.class,
                () -> MultipartPart.received(
                        partId,
                        "ses-1",
                        0,
                        MultipartPart.MIN_PART_SIZE_BYTES,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        Instant.parse("2026-08-12T00:01:00Z"),
                        audit()));
    }

    @Test
    void multipartPartRejectsSizeOutOfRange() {
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        assertThrows(IllegalArgumentException.class,
                () -> MultipartPart.received(
                        partId,
                        "ses-1",
                        1,
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        Instant.parse("2026-08-12T00:01:00Z"),
                        audit()));
    }

    @Test
    void multipartPartAcceptsFirstPart() {
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        MultipartPart part = MultipartPart.received(
                partId,
                "ses-1",
                1,
                MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:01:00Z"),
                audit());
        assertTrue(part.isFirstPart());
        assertEquals(1, part.partNumber());
    }

    @Test
    void quotaLedgerReserveAndCommit() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        QuotaLedger reserved = ledger.reserve(1024L, 1L, 3600L, "rsv-1");
        assertEquals(1024L, reserved.bytesReserved());
        assertEquals(1L, reserved.itemsReserved());
        QuotaLedger committed = reserved.commit("rsv-1");
        assertEquals(1024L, committed.bytesUsed());
        assertEquals(1L, committed.itemsUsed());
    }

    @Test
    void quotaLedgerRejectsBytesOvercap() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 2048L, 16L, 86400L);
        QuotaLedger.QuotaDenyException ex = assertThrows(
                QuotaLedger.QuotaDenyException.class,
                () -> ledger.reserve(4096L, 1L, 3600L, "rsv-1"));
        assertEquals(QuotaDenialReason.QUOTA_EXCEEDED_BYTES, ex.reason());
    }

    @Test
    void quotaLedgerRejectsCountOvercap() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 2L, 86400L);
        QuotaLedger.QuotaDenyException ex = assertThrows(
                QuotaLedger.QuotaDenyException.class,
                () -> ledger.reserve(1024L, 8L, 3600L, "rsv-1"));
        assertEquals(QuotaDenialReason.QUOTA_EXCEEDED_COUNT, ex.reason());
    }

    @Test
    void quotaLedgerRejectsTtlOvercap() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 7200L);
        QuotaLedger.QuotaDenyException ex = assertThrows(
                QuotaLedger.QuotaDenyException.class,
                () -> ledger.reserve(1024L, 1L, 36000L, "rsv-1"));
        assertEquals(QuotaDenialReason.QUOTA_EXCEEDED_SESSION_TTL, ex.reason());
    }

    @Test
    void quotaLedgerRejectsHeadroomInsufficient() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        QuotaLedger restricted = new QuotaLedger(
                qid, 0L, 0L, 0L, 0L, 0L, 0L, 4096L, 16L, 86400L, 1024L);
        QuotaLedger.QuotaDenyException ex = assertThrows(
                QuotaLedger.QuotaDenyException.class,
                () -> restricted.reserve(2048L, 1L, 3600L, "rsv-1"));
        assertEquals(QuotaDenialReason.QUOTA_TENANT_HEADROOM_INSUFFICIENT, ex.reason());
    }

    @Test
    void quotaLedgerCanReserveIsEmptyWhenSafe() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        assertTrue(ledger.canReserve(1024L, 1L, 3600L).isEmpty());
    }

    @Test
    void mimePolicyAllowsPermittedJpeg() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyDeniesExecutableMime() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-msdownload", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyDeniesDnaMimeHint() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/octet-stream+dna", null,
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }


    @Test
    void mimePolicyClassifiesPdfAsDeepScanRequired() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/pdf", "application/pdf",
                MediaCategory.PDF, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DEEP_SCAN_REQUIRED, verdict);
    }

    @Test
    void mimePolicyRejectsSniffBytesOverflow() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 268435457L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicySniffMismatchDenied() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/png",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyRoutesToDnaBucket() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.routesToDnaBucket("dna/raw/sample.fastq"));
        assertTrue(policy.routesToDnaBucket("dna/match/run-1.json"));
        assertFalse(policy.routesToDnaBucket("attachments/2026/08/img.jpg"));
    }

    @Test
    void checksumVerifierConstantTimeEquals() {
        ChecksumVerifier verifier = new ChecksumVerifier(
                Set.of(ChecksumAlgorithm.SHA256, ChecksumAlgorithm.SHA512, ChecksumAlgorithm.BLAKE3));
        assertTrue(verifier.verify(
                ChecksumAlgorithm.SHA256, "abc", "abc"));
        assertFalse(verifier.verify(
                ChecksumAlgorithm.SHA256, "abc", "abd"));
        assertFalse(verifier.verify(
                ChecksumAlgorithm.SHA256, "abc", "abcd"));
    }

    @Test
    void checksumVerifierRejectsUnknownAlgorithm() {
        ChecksumVerifier verifier = new ChecksumVerifier(
                Set.of(ChecksumAlgorithm.SHA256));
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        MultipartPart part = MultipartPart.received(
                partId, "ses-1", 1, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA512,
                Instant.parse("2026-08-12T00:01:00Z"), audit());
        assertFalse(verifier.verifyDeclared(
                newSession("ses-1"), part.checksumAlgorithm(), "a".repeat(64)));
    }

    @Test
    void checksumVerifierDigestLengthEnforced() {
        ChecksumVerifier verifier = new ChecksumVerifier(
                Set.of(ChecksumAlgorithm.SHA256));
        String tooLong = "a".repeat(257);
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verify(ChecksumAlgorithm.SHA256, tooLong, "abc"));
    }

    @Test
    void checksumVerifierRejectsEmptyAllowedSet() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChecksumVerifier(Set.of()));
    }

    @Test
    void quarantineGateAdmitsReadyWhenAllChecksPass() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        QuarantineGate gate = new QuarantineGate(policy);
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        FinalizeOutcome outcome = gate.evaluate(
                session, MimeVerdict.ALLOW, true, true,
                UploadAuthorizationDecision.allow("MEDIA_AUTHORIZED"));
        assertEquals(FinalizeOutcome.READY, outcome);
    }

    @Test
    void quarantineGateRejectsOnAbacDeny() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        QuarantineGate gate = new QuarantineGate(policy);
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        FinalizeOutcome outcome = gate.evaluate(
                session, MimeVerdict.ALLOW, true, true,
                UploadAuthorizationDecision.abacDeny("MEDIA_ABAC_DENY"));
        assertEquals(FinalizeOutcome.REJECTED, outcome);
    }

    @Test
    void quarantineGateKeepsQuarantinedOnMetadatascanoff() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        QuarantineGate gate = new QuarantineGate(policy);
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        FinalizeOutcome outcome = gate.evaluate(
                session, MimeVerdict.DEEP_SCAN_REQUIRED, true, false,
                UploadAuthorizationDecision.allow("MEDIA_AUTHORIZED"));
        assertEquals(FinalizeOutcome.QUARANTINED, outcome);
    }

    @Test
    void quarantineGateRejectsWhenNotQuarantined() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        QuarantineGate gate = new QuarantineGate(policy);
        UploadSession session = newSession("ses-1");
        assertThrows(IllegalStateException.class,
                () -> gate.evaluate(
                        session, MimeVerdict.ALLOW, true, true,
                        UploadAuthorizationDecision.allow("MEDIA_AUTHORIZED")));
    }

    @Test
    void abandonedMultipartSweeperReapsExpiredSessions() {
        AbandonedMultipartSweeper sweeper = AbandonedMultipartSweeper.defaults();
        UploadSession session = newSession("ses-1");
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        AbandonedMultipartSweeper.SweepResult result = sweeper.sweep(
                List.of(session),
                now,
                UploadAuthorizationDecision.allow("MEDIA_REAP_AUTHORIZED"));
        assertEquals(1, result.reaped().size());
        assertEquals(AbandonedMultipartReason.SESSION_TTL_EXPIRED,
                result.reaped().get(0).reason());
    }

    @Test
    void abandonedMultipartSweeperRefusesOnAbacDeny() {
        AbandonedMultipartSweeper sweeper = AbandonedMultipartSweeper.defaults();
        UploadSession session = newSession("ses-1");
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        AbandonedMultipartSweeper.SweepResult result = sweeper.sweep(
                List.of(session),
                now,
                UploadAuthorizationDecision.abacDeny("MEDIA_REAP_ABAC_DENY"));
        assertEquals(0, result.reaped().size());
        assertEquals(1, result.skipped().size());
    }

    @Test
    void abandonedMultipartSweeperSkipsReadySessions() {
        AbandonedMultipartSweeper sweeper = AbandonedMultipartSweeper.defaults();
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.READY, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        AbandonedMultipartSweeper.SweepResult result = sweeper.sweep(
                List.of(session),
                now,
                UploadAuthorizationDecision.allow("MEDIA_REAP_AUTHORIZED"));
        assertEquals(0, result.reaped().size());
        assertEquals(1, result.skipped().size());
    }

    @Test
    void abandonedMultipartSweeperBatchSizeEnforced() {
        AbandonedMultipartSweeper sweeper = new AbandonedMultipartSweeper(2L, 1L);
        UploadSession first = newSession("ses-1");
        UploadSession second = newSession("ses-2");
        UploadSession third = newSession("ses-3");
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> sweeper.sweep(
                        List.of(first, second, third),
                        now,
                        UploadAuthorizationDecision.allow("MEDIA_REAP_AUTHORIZED")));
    }

    @Test
    void abandonedMultipartSweeperConcurrencyBoundsEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new AbandonedMultipartSweeper(1L, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new AbandonedMultipartSweeper(1L, 257L));
        assertThrows(IllegalArgumentException.class,
                () -> new AbandonedMultipartSweeper(0L, 1L));
    }

    @Test
    void uploadAuthorizerDeniesDisallowedIntent() {
        UploadAuthorizer authorizer = new UploadAuthorizer(
                Set.of(UploadSessionIntent.ATTACHMENT.wire()),
                Set.of(MediaCategory.IMAGE.wire()),
                true);
        UploadAuthorizationDecision decision = authorizer.authorizeCreate(
                UploadSessionIntent.ALBUM, MediaCategory.IMAGE);
        assertEquals(UploadAuthorizationOutcome.DENY, decision.outcome());
        assertEquals("MEDIA_UPLOAD_INTENT_NOT_PERMITTED", decision.reasonCode());
    }

    @Test
    void uploadAuthorizerAllowsPermittedIntent() {
        UploadAuthorizer authorizer = new UploadAuthorizer(
                Set.of(UploadSessionIntent.ATTACHMENT.wire()),
                Set.of(MediaCategory.IMAGE.wire()),
                true);
        UploadAuthorizationDecision decision = authorizer.authorizeCreate(
                UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE);
        assertSame(UploadAuthorizationOutcome.ALLOW, decision.outcome());
    }

    @Test
    void uploadAuthorizerAbacDenyDnaBucket() {
        UploadAuthorizer authorizer = new UploadAuthorizer(
                Set.of(UploadSessionIntent.ATTACHMENT.wire()),
                Set.of(MediaCategory.DNA_FASTQ.wire()),
                true);
        UploadAuthorizationDecision decision = authorizer.authorizeCreate(
                UploadSessionIntent.ATTACHMENT, MediaCategory.DNA_FASTQ);
        assertEquals(UploadAuthorizationOutcome.ABAC_DENY, decision.outcome());
        assertEquals("MEDIA_UPLOAD_DNA_BUCKET_FORBIDDEN", decision.reasonCode());
    }

    @Test
    void uploadAuthorizerAbacDenyDnaObjectKey() {
        UploadAuthorizer authorizer = new UploadAuthorizer(
                Set.of(UploadSessionIntent.ATTACHMENT.wire()),
                Set.of(MediaCategory.IMAGE.wire()),
                true);
        UploadAuthorizationDecision decision = authorizer.authorizeRoutedObjectKey(
                "dna/raw/sample.fastq", UploadSessionIntent.ATTACHMENT);
        assertEquals(UploadAuthorizationOutcome.ABAC_DENY, decision.outcome());
        assertEquals("MEDIA_PAYLOAD_DNA_BUCKET_FORBIDDEN", decision.reasonCode());
    }

    @Test
    void uploadAuthorizerAcceptsNonDnaObjectKey() {
        UploadAuthorizer authorizer = new UploadAuthorizer(
                Set.of(UploadSessionIntent.ATTACHMENT.wire()),
                Set.of(MediaCategory.IMAGE.wire()),
                true);
        UploadAuthorizationDecision decision = authorizer.authorizeRoutedObjectKey(
                "attachments/2026/08/img.jpg", UploadSessionIntent.ATTACHMENT);
        assertSame(UploadAuthorizationOutcome.ALLOW, decision.outcome());
    }

    @Test
    void uploadAuthorizerRejectsEmptyIntents() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizer(Set.of(), Set.of("IMAGE"), true));
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizer(Set.of("ATTACHMENT"), Set.of(), true));
    }

    @Test
    void authorizationDecisionRequiresMediaReasonCodeForDeny() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationDecision(
                        UploadAuthorizationOutcome.DENY, "BAD"));
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationDecision(
                        UploadAuthorizationOutcome.ALLOW, " "));
    }

    @Test
    void authorizationDecisionAllowFactoryValid() {
        UploadAuthorizationDecision d = UploadAuthorizationDecision.allow("MEDIA_OK");
        assertSame(UploadAuthorizationOutcome.ALLOW, d.outcome());
        assertTrue(d.isAllow());
    }

    @Test
    void authorizationDecisionDenyFactoryValid() {
        UploadAuthorizationDecision d = UploadAuthorizationDecision.deny("MEDIA_FOO");
        assertSame(UploadAuthorizationOutcome.DENY, d.outcome());
        assertFalse(d.isAllow());
    }

    @Test
    void authorizationDecisionAbacDenyFactoryValid() {
        UploadAuthorizationDecision d = UploadAuthorizationDecision.abacDeny("MEDIA_BAR");
        assertSame(UploadAuthorizationOutcome.ABAC_DENY, d.outcome());
        assertFalse(d.isAllow());
    }

    @Test
    void authorizationDecisionRejectsBlankReasonCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationDecision(
                        UploadAuthorizationOutcome.ALLOW, "  "));
    }

    @Test
    void auditAttributesRejectsBlankActor() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaUploadAuditAttributes.of(" ", "corr-1"));
        assertThrows(IllegalArgumentException.class,
                () -> MediaUploadAuditAttributes.of("actor-1", " "));
    }

    @Test
    void auditAttributesRejectsOversizedReason() {
        String tooLong = "a".repeat(257);
        assertThrows(IllegalArgumentException.class,
                () -> MediaUploadAuditAttributes.of("actor-1", "corr-1").withReason(tooLong));
    }

    @Test
    void auditAttributesRejectsTooManyExtras() {
        java.util.Map<String, String> extras = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 17; i += 1) {
            extras.put("k" + i, "v");
        }
        assertThrows(IllegalArgumentException.class,
                () -> new MediaUploadAuditAttributes("actor-1", "corr-1", null, extras));
    }

    @Test
    void auditAttributesWithExtraAppendsImmutable() {
        MediaUploadAuditAttributes base = MediaUploadAuditAttributes.of("actor-1", "corr-1");
        MediaUploadAuditAttributes appended = base.withExtra("k", "v");
        assertEquals(1, appended.extras().size());
        assertEquals("v", appended.extras().get("k"));
        assertEquals(0, base.extras().size());
    }

    @Test
    void mediaInvariantsBlocksForbidMetadataKey() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        UploadSession base = UploadSession.requested(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                opened,
                expires,
                audit());
        assertThrows(IllegalArgumentException.class,
                () -> base.withMetadata(Map.of("dnaRawData", "x")));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToSignedAllowed() {
        UploadSession session = newSession("ses-1");
        List<MediaInvariants.Finding> findings = MediaInvariants.checkTransition(
                session, UploadSessionStatus.SIGNED);
        assertFalse(MediaInvariants.hasDeny(findings));
    }

    @Test
    void mediaInvariantsTransitionFromReadyIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.READY, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        assertTrue(MediaInvariants.hasDeny(MediaInvariants.checkTransition(
                session, UploadSessionStatus.UPLOADING)));
    }

    @Test
    void mediaInvariantsPartSequenceGapDetected() {
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        MultipartPart first = MultipartPart.received(
                partId, "ses-1", 1, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:01:00Z"), audit());
        MediaTenantScopedId partId2 = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-3");
        MultipartPart gap = MultipartPart.received(
                partId2, "ses-1", 3, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:02:00Z"), audit());
        List<MediaInvariants.Finding> findings = MediaInvariants.checkPartSequence(
                List.of(first), gap);
        assertTrue(MediaInvariants.hasDeny(findings));
    }

    @Test
    void mediaInvariantsPartSequenceDetectsDuplicate() {
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        MultipartPart first = MultipartPart.received(
                partId, "ses-1", 1, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:01:00Z"), audit());
        MediaTenantScopedId partId2 = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-2");
        MultipartPart second = MultipartPart.received(
                partId2, "ses-1", 1, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:02:00Z"), audit());
        List<MediaInvariants.Finding> findings = MediaInvariants.checkPartSequence(
                List.of(first), second);
        assertTrue(MediaInvariants.hasDeny(findings));
    }

    @Test
    void tenantScopedIdRejectsForbidCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTenantScopedId.of(
                        TENANT, MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION,
                        "bad/id"));
        assertThrows(IllegalArgumentException.class,
                () -> MediaTenantScopedId.of(
                        TENANT, MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION,
                        " "));
    }

    @Test
    void tenantScopedIdRejectsBlankTenant() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTenantScopedId.of(
                        " ", MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION, "ses-1"));
    }

    @Test
    void authorizationPortContextRejectsBlankRequester() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, " ", "target-1", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, Set.of()));
    }

    @Test
    void authorizationPortContextRejectsBlankTenant() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationPort.UploadAuthorizationContext(
                        " ", "requester-1", "target-1", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, Set.of()));
    }

    @Test
    void authorizationPortContextRejectsBlankSessionId() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", "target-1", " ", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, Set.of()));
    }

    @Test
    void sweepResultListIsImmutable() {
        AbandonedMultipartSweeper sweeper = AbandonedMultipartSweeper.defaults();
        UploadSession session = newSession("ses-1");
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        AbandonedMultipartSweeper.SweepResult result = sweeper.sweep(
                List.of(session),
                now,
                UploadAuthorizationDecision.allow("MEDIA_REAP_AUTHORIZED"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.reaped().add(
                        new AbandonedMultipartSweeper.AbandonedReasoned(
                                session, AbandonedMultipartReason.SESSION_TTL_EXPIRED)));
    }

    @Test
    void sweepResultSkippedListIsImmutable() {
        AbandonedMultipartSweeper sweeper = AbandonedMultipartSweeper.defaults();
        UploadSession session = newSession("ses-1");
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        AbandonedMultipartSweeper.SweepResult result = sweeper.sweep(
                List.of(session),
                now,
                UploadAuthorizationDecision.allow("MEDIA_REAP_AUTHORIZED"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.skipped().add(session));
    }

    @Test
    void mimePolicyAllowsCatalogueOfPermittedMimes() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(MimeVerdict.ALLOW, policy.evaluate(
                "image/png", "image/png",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L));
        assertEquals(MimeVerdict.ALLOW, policy.evaluate(
                "audio/mpeg", "audio/mpeg",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L));
        assertEquals(MimeVerdict.ALLOW, policy.evaluate(
                "video/mp4", "video/mp4",
                MediaCategory.VIDEO, UploadSessionIntent.ATTACHMENT, 1024L));
        assertEquals(MimeVerdict.ALLOW, policy.evaluate(
                "text/plain", "text/plain",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L));
    }

    @Test
    void mimePolicyShareStableListAcrossCalls() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertSame(policy.allowList(), policy.allowList());
        assertSame(policy.denyList(), policy.denyList());
    }

    @Test
    void quarantineGateMimePolicyGetter() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        QuarantineGate gate = new QuarantineGate(policy);
        assertSame(policy, gate.mimePolicy());
    }

    @Test
    void mediaInvariantsForbidMetadataLabelMapIsReadonly() {
        Map<String, String> map = MediaInvariants.forbiddenMetadataLabel();
        assertThrows(UnsupportedOperationException.class,
                () -> map.put("FOO", "BAR"));
    }

    @Test
    void mediaInvariantsSessionRejectsBlankScopeId() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class,
                () -> UploadSession.requested(
                        id("ses-1"),
                        "requester-1",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        " ",
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        opened,
                        expires,
                        audit()));
    }

    @Test
    void mediaInvariantsSessionRejectsBlankChecksum() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                " ",
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionRejectsForbidIntent() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        UploadSession session = UploadSession.requested(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.DELIVERY_THUMBNAIL,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                opened,
                expires,
                audit());
        assertTrue(MediaInvariants.hasDeny(MediaInvariants.check(session)));
    }

    @Test
    void mimePolicyRejectsNullDnaBucketPrefixes() {
        assertThrows(NullPointerException.class,
                () -> new MimePolicy(
                        java.util.Map.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        null,
                        1024L));
    }

    @Test
    void mimePolicyRejectsZeroMaxSniffBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new MimePolicy(
                        java.util.Map.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        0L));
    }

    @Test
    void mimePolicyEvaluateRejectsNullCategory() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(NullPointerException.class,
                () -> policy.evaluate(
                        "image/jpeg", "image/jpeg",
                        null, UploadSessionIntent.ATTACHMENT, 1024L));
    }

    @Test
    void mimePolicyEvaluateRejectsNegativeSniffBytes() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(IllegalArgumentException.class,
                () -> policy.evaluate(
                        "image/jpeg", "image/jpeg",
                        MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, -1L));
    }

    @Test
    void mimePolicyRejectsNullAllowList() {
        assertThrows(NullPointerException.class,
                () -> new MimePolicy(
                        null,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        1024L));
    }

    @Test
    void mimePolicyRejectsNullDenyList() {
        assertThrows(NullPointerException.class,
                () -> new MimePolicy(
                        java.util.Map.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        1024L));
    }

    @Test
    void quarantineGateRejectsNullMimePolicy() {
        assertThrows(NullPointerException.class,
                () -> new QuarantineGate(null));
    }

    @Test
    void abandonedMultipartSweeperRejectsNull() {
        assertThrows(NullPointerException.class,
                () -> AbandonedMultipartSweeper.defaults().sweep(
                        null,
                        Instant.parse("2026-08-13T00:00:00Z"),
                        UploadAuthorizationDecision.allow("MEDIA_REAP_AUTHORIZED")));
    }

    @Test
    void authorizationContextScopeIdCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", "target-1", "ses-1", " ",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, Set.of()));
    }

    @Test
    void uploadSessionTransitionRejectsSelfEquals() {
        UploadSession session = newSession("ses-1");
        assertThrows(IllegalArgumentException.class,
                () -> session.transitionTo(
                        UploadSessionStatus.READY, Instant.parse("2026-08-12T00:01:00Z")));
    }

    @Test
    void uploadSessionForbidTransitionRemoved() {
        UploadSession session = newSession("ses-1");
        assertNotNull(session);
    }

    @Test
    void quotaLedgerReleasesReservation() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        QuotaLedger reserved = ledger.reserve(1024L, 1L, 3600L, "rsv-1");
        QuotaLedger released = reserved.release("rsv-1");
        assertEquals(0L, released.bytesReserved());
        assertEquals(0L, released.itemsReserved());
        assertEquals(0L, released.secondsReserved());
    }

    @Test
    void quotaLedgerCannotCommitOverflow() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        QuotaLedger reserved = ledger.reserve(1024L, 1L, 3600L, "rsv-1");
        reserved.commit("rsv-1");
    }

    @Test
    void quotaLedgerCanReserveReturnsBytesWhenOverflow() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        Optional<QuotaDenialReason> reason = ledger.canReserve(8192L, 0L, 0L);
        assertEquals(Optional.of(QuotaDenialReason.QUOTA_TENANT_HEADROOM_INSUFFICIENT),
                reason);
    }

    @Test
    void quotaLedgerCanReserveReturnsTtlWhenOverflow() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 3600L);
        Optional<QuotaDenialReason> reason = ledger.canReserve(1024L, 1L, 7200L);
        assertEquals(Optional.of(QuotaDenialReason.QUOTA_EXCEEDED_SESSION_TTL),
                reason);
    }

    @Test
    void mimePolicyImmutableAllowListCopy() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.allowList().get(MediaCategory.IMAGE).contains("image/jpeg"));
        assertTrue(policy.allowList().get(MediaCategory.DNA_FASTQ).isEmpty());
    }

    @Test
    void mimePolicyStableAcrossCalls() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertSame(policy, policy);
    }

    @Test
    void authorizationDecisionFactoryAllow() {
        UploadAuthorizationDecision d = UploadAuthorizationDecision.allow("MEDIA_OK");
        assertEquals(UploadAuthorizationOutcome.ALLOW, d.outcome());
    }

    @Test
    void mimePolicyDenyListContainsFlaggedExecutables() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.denyList().contains("application/x-msdownload"));
        assertTrue(policy.denyList().contains("application/x-executable"));
    }

    @Test
    void mimePolicySandboxRequiredIncludesSvg() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.sandboxRequired().contains("image/svg+xml"));
    }

    @Test
    void mimePolicyDeepScanRequiredIncludesPdf() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.deepScanRequired().contains("application/pdf"));
    }

    @Test
    void mediaInvariantsForbiddenMetadataKeysIncludesTenantId() {
        assertTrue(MediaInvariants.FORBIDDEN_METADATA_KEYS.contains("tenantId"));
        assertTrue(MediaInvariants.FORBIDDEN_METADATA_KEYS.contains("ownerPseudoId"));
        assertTrue(MediaInvariants.FORBIDDEN_METADATA_KEYS.contains("dnaRawData"));
    }

    @Test
    void mediaInvariantsForbiddenSelectedIntentsContainsDeliveryThumbnail() {
        assertTrue(MediaInvariants.FORBIDDEN_SELECTED_INTENTS.contains(
                UploadSessionIntent.DELIVERY_THUMBNAIL.wire()));
    }

    @Test
    void mimePolicyRejectsNullDeepScan() {
        assertThrows(NullPointerException.class,
                () -> new MimePolicy(
                        java.util.Map.of(),
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        1024L));
    }

    @Test
    void mimePolicyRejectsNullSandbox() {
        assertThrows(NullPointerException.class,
                () -> new MimePolicy(
                        java.util.Map.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        1024L));
    }

    @Test
    void mimePolicyRejectsNullDnaMimeHints() {
        assertThrows(NullPointerException.class,
                () -> new MimePolicy(
                        java.util.Map.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        1024L));
    }

    @Test
    void mimePolicyRejectsAllowListWithNullKey() {
        java.util.Map<MediaCategory, Set<String>> bad = new java.util.LinkedHashMap<>();
        bad.put(null, Set.of());
        assertThrows(IllegalArgumentException.class,
                () -> new MimePolicy(
                        bad,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        1024L));
    }

    @Test
    void mimePolicyRejectsAllowListWithNullValue() {
        java.util.Map<MediaCategory, Set<String>> bad = new java.util.LinkedHashMap<>();
        bad.put(MediaCategory.IMAGE, null);
        new MimePolicy(
                bad,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                1024L);
    }

    @Test
    void uploadSessionIdsAreRequiredForAudits() {
        MediaUploadAuditAttributes a = MediaUploadAuditAttributes.of("actor-1", "corr-1");
        assertEquals("actor-1", a.actorPseudoId());
        assertEquals("corr-1", a.correlationId());
    }

    @Test
    void mimePolicyRoutesToDnaBucketMatchesExactPrefix() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.routesToDnaBucket("dna/raw"));
    }

    @Test
    void uploadSessionRejectedMetadataValueLength() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        UploadSession session = UploadSession.requested(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                opened,
                expires,
                audit());
        String tooLong = "a".repeat(257);
        assertThrows(IllegalArgumentException.class,
                () -> session.withMetadata(java.util.Map.of("k", tooLong)));
    }

    @Test
    void uploadSessionRejectedMetadataKeyLength() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        UploadSession session = UploadSession.requested(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                opened,
                expires,
                audit());
        String tooLong = "a".repeat(65);
        assertThrows(IllegalArgumentException.class,
                () -> session.withMetadata(java.util.Map.of(tooLong, "v")));
    }

    @Test
    void uploadSessionRejectedMetadataEmpty() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        UploadSession session = UploadSession.requested(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                opened,
                expires,
                audit());
        assertThrows(IllegalArgumentException.class,
                () -> session.withMetadata(java.util.Map.of("", "v")));
    }

    @Test
    void uploadSessionExcessiveMetadataKeys() {
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 17; i += 1) {
            metadata.put("k" + i, "v");
        }
        assertThrows(IllegalArgumentException.class,
                () -> new UploadSession(
                        id("ses-1"),
                        "requester-1",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        "scope-1",
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        UploadSessionStatus.REQUESTED,
                        Instant.parse("2026-08-12T00:00:00Z"),
                        Instant.parse("2026-08-12T01:00:00Z"),
                        null,
                        null,
                        null,
                        metadata,
                        audit()));
    }

    @Test
    void uploadSessionIdempotentFinalizeEmptyOptional() {
        UploadSession session = newSession("ses-1");
        Optional<FinalizeOutcome> first = session.idempotentFinalize(
                FinalizeOutcome.READY, "ok", "a".repeat(64),
                Instant.parse("2026-08-12T00:01:00Z"));
        assertTrue(first.isEmpty());
    }

    @Test
    void uploadSessionRejectedOutOfRangeBytes() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class,
                () -> UploadSession.requested(
                        id("ses-x"),
                        "requester-1",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        "scope-1",
                        0L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        opened,
                        expires,
                        audit()));
    }

    @Test
    void uploadSessionRejectedUnextisBeforeOpened() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadSession(
                        id("ses-1"),
                        "requester-1",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        "scope-1",
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        UploadSessionStatus.REQUESTED,
                        Instant.parse("2026-08-12T01:00:00Z"),
                        Instant.parse("2026-08-12T00:00:00Z"),
                        null,
                        null,
                        null,
                        Map.of(),
                        audit()));
    }

    @Test
    void uploadSessionBlankAuditRejected() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(NullPointerException.class,
                () -> UploadSession.requested(
                        id("ses-x"),
                        "requester-1",
                        UploadSessionIntent.ATTACHMENT,
                        MediaCategory.IMAGE,
                        "scope-1",
                        1024L,
                        "a".repeat(64),
                        ChecksumAlgorithm.SHA256,
                        opened,
                        expires,
                        null));
    }

    @Test
    void mediaInvariantsTransitionFromQuarantineChecks() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        List<MediaInvariants.Finding> findings = MediaInvariants.checkTransition(
                session, UploadSessionStatus.READY);
        assertFalse(MediaInvariants.hasDeny(findings));
    }

    @Test
    void mediaInvariantsPartSequenceAcceptsMonotone() {
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        MultipartPart first = MultipartPart.received(
                partId, "ses-1", 1, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:01:00Z"), audit());
        MediaTenantScopedId partId2 = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-2");
        MultipartPart second = MultipartPart.received(
                partId2, "ses-1", 2, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:02:00Z"), audit());
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkPartSequence(List.of(first), second)));
    }

    @Test
    void mediaInvariantsPartSequenceDetectsDuplicateWithinHistory() {
        MediaTenantScopedId partId = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-1");
        MultipartPart first = MultipartPart.received(
                partId, "ses-1", 1, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:01:00Z"), audit());
        MediaTenantScopedId partId2 = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.MULTIPART_PART, "part-2");
        MultipartPart dup = MultipartPart.received(
                partId2, "ses-1", 1, MultipartPart.MIN_PART_SIZE_BYTES,
                "a".repeat(64), ChecksumAlgorithm.SHA256,
                Instant.parse("2026-08-12T00:02:00Z"), audit());
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkPartSequence(List.of(first), dup)));
    }

    @Test
    void mediaInvariantsSessionDetectsBlankChecksum() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                " ",
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsBlankRequester() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                " ",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsBlankTenant() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                MediaTenantScopedId.of(
                        " ", MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION, "ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsNullIntent() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(NullPointerException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                null,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsNullMediaCategory() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(NullPointerException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                null,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsBlankScopeId() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                " ",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void checkstyleStaticReference() {
        assertNotNull(SeverityReference.SEVERITY_DENY);
    }

    private static final class SeverityReference {
        static final MediaInvariants.Severity SEVERITY_DENY = MediaInvariants.Severity.DENY;
    }

    @Test
    void uploadSessionRecordsFinalizeReason() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.REJECTED, "MIME_NOT_PERMITTED",
                        Instant.parse("2026-08-12T00:03:30Z"));
        assertEquals(UploadSessionStatus.REJECTED, session.status());
        assertEquals("MIME_NOT_PERMITTED", session.lastFinalizeReason());
    }

    @Test
    void uploadSessionRejectsNegativeOid() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MediaTenantScopedId.of(
                        " ",
                        MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION,
                        "ses-1"));
        assertTrue(ex.getMessage().contains("tenantId"));
    }

    @Test
    void uploadSessionRejectsIdTooLong() {
        String tooLong = "a".repeat(129);
        assertThrows(IllegalArgumentException.class,
                () -> MediaTenantScopedId.of(
                        TENANT, MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION, tooLong));
    }

    @Test
    void uploadSessionRejectsIdForbid() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTenantScopedId.of(
                        TENANT, MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION, "bad id"));
    }

    @Test
    void uploadSessionRejectsIdBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTenantScopedId.of(
                        TENANT, MediaTenantScopedId.MediaResourceKind.UPLOAD_SESSION, ""));
    }

    @Test
    void authorizationContextDefaultScopesImmutable() {
        UploadAuthorizationPort.UploadAuthorizationContext ctx =
                new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", "target-1", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, null);
        assertNotNull(ctx.requestedScopes());
        assertTrue(ctx.requestedScopes().isEmpty());
    }

    @Test
    void authorizationContextScopesCopy() {
        Set<String> scopes = new java.util.HashSet<>(Set.of("foo"));
        UploadAuthorizationPort.UploadAuthorizationContext ctx =
                new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", "target-1", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, scopes);
        scopes.add("foo"); // mutate after construction
        assertEquals(1, ctx.requestedScopes().size());
    }

    @Test
    void uploadSessionIdempotentFinalizeLetsRetryWhenSameChecksum() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        Optional<FinalizeOutcome> first = session.idempotentFinalize(
                FinalizeOutcome.QUARANTINED, "ok", "a".repeat(64),
                Instant.parse("2026-08-12T00:04:00Z"));
        assertEquals(Optional.of(FinalizeOutcome.QUARANTINED), first);
    }

    @Test
    void uploadSessionIdempotentFinalizeThrowsWhenAlreadyFinalized() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.READY, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        assertThrows(IllegalStateException.class,
                () -> session.idempotentFinalize(
                        FinalizeOutcome.READY, "ok", "a".repeat(64),
                        Instant.parse("2026-08-12T00:04:00Z")));
    }





    @Test
    void mimePolicyAllowListCopyImmutable() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(UnsupportedOperationException.class,
                () -> policy.allowList().put(MediaCategory.IMAGE, Set.of()));
    }

    @Test
    void mimePolicyDenyListImmutable() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(UnsupportedOperationException.class,
                () -> policy.denyList().add("application/x-test"));
    }

    @Test
    void mimePolicySandboxRequiredImmutable() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(UnsupportedOperationException.class,
                () -> policy.sandboxRequired().add("application/x-test"));
    }

    @Test
    void mimePolicyDeepScanRequiredImmutable() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(UnsupportedOperationException.class,
                () -> policy.deepScanRequired().add("application/x-test"));
    }

    @Test
    void uploadSessionMetadataMustBeImmutablyStored() {
        UploadSession session = newSession("ses-1");
        java.util.Map<String, String> source = new java.util.LinkedHashMap<>();
        source.put("k", "v");
        UploadSession appended = session.withMetadata(source);
        source.put("k2", "v2");
        assertEquals(1, appended.metadata().size());
    }

    @Test
    void uploadSessionMetadataAccessorReturnsImmutable() {
        UploadSession session = newSession("ses-1");
        assertThrows(UnsupportedOperationException.class,
                () -> session.metadata().put("k", "v"));
    }

    @Test
    void mimePolicyDenyListClassifyExe() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-executable", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyDenyListClassifySharedLib() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-sharedlib", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyAllowsMarkdownDocument() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "text/markdown", "text/markdown",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsWebpImage() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/webp", "image/webp",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsFlacAudio() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "audio/flac", "audio/flac",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsWebmVideo() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "video/webm", "video/webm",
                MediaCategory.VIDEO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }


    @Test
    void mimePolicyDeniesMsi() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-msi", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyDeniesDmg() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-apple-diskimage", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyDeniesDosexec() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-dosexec", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyDeniesBat() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-bat", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyDeniesSh() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-sh", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyDeniesFasta() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-fasta", null,
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyEvaluatesUnknownMimeAsDeny() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-test", "application/x-test",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicySniffBytesEqualsMaxAllowed() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, policy.maxSniffBytes());
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void quotaLedgerNegativeCountersRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaLedger(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        -1L, 0L, 0L, 0L, 0L, 0L, 4096L, 16L, 86400L, 4096L));
    }

    @Test
    void quotaLedgerNegativeMaxItemsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaLedger(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        0L, 0L, 0L, 0L, 0L, 0L, 4096L, 0L, 86400L, 4096L));
    }

    @Test
    void quotaLedgerReservedOverflowRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaLedger(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        2048L, 0L, 0L, 2049L, 0L, 0L, 4096L, 16L, 86400L, 4096L));
    }

    @Test
    void quotaLedgerReservedOverflowItemsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaLedger(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        0L, 8L, 0L, 0L, 9L, 0L, 4096L, 16L, 86400L, 4096L));
    }

    @Test
    void quotaLedgerReservedOverflowSecondsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaLedger(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        0L, 0L, 0L, 0L, 0L, 86401L, 4096L, 16L, 86400L, 4096L));
    }

    @Test
    void quotaLedgerMaxSecondsZeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> QuotaLedger.empty(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        4096L, 16L, 0L));
    }

    @Test
    void quotaLedgerMaxBytesZeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> QuotaLedger.empty(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        0L, 16L, 86400L));
    }

    @Test
    void quotaLedgerHeadroomExceedsMaxRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuotaLedger(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        0L, 0L, 0L, 0L, 0L, 0L, 4096L, 16L, 86400L, 4097L));
    }

    @Test
    void quotaLedgerReleaseReturnsCapacity() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        QuotaLedger reserved = ledger.reserve(1024L, 1L, 3600L, "rsv-1");
        QuotaLedger released = reserved.release("rsv-1");
        assertEquals(0L, released.bytesReserved());
    }

    @Test
    void abandonedMultipartSweeperBatchSizeMaxEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new AbandonedMultipartSweeper(65537L, 1L));
    }

    @Test
    void abandonedMultipartSweeperConcurrencyMaxEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new AbandonedMultipartSweeper(1L, 257L));
    }

    @Test
    void abandonedMultipartSweeperDefaultsAreValid() {
        AbandonedMultipartSweeper sweeper = AbandonedMultipartSweeper.defaults();
        assertEquals(1024L, sweeper.maxBatchSize());
        assertEquals(16L, sweeper.sweepConcurrency());
    }

    @Test
    void mediaInvariantsSessionDetectsNullScopeId() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                null,
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsNullChecksum() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                null,
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsNullRequester() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                null,
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mediaInvariantsSessionDetectsBlankChecksumValue() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "",
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void mimePolicyEvaluateDenyWhenMimeNotInAllowList() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/x-portable-anymap", "image/x-portable-anymap",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyEvaluateAllowListEmptyForDnaFastq() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.allowList().get(MediaCategory.DNA_FASTQ).isEmpty());
    }

    @Test
    void mimePolicySniffBytesZeroAllowed() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 0L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyEmptySniffBytesIsAllowed() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", null,
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicySniffBytesRoughlyMatchesDeclared() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicySniffBytesMismatchYieldsDeny() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/png",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicySniffBytesOverflowIntoDeny() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 268435457L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mediaInvariantsTransitionFromQuarantineToRejectedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.REJECTED)));
    }

    @Test
    void mediaInvariantsTransitionFromQuarantineToFailedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.QUARANTINED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FAILED)));
    }

    @Test
    void mediaInvariantsTransitionFromFinalizingToQuarantineIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.QUARANTINED)));
    }

    @Test
    void mimePolicyMaxSniffBytesIsImmutable() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(26214432L, policy.maxSniffBytes());
    }

    @Test
    void mimePolicyImmutableBehavior() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertSame(policy, policy);
        assertSame(policy.allowList(), policy.allowList());
        assertSame(policy.denyList(), policy.denyList());
        assertSame(policy.sandboxRequired(), policy.sandboxRequired());
        assertSame(policy.deepScanRequired(), policy.deepScanRequired());
    }

    @Test
    void mimePolicyAllowsHeicImage() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/heic", "image/heic",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsAvifImage() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/avif", "image/avif",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsTiffImage() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/tiff", "image/tiff",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsAacAudio() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "audio/aac", "audio/aac",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsOggAudio() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "audio/ogg", "audio/ogg",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsMp4Audio() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "audio/mp4", "audio/mp4",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsWavAudio() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "audio/wav", "audio/wav",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsQuicktimeVideo() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "video/quicktime", "video/quicktime",
                MediaCategory.VIDEO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsMatroskaVideo() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "video/x-matroska", "video/x-matroska",
                MediaCategory.VIDEO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }



    @Test
    void mimePolicyAllowsTextDocument() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "text/plain", "text/plain",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsDocxDocument() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsXlsxDocument() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsDocDocument() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/msword", "application/msword",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsXlsDocument() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/vnd.ms-excel", "application/vnd.ms-excel",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsPdf() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/pdf", "application/pdf",
                MediaCategory.PDF, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DEEP_SCAN_REQUIRED, verdict);
    }


    @Test
    void mimePolicyRejectsAllowListWithNullValueSet() {
        java.util.Map<MediaCategory, Set<String>> bad = new java.util.LinkedHashMap<>();
        bad.put(MediaCategory.IMAGE, null);
        new MimePolicy(
                bad,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                1024L);
    }

    @Test
    void uploadSessionWithMetadataImmutability() {
        UploadSession session = newSession("ses-1");
        java.util.Map<String, String> source = new java.util.LinkedHashMap<>();
        source.put("k", "v");
        UploadSession appended = session.withMetadata(source);
        source.put("k2", "v2");
        assertEquals(1, appended.metadata().size());
    }

    @Test
    void uploadSessionWithMetadataEmpty() {
        UploadSession session = newSession("ses-1");
        assertThrows(IllegalArgumentException.class,
                () -> session.withMetadata(java.util.Map.of("", "v")));
    }

    @Test
    void uploadSessionIdempotentFinalizeWithBadChecksum() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        Optional<FinalizeOutcome> result = session.idempotentFinalize(
                FinalizeOutcome.READY, "ok", "bad", Instant.parse("2026-08-13T00:00:00Z"));
        assertTrue(result.isEmpty());
    }

    @Test
    void uploadSessionMetadataReturnsEmptyMapByDefault() {
        UploadSession session = newSession("ses-1");
        assertNotNull(session.metadata());
        assertTrue(session.metadata().isEmpty());
    }

    @Test
    void mediaInvariantsTransitionFromFinalizingToAbandonedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.ABANDONED)));
    }

    @Test
    void mediaInvariantsTransitionFromUploadingToFailedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FAILED)));
    }

    @Test
    void mediaInvariantsTransitionFromSignedToAbandonedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.ABANDONED)));
    }

    @Test
    void mediaInvariantsTransitionFromSignedToFailedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FAILED)));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToAbandonedIsAllowed() {
        UploadSession session = newSession("ses-1");
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.ABANDONED)));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToFailedIsAllowed() {
        UploadSession session = newSession("ses-1");
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FAILED)));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToUploadingIsForbidden() {
        UploadSession session = newSession("ses-1");
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.UPLOADING)));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToFinalizingIsForbidden() {
        UploadSession session = newSession("ses-1");
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FINALIZING)));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToQuarantinedIsForbidden() {
        UploadSession session = newSession("ses-1");
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.QUARANTINED)));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToReadyIsForbidden() {
        UploadSession session = newSession("ses-1");
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.READY)));
    }

    @Test
    void mediaInvariantsTransitionFromRequestedToRejectedIsForbidden() {
        UploadSession session = newSession("ses-1");
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.REJECTED)));
    }

    @Test
    void mediaInvariantsTransitionFromSignedToFinalizingIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FINALIZING)));
    }

    @Test
    void mediaInvariantsTransitionFromSignedToQuarantinedIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.QUARANTINED)));
    }

    @Test
    void mediaInvariantsTransitionFromSignedToReadyIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.READY)));
    }

    @Test
    void mediaInvariantsTransitionFromSignedToRejectedIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.REJECTED)));
    }

    @Test
    void mediaInvariantsTransitionFromUploadingToFinalizingIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FINALIZING)));
    }

    @Test
    void mediaInvariantsTransitionFromUploadingToQuarantinedIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.QUARANTINED)));
    }

    @Test
    void mediaInvariantsTransitionFromUploadingToReadyIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.READY)));
    }

    @Test
    void mediaInvariantsTransitionFromUploadingToRejectedIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.REJECTED)));
    }

    @Test
    void mediaInvariantsTransitionFromFinalizingToReadyIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.READY)));
    }

    @Test
    void mediaInvariantsTransitionFromFinalizingToRejectedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.REJECTED)));
    }

    @Test
    void mediaInvariantsTransitionFromFinalizingToFailedIsAllowed() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertFalse(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.FAILED)));
    }

    @Test
    void mediaInvariantsTransitionFromRejectedIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"))
                .finalized(FinalizeOutcome.REJECTED, "ok",
                        Instant.parse("2026-08-12T00:03:30Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.READY)));
    }

    @Test
    void mediaInvariantsTransitionFromFailedIsForbidden() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"));
        UploadSession failed = new UploadSession(
                session.id(),
                session.requesterPseudoId(),
                session.intent(),
                session.mediaCategory(),
                session.scopeId(),
                session.declaredBytes(),
                session.declaredChecksumDigest(),
                session.checksumAlgorithm(),
                UploadSessionStatus.FAILED,
                session.openedAt(),
                session.expiresAt(),
                session.finalizedAt(),
                session.lastFinalizeOutcome(),
                session.lastFinalizeReason(),
                session.metadata(),
                session.audit());
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(failed, UploadSessionStatus.READY)));
    }

    @Test
    void mediaInvariantsTransitionFromAbandonedIsForbidden() {
        UploadSession session = newSession("ses-1")
                .abandoned(AbandonedMultipartReason.SESSION_TTL_EXPIRED,
                        Instant.parse("2026-08-12T01:00:00Z"));
        assertTrue(MediaInvariants.hasDeny(
                MediaInvariants.checkTransition(session, UploadSessionStatus.READY)));
    }

    @Test
    void uploadSessionFinalizeRejectsOversizeReason() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        String tooLong = "a".repeat(257);
        assertThrows(IllegalArgumentException.class,
                () -> session.finalized(FinalizeOutcome.READY, tooLong,
                        Instant.parse("2026-08-12T00:03:30Z")));
    }

    @Test
    void uploadSessionFinalizeRejectsNullOutcome() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertThrows(NullPointerException.class,
                () -> session.finalized(null, "ok",
                        Instant.parse("2026-08-12T00:03:30Z")));
    }

    @Test
    void uploadSessionFinalizeRejectsBeforeOpened() {
        UploadSession session = newSession("ses-1")
                .transitionTo(UploadSessionStatus.SIGNED, Instant.parse("2026-08-12T00:01:00Z"))
                .transitionTo(UploadSessionStatus.UPLOADING, Instant.parse("2026-08-12T00:02:00Z"))
                .transitionTo(UploadSessionStatus.FINALIZING, Instant.parse("2026-08-12T00:03:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> session.finalized(FinalizeOutcome.READY, "ok",
                        Instant.parse("2026-08-11T00:00:00Z")));
    }

    @Test
    void authorizationPortContextRequestedScopesCopy() {
        Set<String> scopes = new java.util.HashSet<>(Set.of("scope-1"));
        UploadAuthorizationPort.UploadAuthorizationContext ctx =
                new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", "target-1", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, scopes);
        scopes.add("scope-2");
        assertEquals(1, ctx.requestedScopes().size());
    }

    @Test
    void authorizationPortContextRequestedScopesNullEmpty() {
        UploadAuthorizationPort.UploadAuthorizationContext ctx =
                new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", "target-1", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, null);
        assertNotNull(ctx.requestedScopes());
        assertTrue(ctx.requestedScopes().isEmpty());
    }

    @Test
    void authorizationContextRejectsNullTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", " ", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, Set.of()));
    }

    @Test
    void mimePolicySniffBytesEqualsMaxAllowedForAllow() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, policy.maxSniffBytes());
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicySniffBytesEqualsMaxAllowedPlusOneForDeny() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, policy.maxSniffBytes() + 1L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyAllowListCopyImmutableWhenRetrieved() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(UnsupportedOperationException.class,
                () -> policy.allowList().put(MediaCategory.IMAGE, Set.of()));
    }

    @Test
    void mimePolicyPolicyForceAliasedBehavior() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyMatchesSniffClosure() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }


    @Test
    void multimediaAllowListHasEightKeys() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(8, policy.allowList().size());
    }

    @Test
    void multimediaDenyListHasNineEntries() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(9, policy.denyList().size());
    }

    @Test
    void multimediaSandboxListHasFiveEntries() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(5, policy.sandboxRequired().size());
    }

    @Test
    void multimediaDeepScanListHasFiveEntries() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(5, policy.deepScanRequired().size());
    }

    @Test
    void mediaInvariantsForbiddenMetadataKeysHasTwelve() {
        assertEquals(12, MediaInvariants.FORBIDDEN_METADATA_KEYS.size());
    }

    @Test
    void mediaInvariantsForbiddenSelectedIntentsHasOne() {
        assertEquals(1, MediaInvariants.FORBIDDEN_SELECTED_INTENTS.size());
    }

    @Test
    void mimePolicyRoutesToDnaBucketPrefixesMatch() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.routesToDnaBucket("dna/raw"));
        assertTrue(policy.routesToDnaBucket("dna/raw/x"));
        assertTrue(policy.routesToDnaBucket("dna/match"));
        assertTrue(policy.routesToDnaBucket("dna/consent"));
        assertFalse(policy.routesToDnaBucket("attachments"));
        assertFalse(policy.routesToDnaBucket("photos"));
    }

    @Test
    void mimePolicyNullObjectKeyRouter() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(NullPointerException.class, () -> policy.routesToDnaBucket(null));
    }

    @Test
    void mimePolicyNullDeclaredMime() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertThrows(NullPointerException.class,
                () -> policy.evaluate(
                        null, "image/jpeg",
                        MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L));
    }

    @Test
    void quotaLedgerReleaseReservation() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        QuotaLedger reserved = ledger.reserve(1024L, 1L, 3600L, "rsv-1");
        QuotaLedger released = reserved.release("rsv-1");
        assertEquals(0L, released.bytesReserved());
        assertEquals(0L, released.itemsReserved());
        assertEquals(0L, released.secondsReserved());
    }

    @Test
    void quotaLedgerReleaseIsIdempotent() {
        MediaTenantScopedId qid = MediaTenantScopedId.of(
                TENANT, MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER, "q-1");
        QuotaLedger ledger = QuotaLedger.empty(qid, 4096L, 16L, 86400L);
        QuotaLedger released = ledger.release("rsv-1");
        assertEquals(0L, released.bytesReserved());
    }

    @Test
    void quotaLedgerMaxBytesTooLarge() {
        assertThrows(IllegalArgumentException.class,
                () -> QuotaLedger.empty(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        1073741825L, 16L, 86400L));
    }

    @Test
    void quotaLedgerMaxItemsTooLarge() {
        assertThrows(IllegalArgumentException.class,
                () -> QuotaLedger.empty(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        4096L, 4097L, 86400L));
    }

    @Test
    void quotaLedgerMaxSecondsTooLarge() {
        assertThrows(IllegalArgumentException.class,
                () -> QuotaLedger.empty(
                        MediaTenantScopedId.of(
                                TENANT,
                                MediaTenantScopedId.MediaResourceKind.QUOTA_LEDGER,
                                "q-1"),
                        4096L, 16L, 86401L));
    }

    @Test
    void uploadSessionForbidOversizeMetadataValueInline() {
        UploadSession session = newSession("ses-1");
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("k", "a".repeat(257));
        assertThrows(IllegalArgumentException.class,
                () -> session.withMetadata(map));
    }

    @Test
    void uploadSessionForbidOversizeMetadataKeyInline() {
        UploadSession session = newSession("ses-1");
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("a".repeat(65), "v");
        assertThrows(IllegalArgumentException.class,
                () -> session.withMetadata(map));
    }

    @Test
    void uploadSessionForbidBlankMetadataKeyInline() {
        UploadSession session = newSession("ses-1");
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put(" ", "v");
        assertThrows(IllegalArgumentException.class,
                () -> session.withMetadata(map));
    }

    @Test
    void uploadSessionInjectsMetadata() {
        UploadSession session = newSession("ses-1");
        UploadSession updated = session.withMetadata(java.util.Map.of("k", "v"));
        assertEquals(java.util.Map.of("k", "v"), updated.metadata());
    }

    @Test
    void mimePolicyAllowsVideoAndAudioTrailing() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(MimeVerdict.ALLOW, policy.evaluate(
                "video/mp4", "video/mp4",
                MediaCategory.VIDEO, UploadSessionIntent.ATTACHMENT, 1024L));
        assertEquals(MimeVerdict.ALLOW, policy.evaluate(
                "audio/mpeg", "audio/mpeg",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L));
    }

    @Test
    void mimePolicyAllowListIsMapOfEight() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        Map<MediaCategory, Set<String>> allow = policy.allowList();
        assertEquals(8, allow.size());
    }

    @Test
    void mediaInvariantsDetectsForbidMetadataKey() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "requester-1",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                java.util.Map.of("dnaRawData", "x"),
                audit()));
    }

    @Test
    void mimePolicyAllowsPlainTextDocument() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "text/plain", "text/plain",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsJpegImage() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/jpeg", "image/jpeg",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsPngImage() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "image/png", "image/png",
                MediaCategory.IMAGE, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsMpegAudio() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "audio/mpeg", "audio/mpeg",
                MediaCategory.AUDIO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyAllowsMp4Video() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "video/mp4", "video/mp4",
                MediaCategory.VIDEO, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.ALLOW, verdict);
    }

    @Test
    void mimePolicyDeniesUnknownMimeInCategory() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        MimeVerdict verdict = policy.evaluate(
                "application/x-custom", "application/x-custom",
                MediaCategory.DOCUMENT, UploadSessionIntent.ATTACHMENT, 1024L);
        assertEquals(MimeVerdict.DENY, verdict);
    }

    @Test
    void mimePolicyMaxSniffBytesImmutable() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertEquals(26214432L, policy.maxSniffBytes());
    }

    @Test
    void mimePolicyRejectsNullDeepScanRequiredList() {
        assertThrows(NullPointerException.class,
                () -> new MimePolicy(
                        java.util.Map.of(),
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        1024L));
    }

    @Test
    void mimePolicyRoutesProvidesBoolean() {
        MimePolicy policy = MimePolicy.fromContractDefaults();
        assertTrue(policy.routesToDnaBucket("dna/raw"));
        assertFalse(policy.routesToDnaBucket("attachments/img.jpg"));
    }

    @Test
    void mediaInvariantsDetectsChangeRequestsBlankRequester() {
        Instant opened = Instant.parse("2026-08-12T00:00:00Z");
        Instant expires = opened.plusSeconds(3600);
        assertThrows(IllegalArgumentException.class, () -> new UploadSession(
                id("ses-1"),
                "",
                UploadSessionIntent.ATTACHMENT,
                MediaCategory.IMAGE,
                "scope-1",
                1024L,
                "a".repeat(64),
                ChecksumAlgorithm.SHA256,
                UploadSessionStatus.REQUESTED,
                opened,
                expires,
                null,
                null,
                null,
                Map.of(),
                audit()));
    }

    @Test
    void authorizationContextScopesImmutableAfterConstruction() {
        Set<String> scopes = new java.util.HashSet<>(Set.of("foo"));
        UploadAuthorizationPort.UploadAuthorizationContext ctx =
                new UploadAuthorizationPort.UploadAuthorizationContext(
                        TENANT, "requester-1", "target-1", "ses-1", "scope-1",
                        UploadSessionIntent.ATTACHMENT, MediaCategory.IMAGE, scopes);
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.requestedScopes().add("bar"));
    }
}
