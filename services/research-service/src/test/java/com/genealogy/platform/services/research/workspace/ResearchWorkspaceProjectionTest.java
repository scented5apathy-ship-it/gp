package com.genealogy.platform.services.research.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.research.workspace.ResearchWorkspaceProjection.RedactionReason;
import com.genealogy.platform.services.research.workspace.ResearchWorkspaceProjection.Visibility;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchWorkspaceProjectionTest {

    @Test
    @DisplayName("withVisibility bumps projection_version and updates updated_at")
    void visibilityUpdate() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        ResearchWorkspaceProjection row = new ResearchWorkspaceProjection(
                "tenant-a",
                "tree-1",
                "claim-1",
                "person-1",
                "PERSON",
                Visibility.PRIVATE,
                false,
                null,
                null,
                null,
                1L,
                now,
                now);
        ResearchWorkspaceProjection next = row.withVisibility(Visibility.PUBLIC, now);
        assertThat(next.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(next.projectionVersion()).isEqualTo(2L);
        assertThat(next.updatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("withRedactionOverlay flips redacted=true and stamps reason")
    void redactionOverlay() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        ResearchWorkspaceProjection row = new ResearchWorkspaceProjection(
                "tenant-a",
                "tree-1",
                "claim-1",
                "person-1",
                "PERSON",
                Visibility.PUBLIC,
                false,
                null,
                null,
                null,
                1L,
                now,
                now);
        ResearchWorkspaceProjection next = row.withRedactionOverlay(RedactionReason.LIVING, now);
        assertThat(next.redacted()).isTrue();
        assertThat(next.lastRedactionReason()).isEqualTo(RedactionReason.LIVING);
        assertThat(next.lastRedactedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("compact constructor refuses to redact without a reason")
    void constructorRequiresReasonOnRedaction() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new ResearchWorkspaceProjection(
                        "tenant-a",
                        "tree-1",
                        "claim-1",
                        "person-1",
                        "PERSON",
                        Visibility.PUBLIC,
                        true,
                        null,
                        null,
                        null,
                        1L,
                        now,
                        now));
    }

    @Test
    @DisplayName("in-memory repo: applyRedactionOverlay touches every row that references the subject")
    void inMemoryRepoOverlay() {
        ResearchJdbcWorkspaceProjectionRepository.InMemory repo =
                new ResearchJdbcWorkspaceProjectionRepository.InMemory(List.of(
                        new ResearchWorkspaceProjection(
                                "tenant-a",
                                "tree-1",
                                "claim-1",
                                "person-1",
                                "PERSON",
                                Visibility.PRIVATE,
                                false,
                                null,
                                null,
                                null,
                                1L,
                                Instant.parse("2026-08-01T00:00:00Z"),
                                Instant.parse("2026-08-01T00:00:00Z")),
                        new ResearchWorkspaceProjection(
                                "tenant-a",
                                "tree-2",
                                "claim-2",
                                "person-1",
                                "PERSON",
                                Visibility.PUBLIC,
                                false,
                                null,
                                null,
                                null,
                                1L,
                                Instant.parse("2026-08-01T00:00:00Z"),
                                Instant.parse("2026-08-01T00:00:00Z"))));
        int touched = repo.applyRedactionOverlay(
                "tenant-a",
                "person-1",
                RedactionReason.CONSENT_REVOKED,
                Instant.parse("2026-08-01T00:00:01Z"));
        assertThat(touched).isEqualTo(2);
        assertThat(repo.redactionEvents()).hasSize(2);
        assertThat(repo.find("tenant-a", "tree-1", "claim-1").orElseThrow().redacted())
                .isTrue();
    }
}
