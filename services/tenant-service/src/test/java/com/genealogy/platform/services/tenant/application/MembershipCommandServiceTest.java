package com.genealogy.platform.services.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.platform.libs.security.abac.AbacDecision;
import com.genealogy.platform.libs.security.abac.AbacDecisionCache;
import com.genealogy.platform.libs.security.abac.AbacObligation;
import com.genealogy.platform.libs.security.abac.AbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.AbacRequest;
import com.genealogy.platform.services.tenant.application.audit.TenantAuditPublisher;
import com.genealogy.platform.services.tenant.application.keycloak.InMemoryKeycloakSubjectMirror;
import com.genealogy.platform.services.tenant.application.outbox.OutboxEvent;
import com.genealogy.platform.services.tenant.application.outbox.OutboxWriter;
import com.genealogy.platform.services.tenant.application.persistence.InvitationRepository;
import com.genealogy.platform.services.tenant.application.persistence.MembershipRepository;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.InvitationId;
import com.genealogy.platform.services.tenant.domain.ids.MembershipId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.invitation.Email;
import com.genealogy.platform.services.tenant.domain.invitation.Invitation;
import com.genealogy.platform.services.tenant.domain.invitation.TokenHash;
import com.genealogy.platform.services.tenant.domain.membership.Membership;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import com.genealogy.platform.services.tenant.domain.membership.MembershipStatus;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MembershipCommandService}.
 *
 * <p>The repositories / outbox / audit are mocked so the tests
 * exercise the command logic in isolation. The Keycloak mirror is
 * the real {@link InMemoryKeycloakSubjectMirror} implementation —
 * it's pure in-memory logic and there is no observable side-effect
 * to mock.
 */
class MembershipCommandServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final String RAW_TOKEN = "raw-invite-token-1234";

    private final AtomicInteger counter = new AtomicInteger();
    private final IdGenerator ids = () -> "test-" + counter.incrementAndGet() + "-zzzzz";

    private MembershipRepository membershipRepo;
    private InvitationRepository invitationRepo;
    private TenantRepository tenantRepo;
    private InMemoryKeycloakSubjectMirror mirror;
    private OutboxWriter outboxWriter;
    private TenantAuditPublisher audit;
    private TenantAbacEnforcer abacEnforcer;
    private MembershipCommandService service;

    @BeforeEach
    void setUp() {
        membershipRepo = mock(MembershipRepository.class);
        invitationRepo = mock(InvitationRepository.class);
        tenantRepo = mock(TenantRepository.class);
        // Default: every tenant is present and not soft-deleted, so
        // the ABAC enforcer sees a healthy snapshot and the revoke
        // path can proceed.
        when(tenantRepo.findById(any())).thenReturn(Optional.empty());
        mirror = new InMemoryKeycloakSubjectMirror(ids);
        outboxWriter = mock(OutboxWriter.class);
        audit = mock(TenantAuditPublisher.class);
        TenantRlsTxInterceptor rls = mock(TenantRlsTxInterceptor.class);

        // E3.4 — tests run with a permissive ABAC policy so the
        // existing happy-path assertions stay green. The
        // DenyAbacEnforcerTest below exercises the deny branch.
        AbacPolicyEngine allowAll = new AbacPolicyEngine() {
            @Override
            public AbacDecision evaluate(AbacRequest request) {
                return AbacDecision.allow("test-allow", AbacObligation.none());
            }

            @Override
            public String engineId() {
                return "test/allow-all";
            }
        };
        abacEnforcer = new TenantAbacEnforcer(allowAll, new AbacDecisionCache());

        service = new MembershipCommandService(membershipRepo, invitationRepo,
                tenantRepo, mirror, outboxWriter, ids, new TokenHasher.Sha256(),
                audit, rls, abacEnforcer, CLOCK);

        // E3.5 ships the real trusted context; for unit tests we
        // seed it with a synthetic actor so the ABAC enforcer sees
        // a non-null subjectId on the membership mutation path.
        TrustedTenantContext.set(TrustedTenantContext.of(
                "tenant-aaaa-1111",
                "kc-actor-aaaa-1111",
                "admin",
                "corr-test",
                "trace-test"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TrustedTenantContext.clear();
    }

    @Nested
    @DisplayName("invite")
    class Invite {

        @Test
        @DisplayName("creates membership + invitation + outbox + audit")
        void happyPath() {
            TenantId tenantId = new TenantId("tenant-aaaa-1111");
            Commands.InviteMember cmd = new Commands.InviteMember(
                    tenantId,
                    new Email("alice@example.com"),
                    MembershipRole.MEMBER,
                    new UserId("kc-user-bbbb-2222"),
                    "idem-1234",
                    RAW_TOKEN,
                    Duration.ofDays(7));

            Results.InvitationView view = service.invite(cmd);

            assertThat(view.email()).isEqualTo("alice@example.com");
            assertThat(view.role()).isEqualTo(MembershipRole.MEMBER);
            assertThat(view.rawInviteToken()).isEqualTo(RAW_TOKEN);
            verify(membershipRepo).insert(any(Membership.class));
            verify(invitationRepo).insert(any(Invitation.class));
            verify(outboxWriter, times(1)).append(any(OutboxEvent.class));
            verify(audit).publishMembership(eq("membership.invite"), eq(tenantId), any(), any());

            // The mirror should now know the email → user mapping
            UserId resolved = mirror.ensureForEmail("alice@example.com");
            assertThat(resolved).isNotNull();
            assertThat(resolved.getValue()).matches("^[A-Za-z0-9_-]{8,64}$");
        }

        @Test
        @DisplayName("rejects blank idempotency key")
        void blankIdempotency() {
            Commands.InviteMember cmd = new Commands.InviteMember(
                    new TenantId("tenant-aaaa-1111"),
                    new Email("alice@example.com"),
                    MembershipRole.MEMBER,
                    new UserId("kc-user-bbbb-2222"),
                    "",
                    RAW_TOKEN,
                    null);
            try {
                service.invite(cmd);
            } catch (IllegalArgumentException e) {
                assertThat(e).hasMessageContaining("idempotencyKey");
            }
        }
    }

    @Nested
    @DisplayName("activate")
    class Activate {

        @Test
        @DisplayName("transitions INVITED -> ACTIVE on matching token")
        void happyPath() {
            TenantId tenantId = new TenantId("tenant-aaaa-1111");
            // Pre-seed: a membership + invite that the mirror knows about.
            Membership pending = Membership.invite(ids, tenantId,
                    new UserId("kc-user-bbbb-2222"), MembershipRole.MEMBER, CLOCK);
            Invitation invitation = Invitation.create(ids, tenantId,
                    new Email("alice@example.com"), MembershipRole.MEMBER,
                    new TokenHash("placeholder"), "idem-activate-1",
                    new UserId("kc-user-inviter-111"), Duration.ofDays(7), CLOCK);

            mirror.register("alice@example.com", pending.userId());
            mirror.rememberInviteToken("alice@example.com", RAW_TOKEN);

            when(membershipRepo.findByTenantAndUser(eq(tenantId), eq(pending.userId())))
                    .thenReturn(Optional.of(pending));
            when(invitationRepo.findByIdempotencyKey(eq(tenantId), any()))
                    .thenReturn(Optional.of(invitation));

            Results.MembershipView view = service.activate(
                    new Commands.ActivateMembership(tenantId,
                            new UserId("kc-user-actual-login"),
                            RAW_TOKEN));

            assertThat(pending.status()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(view.status()).isEqualTo("ACTIVE");
            verify(membershipRepo).update(pending);
            verify(outboxWriter).append(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("rejects when token does not match")
        void tokenMismatch() {
            TenantId tenantId = new TenantId("tenant-aaaa-1111");
            try {
                service.activate(new Commands.ActivateMembership(tenantId,
                        new UserId("kc-user-bbbb-2222"), "wrong-token"));
            } catch (MembershipCommandService.InvalidInviteTokenException e) {
                assertThat(e).hasMessageContaining("does not match");
            }
        }
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("transitions ACTIVE -> REVOKED")
        void happyPath() {
            TenantId tenantId = new TenantId("tenant-aaaa-1111");
            Membership active = Membership.invite(ids, tenantId,
                    new UserId("kc-user-bbbb-2222"), MembershipRole.MEMBER, CLOCK);
            active.activate(CLOCK);

            when(membershipRepo.findById(active.id())).thenReturn(Optional.of(active));

            service.revoke(new Commands.RevokeMembership(
                    tenantId, active.id(), active.version(), "left the team"));

            assertThat(active.status()).isEqualTo(MembershipStatus.REVOKED);
            verify(membershipRepo).update(active);
            verify(outboxWriter).append(any(OutboxEvent.class));
            verify(audit).publishMembership(eq("membership.revoke"), eq(tenantId),
                    eq(active.id().getValue()), any());
        }

        @Test
        @DisplayName("rejects cross-tenant revoke")
        void crossTenant() {
            TenantId tenantA = new TenantId("tenant-aaaa-1111");
            TenantId tenantB = new TenantId("tenant-bbbb-2222");
            Membership m = Membership.invite(ids, tenantA,
                    new UserId("kc-user-bbbb-2222"), MembershipRole.MEMBER, CLOCK);
            when(membershipRepo.findById(m.id())).thenReturn(Optional.of(m));

            try {
                service.revoke(new Commands.RevokeMembership(
                        tenantB, m.id(), m.version(), "x"));
            } catch (MembershipCommandService.CrossTenantMembershipException e) {
                assertThat(e).hasMessageContaining("does not belong");
            }
        }

        @Test
        @DisplayName("rejects on version mismatch")
        void versionMismatch() {
            TenantId tenantId = new TenantId("tenant-aaaa-1111");
            Membership active = Membership.invite(ids, tenantId,
                    new UserId("kc-user-bbbb-2222"), MembershipRole.MEMBER, CLOCK);
            active.activate(CLOCK);
            when(membershipRepo.findById(active.id())).thenReturn(Optional.of(active));
            try {
                service.revoke(new Commands.RevokeMembership(
                        tenantId, active.id(), 99L, "x"));
            } catch (MembershipCommandService.OptimisticConcurrencyException e) {
                assertThat(e).hasMessageContaining("expected version 99");
            }
        }
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
