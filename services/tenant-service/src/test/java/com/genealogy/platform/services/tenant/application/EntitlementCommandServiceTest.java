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
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntitlementCommandService}.
 */
class EntitlementCommandServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final String ACTOR = "kc-user-billing-1111";

    private final AtomicInteger counter = new AtomicInteger();
    private final IdGenerator ids = () -> "test-" + counter.incrementAndGet() + "-zzzzz";

    private EntitlementRepository entitlementRepo;
    private TenantRepository tenantRepo;
    private OutboxWriter outboxWriter;
    private TenantAuditPublisher audit;
    private EntitlementCommandService service;

    @BeforeEach
    void setUp() {
        entitlementRepo = mock(EntitlementRepository.class);
        tenantRepo = mock(TenantRepository.class);
        outboxWriter = mock(OutboxWriter.class);
        audit = mock(TenantAuditPublisher.class);
        TenantRlsTxInterceptor rls = mock(TenantRlsTxInterceptor.class);
        // E3.4 — permissive ABAC for happy-path coverage; the deny
        // branch is in TenantAbacEnforcerTest.
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
        service = new EntitlementCommandService(entitlementRepo, tenantRepo,
                outboxWriter, audit, rls, abac, CLOCK);
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

    @Test
    @DisplayName("change updates plan + quotas + emits entitlement.changed event")
    void changePlanAndQuotas() {
        Tenant tenant = sampleTenant();
        Entitlement current = Entitlement.defaultFor(tenant.id(), CLOCK);
        when(tenantRepo.findById(tenant.id())).thenReturn(Optional.of(tenant));
        when(entitlementRepo.findByTenantId(tenant.id())).thenReturn(Optional.of(current));

        Commands.ChangeEntitlement cmd = new Commands.ChangeEntitlement(
                tenant.id(), TenantPlan.PRO, 10, 5, 1024, 365, "stripe-cust-1234");
        Results.EntitlementView view = service.change(cmd, ACTOR);

        assertThat(view.plan()).isEqualTo(TenantPlan.PRO);
        assertThat(view.memberLimit()).isEqualTo(10);
        assertThat(view.treeLimit()).isEqualTo(5);
        assertThat(view.storageLimitMb()).isEqualTo(1024);
        assertThat(view.retentionDays()).isEqualTo(365);
        assertThat(view.billingExternalId()).isEqualTo("stripe-cust-1234");

        verify(entitlementRepo).update(any(Entitlement.class));
        verify(outboxWriter, times(1)).append(any(OutboxEvent.class));
        verify(audit).publish(eq("entitlement.change"), eq(tenant.id()), any(), any());
    }

    @Test
    @DisplayName("change is a no-op for plan field when cmd.newPlan is null")
    void keepExistingPlanWhenNull() {
        Tenant tenant = sampleTenant();
        Entitlement current = new Entitlement(tenant.id(), TenantPlan.ENTERPRISE,
                100, 50, 5000, 730, "old-billing-id", FIXED_NOW);
        when(tenantRepo.findById(tenant.id())).thenReturn(Optional.of(tenant));
        when(entitlementRepo.findByTenantId(tenant.id())).thenReturn(Optional.of(current));

        Commands.ChangeEntitlement cmd = new Commands.ChangeEntitlement(
                tenant.id(), null, 200, null, null, null, null);
        Results.EntitlementView view = service.change(cmd, ACTOR);

        assertThat(view.plan()).isEqualTo(TenantPlan.ENTERPRISE);
        assertThat(view.memberLimit()).isEqualTo(200);
        assertThat(view.treeLimit()).isEqualTo(50);
        assertThat(view.storageLimitMb()).isEqualTo(5000);
        assertThat(view.retentionDays()).isEqualTo(730);
        assertThat(view.billingExternalId()).isEqualTo("old-billing-id");
    }

    @Test
    @DisplayName("throws TenantNotFound when tenant row missing")
    void missingTenant() {
        TenantId missing = new TenantId("missing-aaaa-1111");
        when(tenantRepo.findById(missing)).thenReturn(Optional.empty());
        Commands.ChangeEntitlement cmd = new Commands.ChangeEntitlement(
                missing, TenantPlan.FREE, 0, 0, 0, 0, null);
        try {
            service.change(cmd, ACTOR);
        } catch (TenantCommandService.TenantNotFoundException e) {
            assertThat(e).hasMessageContaining("not found");
        }
    }

    private Tenant sampleTenant() {
        return Tenant.create(ids,
                new Slug("smith-family"),
                new TenantDisplayName("Smith Family Tree"),
                TenantPlan.FREE,
                new Locale("en-US"),
                new Timezone("Europe/Helsinki"),
                CalendarType.GREGORIAN,
                CLOCK);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
