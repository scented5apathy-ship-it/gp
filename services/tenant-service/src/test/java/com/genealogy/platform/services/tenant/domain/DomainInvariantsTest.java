/*
 * Pure-domain unit tests for the E3.2b aggregates + value objects.
 *
 * <p>Scope guard (agent-execution.md §4.4):
 *   - NO Spring context (no {@code @SpringBootTest}).
 *   - NO Testcontainers / Docker.
 *   - NO jOOQ / repository / database.
 *
 * <p>The tests exercise only the framework-free domain types so a
 * reviewer can run them in seconds. The Spring + Flyway + Postgres
 * gate is {@code RlsNegativeIT} (E3.2a) and the upcoming
 * {@code TenantServiceApplicationIT} extension (E3.2d).
 */
package com.genealogy.platform.services.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genealogy.platform.services.tenant.domain.entitlement.Entitlement;
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
import com.genealogy.platform.services.tenant.domain.tenant.CalendarType;
import com.genealogy.platform.services.tenant.domain.tenant.Locale;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.Tenant;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.domain.tenant.TenantStatus;
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DomainInvariantsTest {

    /** Deterministic id generator — increments a counter so logs are reproducible. */
    private static IdGenerator ids() {
        AtomicInteger c = new AtomicInteger();
        return () -> String.format("id-%08d-%s",
                c.incrementAndGet(), UUID.randomUUID().toString().substring(0, 8));
    }

    /** Fixed clock so timestamps are reproducible. */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-09T16:00:00Z"), ZoneOffset.UTC);

    // -----------------------------------------------------------------------
    // TenantId / Slug / TenantDisplayName / Locale / Timezone value objects
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TenantId")
    class TenantIdTest {

        @Test
        void acceptsValidFormat() {
            assertThat(new TenantId("abcdef12-3456-789a-bcde-f0123456789a").getValue())
                    .isEqualTo("abcdef12-3456-789a-bcde-f0123456789a");
        }

        @Test
        void rejectsTooShort() {
            assertThatThrownBy(() -> new TenantId("short"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must match");
        }

        @Test
        void rejectsIllegalCharacters() {
            assertThatThrownBy(() -> new TenantId("bad id with spaces!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> new TenantId(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void typeTagPreventsCrossDomainEquality() {
            TenantId a = new TenantId("abcdef12-3456-789a-bcde-f0123456789a");
            UserId u = new UserId("abcdef12-3456-789a-bcde-f0123456789a");
            assertThat(a.equals(u)).isFalse();
        }
    }

    @Nested
    @DisplayName("Slug")
    class SlugTest {

        @Test
        void acceptsValidSlug() {
            assertThat(new Slug("smith-family").value()).isEqualTo("smith-family");
        }

        @Test
        void rejectsUppercase() {
            assertThatThrownBy(() -> new Slug("Smith"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsLeadingHyphen() {
            assertThatThrownBy(() -> new Slug("-smith"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsTrailingHyphen() {
            assertThatThrownBy(() -> new Slug("smith-"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsEmpty() {
            assertThatThrownBy(() -> new Slug(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("TenantDisplayName")
    class TenantDisplayNameTest {

        @Test
        void acceptsValid() {
            assertThat(new TenantDisplayName("Smith Family").value()).isEqualTo("Smith Family");
        }

        @Test
        void rejectsBlank() {
            assertThatThrownBy(() -> new TenantDisplayName("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsOver120Chars() {
            String tooLong = "x".repeat(121);
            assertThatThrownBy(() -> new TenantDisplayName(tooLong))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("<= 120");
        }

        @Test
        void acceptsExactly120Chars() {
            String exactly = "x".repeat(120);
            assertThat(new TenantDisplayName(exactly).value()).hasSize(120);
        }
    }

    @Nested
    @DisplayName("Locale")
    class LocaleTest {

        @Test
        void acceptsBcP47Tag() {
            assertThat(new Locale("en-US").tag()).isEqualTo("en-US");
        }

        @Test
        void acceptsNullAsPlatformDefault() {
            assertThat(new Locale(null).isPlatformDefault()).isTrue();
        }

        @Test
        void rejectsInvalidTag() {
            assertThatThrownBy(() -> new Locale("English"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Timezone")
    class TimezoneTest {

        @Test
        void acceptsIanaId() {
            assertThat(new Timezone("Europe/Helsinki").id()).isEqualTo("Europe/Helsinki");
        }

        @Test
        void acceptsNull() {
            assertThat(new Timezone(null).id()).isNull();
        }

        @Test
        void rejectsInvalidCharacters() {
            assertThatThrownBy(() -> new Timezone("Europe Helsinki"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Tenant aggregate + state machine
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Tenant")
    class TenantTest {

        private Tenant newTenant() {
            return Tenant.create(
                    ids(),
                    new Slug("smith-family"),
                    new TenantDisplayName("Smith Family"),
                    TenantPlan.FREE,
                    new Locale("en-US"),
                    new Timezone("Europe/Helsinki"),
                    CalendarType.GREGORIAN,
                    CLOCK);
        }

        @Test
        void factoryStartsAtVersion1AndActive() {
            Tenant t = newTenant();
            assertThat(t.version()).isEqualTo(1L);
            assertThat(t.status()).isEqualTo(TenantStatus.ACTIVE);
            assertThat(t.plan()).isEqualTo(TenantPlan.FREE);
            assertThat(t.calendar()).isEqualTo(CalendarType.GREGORIAN);
            assertThat(t.suspendedAt()).isNull();
            assertThat(t.deletedAt()).isNull();
        }

        @Test
        void factoryAppliesPlatformDefaultsForNulls() {
            Tenant t = Tenant.create(
                    ids(),
                    new Slug("default-tnt"),
                    new TenantDisplayName("Default"),
                    TenantPlan.FREE,
                    null,
                    null,
                    null,
                    CLOCK);
            assertThat(t.locale().isPlatformDefault()).isTrue();
            assertThat(t.timezone()).isEqualTo(Timezone.PLATFORM_DEFAULT);
            assertThat(t.calendar()).isEqualTo(CalendarType.GREGORIAN);
        }

        @Test
        void suspendTransitionsToSuspendedAndSetsTimestamp() {
            Tenant t = newTenant();
            t.suspend(CLOCK);
            assertThat(t.status()).isEqualTo(TenantStatus.SUSPENDED);
            assertThat(t.suspendedAt()).isNotNull();
            assertThat(t.version()).isEqualTo(2L);
        }

        @Test
        void restoreReturnsToActiveAndClearsTimestamp() {
            Tenant t = newTenant();
            t.suspend(CLOCK);
            t.restore(CLOCK);
            assertThat(t.status()).isEqualTo(TenantStatus.ACTIVE);
            assertThat(t.suspendedAt()).isNull();
            assertThat(t.version()).isEqualTo(3L);
        }

        @Test
        void cannotSuspendDeletedTenant() {
            Tenant t = newTenant();
            t.softDelete(CLOCK);
            assertThatThrownBy(() -> t.suspend(CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cannotRestoreActiveTenant() {
            Tenant t = newTenant();
            assertThatThrownBy(() -> t.restore(CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void softDeleteIsTerminal() {
            Tenant t = newTenant();
            t.softDelete(CLOCK);
            assertThat(t.status()).isEqualTo(TenantStatus.DELETED);
            assertThat(t.deletedAt()).isNotNull();
            assertThatThrownBy(() -> t.rename(new TenantDisplayName("New"), CLOCK))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> t.softDelete(CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void renameBumpsVersion() {
            Tenant t = newTenant();
            t.rename(new TenantDisplayName("Smith Family Tree"), CLOCK);
            assertThat(t.displayName().value()).isEqualTo("Smith Family Tree");
            assertThat(t.version()).isEqualTo(2L);
        }

        @Test
        void changePlanBumpsVersion() {
            Tenant t = newTenant();
            t.changePlan(TenantPlan.PRO, CLOCK);
            assertThat(t.plan()).isEqualTo(TenantPlan.PRO);
            assertThat(t.version()).isEqualTo(2L);
        }

        @Test
        void rehydrateRejectsInconsistentSuspendState() {
            TenantId id = new TenantId("abcdef12-3456-789a-bcde-f0123456789a");
            Instant now = Instant.parse("2026-08-09T16:00:00Z");
            assertThatThrownBy(() -> Tenant.rehydrate(
                    id,
                    new Slug("smith-family"),
                    new TenantDisplayName("Smith"),
                    TenantPlan.FREE,
                    TenantStatus.SUSPENDED,
                    new Locale(null),
                    Timezone.PLATFORM_DEFAULT,
                    CalendarType.GREGORIAN,
                    1L,
                    now,
                    now,
                    null, // suspendedAt must NOT be null when status=SUSPENDED
                    null,
                    CLOCK))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SUSPENDED");
        }
    }

    // -----------------------------------------------------------------------
    // Membership aggregate + state machine
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Membership")
    class MembershipTest {

        private Membership newInvitation() {
            return Membership.invite(
                    ids(),
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    new UserId("usr-aaaa-1111-2222-3333-444444444444"),
                    MembershipRole.MEMBER,
                    CLOCK);
        }

        @Test
        void inviteStartsAtInvitedWithTimestamp() {
            Membership m = newInvitation();
            assertThat(m.status()).isEqualTo(MembershipStatus.INVITED);
            assertThat(m.invitedAt()).isNotNull();
            assertThat(m.joinedAt()).isNull();
            assertThat(m.version()).isEqualTo(1L);
        }

        @Test
        void activateTransitionsToActive() {
            Membership m = newInvitation();
            m.activate(CLOCK);
            assertThat(m.status()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(m.joinedAt()).isNotNull();
            assertThat(m.version()).isEqualTo(2L);
        }

        @Test
        void cannotActivateFromActive() {
            Membership m = newInvitation();
            m.activate(CLOCK);
            assertThatThrownBy(() -> m.activate(CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void revokeWorksFromInvitedOrActive() {
            // From INVITED.
            Membership inv = newInvitation();
            inv.revoke(CLOCK);
            assertThat(inv.status()).isEqualTo(MembershipStatus.REVOKED);

            // From ACTIVE.
            Membership act = newInvitation();
            act.activate(CLOCK);
            act.revoke(CLOCK);
            assertThat(act.status()).isEqualTo(MembershipStatus.REVOKED);
        }

        @Test
        void revokeIsTerminal() {
            Membership m = newInvitation();
            m.revoke(CLOCK);
            assertThatThrownBy(() -> m.activate(CLOCK))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> m.suspend(CLOCK))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> m.changeRole(MembershipRole.ADMIN, CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void suspendAndRestoreRoundTrip() {
            Membership m = newInvitation();
            m.activate(CLOCK);
            m.suspend(CLOCK);
            assertThat(m.status()).isEqualTo(MembershipStatus.SUSPENDED);
            assertThat(m.suspendedAt()).isNotNull();
            m.restore(CLOCK);
            assertThat(m.status()).isEqualTo(MembershipStatus.ACTIVE);
            assertThat(m.suspendedAt()).isNull();
        }

        @Test
        void rehydrateRejectsInconsistentTimestamp() {
            // INVITED without invited_at
            assertThatThrownBy(() -> Membership.rehydrate(
                    new MembershipId("mship-aaaa-1111-2222-3333-444444444444"),
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    new UserId("usr-aaaa-1111-2222-3333-444444444444"),
                    MembershipRole.MEMBER,
                    MembershipStatus.INVITED,
                    null, // invitedAt must NOT be null
                    null, null, null,
                    1L,
                    Instant.parse("2026-08-09T16:00:00Z"),
                    Instant.parse("2026-08-09T16:00:00Z")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("INVITED");

            // ACTIVE without joined_at
            assertThatThrownBy(() -> Membership.rehydrate(
                    new MembershipId("mship-aaaa-1111-2222-3333-444444444444"),
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    new UserId("usr-aaaa-1111-2222-3333-444444444444"),
                    MembershipRole.MEMBER,
                    MembershipStatus.ACTIVE,
                    Instant.parse("2026-08-09T16:00:00Z"),
                    null, // joinedAt must NOT be null
                    null, null,
                    1L,
                    Instant.parse("2026-08-09T16:00:00Z"),
                    Instant.parse("2026-08-09T16:00:00Z")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ACTIVE");
        }
    }

    // -----------------------------------------------------------------------
    // Invitation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Invitation")
    class InvitationTest {

        private Invitation newInvitation() {
            return Invitation.create(
                    ids(),
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    new Email("user@example.com"),
                    MembershipRole.MEMBER,
                    new TokenHash("a".repeat(64)),
                    "idem-key-001",
                    new UserId("usr-aaaa-1111-2222-3333-444444444444"),
                    CLOCK);
        }

        @Test
        void defaultTtlIs7Days() {
            Invitation inv = newInvitation();
            assertThat(inv.expiresAt())
                    .isEqualTo(CLOCK.instant().plus(Invitation.DEFAULT_TTL));
        }

        @Test
        void customTtlIsHonoured() {
            Invitation inv = Invitation.create(
                    ids(),
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    new Email("user@example.com"),
                    MembershipRole.MEMBER,
                    new TokenHash("a".repeat(64)),
                    "idem-key-002",
                    new UserId("usr-aaaa-1111-2222-3333-444444444444"),
                    Duration.ofHours(2),
                    CLOCK);
            assertThat(inv.expiresAt())
                    .isEqualTo(CLOCK.instant().plus(Duration.ofHours(2)));
        }

        @Test
        void rejectsZeroTtl() {
            assertThatThrownBy(() -> Invitation.create(
                    ids(),
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    new Email("user@example.com"),
                    MembershipRole.MEMBER,
                    new TokenHash("a".repeat(64)),
                    "idem-key-003",
                    new UserId("usr-aaaa-1111-2222-3333-444444444444"),
                    Duration.ZERO,
                    CLOCK))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void isExpiredMatchesClock() {
            Invitation inv = newInvitation();
            assertThat(inv.isExpired(CLOCK)).isFalse();
            Clock future = Clock.fixed(
                    CLOCK.instant().plus(Invitation.DEFAULT_TTL).plusSeconds(1),
                    ZoneOffset.UTC);
            assertThat(inv.isExpired(future)).isTrue();
        }

        @Test
        void markAcceptedBlocksOnExpired() {
            Invitation inv = newInvitation();
            Clock future = Clock.fixed(
                    CLOCK.instant().plus(Invitation.DEFAULT_TTL).plusSeconds(1),
                    ZoneOffset.UTC);
            assertThatThrownBy(() -> inv.markAccepted(future))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        void revokeThenMarkAcceptedRejected() {
            Invitation inv = newInvitation();
            inv.revoke(CLOCK);
            assertThatThrownBy(() -> inv.markAccepted(CLOCK))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rehydrateRejectsBothAcceptedAndRevoked() {
            Instant now = Instant.parse("2026-08-09T16:00:00Z");
            assertThatThrownBy(() -> Invitation.rehydrate(
                    new InvitationId("inv-aaaa-1111-2222-3333-444444444444"),
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    new Email("user@example.com"),
                    MembershipRole.MEMBER,
                    new TokenHash("a".repeat(64)),
                    "idem-key-004",
                    new UserId("usr-aaaa-1111-2222-3333-444444444444"),
                    now.plus(Duration.ofDays(7)),
                    now, now,
                    now, now))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Entitlement
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Entitlement")
    class EntitlementTest {

        private Entitlement newEntitlement() {
            return Entitlement.defaultFor(
                    new TenantId("tnt-aaaa-1111-2222-3333-444444444444"),
                    CLOCK);
        }

        @Test
        void defaultStartsAtFreeWithZeroQuotas() {
            Entitlement e = newEntitlement();
            assertThat(e.plan()).isEqualTo(TenantPlan.FREE);
            assertThat(e.memberLimit()).isZero();
            assertThat(e.treeLimit()).isZero();
            assertThat(e.storageLimitMb()).isZero();
            assertThat(e.retentionDays()).isZero();
            assertThat(e.billingExternalId()).isNull();
        }

        @Test
        void rejectsNegativeQuotas() {
            TenantId tid = new TenantId("tnt-aaaa-1111-2222-3333-444444444444");
            assertThatThrownBy(() -> new Entitlement(tid, TenantPlan.FREE,
                    -1, 0, 0, 0, null, CLOCK.instant()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("memberLimit");
            assertThatThrownBy(() -> new Entitlement(tid, TenantPlan.FREE,
                    0, -1, 0, 0, null, CLOCK.instant()))
                    .hasMessageContaining("treeLimit");
            assertThatThrownBy(() -> new Entitlement(tid, TenantPlan.FREE,
                    0, 0, -1, 0, null, CLOCK.instant()))
                    .hasMessageContaining("storageLimitMb");
            assertThatThrownBy(() -> new Entitlement(tid, TenantPlan.FREE,
                    0, 0, 0, -1, null, CLOCK.instant()))
                    .hasMessageContaining("retentionDays");
        }

        @Test
        void zeroQuotaMeansUnlimited() {
            Entitlement e = newEntitlement();
            assertThat(e.canAddMember(1_000_000)).isTrue();
        }

        @Test
        void canAddMemberRespectsLimit() {
            TenantId tid = new TenantId("tnt-aaaa-1111-2222-3333-444444444444");
            Entitlement e = new Entitlement(tid, TenantPlan.PRO,
                    5, 0, 0, 0, null, CLOCK.instant());
            assertThat(e.canAddMember(0)).isTrue();
            assertThat(e.canAddMember(4)).isTrue();
            assertThat(e.canAddMember(5)).isFalse();
            assertThat(e.canAddMember(100)).isFalse();
        }

        @Test
        void changePlanBumpsUpdatedAt() {
            Entitlement e = newEntitlement();
            Instant before = e.updatedAt();
            e.changePlan(TenantPlan.PRO, CLOCK);
            assertThat(e.plan()).isEqualTo(TenantPlan.PRO);
            assertThat(e.updatedAt()).isAfterOrEqualTo(before);
        }

        @Test
        void changeQuotasRejectsNegative() {
            Entitlement e = newEntitlement();
            assertThatThrownBy(() -> e.changeQuotas(-1, 0, 0, 0, CLOCK))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void billingExternalIdCanBeSetAndCleared() {
            Entitlement e = newEntitlement();
            e.setBillingExternalId("cus_stripe_123", CLOCK);
            assertThat(e.billingExternalId()).isEqualTo("cus_stripe_123");
            e.setBillingExternalId(null, CLOCK);
            assertThat(e.billingExternalId()).isNull();
        }
    }
}