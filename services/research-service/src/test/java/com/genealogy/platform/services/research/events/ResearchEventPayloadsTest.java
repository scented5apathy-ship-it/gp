package com.genealogy.platform.services.research.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.genealogy.platform.services.research.events.ResearchEventPayloads;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchEventPayloadsTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("CitationCreated serialises to JSON without leaking actor identity")
    void citationCreatedSerialisesClean() throws Exception {
        ResearchEventPayloads.CitationCreatedEvent event = new ResearchEventPayloads.CitationCreatedEvent(
                "cite-123",
                "tenant-a",
                "src-9",
                "claim-42",
                "BIRTH",
                "PRIMARY",
                "SUPPORTING",
                "CERTAIN",
                0.95,
                "actor-pseudo-1",
                "corr-42",
                Instant.parse("2026-08-01T00:00:00Z"));
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"citationId\":\"cite-123\"");
        assertThat(json).contains("\"tenantId\":\"tenant-a\"");
        assertThat(json).contains("\"actorPseudoId\":\"actor-pseudo-1\"");
        assertThat(json).doesNotContain("dnaRaw");
        assertThat(json).doesNotContain("rawEmail");
        assertThat(json).doesNotContain("rawSubjectId");
    }

    @Test
    @DisplayName("ClaimVerified carries the verifying citation + tenant pseudonym only")
    void claimVerifiedCarriesOpaqueIds() throws Exception {
        ResearchEventPayloads.ClaimVerifiedEvent event = new ResearchEventPayloads.ClaimVerifiedEvent(
                "claim-42",
                "tenant-a",
                "cite-123",
                Instant.parse("2026-08-01T00:00:00Z"),
                "actor-pseudo-1",
                "corr-42");
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"claimReference\":\"claim-42\"");
        assertThat(json).contains("\"verifyingCitationId\":\"cite-123\"");
        assertThat(json).contains("\"tenantId\":\"tenant-a\"");
        assertThat(json).doesNotContain("dnaRaw");
        assertThat(json).doesNotContain("rawEmail");
    }

    @Test
    @DisplayName("ConflictDetected carries kind + participant count closed-set")
    void conflictDetectedClosedSet() throws Exception {
        ResearchEventPayloads.ConflictDetectedEvent event = new ResearchEventPayloads.ConflictDetectedEvent(
                UUID.randomUUID().toString(),
                "tenant-a",
                "FACTUAL",
                2,
                "summary",
                "actor-pseudo-1",
                "corr-42",
                Instant.parse("2026-08-01T00:00:00Z"));
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"kind\":\"FACTUAL\"");
        assertThat(json).contains("\"participantCount\":2");
        assertThat(json).doesNotContain("dnaRaw");
    }
}
