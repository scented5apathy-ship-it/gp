package com.genealogy.platform.services.research.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genealogy.platform.services.research.domain.AttachmentKind;
import com.genealogy.platform.services.research.domain.Locator;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DraftDomainMapper}. The mapper is the
 * only place where the public JSON boundary touches the closed-set
 * enums — it must translate unknown enum values into
 * {@link DraftDomainMapper.InvalidRequestException} before the
 * aggregate is built.
 */
class DraftDomainMapperTest {

    @Test
    @DisplayName("repositoryKind: known value decodes")
    void repositoryKindKnown() {
        assertThat(DraftDomainMapper.repositoryKind("ARCHIVE"))
                .isEqualTo(RepositoryKind.ARCHIVE);
        assertThat(DraftDomainMapper.repositoryKind("family_holding"))
                .isEqualTo(RepositoryKind.FAMILY_HOLDING);
    }

    @Test
    @DisplayName("repositoryKind: unknown value raises InvalidRequestException")
    void repositoryKindUnknown() {
        assertThatThrownBy(() -> DraftDomainMapper.repositoryKind("UNKNOWN_KIND"))
                .isInstanceOf(DraftDomainMapper.InvalidRequestException.class)
                .hasMessageContaining("UNKNOWN_KIND");
    }

    @Test
    @DisplayName("repositoryKind: null yields null (no default substitute)")
    void repositoryKindNull() {
        assertThat(DraftDomainMapper.repositoryKind(null)).isNull();
    }

    @Test
    @DisplayName("locator enforces non-blank raw")
    void locatorRawRequired() {
        assertThatThrownBy(() -> DraftDomainMapper.locator("", null, null, null))
                .isInstanceOf(DraftDomainMapper.InvalidRequestException.class)
                .hasMessageContaining("locator.raw");
        assertThatThrownBy(() -> DraftDomainMapper.locator(null, null, null, null))
                .isInstanceOf(DraftDomainMapper.InvalidRequestException.class);
    }

    @Test
    @DisplayName("locator keeps structured parts when supplied")
    void locatorStructuredParts() {
        Locator locator = DraftDomainMapper.locator("p. 12", "12", null, null);
        assertThat(locator.raw()).isEqualTo("p. 12");
        assertThat(locator.page()).isEqualTo("12");
        assertThat(locator.hasStructuredParts()).isTrue();
    }

    @Test
    @DisplayName("attachment: unknown attachmentKind raises InvalidRequestException")
    void attachmentKindUnknown() {
        assertThatThrownBy(() -> DraftDomainMapper.attachment(
                "UNKNOWN", "media-1", null, null, null))
                .isInstanceOf(DraftDomainMapper.InvalidRequestException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("attachment: missing mediaObjectId raises InvalidRequestException")
    void attachmentMissingMediaObjectId() {
        assertThatThrownBy(() -> DraftDomainMapper.attachment(
                AttachmentKind.OTHER.name(), null, null, null, null))
                .isInstanceOf(DraftDomainMapper.InvalidRequestException.class)
                .hasMessageContaining("mediaObjectId");
    }

    @Test
    @DisplayName("assertNoDeny: empty list is a no-op")
    void assertNoDenyEmpty() {
        DraftDomainMapper.assertNoDeny(java.util.List.of());
    }

    @Test
    @DisplayName("assertNoDeny: WARN findings are tolerated")
    void assertNoDenyWarnTolerated() {
        var warn = new com.genealogy.platform.services.research.domain.ResearchInvariants.Finding(
                com.genealogy.platform.services.research.domain.ResearchInvariants.Severity.WARN,
                com.genealogy.platform.services.research.domain.ResearchInvariants.ConflictCode
                        .TRANSCRIPT_LINE_OUT_OF_ORDER,
                "warn");
        DraftDomainMapper.assertNoDeny(java.util.List.of(warn));
    }

    @Test
    @DisplayName("assertNoDeny: DENY findings raise InvariantViolationException")
    void assertNoDenyRaise() {
        var deny = new com.genealogy.platform.services.research.domain.ResearchInvariants.Finding(
                com.genealogy.platform.services.research.domain.ResearchInvariants.Severity.DENY,
                com.genealogy.platform.services.research.domain.ResearchInvariants.ConflictCode
                        .CONFLICT_REQUIRE_MULTIPLE_PARTICIPANTS,
                "deny");
        assertThatThrownBy(() -> DraftDomainMapper.assertNoDeny(java.util.List.of(deny)))
                .isInstanceOf(DraftDomainMapper.InvariantViolationException.class)
                .hasMessageContaining("deny");
    }
}
