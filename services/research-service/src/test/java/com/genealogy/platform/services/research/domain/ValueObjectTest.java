package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the value-object records. The compact
 * constructor rejects blank / oversized / control-character
 * input; the tests pin every rejection.
 */
class ValueObjectTest {

    @Test
    void locatorRejectsBlankRaw() {
        assertThrows(IllegalArgumentException.class, () -> Locator.of(""));
        assertThrows(IllegalArgumentException.class, () -> Locator.of("   "));
    }

    @Test
    void locatorRejectsOversizedRaw() {
        String huge = "a".repeat(Locator.MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> Locator.of(huge));
    }

    @Test
    void locatorRejectsControlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Locator.of("page\u0001"));
        assertThrows(IllegalArgumentException.class, () -> Locator.of("page\u007F"));
    }

    @Test
    void locatorAcceptsStructuredForms() {
        Locator page = Locator.page("register", "12");
        assertTrue(page.hasStructuredParts());
        Locator free = Locator.of("register");
        assertFalse(free.hasStructuredParts());
    }

    @Test
    void attachmentRefRejectsBlankMediaObjectId() {
        assertThrows(IllegalArgumentException.class, () -> new AttachmentRef(
                AttachmentKind.DIGITAL_IMAGE, "", null, null, null));
    }

    @Test
    void attachmentRefAcceptsBcp47Locale() {
        AttachmentRef ref = new AttachmentRef(
                AttachmentKind.DIGITAL_IMAGE, "obj-1", "https://example.com", "caption", "en-US");
        assertEquals("en-US", ref.locale());
        assertTrue(ref.hasCanonicalUrl());
    }

    @Test
    void attachmentRefRejectsBadLocale() {
        assertThrows(IllegalArgumentException.class, () -> new AttachmentRef(
                AttachmentKind.DIGITAL_IMAGE, "obj-1", null, null, "en_US"));
    }

    @Test
    void confidenceRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> Confidence.requireInRange(-0.1));
        assertThrows(IllegalArgumentException.class, () -> Confidence.requireInRange(1.5));
        assertThrows(IllegalArgumentException.class, () -> Confidence.requireInRange(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Confidence.requireInRange(Double.POSITIVE_INFINITY));
        assertNull(Confidence.requireInRange(null));
        assertEquals(0.0, Confidence.requireInRange(0.0));
        assertEquals(1.0, Confidence.requireInRange(1.0));
    }

    @Test
    void transcriptSegmentRejectsNewline() {
        assertThrows(IllegalArgumentException.class, () -> new TranscriptSegment(
                1, "line1\nline2", null, null, null));
    }

    @Test
    void transcriptSegmentRejectsBlankText() {
        assertThrows(IllegalArgumentException.class, () -> new TranscriptSegment(
                1, "   ", null, null, null));
    }

    @Test
    void transcriptSegmentRejectsOutOfRangeLineNumber() {
        assertThrows(IllegalArgumentException.class, () -> new TranscriptSegment(
                0, "hello", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TranscriptSegment(
                TranscriptSegment.MAX_LINE_NUMBER + 1, "hello", null, null, null));
    }

    @Test
    void tenantScopedIdRejectsBlankParts() {
        assertThrows(IllegalArgumentException.class, () -> new TenantScopedId(
                "", TenantScopedId.ResourceKind.REPOSITORY, "id"));
        assertThrows(IllegalArgumentException.class, () -> new TenantScopedId(
                "tenant", TenantScopedId.ResourceKind.REPOSITORY, ""));
        assertThrows(IllegalArgumentException.class, () -> new TenantScopedId(
                "tenant", TenantScopedId.ResourceKind.REPOSITORY, "bad/id"));
    }

    @Test
    void tenantScopedIdAcceptsOpaqueId() {
        TenantScopedId id = TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.CITATION, "cit_abc-123");
        assertEquals("tenant-1", id.tenantId());
        assertEquals("cit_abc-123", id.resourceId());
    }

    @Test
    void auditAttributesRequireActorAndCorrelation() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchAuditAttributes(
                "", "corr-1", null, null));
        assertThrows(IllegalArgumentException.class, () -> new ResearchAuditAttributes(
                "actor", " ", null, null));
    }

    @Test
    void auditAttributesRejectOversizedExtras() {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ResearchAuditAttributes.MAX_EXTRAS + 1; i += 1) {
            map.put("k" + i, "v");
        }
        assertThrows(IllegalArgumentException.class, () -> new ResearchAuditAttributes(
                "actor", "corr", null, map));
    }
}
