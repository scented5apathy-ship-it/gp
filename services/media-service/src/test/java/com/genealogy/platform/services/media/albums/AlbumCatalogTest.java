package com.genealogy.platform.services.media.albums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link AlbumCatalog} against the
 * {@code contracts/media/albums-linking-policy.yaml}
 * state machine + guard rails (E7.5).
 */
class AlbumCatalogTest {

    private static final Instant ISSUED_AT =
            Instant.parse("2026-08-15T12:00:00Z");

    private static AlbumOperationRequest.Builder baseRequest() {
        return AlbumOperationRequest.builder()
                .albumId("album-1")
                .tenantScopeId("tenant-1")
                .actorPseudoId("actor-1234")
                .correlationId("corr-abc-001")
                .albumVersion(7L)
                .visibility(AlbumVisibility.PRIVATE)
                .lifecycleState(AlbumLifecycleState.ACTIVE)
                .membershipActive(true)
                .quotaAllowancePresent(true)
                .objectLockComplianceAvailable(false)
                .items(List.of(new AlbumItemRequest(
                        "item-1",
                        AlbumMemberKind.ASSET,
                        AlbumMemberSource.USER_UPLOAD,
                        "media/tenant-1/asset-abc/v8.15.0/"
                                + "image_transcode/v0",
                        true,
                        0,
                        List.of(new AlbumReferenceRequest(
                                AlbumReferenceKind.PERSON,
                                "person-pseudo-1",
                                "gp.genealogy.v1",
                                AlbumReferenceOutcome.RESOLVED)),
                        List.of("alpha"),
                        null,
                        null)))
                .dnaBucketKey(false);
    }

