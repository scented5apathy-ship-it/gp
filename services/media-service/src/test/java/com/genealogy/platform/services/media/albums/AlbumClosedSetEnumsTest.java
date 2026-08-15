package com.genealogy.platform.services.media.albums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Pins every closed-set enum + record carried from
 * {@code contracts/media/albums-linking-policy.yaml} (E7.5).
 */
class AlbumClosedSetEnumsTest {

    @Test
    void albumVisibilityFromWire() {
        assertEquals(AlbumVisibility.PRIVATE,
                AlbumVisibility.fromWire("private"));
        assertEquals(AlbumVisibility.LEGAL_HOLD,
                AlbumVisibility.fromWire("LEGAL_HOLD"));
        assertEquals(AlbumVisibility.PUBLIC,
                AlbumVisibility.fromWire(" Public "));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumVisibility.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumVisibility.fromWire("UNKNOWN"));
    }

    @Test
    void albumLifecycleStateFromWire() {
        assertEquals(AlbumLifecycleState.ACTIVE,
                AlbumLifecycleState.fromWire("active"));
        assertEquals(AlbumLifecycleState.PURGED,
                AlbumLifecycleState.fromWire("PURGED"));
        assertEquals(AlbumLifecycleState.LEGAL_HOLD,
                AlbumLifecycleState.fromWire("Legal_Hold"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumLifecycleState.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumLifecycleState.fromWire("RELEASED"));
    }

    @Test
    void albumMemberKindFromWire() {
        assertEquals(AlbumMemberKind.ASSET,
                AlbumMemberKind.fromWire("asset"));
        assertEquals(AlbumMemberKind.COLLECTION,
                AlbumMemberKind.fromWire("COLLECTION"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumMemberKind.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumMemberKind.fromWire("STREAM"));
    }

    @Test
    void albumMemberSourceFromWire() {
        assertEquals(AlbumMemberSource.DERIVATIVE,
                AlbumMemberSource.fromWire("derivative"));
        assertEquals(AlbumMemberSource.PREVIEW,
                AlbumMemberSource.fromWire("PREVIEW"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumMemberSource.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumMemberSource.fromWire("RAW"));
    }

    @Test
    void albumSortOrderFromWire() {
        assertEquals(AlbumSortOrder.MANUAL_PIN,
                AlbumSortOrder.fromWire("manual_pin"));
        assertEquals(AlbumSortOrder.ADDED_AT_DESC,
                AlbumSortOrder.fromWire("ADDED_AT_DESC"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumSortOrder.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumSortOrder.fromWire("ALPHABETICAL"));
    }

    @Test
    void albumReferenceKindFromWire() {
        assertEquals(AlbumReferenceKind.PERSON,
                AlbumReferenceKind.fromWire("person"));
        assertEquals(AlbumReferenceKind.DATE,
                AlbumReferenceKind.fromWire("DATE"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumReferenceKind.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumReferenceKind.fromWire("MEDIA"));
    }

    @Test
    void albumReferenceOutcomeFromWire() {
        assertEquals(AlbumReferenceOutcome.RESOLVED,
                AlbumReferenceOutcome.fromWire("resolved"));
        assertEquals(AlbumReferenceOutcome.DANGLING,
                AlbumReferenceOutcome.fromWire("DANGLING"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumReferenceOutcome.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumReferenceOutcome.fromWire("STALE"));
    }

    @Test
    void reconciliationOutcomeFromWire() {
        assertEquals(ReconciliationOutcome.HEALTHY,
                ReconciliationOutcome.fromWire("healthy"));
        assertEquals(ReconciliationOutcome.PURGED,
                ReconciliationOutcome.fromWire("PURGED"));
        assertThrows(IllegalArgumentException.class,
                () -> ReconciliationOutcome.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> ReconciliationOutcome.fromWire("UNKNOWN"));
    }

    @Test
    void albumFailureReasonFromWire() {
        assertEquals(AlbumFailureReason.ALBUM_NOT_FOUND,
                AlbumFailureReason.fromWire("album_not_found"));
        assertEquals(AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                AlbumFailureReason.fromWire("ALBUM_DNA_BUCKET_FORBIDDEN"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumFailureReason.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumFailureReason.fromWire("UNKNOWN"));
    }

    @Test
    void albumAuditEventFromWire() {
        assertEquals(AlbumAuditEvent.ALBUM_CREATED,
                AlbumAuditEvent.fromWire("album_created"));
        assertEquals(AlbumAuditEvent.ALBUM_RECONCILIATION_PURGED,
                AlbumAuditEvent.fromWire(
                        "ALBUM_RECONCILIATION_PURGED"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumAuditEvent.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumAuditEvent.fromWire("DELETED"));
    }

    @Test
    void tagNormalizationRuleFromWire() {
        assertEquals(TagNormalizationRule.LOWERCASE_TRIM_DASH,
                TagNormalizationRule.fromWire("lowercase_trim_dash"));
        assertThrows(IllegalArgumentException.class,
                () -> TagNormalizationRule.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> TagNormalizationRule.fromWire("CAMEL"));
    }

    @Test
    void albumOperationOutcomeFromWire() {
        assertEquals(AlbumOperationOutcome.ALLOWED,
                AlbumOperationOutcome.fromWire("allowed"));
        assertEquals(AlbumOperationOutcome.SOFT_DELETED,
                AlbumOperationOutcome.fromWire("SOFT_DELETED"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOperationOutcome.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOperationOutcome.fromWire("PARTIAL"));
    }

    @Test
    void albumItemOutcomeFromWire() {
        assertEquals(AlbumItemOutcome.HEALTHY,
                AlbumItemOutcome.fromWire("healthy"));
        assertEquals(AlbumItemOutcome.QUOTA_EXCEEDED,
                AlbumItemOutcome.fromWire("QUOTA_EXCEEDED"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumItemOutcome.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumItemOutcome.fromWire("STALE"));
    }

    @Test
    void albumOpenFgaOutcomeFromWire() {
        assertEquals(AlbumOpenFgaOutcome.ALLOW,
                AlbumOpenFgaOutcome.fromWire("allow"));
        assertEquals(AlbumOpenFgaOutcome.DENY,
                AlbumOpenFgaOutcome.fromWire("DENY"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOpenFgaOutcome.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOpenFgaOutcome.fromWire("MAYBE"));
    }

    @Test
    void closedSetCoverage() {
        assertEquals(5, AlbumVisibility.values().length);
        assertEquals(5, AlbumLifecycleState.values().length);
        assertEquals(4, AlbumMemberKind.values().length);
        assertEquals(6, AlbumMemberSource.values().length);
        assertEquals(6, AlbumSortOrder.values().length);
        assertEquals(6, AlbumReferenceKind.values().length);
        assertEquals(4, AlbumReferenceOutcome.values().length);
        assertEquals(6, ReconciliationOutcome.values().length);
        assertEquals(23, AlbumFailureReason.values().length);
        assertEquals(18, AlbumAuditEvent.values().length);
        assertEquals(1, TagNormalizationRule.values().length);
        assertEquals(4, AlbumOperationOutcome.values().length);
        assertEquals(7, AlbumItemOutcome.values().length);
        assertEquals(2, AlbumOpenFgaOutcome.values().length);
    }

    @Test
    void limitsArePinnedToContract() {
        assertEquals(4096, AlbumCatalogLimits.MAX_ITEMS_PER_ALBUM);
        assertEquals(8192, AlbumCatalogLimits.MAX_ALBUMS_PER_TENANT);
        assertEquals(512, AlbumCatalogLimits.MAX_ALBUMS_PER_USER);
        assertEquals(64, AlbumCatalogLimits.MAX_REFERENCES_PER_ITEM);
        assertEquals(4096, AlbumCatalogLimits.MAX_CAPTION_LENGTH);
        assertEquals(64, AlbumCatalogLimits.MAX_TAG_LENGTH);
        assertEquals(365, AlbumCatalogLimits.SOFT_DELETE_RETENTION_DAYS);
        assertEquals(30, AlbumCatalogLimits.OBJECT_LOCK_COMPLIANCE_DAYS);
        assertEquals(24, AlbumCatalogLimits.RECONCILIATION_CADENCE_HOURS);
        assertEquals(1024, AlbumCatalogLimits.RECONCILIATION_BATCH_SIZE);
        assertEquals(168, AlbumCatalogLimits.RECONCILIATION_LOOKBACK_HOURS);
        assertEquals(150,
                AlbumCatalogLimits.RECONCILIATION_P95_BUDGET_SECONDS);
        assertEquals(256,
                AlbumCatalogLimits.RECONCILIATION_OUTBOX_BATCH_SIZE);
        assertEquals(64, AlbumCatalogLimits.ALBUM_ID_LENGTH);
        assertEquals(64, AlbumCatalogLimits.ALBUM_ITEM_ID_LENGTH);
        assertEquals(64,
                AlbumCatalogLimits.ALBUM_REFERENCE_PSEUDO_ID_LENGTH);
        assertEquals(64, AlbumCatalogLimits.ACTOR_PSEUDO_ID_LENGTH);
        assertEquals(128, AlbumCatalogLimits.CORRELATION_ID_LENGTH);
        assertEquals(1024, AlbumCatalogLimits.ALBUM_OBJECT_KEY_LENGTH);
        assertEquals(64, AlbumCatalogLimits.ALBUM_BCP47_TAG_LENGTH);
        assertEquals(128, AlbumCatalogLimits.ALBUM_ETAG_LENGTH);
        assertEquals(30, AlbumCatalogLimits.ACTIVITY_HEARTBEAT_SECONDS);
        assertEquals(6, AlbumCatalogLimits.ACTIVITY_HEARTBEAT_MULTIPLIER);
    }

    @Test
    void isDnaBucketKeyMatchesAllThreeClosedSetPrefixes() {
        assertTrue(AlbumCatalog.isDnaBucketKey("dna/raw/sample.fastq"));
        assertTrue(AlbumCatalog.isDnaBucketKey("dna/match/run-1.json"));
        assertTrue(AlbumCatalog.isDnaBucketKey("dna/consent/policy-7"));
        assertTrue(AlbumCatalog.isDnaBucketKey("dna/anything"));
        assertFalse(AlbumCatalog.isDnaBucketKey(
                "media/tenant-1/asset-abc/v8.15.0/image_transcode/v0"));
        assertFalse(AlbumCatalog.isDnaBucketKey("notes/raw/diary.txt"));
    }

    @Test
    void normaliseTagAppliesLowercaseTrimDash() {
        assertEquals("foo-bar",
                AlbumCatalog.normaliseTag("  Foo  Bar  "));
        assertEquals("a-b-c",
                AlbumCatalog.normaliseTag("A B C"));
        assertEquals("hello-world",
                AlbumCatalog.normaliseTag("-Hello-World-"));
        assertNull(AlbumCatalog.normaliseTag(null));
    }

    @Test
    void requestCompactConstructorEnforcesLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOperationRequest.builder()
                        .albumId("")
                        .tenantScopeId("t")
                        .actorPseudoId("a")
                        .correlationId("c")
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOperationRequest.builder()
                        .albumId("ok")
                        .tenantScopeId("")
                        .actorPseudoId("a")
                        .correlationId("c")
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOperationRequest.builder()
                        .albumId("ok")
                        .tenantScopeId("t")
                        .actorPseudoId("a")
                        .correlationId("c")
                        .albumVersion(0L)
                        .build());
        assertThrows(RuntimeException.class,
                () -> new AlbumItemRequest(
                        "item-1",
                        AlbumMemberKind.ASSET,
                        AlbumMemberSource.USER_UPLOAD,
                        "media/tenant-1/asset/v8/x",
                        true,
                        0,
                        List.of(),
                        List.of(),
                        "no language tag",
                        null));
        assertThrows(RuntimeException.class,
                () -> new AlbumReferenceRequest(
                        null,
                        "person-pseudo-1",
                        "gp.genealogy.v1",
                        AlbumReferenceOutcome.RESOLVED));
    }

    @Test
    void decisionCompactConstructorEnforcesShape() {
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOperationDecision.allowed(
                        "album-1", 1L, null, "no etag"));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOperationDecision.denied(
                        "album-1",
                        null,
                        Map.of(),
                        "no failureReason"));
        AlbumOperationDecision ok = AlbumOperationDecision.allowed(
                "album-1", 1L, "etag-1", "ok");
        assertEquals(AlbumOperationOutcome.ALLOWED, ok.outcome());
        assertNotNull(ok.newAlbumVersion());
        assertNotNull(ok.etag());
        AlbumOperationDecision denied = AlbumOperationDecision.denied(
                "album-1",
                AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                Map.of(),
                "denied");
        assertEquals(AlbumOperationOutcome.DENIED, denied.outcome());
        assertEquals(AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                denied.failureReason());
    }

    @Test
    void openFgaVerdictCompactConstructorEnforcesShape() {
        AlbumOpenFgaVerdict allow = AlbumOpenFgaVerdict.allow();
        assertEquals(AlbumOpenFgaOutcome.ALLOW, allow.outcome());
        assertNull(allow.failureReason());
        assertThrows(IllegalArgumentException.class,
                () -> new AlbumOpenFgaVerdict(
                        AlbumOpenFgaOutcome.ALLOW,
                        AlbumFailureReason.ALBUM_NOT_FOUND,
                        "x",
                        Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> AlbumOpenFgaVerdict.deny(null, "x"));
        AlbumOpenFgaVerdict deny = AlbumOpenFgaVerdict.deny(
                AlbumFailureReason.ALBUM_NOT_FOUND, "tuple missing");
        assertEquals(AlbumOpenFgaOutcome.DENY, deny.outcome());
    }

    @Test
    void referenceVerdictCompactConstructorEnforcesShape() {
        assertEquals(AlbumReferenceOutcome.RESOLVED,
                AlbumReferenceVerdict.resolved().outcome());
        assertThrows(IllegalArgumentException.class,
                () -> new AlbumReferenceVerdict(
                        AlbumReferenceOutcome.DANGLING,
                        null,
                        Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AlbumReferenceVerdict(
                        AlbumReferenceOutcome.RESOLVED,
                        AlbumFailureReason.ALBUM_NOT_FOUND,
                        Map.of()));
        assertEquals(AlbumReferenceOutcome.DANGLING,
                AlbumReferenceVerdict.unresolved(
                        AlbumReferenceOutcome.DANGLING,
                        AlbumFailureReason.ALBUM_REFERENCE_DANGLING)
                        .outcome());
    }

    @Test
    void reconcileProducesOutcomeMap() {
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        ReconciliationReport healthy = AlbumCatalog.reconcile(
                "rep-1", "album-1", "tenant-1",
                List.of(AlbumItemOutcome.HEALTHY, AlbumItemOutcome.HEALTHY),
                "actor", "corr", now);
        assertEquals(ReconciliationOutcome.HEALTHY, healthy.outcome());
        assertEquals(2, healthy.totalItems());
        ReconciliationReport dangling = AlbumCatalog.reconcile(
                "rep-2", "album-1", "tenant-1",
                List.of(AlbumItemOutcome.HEALTHY,
                        AlbumItemOutcome.DANGLING_REFERENCES),
                "actor", "corr", now);
        assertEquals(ReconciliationOutcome.DANGLING_REFERENCES,
                dangling.outcome());
        assertEquals(1, dangling.danglingItems());
        ReconciliationReport purged = AlbumCatalog.reconcile(
                "rep-3", "album-1", "tenant-1",
                List.of(AlbumItemOutcome.PURGED),
                "actor", "corr", now);
        assertEquals(ReconciliationOutcome.PURGED, purged.outcome());
        assertTrue(purged.reconciliationPurgeScheduled());
        assertNotNull(purged.purgeAfter());
    }

    @Test
    void requestRecordsAllItemsAndReferences() {
        AlbumOperationRequest r = AlbumOperationRequest.builder()
                .albumId("album-1")
                .tenantScopeId("tenant-1")
                .actorPseudoId("actor")
                .correlationId("corr")
                .albumVersion(7L)
                .items(List.of(new AlbumItemRequest(
                        "item-1",
                        AlbumMemberKind.ASSET,
                        AlbumMemberSource.USER_UPLOAD,
                        "media/tenant-1/asset/v8/x",
                        true,
                        0,
                        List.of(new AlbumReferenceRequest(
                                AlbumReferenceKind.PERSON,
                                "person-pseudo-1",
                                "gp.genealogy.v1",
                                AlbumReferenceOutcome.RESOLVED)),
                        List.of("alpha", "beta"),
                        "a caption",
                        "en")))
                .references(List.of())
                .tags(Map.of("a", "b"))
                .captions(Map.of("en", "hello"))
                .build();
        assertEquals(1, r.items().size());
        assertEquals(0, r.references().size());
        assertEquals(1, r.items().get(0).references().size());
        assertEquals(7L, r.albumVersion());
    }

    @Test
    void wireAndRoundTripEveryEnum() {
        for (AlbumVisibility v : AlbumVisibility.values()) {
            assertEquals(v, AlbumVisibility.fromWire(v.wire()));
        }
        for (AlbumLifecycleState v : AlbumLifecycleState.values()) {
            assertEquals(v, AlbumLifecycleState.fromWire(v.wire()));
        }
        for (AlbumMemberKind v : AlbumMemberKind.values()) {
            assertEquals(v, AlbumMemberKind.fromWire(v.wire()));
        }
        for (AlbumMemberSource v : AlbumMemberSource.values()) {
            assertEquals(v, AlbumMemberSource.fromWire(v.wire()));
        }
        for (AlbumSortOrder v : AlbumSortOrder.values()) {
            assertEquals(v, AlbumSortOrder.fromWire(v.wire()));
        }
        for (AlbumReferenceKind v : AlbumReferenceKind.values()) {
            assertEquals(v, AlbumReferenceKind.fromWire(v.wire()));
        }
        for (AlbumReferenceOutcome v : AlbumReferenceOutcome.values()) {
            assertEquals(v, AlbumReferenceOutcome.fromWire(v.wire()));
        }
        for (ReconciliationOutcome v : ReconciliationOutcome.values()) {
            assertEquals(v, ReconciliationOutcome.fromWire(v.wire()));
        }
        for (AlbumFailureReason v : AlbumFailureReason.values()) {
            assertEquals(v, AlbumFailureReason.fromWire(v.wire()));
        }
        for (AlbumAuditEvent v : AlbumAuditEvent.values()) {
            assertEquals(v, AlbumAuditEvent.fromWire(v.wire()));
        }
        for (TagNormalizationRule v : TagNormalizationRule.values()) {
            assertEquals(v, TagNormalizationRule.fromWire(v.wire()));
        }
        for (AlbumOperationOutcome v : AlbumOperationOutcome.values()) {
            assertEquals(v, AlbumOperationOutcome.fromWire(v.wire()));
        }
        for (AlbumItemOutcome v : AlbumItemOutcome.values()) {
            assertEquals(v, AlbumItemOutcome.fromWire(v.wire()));
        }
        for (AlbumOpenFgaOutcome v : AlbumOpenFgaOutcome.values()) {
            assertEquals(v, AlbumOpenFgaOutcome.fromWire(v.wire()));
        }
    }
}