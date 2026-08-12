package com.genealogy.platform.services.research.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.research.application.Commands;
import com.genealogy.platform.services.research.application.ResearchCommandService;
import com.genealogy.platform.services.research.application.ResearchQueryService;
import com.genealogy.platform.services.research.application.Results;
import com.genealogy.platform.services.research.domain.Citation;
import com.genealogy.platform.services.research.domain.Conflict;
import com.genealogy.platform.services.research.domain.ConflictKind;
import com.genealogy.platform.services.research.domain.Hypothesis;
import com.genealogy.platform.services.research.domain.ResearchTask;
import com.genealogy.platform.services.research.domain.ResearchTaskStatus;
import com.genealogy.platform.services.research.domain.Repository;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import com.genealogy.platform.services.research.domain.Source;
import com.genealogy.platform.services.research.domain.SourceKind;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link ResearchController} + the
 * {@link ResearchExceptionHandler} that turns domain exceptions
 * into RFC 9457 {@code application/problem+json} responses.
 *
 * <p>The command + query services are mocked so the test
 * exercises only the HTTP wiring: header parsing, status code
 * mapping, problem body shape and the trusted tenant context
 * lookup. The trusted tenant context is seeded with a
 * deterministic id so the controller can resolve the tenant
 * id without going through the platform filter.
 */
class ResearchControllerTest {

    private static final String TENANT = "tenant-aaaa-1111";
    private static final Instant FIXED = Instant.parse("2026-08-10T00:00:00Z");

    private ResearchCommandService commandService;
    private ResearchQueryService queryService;
    private IdempotencyCache idempotencyCache;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(ResearchCommandService.class);
        queryService = mock(ResearchQueryService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        idempotencyCache = new IdempotencyCache(java.time.Clock.fixed(FIXED, java.time.ZoneOffset.UTC));
        ResearchController controller = new ResearchController(
                commandService, queryService, idempotencyCache, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ResearchExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        TrustedTenantContext.set(TrustedTenantContext.of(
                TENANT, "actor-pseudo-1", "EDITOR", "corr-id", "trace-id"));
    }

    @AfterEach
    void tearDown() {
        TrustedTenantContext.clear();
    }

    @Nested
    @DisplayName("POST /api/v1/repositories")
    class CreateRepository {

        @Test
        @DisplayName("creates repository, returns 201 + ETag + Location")
        void creates() throws Exception {
            Results.RepositoryView view = sampleRepository();
            when(commandService.createRepository(any(Commands.CreateRepository.class)))
                    .thenReturn(view);
            String body = "{\"name\":\"Smith Archive\",\"kind\":\"ARCHIVE\"}";
            MvcResult res = mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/repositories")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-create-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(MockMvcResultMatchers.status().isCreated())
                    .andExpect(MockMvcResultMatchers.header().exists("ETag"))
                    .andExpect(MockMvcResultMatchers.header().exists("Location"))
                    .andReturn();
            assertThat(res.getResponse().getContentAsString()).contains("\"name\":\"Smith Archive\"");
        }

        @Test
        @DisplayName("replays cached response when Idempotency-Key matches")
        void idempotentReplay() throws Exception {
            Results.RepositoryView view = sampleRepository();
            when(commandService.createRepository(any(Commands.CreateRepository.class)))
                    .thenReturn(view);
            String body = "{\"name\":\"Smith Archive\",\"kind\":\"ARCHIVE\"}";
            MvcResult first = mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/repositories")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-replay-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andReturn();
            assertThat(first.getResponse().getStatus()).isEqualTo(201);
            MvcResult replay = mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/repositories")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-replay-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(MockMvcResultMatchers.header().string(
                            "X-Idempotent-Replay", "true"))
                    .andReturn();
            assertThat(replay.getResponse().getStatus()).isEqualTo(201);
        }

        @Test
        @DisplayName("missing name returns 400 invalid-request problem")
        void missingName() throws Exception {
            mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/repositories")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-missing-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"kind\":\"ARCHIVE\"}"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }

