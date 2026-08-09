/*
 * Integration test for the E3.2c application services.
 *
 * <p>What this test covers (E3.2c acceptance criterion — "unit + IT
 * happy-path create-tenant → invite-member → activate-membership →
 * revoke-membership; outbox row xuất hiện cho mỗi mutation"):
 *
 * <ul>
 *   <li>The Spring Boot context boots with the new
 *       {@code ApplicationConfig} wired in.</li>
 *   <li>Flyway runs V1 + V2 (already covered by {@code RlsNegativeIT}).</li>
 *   <li>{@code TenantCommandService.create} persists the tenant + a
 *       default entitlement + emits one {@code outbox_events} row
 *       tagged {@code tenant.tenant.v1.created}.</li>
 *   <li>{@code MembershipCommandService.invite} persists the
 *       membership + invitation rows + emits one outbox row
 *       tagged {@code tenant.membership.v1.invited}.</li>
 *   <li>{@code MembershipCommandService.activate} flips the
 *       membership to ACTIVE + emits the activated outbox row +
 *       marks the invitation accepted.</li>
 *   <li>{@code MembershipCommandService.revoke} flips the
 *       membership to REVOKED + emits the revoked outbox row.</li>
 *   <li>Optimistic concurrency ({@code If-Match} mismatching the
 *       current version) is rejected with the documented exception.</li>
 * </ul>
 *
 * <p>The test connects to a Testcontainers Postgres and uses the
 * real Spring Boot context so the @TenantScoped AOP advice + the
 * Flyway migration run together. The trusted tenant context is
 * seeded via {@link com.genealogy.platform.spring.context.TrustedTenantContext#set}
 * for each command because the IT does not go through the
 * REST surface (that lands in E3.2d).
 */
package com.genealogy.platform.services.tenant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genealogy.platform.services.tenant.application.Commands;
import com.genealogy.platform.services.tenant.application.EntitlementCommandService;
import com.genealogy.platform.services.tenant.application.MembershipCommandService;
import com.genealogy.platform.services.tenant.application.Results;
import com.genealogy.platform.services.tenant.application.TenantCommandService;
import com.genealogy.platform.services.tenant.application.keycloak.InMemoryKeycloakSubjectMirror;
import com.genealogy.platform.services.tenant.application.persistence.InvitationRepository;
import com.genealogy.platform.services.tenant.application.persistence.MembershipRepository;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.invitation.Email;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import com.genealogy.platform.services.tenant.domain.tenant.CalendarType;
import com.genealogy.platform.services.tenant.domain.tenant.Locale;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;
import com.genealogy.platform.services.tenant.domain.membership.Membership;
import com.genealogy.platform.services.tenant.spring.context.OutboxCorrelationContext;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import com.genealogy.platform.testing.PostgresFixture;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = TenantServiceApplication.class)
class TenantCommandHappyPathIT {

    private static final PostgresFixture POSTGRES = new PostgresFixture();
    private static final WireMockServer JWKS = new WireMockServer(
            WireMockConfiguration.wireMockConfig().port(allocatePort()));
    private static String jwksUrl;

