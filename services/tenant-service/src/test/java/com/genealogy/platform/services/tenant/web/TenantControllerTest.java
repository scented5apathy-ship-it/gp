package com.genealogy.platform.services.tenant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.tenant.application.Commands;
import com.genealogy.platform.services.tenant.application.EntitlementCommandService;
import com.genealogy.platform.services.tenant.application.EntitlementQueryService;
import com.genealogy.platform.services.tenant.application.MembershipCommandService;
import com.genealogy.platform.services.tenant.application.MembershipQueryService;
import com.genealogy.platform.services.tenant.application.Results;
import com.genealogy.platform.services.tenant.application.TenantCommandService;
import com.genealogy.platform.services.tenant.application.TenantQueryService;
import com.genealogy.platform.services.tenant.domain.ids.MembershipId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
 * Unit tests for {@link TenantController} + {@link MembershipController} +
 * the {@link TenantExceptionHandler} that turns domain exceptions into
 * RFC 9457 {@code application/problem+json} responses.
 *
 * <p>The command + query services are mocked so the test exercises only
 * the HTTP wiring: header parsing, status code mapping, problem body
 * shape and cross-tenant guard. The trusted tenant context is seeded
 * with a deterministic id so the {@code validateOwnership} path can be
 * exercised.
 */
class TenantControllerTest {

    private static final String TENANT = "tenant-aaaa-1111";
    private static final Instant FIXED = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    private TenantCommandService tenantCommandService;
    private TenantQueryService tenantQueryService;
    private EntitlementQueryService entitlementQueryService;
    private EntitlementCommandService entitlementCommandService;
    private MembershipCommandService membershipCommandService;
    private MembershipQueryService membershipQueryService;
    private IdempotencyCache idempotencyCache;
    private TenantController tenantController;
    private MembershipController membershipController;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tenantCommandService = mock(TenantCommandService.class);
        tenantQueryService = mock(TenantQueryService.class);
        entitlementQueryService = mock(EntitlementQueryService.class);
        entitlementCommandService = mock(EntitlementCommandService.class);
        membershipCommandService = mock(MembershipCommandService.class);
        membershipQueryService = mock(MembershipQueryService.class);
        idempotencyCache = new IdempotencyCache(CLOCK);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        tenantController = new TenantController(
                tenantCommandService, tenantQueryService,
                entitlementQueryService, entitlementCommandService,
                idempotencyCache, objectMapper);
        membershipController = new MembershipController(
                membershipCommandService, membershipQueryService,
                idempotencyCache, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(tenantController, membershipController)
                .setControllerAdvice(new TenantExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new org.springframework.http.converter.StringHttpMessageConverter())
                .build();
        TrustedTenantContext.set(TrustedTenantContext.of(
                TENANT, "kc-user-actor", "OWNER", "corr-id", "trace-id"));
    }

    @AfterEach
    void tearDown() {
        TrustedTenantContext.clear();
    }

    @Nested
    @DisplayName("POST /api/v1/tenants")
    class CreateTenant {

        @Test
        @DisplayName("creates tenant, returns 201 + ETag + Location")
        void creates() throws Exception {
            Results.TenantView view = sampleTenant();
            when(tenantCommandService.create(any(Commands.CreateTenant.class), any()))
                    .thenReturn(view);
            String body = "{\"slug\":\"smith-family\",\"displayName\":\"Smith Family\"}";
            MvcResult res = mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/tenants")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-create-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(MockMvcResultMatchers.status().isCreated())
                    .andExpect(MockMvcResultMatchers.header().exists("ETag"))
                    .andExpect(MockMvcResultMatchers.header().exists("Location"))
                    .andReturn();
            assertThat(res.getResponse().getContentAsString()).contains("\"tenantId\"");
        }

        @Test
        @DisplayName("replays cached response when Idempotency-Key matches")
        void idempotentReplay() throws Exception {
            Results.TenantView view = sampleTenant();
            when(tenantCommandService.create(any(Commands.CreateTenant.class), any()))
                    .thenReturn(view);
            String body = "{\"slug\":\"smith-family\",\"displayName\":\"Smith Family\"}";
            MvcResult first = mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/tenants")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-replay-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andReturn();
            assertThat(first.getResponse().getStatus()).isEqualTo(201);
            // Replay with the same key — service MUST NOT be called again.
            MvcResult replay = mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/tenants")
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
        @DisplayName("missing slug returns 400 invalid-request problem")
        void missingSlug() throws Exception {
            mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/tenants")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", "idem-missing-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"displayName\":\"x\"}"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tenants/{tenantId}")
    class GetTenant {

        @Test
        @DisplayName("returns 200 + ETag when found in trusted tenant")
        void getOk() throws Exception {
            Results.TenantView view = sampleTenant();
            when(tenantQueryService.findById(any(TenantId.class)))
                    .thenReturn(Optional.of(view));
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/api/v1/tenants/" + TENANT)
                            .header("X-Tenant-Id", TENANT))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "ETag", "\"v1\""));
        }

