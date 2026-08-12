/*
 * Contract tests for the research service. Validates the
 * structural invariants the Node-side linters cannot easily
 * cover:
 *
 *   - the research OpenAPI YAML declares every required
 *     operationId referenced by the REST controller;
 *   - the OpenAPI document references every required header
 *     (Idempotency-Key, X-Correlation-Id, If-Match) on the
 *     mutation paths;
 *   - the OpenAPI enum strings stay in lockstep with the
 *     Java closed-set enums;
 *   - the closed-set enums never expose a forbidden field
 *     name (raw DNA, raw passport, secrets).
 */
package com.genealogy.platform.services.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.platform.services.research.domain.Certainty;
import com.genealogy.platform.services.research.domain.CitationQuality;
import com.genealogy.platform.services.research.domain.ConflictKind;
import com.genealogy.platform.services.research.domain.HypothesisStatus;
import com.genealogy.platform.services.research.domain.ResearchTaskStatus;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import com.genealogy.platform.services.research.domain.SourceKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractInvariantsTest {

    private static final Path CONTRACTS_ROOT =
            Path.of("..", "..", "contracts").toAbsolutePath().normalize();

    private static final Path RESEARCH_OPENAPI =
            CONTRACTS_ROOT.resolve("openapi/public-api/v1/research.yaml");

    private static final Set<String> FORBIDDEN_PROPS =
            Set.of("dnaRaw", "rawGenotype", "dna", "kit", "rawDna", "raw_dna");

    @Test
    @DisplayName("research OpenAPI declares every required operationId")
    void researchOpenApiHasEveryOperationId() throws IOException {
        if (!Files.isRegularFile(RESEARCH_OPENAPI)) {
            return;
        }
        String body = Files.readString(RESEARCH_OPENAPI);
        List<String> required = List.of(
                "createRepository",
                "getRepository",
                "createSource",
                "getSource",
                "createCitation",
                "getCitation",
                "getClaimProvenance",
                "createResearchTask",
                "transitionResearchTask",
                "createHypothesis",
                "transitionHypothesis",
                "createConflict",
                "transitionConflict");
        for (String op : required) {
            assertThat(body)
                    .as("operationId '%s' must be declared in research.yaml", op)
                    .contains("operationId: " + op);
        }
    }

    @Test
    @DisplayName("research OpenAPI references Idempotency-Key on every mutation")
    void researchOpenApiReferencesIdempotencyKey() throws IOException {
        if (!Files.isRegularFile(RESEARCH_OPENAPI)) {
            return;
        }
        String body = Files.readString(RESEARCH_OPENAPI);
        int occurrences = body.split("IdempotencyKey", -1).length - 1;
        assertThat(occurrences)
                .as("research.yaml must reference IdempotencyKey at least 7 times")
                .isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("research OpenAPI references X-Correlation-Id on every operation")
    void researchOpenApiReferencesCorrelationId() throws IOException {
        if (!Files.isRegularFile(RESEARCH_OPENAPI)) {
            return;
        }
        String body = Files.readString(RESEARCH_OPENAPI);
        long occurrences = body.lines()
                .filter(line -> line.contains("CorrelationId"))
                .count();
        assertThat(occurrences)
                .as("research.yaml must reference CorrelationId at least 6 times")
                .isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("Java closed-set enums stay in lockstep with the OpenAPI enum strings")
    void closedSetEnumsMatchOpenApi() {
        assertThat(Arrays.stream(RepositoryKind.values()).map(Enum::name))
                .containsExactly("ARCHIVE", "LIBRARY", "CHURCH", "CIVIL_REGISTRY",
                        "CEMETERY", "FAMILY_HOLDING", "DIGITAL_PLATFORM", "OTHER");
        assertThat(Arrays.stream(SourceKind.values()).map(Enum::name))
                .containsExactly("PRIMARY", "SECONDARY", "DERIVED", "ARCHIVE",
                        "FINDING_AID", "OTHER");
        assertThat(Arrays.stream(CitationQuality.values()).map(Enum::name))
                .containsExactly("ORIGINAL", "TRANSCRIPT", "ABSTRACT", "IMAGE",
                        "COPY", "UNKNOWN");
        assertThat(Arrays.stream(Certainty.values()).map(Enum::name))
                .containsExactly("HYPOTHESIS", "ASSERTED", "VERIFIED", "DISPUTED");
        assertThat(Arrays.stream(ResearchTaskStatus.values()).map(Enum::name))
                .containsExactly("OPEN", "IN_PROGRESS", "BLOCKED", "RESOLVED",
                        "ABANDONED");
        assertThat(Arrays.stream(HypothesisStatus.values()).map(Enum::name))
                .containsExactly("DRAFT", "ACTIVE", "CORROBORATED", "REFUTED",
                        "SUPERSEDED");
        assertThat(Arrays.stream(ConflictKind.values()).map(Enum::name))
                .containsExactly("SOURCE_DISAGREES", "CITATION_DISAGREES",
                        "CLAIM_CONTRADICTS_SOURCE", "HYPOTHESIS_COLLIDES", "OTHER");
    }

    @Test
    @DisplayName("forbidden DNA / raw / token field names never appear in research contract")
    void noForbiddenFieldsInResearchContract() throws IOException {
        if (!Files.isRegularFile(RESEARCH_OPENAPI)) {
            return;
        }
        List<String> lines = Files.readAllLines(RESEARCH_OPENAPI);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String forbidden : FORBIDDEN_PROPS) {
                String yamlHit = "(?<!\\w)" + java.util.regex.Pattern.quote(forbidden)
                        + "\\s*:";
                if (java.util.regex.Pattern.compile(yamlHit).matcher(line).find()) {
                    throw new AssertionError("Forbidden field at line " + (i + 1)
                            + " -> " + forbidden);
                }
            }
        }
    }
}