        @Test
        @DisplayName("invalid enum constant returns 400 invalid-request problem")
        void invalidEnum() throws Exception {
            mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/repositories")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-bad-enum-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"name\":\"x\",\"kind\":\"NOT_A_KIND\"}"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/repositories/{id}")
    class GetRepository {

        @Test
        @DisplayName("returns 200 + ETag when found")
        void getOk() throws Exception {
            when(commandService.findRepository(any())).thenReturn(sampleRepository());
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/api/v1/repositories/repo-aaaa-1111")
                            .header("X-Tenant-Id", TENANT))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "ETag", "\"v1\""));
        }

        @Test
        @DisplayName("missing repository returns 404 problem")
        void notFound() throws Exception {
            when(commandService.findRepository(any())).thenThrow(
                    new ResearchCommandService.RepositoryNotFoundException(
                            "repository repo-x not found"));
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/api/v1/repositories/repo-x")
                            .header("X-Tenant-Id", TENANT))
                    .andExpect(MockMvcResultMatchers.status().isNotFound())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/research-tasks/{id}/transitions")
    class TransitionResearchTask {

        @Test
        @DisplayName("invalid transition returns 409 conflict")
        void invalidTransition() throws Exception {
            when(commandService.transitionResearchTask(any(), any())).thenThrow(
                    new ResearchCommandService.InvalidTransitionException(
                            "illegal researchTaskStatus transition: RESOLVED -> OPEN"));
            mockMvc.perform(MockMvcRequestBuilders.post(
                            "/api/v1/research-tasks/task-1/transitions")
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-task-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toStatus\":\"OPEN\"}"))
                    .andExpect(MockMvcResultMatchers.status().isConflict())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/claims/{claimId}/provenance")
    class ClaimProvenance {

        @Test
        @DisplayName("returns 200 + chain when citations exist")
        void provenance() throws Exception {
            Results.ProvenanceChainView view = new Results.ProvenanceChainView(
                    TENANT, "claim-1", List.of(new Results.ProvenanceHopView(
                            "cit-1", "src-1", "Smith Archive",
                            SourceKind.PRIMARY, "repo-1", "Smith",
                            RepositoryKind.ARCHIVE,
                            com.genealogy.platform.services.research.domain.CitationQuality.ORIGINAL,
                            Results.Disposition.SUPPORTS,
                            com.genealogy.platform.services.research.domain.Certainty.VERIFIED,
                            0.95, "p. 12", "John Smith, 1850")));
            when(queryService.traverseByClaim(any())).thenReturn(view);
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/api/v1/claims/claim-1/provenance")
                            .header("X-Tenant-Id", TENANT))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath(
                            "$.claimReference").value("claim-1"))
                    .andExpect(MockMvcResultMatchers.jsonPath(
                            "$.hops[0].citationId").value("cit-1"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/conflicts")
    class CreateConflict {

        @Test
        @DisplayName("fewer than two participants returns 400 invalid-request")
        void tooFewParticipants() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/conflicts")
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-conflict-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"summary\":\"x\",\"kind\":\"OTHER\","
                                    + "\"participants\":[{\"reference\":\"a\"}]}"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }
    }

    private static Results.RepositoryView sampleRepository() {
        TenantScopedId id = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.REPOSITORY, "repo-aaaa-1111");
        Repository repository = Repository.create(id, "Smith Archive",
                RepositoryKind.ARCHIVE, null, null, null,
                com.genealogy.platform.services.research.domain.ResearchAuditAttributes.of(
                        "actor-pseudo-1", "corr-id"));
        return new Results.RepositoryView(
                repository.id().resourceId(), repository.id().tenantId(),
                repository.name(),
                repository.kind(),
                repository.locationLabel(),
                repository.websiteUrl(),
                repository.description(),
                repository.privateHolding(),
                repository.createdAt(),
                repository.updatedAt(),
                repository.archivedAt(),
                repository.version(),
                com.genealogy.platform.services.research.application.persistence.RepositoryRepository
                        .etagFor(repository.version()),
                repository.metadata());
    }

    @SuppressWarnings("unused")
    private static Source sampleSource() {
        TenantScopedId repoId = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.REPOSITORY, "repo-1");
        TenantScopedId sourceId = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.SOURCE, "src-1");
        return Source.create(sourceId, repoId, "Birth register 1850",
                SourceKind.PRIMARY, "Parish of St Mary", null, 1850, "London",
                com.genealogy.platform.services.research.domain.Locator.of("p. 12"),
                List.of(), null,
                com.genealogy.platform.services.research.domain.ResearchAuditAttributes.of(
                        "actor-pseudo-1", "corr-id"));
    }

    @SuppressWarnings("unused")
    private static Citation sampleCitation() {
        TenantScopedId sourceId = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.SOURCE, "src-1");
        TenantScopedId citationId = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.CITATION, "cit-1");
        return Citation.create(citationId, sourceId, "claim-1", "birth",
                com.genealogy.platform.services.research.domain.Locator.of("p. 12"),
                com.genealogy.platform.services.research.domain.CitationQuality.ORIGINAL,
                Citation.Disposition.SUPPORTS,
                com.genealogy.platform.services.research.domain.Certainty.VERIFIED,
                0.95, "John Smith, 1850",
                List.of(), List.of(), List.of(),
                com.genealogy.platform.services.research.domain.ResearchAuditAttributes.of(
                        "actor-pseudo-1", "corr-id"));
    }

    @SuppressWarnings("unused")
    private static ResearchTask sampleResearchTask() {
        TenantScopedId taskId = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.RESEARCH_TASK, "task-1");
        return ResearchTask.create(taskId, "Verify date of birth", null,
                "claim-1", "claim",
                com.genealogy.platform.services.research.domain.ResearchAuditAttributes.of(
                        "actor-pseudo-1", "corr-id"));
    }

    @SuppressWarnings("unused")
    private static Hypothesis sampleHypothesis() {
        TenantScopedId hypId = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.HYPOTHESIS, "hyp-1");
        return Hypothesis.create(hypId, "John was born in 1850", "claim-1", "claim",
                com.genealogy.platform.services.research.domain.Certainty.HYPOTHESIS,
                0.7,
                com.genealogy.platform.services.research.domain.ResearchAuditAttributes.of(
                        "actor-pseudo-1", "corr-id"));
    }

    @SuppressWarnings("unused")
    private static Conflict sampleConflict() {
        TenantScopedId conflictId = TenantScopedId.of(
                TENANT, TenantScopedId.ResourceKind.CONFLICT, "conf-1");
        List<Conflict.Participant> participants = List.of(
                new Conflict.Participant("source-A", "source", null, List.of()),
                new Conflict.Participant("source-B", "source", null, List.of()));
        return Conflict.create(conflictId, "Two registers disagree on birth year",
                ConflictKind.SOURCE_DISAGREES, null, participants,
                com.genealogy.platform.services.research.domain.ResearchAuditAttributes.of(
                        "actor-pseudo-1", "corr-id"));
    }

    @SuppressWarnings("unused")
    private static ResearchTaskStatus sampleStatus() {
        return ResearchTaskStatus.OPEN;
    }
}
