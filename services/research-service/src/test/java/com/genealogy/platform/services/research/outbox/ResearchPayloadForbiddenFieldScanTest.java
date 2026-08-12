package com.genealogy.platform.services.research.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchPayloadForbiddenFieldScanTest {

    @Test
    @DisplayName("flags dnaRaw / rawGenotype / rawEmail")
    void flagsForbiddenKeys() {
        ResearchPayloadForbiddenFieldScan scan = new ResearchPayloadForbiddenFieldScan();
        assertThat(scan.containsForbiddenField(
                "{\"citationId\":\"cite-1\",\"dnaRaw\":\"hidden\"}")).isTrue();
        assertThat(scan.containsForbiddenField(
                "{\"rawGenotype\":\"hidden\"}")).isTrue();
        assertThat(scan.containsForbiddenField(
                "{\"rawEmail\":\"someone@example.com\"}")).isTrue();
    }

    @Test
    @DisplayName("does not flag clean payloads")
    void cleanPayloadPasses() {
        ResearchPayloadForbiddenFieldScan scan = new ResearchPayloadForbiddenFieldScan();
        assertThat(scan.containsForbiddenField(
                "{\"citationId\":\"cite-1\",\"tenantId\":\"tenant-a\"}")).isFalse();
        assertThat(scan.containsForbiddenField("not-json")).isFalse();
        assertThat(scan.containsForbiddenField("")).isFalse();
    }
}