    @Test
    void happyPathYieldsAllowed() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest().build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.ALLOWED, d.outcome());
        assertNull(d.failureReason());
        assertNotNull(d.newAlbumVersion());
        assertNotNull(d.etag());
    }

    @Test
    void legalHoldWithoutComplianceYieldsVisibilityForbidden() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest()
                        .visibility(AlbumVisibility.LEGAL_HOLD)
                        .objectLockComplianceAvailable(false)
                        .build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_VISIBILITY_FORBIDDEN,
                d.failureReason());
    }

    @Test
    void legalHoldWithComplianceYieldsAllowed() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest()
                        .visibility(AlbumVisibility.LEGAL_HOLD)
                        .objectLockComplianceAvailable(true)
                        .build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.ALLOWED, d.outcome());
    }

    @Test
    void membershipInactiveYieldsDenied() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest().membershipActive(false).build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_VISIBILITY_FORBIDDEN,
                d.failureReason());
    }

    @Test
    void quotaMissingYieldsDenied() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest().quotaAllowancePresent(false).build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_QUOTA_EXCEEDED,
                d.failureReason());
    }

    @Test
    void purgedLifecycleYieldsLifecycleForbidden() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest()
                        .lifecycleState(AlbumLifecycleState.PURGED)
                        .build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_LIFECYCLE_FORBIDDEN,
                d.failureReason());
    }

    @Test
    void softDeletedLifecycleYieldsSoftDeleted() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest()
                        .lifecycleState(AlbumLifecycleState.SOFT_DELETED)
                        .build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.SOFT_DELETED, d.outcome());
        assertNotNull(d.newAlbumVersion());
        assertNotNull(d.etag());
        assertTrue(d.summary().contains("retention"));
    }

    @Test
    void dnaBucketFlagYieldsDnaBucketForbidden() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest().dnaBucketKey(true).build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                d.failureReason());
    }

    @Test
    void dnaBucketObjectKeyYieldsDnaBucketForbidden() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest()
                        .items(List.of(new AlbumItemRequest(
                                "item-2",
                                AlbumMemberKind.ASSET,
                                AlbumMemberSource.DERIVATIVE,
                                "dna/raw/sample.fastq",
                                true,
                                0,
                                List.of(),
                                List.of(),
                                null,
                                null)))
                        .build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                d.failureReason());
    }

    @Test
    void notReadyItemYieldsDerivedObjectKeyNotReady() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest()
                        .items(List.of(new AlbumItemRequest(
                                "item-3",
                                AlbumMemberKind.ASSET,
                                AlbumMemberSource.USER_UPLOAD,
                                "media/tenant-1/asset/v8/x",
                                false,
                                0,
                                List.of(),
                                List.of(),
                                null,
                                null)))
                        .build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_DERIVED_OBJECT_KEY_NOT_READY,
                d.failureReason());
    }

    @Test
    void captionWithoutBcp47YieldsCaptionLanguageMissing() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> new AlbumItemRequest(
                        "item-4",
                        AlbumMemberKind.ASSET,
                        AlbumMemberSource.USER_UPLOAD,
                        "media/tenant-1/asset/v8/x",
                        true,
                        0,
                        List.of(),
                        List.of(),
                        "caption with no language",
                        null));
        assertTrue(ex.getMessage()
                .contains("captionBcp47Language missing"));
    }

    @Test
    void captionWithInvalidBcp47YieldsCaptionLanguageMissing() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest()
                        .items(List.of(new AlbumItemRequest(
                                "item-5",
                                AlbumMemberKind.ASSET,
                                AlbumMemberSource.USER_UPLOAD,
                                "media/tenant-1/asset/v8/x",
                                true,
                                0,
                                List.of(),
                                List.of(),
                                "caption",
                                "not_a_bcp47_tag")))
                        .build(),
                new AllowOpenFgaPort(),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_CAPTION_LANGUAGE_MISSING,
                d.failureReason());
    }

    @Test
    void openFgaDenyYieldsOpenFgaFailureReason() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest().build(),
                new DenyOpenFgaPort(
                        AlbumFailureReason.ALBUM_VISIBILITY_FORBIDDEN,
                        "tuple missing"),
                new ResolvedReferencePort(),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_VISIBILITY_FORBIDDEN,
                d.failureReason());
    }

    @Test
    void danglingReferenceYieldsReferenceDangling() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest().build(),
                new AllowOpenFgaPort(),
                new StaticReferencePort(
                        AlbumReferenceVerdict.unresolved(
                                AlbumReferenceOutcome.DANGLING,
                                AlbumFailureReason
                                        .ALBUM_REFERENCE_DANGLING)),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_REFERENCE_DANGLING,
                d.failureReason());
    }

    @Test
    void revokedReferenceYieldsReferenceRevoked() {
        AlbumOperationDecision d = AlbumCatalog.apply(
                baseRequest().build(),
                new AllowOpenFgaPort(),
                new StaticReferencePort(
                        AlbumReferenceVerdict.unresolved(
                                AlbumReferenceOutcome.REVOKED,
                                AlbumFailureReason
                                        .ALBUM_REFERENCE_REVOKED)),
                ISSUED_AT);
        assertEquals(AlbumOperationOutcome.DENIED, d.outcome());
        assertEquals(
                AlbumFailureReason.ALBUM_REFERENCE_REVOKED,
                d.failureReason());
    }

    @Test
    void reconcileHealthyAndDangling() {
        Instant now = Instant.parse("2026-08-15T12:00:00Z");
        ReconciliationReport healthy = AlbumCatalog.reconcile(
                "rep-1", "album-1", "tenant-1",
                List.of(AlbumItemOutcome.HEALTHY, AlbumItemOutcome.HEALTHY),
                "actor", "corr", now);
        assertEquals(ReconciliationOutcome.HEALTHY, healthy.outcome());
        ReconciliationReport dangling = AlbumCatalog.reconcile(
                "rep-2", "album-1", "tenant-1",
                List.of(AlbumItemOutcome.HEALTHY,
                        AlbumItemOutcome.DANGLING_REFERENCES),
                "actor", "corr", now);
        assertEquals(ReconciliationOutcome.DANGLING_REFERENCES,
                dangling.outcome());
    }

    @Test
    void normaliseTagLowercasesTrimsAndDashes() {
        assertEquals("a-b",
                AlbumCatalog.normaliseTag(" A  B "));
        assertEquals("foo",
                AlbumCatalog.normaliseTag("-FOO-"));
    }

    @Test
    void exceptionCarriesFailureReason() {
        AlbumCatalogException ex = new AlbumCatalogException(
                AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                "refused",
                Map.of("k", "v"));
        assertEquals(
                AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                ex.failureReason());
        assertEquals(1, ex.facts().size());
    }

    /**
     * Allow-stub for the OpenFGA port.
     */
    private static final class AllowOpenFgaPort
            implements AlbumOpenFgaPort {
        @Override
        public AlbumOpenFgaVerdict check(
                String tenantScopeId,
                String albumId,
                String actorPseudoId,
                String correlationId) {
            return AlbumOpenFgaVerdict.allow();
        }
    }

    /**
     * Deny-stub for the OpenFGA port.
     */
    private static final class DenyOpenFgaPort
            implements AlbumOpenFgaPort {
        private final AlbumFailureReason reason;
        private final String code;

        DenyOpenFgaPort(AlbumFailureReason reason, String code) {
            this.reason = reason;
            this.code = code;
        }

        @Override
        public AlbumOpenFgaVerdict check(
                String tenantScopeId,
                String albumId,
                String actorPseudoId,
                String correlationId) {
            return AlbumOpenFgaVerdict.deny(reason, code);
        }
    }

    /**
     * Resolved-stub for the reference resolver port.
     */
    private static final class ResolvedReferencePort
            implements AlbumReferenceResolverPort {
        @Override
        public AlbumReferenceVerdict resolve(
                String tenantScopeId,
                AlbumReferenceKind kind,
                String referencePseudoId) {
            return AlbumReferenceVerdict.resolved();
        }
    }

    /**
     * Static-stub for the reference resolver port (returns
     * the same verdict for every call).
     */
    private static final class StaticReferencePort
            implements AlbumReferenceResolverPort {
        private final AlbumReferenceVerdict verdict;

        StaticReferencePort(AlbumReferenceVerdict verdict) {
            this.verdict = verdict;
        }

        @Override
        public AlbumReferenceVerdict resolve(
                String tenantScopeId,
                AlbumReferenceKind kind,
                String referencePseudoId) {
            return verdict;
        }
    }
}