        @Test
        @DisplayName("cross-tenant: GET /tenants/{other} with X-Tenant-Id=A returns 404 problem")
        void crossTenantReturns404() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/api/v1/tenants/some-other-tenant-aaaa")
                            .header("X-Tenant-Id", TENANT))
                    .andExpect(MockMvcResultMatchers.status().isNotFound())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }

        @Test
        @DisplayName("missing tenant returns 404 tenant-not-found")
        void notFound() throws Exception {
            when(tenantQueryService.findById(any(TenantId.class)))
                    .thenReturn(Optional.empty());
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/api/v1/tenants/" + TENANT)
                            .header("X-Tenant-Id", TENANT))
                    .andExpect(MockMvcResultMatchers.status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/tenants/{tenantId}")
    class UpdateTenant {

        @Test
        @DisplayName("If-Match mismatch returns 412 invalid-etag")
        void ifMatchMismatch() throws Exception {
            doThrow(new TenantCommandService.OptimisticConcurrencyException(
                    "expected version 999 but tenant is at 1"))
                    .when(tenantCommandService).update(any());
            mockMvc.perform(MockMvcRequestBuilders.patch(
                            "/api/v1/tenants/" + TENANT)
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-patch-1")
                            .header("If-Match", "\"v999\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Renamed\"}"))
                    .andExpect(MockMvcResultMatchers.status().isPreconditionFailed())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }

        @Test
        @DisplayName("happy path: 200 + new ETag")
        void happy() throws Exception {
            Results.TenantView view = new Results.TenantView(
                    new TenantId(TENANT), new Slug("smith-family"),
                    new TenantDisplayName("Renamed"), TenantPlan.FAMILY,
                    "ACTIVE", null, null, null, 2L, "\"v2\"",
                    FIXED, FIXED, null, null);
            when(tenantCommandService.update(any())).thenReturn(view);
            mockMvc.perform(MockMvcRequestBuilders.patch(
                            "/api/v1/tenants/" + TENANT)
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-patch-2")
                            .header("If-Match", "\"v1\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Renamed\"}"))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.header().string("ETag", "\"v2\""));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/tenants/{tenantId}/memberships")
    class InviteMembership {

        @Test
        @DisplayName("returns 202 + invitation body")
        void invite() throws Exception {
            Results.InvitationView view = new Results.InvitationView(
                    new com.genealogy.platform.services.tenant.domain.ids.InvitationId(
                            "inv-aaaa-1111"),
                    new TenantId(TENANT), "alice@example.com",
                    MembershipRole.MEMBER,
                    FIXED.plusSeconds(604800), null, null, "raw-token-aaaa-bbbb");
            when(membershipCommandService.invite(any())).thenReturn(view);
            mockMvc.perform(MockMvcRequestBuilders.post(
                            "/api/v1/tenants/" + TENANT + "/memberships")
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-invite-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"alice@example.com\",\"role\":\"MEMBER\"}"))
                    .andExpect(MockMvcResultMatchers.status().isAccepted())
                    .andExpect(MockMvcResultMatchers.header().exists("Location"));
        }

        @Test
        @DisplayName("missing Idempotency-Key returns 400")
        void missingIdempotencyKey() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.post(
                            "/api/v1/tenants/" + TENANT + "/memberships")
                            .header("X-Tenant-Id", TENANT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"alice@example.com\",\"role\":\"MEMBER\"}"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest());
        }

        @Test
        @DisplayName("cross-tenant invite returns 404")
        void crossTenant() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.post(
                            "/api/v1/tenants/some-other-tenant-aaaa/memberships")
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-xb-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"bob@example.com\",\"role\":\"MEMBER\"}"))
                    .andExpect(MockMvcResultMatchers.status().isNotFound())
                    .andExpect(MockMvcResultMatchers.header().string(
                            "Content-Type", "application/problem+json"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tenants/{tenantId}/memberships")
    class ListMemberships {

        @Test
        @DisplayName("returns page with items")
        void listOk() throws Exception {
            Results.MembershipView mv = new Results.MembershipView(
                    new MembershipId("mship-aaaa-1111"),
                    new TenantId(TENANT),
                    new UserId("user-alice-aaaa"),
                    MembershipRole.MEMBER,
                    "INVITED", 1L, FIXED, null, null, null);
            when(membershipQueryService.listForCurrentTenant(
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new MembershipQueryService.MembershipPage(
                            List.of(mv), null));
            mockMvc.perform(MockMvcRequestBuilders.get(
                            "/api/v1/tenants/" + TENANT + "/memberships")
                            .header("X-Tenant-Id", TENANT))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].role")
                            .value("MEMBER"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/tenants/{tenantId}/memberships/{membershipId}")
    class RevokeMembership {

        @Test
        @DisplayName("happy path: 202 Accepted")
        void revokeOk() throws Exception {
            org.mockito.Mockito.doReturn(null)
                    .when(membershipCommandService).revoke(any());
            mockMvc.perform(MockMvcRequestBuilders.delete(
                            "/api/v1/tenants/" + TENANT + "/memberships/mship-aaaa-1111")
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-revoke-1")
                            .header("If-Match", "\"v1\"")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"left the team\"}"))
                    .andExpect(MockMvcResultMatchers.status().isAccepted());
        }

        @Test
        @DisplayName("optimistic concurrency 412")
        void revokeVersionMismatch() throws Exception {
            doThrow(new TenantCommandService.OptimisticConcurrencyException(
                    "expected version 999 but membership is at 1"))
                    .when(membershipCommandService).revoke(any());
            mockMvc.perform(MockMvcRequestBuilders.delete(
                            "/api/v1/tenants/" + TENANT + "/memberships/mship-aaaa-1111")
                            .header("X-Tenant-Id", TENANT)
                            .header("Idempotency-Key", "idem-revoke-2")
                            .header("If-Match", "\"v999\"")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(MockMvcResultMatchers.status().isPreconditionFailed());
        }
    }

    private Results.TenantView sampleTenant() {
        return new Results.TenantView(
                new TenantId(TENANT), new Slug("smith-family"),
                new TenantDisplayName("Smith Family"), TenantPlan.FAMILY,
                "ACTIVE", null, null, null, 1L, "\"v1\"",
                FIXED, FIXED, null, null);
    }
}
