package com.genealogy.platform.services.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.platform.libs.security.abac.AbacDecision;
import com.genealogy.platform.libs.security.abac.AbacDecisionCache;
import com.genealogy.platform.libs.security.abac.AbacObligation;
import com.genealogy.platform.libs.security.abac.AbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.AbacRequest;
import com.genealogy.platform.services.tenant.application.audit.TenantAuditPublisher;
import com.genealogy.platform.services.tenant.application.outbox.OutboxEvent;
import com.genealogy.platform.services.tenant.application.outbox.OutboxWriter;
import com.genealogy.platform.services.tenant.application.persistence.EntitlementRepository;
import com.genealogy.platform.services.tenant.application.persistence.TenantRepository;
import com.genealogy.platform.services.tenant.application.rls.TenantRlsTxInterceptor;
import com.genealogy.platform.services.tenant.domain.entitlement.Entitlement;
import com.genealogy.platform.services.tenant.domain.ids.IdGenerator;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.tenant.CalendarType;
import com.genealogy.platform.services.tenant.domain.tenant.Locale;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.Tenant;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.domain.tenant.TenantStatus;
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantCommandService}.
 *
 * <p>The repositories and outbox writer are mocked so the test
 * exercises the command service in isolation (no DB / no Spring
 * context). The clock is fixed and the id generator is deterministic.
 *
 * <p>The {@link TenantRlsTxInterceptor} is replaced by a no-op stub
 * because the unit test runs without a Spring transaction; the
 * production interceptor's {@code isActualTransactionActive} guard
 * would otherwise fire. The interceptor itself is exercised by the
 * {@code TenantCommandHappyPathIT}.
 */
class TenantCommandServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final String ACTOR = "kc-user-1111-aaaa-1111";

    private final AtomicInteger counter = new AtomicInteger();
    private final IdGenerator ids = () -> "test-" + counter.incrementAndGet() + "-zzzzz";

    private TenantRepository tenantRepo;
    private EntitlementRepository entitlementRepo;
    private OutboxWriter outboxWriter;
    private TenantAuditPublisher audit;
    private TenantRlsTxInterceptor rls;
    private TenantCommandService service;

    @BeforeEach
    void setUp() {
        tenantRepo = mock(TenantRepository.class);
        entitlementRepo = mock(EntitlementRepository.class);
        outboxWriter = mock(OutboxWriter.class);
        audit = mock(TenantAuditPublisher.class);
        rls = mock(TenantRlsTxInterceptor.class);
        // E3.4 — tests use a permissive ABAC policy so the existing
        // happy-path assertions stay green. The deny branch is
        // covered by TenantAbacEnforcerTest.
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
        TenantAbacEnforcer abac = new TenantAbacEnforcer(allowAll,
                new AbacDecisionCache());
        service = new TenantCommandService(tenantRepo, entitlementRepo,
                outboxWriter, ids, audit, rls, abac, CLOCK);
        // E3.5 ships the real trusted context; for unit tests we
        // seed it with a synthetic actor so the ABAC enforcer sees
        // a non-null subjectId on the mutation paths.
        TrustedTenantContext.set(TrustedTenantContext.of(
                "tenant-aaaa-1111",
                ACTOR,
                "admin",
                "corr-test",
                "trace-test"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TrustedTenantContext.clear();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persists tenant + entitlement + emits outbox + audits")
        void persistsAndEmits() {
            Commands.CreateTenant cmd = new Commands.CreateTenant(
                    new Slug("smith-family"),
                    new TenantDisplayName("Smith Family Tree"),
                    TenantPlan.FAMILY,
                    new Locale("en-US"),
                    new Timezone("Europe/Helsinki"),
                    CalendarType.GREGORIAN);

            Results.TenantView view = service.create(cmd, ACTOR);

            assertThat(view.slug().value()).isEqualTo("smith-family");
            assertThat(view.plan()).isEqualTo(TenantPlan.FAMILY);
            assertThat(view.status()).isEqualTo("ACTIVE");
            assertThat(view.version()).isEqualTo(1L);
            assertThat(view.etag()).isEqualTo("\"v1\"");

            verify(tenantRepo).insert(any(Tenant.class));
            verify(entitlementRepo).insert(any(Entitlement.class));
            verify(outboxWriter, times(1)).append(any(OutboxEvent.class));
            verify(audit).publish(eq("tenant.create"), eq(view.id()),
                    eq("smith-family"), any());
            verify(rls).bind();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("renames when version matches")
        void renamesOnVersionMatch() {
            Tenant existing = sampleTenant();
            when(tenantRepo.findById(existing.id())).thenReturn(Optional.of(existing));

            Commands.UpdateTenant cmd = new Commands.UpdateTenant(
                    existing.id(), 1L, new TenantDisplayName("Renamed"));
            Results.TenantView view = service.update(cmd);

            assertThat(view.displayName().value()).isEqualTo("Renamed");
            assertThat(view.version()).isEqualTo(2L);
            verify(tenantRepo).update(any(Tenant.class));
            verify(audit).publish(eq("tenant.update"), eq(existing.id()), any(), any());
        }

        @Test
        @DisplayName("rejects when expected version mismatches")
        void rejectsOnVersionMismatch() {
            Tenant existing = sampleTenant();
            when(tenantRepo.findById(existing.id())).thenReturn(Optional.of(existing));

            Commands.UpdateTenant cmd = new Commands.UpdateTenant(
                    existing.id(), 99L, new TenantDisplayName("Renamed"));

            try {
                service.update(cmd);
            } catch (TenantCommandService.OptimisticConcurrencyException e) {
                assertThat(e).hasMessageContaining("expected version 99");
            }
            verify(tenantRepo, never()).update(any(Tenant.class));
            verify(outboxWriter, never()).append(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("throws TenantNotFound when missing")
        void throwsWhenMissing() {
            TenantId missing = new TenantId("missing-aaaa-1111");
            when(tenantRepo.findById(missing)).thenReturn(Optional.empty());
            Commands.UpdateTenant cmd = new Commands.UpdateTenant(
                    missing, 1L, new TenantDisplayName("x"));
            try {
                service.update(cmd);
            } catch (TenantCommandService.TenantNotFoundException e) {
                assertThat(e).hasMessageContaining("not found");
            }
        }
    }

    @Nested
    @DisplayName("lifecycle (suspend / restore / softDelete)")
    class Lifecycle {

        @Test
        @DisplayName("suspend transitions ACTIVE -> SUSPENDED")
        void suspend() {
            Tenant existing = sampleTenant();
            when(tenantRepo.findById(existing.id())).thenReturn(Optional.of(existing));
            service.suspend(new Commands.SuspendTenant(existing.id(), 1L));
            verify(tenantRepo).update(any(Tenant.class));
            assertThat(existing.status()).isEqualTo(TenantStatus.SUSPENDED);
        }

        @Test
        @DisplayName("restore transitions SUSPENDED -> ACTIVE")
        void restore() {
            Tenant existing = sampleTenant();
            existing.suspend(CLOCK);
            when(tenantRepo.findById(existing.id())).thenReturn(Optional.of(existing));
            service.restore(new Commands.RestoreTenant(existing.id(), existing.version()));
            assertThat(existing.status()).isEqualTo(TenantStatus.ACTIVE);
        }

        @Test
        @DisplayName("softDelete transitions ACTIVE -> DELETED")
        void softDelete() {
            Tenant existing = sampleTenant();
            when(tenantRepo.findById(existing.id())).thenReturn(Optional.of(existing));
            service.softDelete(new Commands.SoftDeleteTenant(existing.id(), 1L));
            assertThat(existing.status()).isEqualTo(TenantStatus.DELETED);
        }
    }

    private Tenant sampleTenant() {
        return Tenant.create(ids,
                new Slug("smith-family"),
                new TenantDisplayName("Smith Family Tree"),
                TenantPlan.FAMILY,
                new Locale("en-US"),
                new Timezone("Europe/Helsinki"),
                CalendarType.GREGORIAN,
                CLOCK);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