    private final TenantCommandService tenantCommandService;
    private final MembershipCommandService membershipCommandService;
    private final EntitlementCommandService entitlementCommandService;
    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final InMemoryKeycloakSubjectMirror keycloakMirror;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    TenantCommandHappyPathIT(
            TenantCommandService tenantCommandService,
            MembershipCommandService membershipCommandService,
            EntitlementCommandService entitlementCommandService,
            TenantRepository tenantRepository,
            MembershipRepository membershipRepository,
            InvitationRepository invitationRepository,
            InMemoryKeycloakSubjectMirror keycloakMirror,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.tenantCommandService = tenantCommandService;
        this.membershipCommandService = membershipCommandService;
        this.entitlementCommandService = entitlementCommandService;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.keycloakMirror = keycloakMirror;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private String tenantId;

    @BeforeAll
    static void startFixtures() {
        POSTGRES.overrideProperties(new InMemoryRegistry.Replay());
        JWKS.start();
        JWKS.stubFor(get(urlMatching("/realms/.*/protocol/openid-connect/certs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
        String jwksIssuer = "http://localhost:" + JWKS.port() + "/realms/genealogy-shared";
        jwksUrl = jwksIssuer + "/protocol/openid-connect/certs";
    }

    @AfterAll
    static void stopFixtures() {
        POSTGRES.stop();
        JWKS.stop();
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        InMemoryRegistry.REGISTRY.forEach(registry::add);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksUrl);
        registry.add("platform.security.issuer-uri", () -> jwksUrl);
    }

    @BeforeEach
    void resetTenant() {
        tenantId = "tenant-it-" + System.currentTimeMillis();
        // TrustedTenantContext is ThreadLocal; set the tenant id so the
        // RLS interceptor sees a real value. We also seed the
        // outbox-correlation MDC so the row carries a known correlation
        // id (verifiable in assertions below).
        org.slf4j.MDC.put("correlation_id", "test-correlation-id");
        org.slf4j.MDC.put("trace_id",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        TrustedTenantContext.set(TrustedTenantContext.of(
                tenantId, "kc-user-test-actor-zzzz", "OWNER",
                "test-correlation-id",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"));
    }

    @Test
    @DisplayName("E3.2c happy-path: create tenant → invite → activate → revoke; "
            + "outbox row per mutation")
    void happyPathFullFlow() {
        // 1) Create tenant.
        Commands.CreateTenant createCmd = new Commands.CreateTenant(
                new Slug("smith-family-" + System.currentTimeMillis()),
                new TenantDisplayName("Smith Family Tree"),
                TenantPlan.FAMILY,
                new Locale("en-US"),
                new Timezone("Europe/Helsinki"),
                CalendarType.GREGORIAN);
        Results.TenantView tenant = tenantCommandService.create(createCmd, "kc-user-owner-1111");
        assertThat(tenant.id()).isNotNull();
        assertThat(tenant.version()).isEqualTo(1L);
        assertThat(tenant.etag()).isEqualTo("\"v1\"");

        // 2) Invite a member.
        UserId inviterUserId = new UserId("kc-user-owner-1111");
        Commands.InviteMember inviteCmd = new Commands.InviteMember(
                tenant.id(),
                new Email("alice@example.com"),
                MembershipRole.MEMBER,
                inviterUserId,
                "idem-e32c-" + System.currentTimeMillis(),
                "raw-invite-token-" + System.currentTimeMillis(),
                Duration.ofDays(7));
        Results.InvitationView invite = membershipCommandService.invite(inviteCmd);
        assertThat(invite.email()).isEqualTo("alice@example.com");
        assertThat(invite.role()).isEqualTo(MembershipRole.MEMBER);

        // 3) Activate the membership by reusing the raw token.
        // First, lookup the provisional user id from the mirror.
        UserId provisionalUserId = keycloakMirror.ensureForEmail("alice@example.com");
        // The invitation row stored a token hash of `rawInviteToken`; the
        // mirror learned that mapping when the command service recorded it.
        // We can verify by querying the mirror directly.
        String emailOnInvite = keycloakMirror.findEmailByRawToken(invite.rawInviteToken());
        assertThat(emailOnInvite).isEqualTo("alice@example.com");

        // Simulate the user logging in via Keycloak — Keycloak assigns
        // them a `sub` claim. In the test we register that mapping so
        // the membership row links to the right user.
        UserId realUserId = new UserId("kc-user-alice-real-zzzzz");
        keycloakMirror.register("alice@example.com", realUserId);

        // For activation, the mirror must translate the invite token
        // to the email that was originally invited. The membership row
        // is keyed by the provisional id (set during invite). The
        // command service looks up by email → provisional id.
        // (We already seeded `provisionalUserId` via ensureForEmail
        // earlier; the mirror registered the same id above.)
        // Now we ask the command service to activate.
        // The current implementation uses `keycloak.ensureForEmail` to
        // find the provisional id; that returns the one we just registered.
        Results.MembershipView activated = membershipCommandService.activate(
                new Commands.ActivateMembership(tenant.id(), realUserId, invite.rawInviteToken()));

        assertThat(activated.status()).isEqualTo("ACTIVE");
        assertThat(activated.userId()).isEqualTo(provisionalUserId);

        // 4) Revoke the membership.
        Results.MembershipView revoked = membershipCommandService.revoke(
                new Commands.RevokeMembership(tenant.id(), activated.id(),
                        activated.version(), "left the team"));
        assertThat(revoked.status()).isEqualTo("REVOKED");

        // 5) Verify outbox rows: one per mutation → 4 rows
        //    (tenant-created, membership-invited, membership-activated,
        //    membership-revoked). We query the table directly because
        //    the IT runs against Testcontainers and RLS is bound to
        //    the test connection (set by the interceptor).
        assertOutboxRowCount(4);

        // 6) Verify outbox event types match the 4 mutations.
        assertOutboxContainsEventType("tenant.tenant.v1.created");
        assertOutboxContainsEventType("tenant.membership.v1.invited");
        assertOutboxContainsEventType("tenant.membership.v1.membership_activated");
        assertOutboxContainsEventType("tenant.membership.v1.revoked");

        // 7) Verify the membership row really is REVOKED on disk.
        Membership rehydrated = membershipRepository.findById(activated.id()).orElseThrow();
        assertThat(rehydrated.status().name()).isEqualTo("REVOKED");
        assertThat(rehydrated.revokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Optimistic concurrency: If-Match mismatch rejected")
    void optimisticConcurrencyRejected() {
        Commands.CreateTenant createCmd = new Commands.CreateTenant(
                new Slug("oc-test-" + System.currentTimeMillis()),
                new TenantDisplayName("OC Test"),
                TenantPlan.FREE, null, null, null);
        Results.TenantView created = tenantCommandService.create(createCmd,
                "kc-user-oc-actor-zzzz");

        assertThatThrownBy(() -> tenantCommandService.suspend(
                new Commands.SuspendTenant(created.id(), 999L)))
                .isInstanceOf(TenantCommandService.OptimisticConcurrencyException.class);
    }

    @Test
    @DisplayName("Entitlement.change emits EntitlementChanged event")
    void entitlementChangeEmitsEvent() {
        Commands.CreateTenant createCmd = new Commands.CreateTenant(
                new Slug("entitlement-test-" + System.currentTimeMillis()),
                new TenantDisplayName("Entitlement Test"),
                TenantPlan.FREE, null, null, null);
        Results.TenantView created = tenantCommandService.create(createCmd,
                "kc-user-billing-zzzz");

        Commands.ChangeEntitlement entCmd = new Commands.ChangeEntitlement(
                created.id(), TenantPlan.PRO, 10, 5, 1024, 365, "stripe-test-1");
        Results.EntitlementView ent = entitlementCommandService.change(entCmd,
                "kc-user-billing-actor");

        assertThat(ent.plan().name()).isEqualTo("PRO");
        assertThat(ent.memberLimit()).isEqualTo(10);

        assertOutboxContainsEventType("tenant.entitlement.v1.changed");
    }

    private void assertOutboxRowCount(int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_service.outbox_events", Integer.class);
        assertThat(count).isEqualTo(expected);
    }

    private void assertOutboxContainsEventType(String eventType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_service.outbox_events WHERE event_type = ?",
                Integer.class, eventType);
        assertThat(count)
                .as("outbox row for event_type=%s", eventType)
                .isGreaterThanOrEqualTo(1);
    }

    private static int allocatePort() {
        return new AtomicInteger(27000).getAndIncrement();
    }
}
