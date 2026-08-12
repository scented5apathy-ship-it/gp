package com.genealogy.platform.services.research.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchPartitionKeyPolicyTest {

    @Test
    @DisplayName("partition-key class is AGGREGATE_ONLY for every research event")
    void partitionKeyClassForKnownEvents() {
        assertThat(ResearchPartitionKeyPolicy.classify(
                "gp.research.v1.CitationCreated"))
                .isEqualTo(ResearchPartitionKeyClass.AGGREGATE_ONLY);
        assertThat(ResearchPartitionKeyPolicy.classify(
                "gp.research.v1.ClaimVerified"))
                .isEqualTo(ResearchPartitionKeyClass.AGGREGATE_ONLY);
        assertThat(ResearchPartitionKeyPolicy.classify(
                "gp.research.v1.ConflictDetected"))
                .isEqualTo(ResearchPartitionKeyClass.AGGREGATE_ONLY);
    }

    @Test
    @DisplayName("unknown event type is rejected (closed-set)")
    void unknownEventRejected() {
        assertThatThrownBy(() -> ResearchPartitionKeyPolicy.classify(
                "gp.other.v1.Foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown eventType");
    }

    @Test
    @DisplayName("derive() returns aggregateId for AGGREGATE_ONLY")
    void deriveForAggregateOnly() {
        String key = ResearchPartitionKeyPolicy.derive(
                "gp.research.v1.CitationCreated",
                "tenant-a",
                "cite-123");
        assertThat(key).isEqualTo("cite-123");
    }

    @Test
    @DisplayName("schemaId() returns the Apicurio schema id for each event")
    void schemaIdForKnownEvents() {
        assertThat(ResearchPartitionKeyPolicy.schemaId("gp.research.v1.CitationCreated"))
                .isEqualTo("research/v1/citation-created.avsc");
        assertThat(ResearchPartitionKeyPolicy.schemaId("gp.research.v1.ClaimVerified"))
                .isEqualTo("research/v1/claim-verified.avsc");
        assertThat(ResearchPartitionKeyPolicy.schemaId("gp.research.v1.ConflictDetected"))
                .isEqualTo("research/v1/conflict-detected.avsc");
    }
}
