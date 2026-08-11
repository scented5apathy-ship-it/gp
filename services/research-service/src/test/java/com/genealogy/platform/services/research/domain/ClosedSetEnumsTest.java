package com.genealogy.platform.services.research.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the closed-set enums. Mirrors the
 * structural test that the genealogy-service applies to its
 * certainties / visibility / partner-sub-kinds enums (E4.x).
 */
class ClosedSetEnumsTest {

    @Test
    void sourceKindClosedSetIsFixed() {
        assertEquals(
                List.of("PRIMARY", "SECONDARY", "DERIVED", "ARCHIVE", "FINDING_AID", "OTHER"),
                java.util.Arrays.stream(SourceKind.values()).map(Enum::name).toList());
    }

    @Test
    void sourceKindIsAnchorMatchesSpec() {
        assertTrue(SourceKind.PRIMARY.isAnchor());
        assertTrue(SourceKind.SECONDARY.isAnchor());
        assertTrue(SourceKind.DERIVED.isAnchor());
        assertTrue(SourceKind.OTHER.isAnchor());
        assertFalse(SourceKind.ARCHIVE.isAnchor());
        assertFalse(SourceKind.FINDING_AID.isAnchor());
    }

    @Test
    void sourceKindFromWireNormalisesCase() {
        assertSame(SourceKind.PRIMARY, SourceKind.fromWire("primary"));
        assertSame(SourceKind.OTHER, SourceKind.fromWire(" OTHER "));
        assertThrows(IllegalArgumentException.class, () -> SourceKind.fromWire(null));
        assertThrows(IllegalArgumentException.class, () -> SourceKind.fromWire("nope"));
    }

    @Test
    void citationQualityClosedSetIsFixed() {
        assertEquals(
                List.of("ORIGINAL", "TRANSCRIPT", "ABSTRACT", "IMAGE", "COPY", "UNKNOWN"),
                java.util.Arrays.stream(CitationQuality.values()).map(Enum::name).toList());
    }

    @Test
    void researchTaskStatusTerminalSetIsFixed() {
        assertTrue(ResearchTaskStatus.RESOLVED.isTerminal());
        assertTrue(ResearchTaskStatus.ABANDONED.isTerminal());
        assertFalse(ResearchTaskStatus.OPEN.isTerminal());
        assertFalse(ResearchTaskStatus.IN_PROGRESS.isTerminal());
        assertFalse(ResearchTaskStatus.BLOCKED.isTerminal());
    }

    @Test
    void hypothesisStatusTerminalSetIsFixed() {
        assertTrue(HypothesisStatus.REFUTED.isTerminal());
        assertTrue(HypothesisStatus.SUPERSEDED.isTerminal());
        assertFalse(HypothesisStatus.DRAFT.isTerminal());
        assertFalse(HypothesisStatus.ACTIVE.isTerminal());
        assertFalse(HypothesisStatus.CORROBORATED.isTerminal());
    }

    @Test
    void certaintiesPublishableMatchesSpec() {
        assertTrue(Certainty.ASSERTED.isPublishable());
        assertTrue(Certainty.VERIFIED.isPublishable());
        assertFalse(Certainty.HYPOTHESIS.isPublishable());
        assertFalse(Certainty.DISPUTED.isPublishable());
    }

    @Test
    void citationDispositionClosedSetIsFixed() {
        assertEquals(
                List.of("SUPPORTS", "REFUTES", "MENTIONS", "UNCERTAIN"),
                java.util.Arrays.stream(Citation.Disposition.values()).map(Enum::name).toList());
    }
}
