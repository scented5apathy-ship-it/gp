package com.genealogy.platform.services.tenant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genealogy.platform.libs.security.abac.AbacDecision;
import com.genealogy.platform.libs.security.abac.AbacDecisionCache;
import com.genealogy.platform.libs.security.abac.AbacObligation;
import com.genealogy.platform.libs.security.abac.AbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.AbacRequest;
import com.genealogy.platform.libs.security.abac.Jurisdiction;
import com.genealogy.platform.libs.security.abac.PrivacyClass;
import com.genealogy.platform.libs.security.abac.ReasonCode;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the E3.4 ABAC overlay enforcer.
 *
 * <p>Verifies the three behaviours the task mandates:
 * <ol>
 *   <li>deny is thrown as {@link TenantAbacEnforcer.AbacDeniedException}
 *       so the REST layer can map to {@code 403 Forbidden};</li>
 *   <li>short-lived caching reduces policy evaluation count;</li>
 *   <li>{@code invalidateOnChange} clears the cache when a
 *       membership / role / consent row changes.</li>
 * </ol>
 */
class TenantAbacEnforcerTest {

    private TenantAbacEnforcer enforcer;
    private CountingEngine engine;
    private AbacDecisionCache cache;

    @BeforeEach
    void setUp() {
        cache = new AbacDecisionCache();
        engine = new CountingEngine();
        enforcer = new TenantAbacEnforcer(engine, cache);
    }

    @Test
    @DisplayName("allow decision is returned and cached; second call does not re-evaluate")
    void allowIsCached() {
        AbacRequest request = enforcer.membershipRequest(
                "t1", "u1", "viewer", "m1", false, false, Jurisdiction.EU);

        AbacDecision first = enforcer.requireAllow(request,
                TenantAbacEnforcer.Actions.MEMBERSHIP_REVOKE);
        AbacDecision second = enforcer.requireAllow(request,
                TenantAbacEnforcer.Actions.MEMBERSHIP_REVOKE);

        assertThat(first.isAllow()).isTrue();
        assertThat(second.decisionId()).isEqualTo(first.decisionId());
        assertThat(engine.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("deny decision throws AbacDeniedException carrying the reason code")
    void denyThrows() {
        engine.nextReason = ReasonCode.CONTEXTUAL_DENY;
        AbacRequest request = enforcer.membershipRequest(
                "t1", "u1", "viewer", "m1", false, false, Jurisdiction.EU);

        assertThatThrownBy(() -> enforcer.requireAllow(request,
                TenantAbacEnforcer.Actions.MEMBERSHIP_REVOKE))
                .isInstanceOf(TenantAbacEnforcer.AbacDeniedException.class)
                .satisfies(ex -> {
                    TenantAbacEnforcer.AbacDeniedException denied =
                            (TenantAbacEnforcer.AbacDeniedException) ex;
                    assertThat(denied.reasonId()).isEqualTo("contextual_deny");
                    assertThat(denied.problemType())
                            .isEqualTo("/problems/abac/contextual_deny");
                    assertThat(denied.problemExtensions())
                            .containsKey("decisionId");
                });
    }

    @Test
    @DisplayName("invalidateOnChange clears the cache for a single resource")
    void invalidateOnChange() {
        AbacRequest m1 = enforcer.membershipRequest(
                "t1", "u1", "viewer", "m1", false, false, Jurisdiction.EU);
        AbacRequest m2 = enforcer.membershipRequest(
                "t1", "u1", "viewer", "m2", false, false, Jurisdiction.EU);
        enforcer.requireAllow(m1, TenantAbacEnforcer.Actions.MEMBERSHIP_REVOKE);
        enforcer.requireAllow(m2, TenantAbacEnforcer.Actions.MEMBERSHIP_REVOKE);
        assertThat(cache.size()).isEqualTo(2);

        int removed = enforcer.invalidateOnChange("t1", "membership", "m1");
        assertThat(removed).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalidateTenant clears every cached decision for a tenant")
    void invalidateTenant() {
        AbacRequest r1 = enforcer.membershipRequest(
                "t1", "u1", "viewer", "m1", false, false, Jurisdiction.EU);
        AbacRequest r2 = enforcer.tenantRequest(
                "t1", "u1", "admin", "t1", false, false, Jurisdiction.EU);
        AbacRequest r3 = enforcer.tenantRequest(
                "t2", "u1", "admin", "t2", false, false, Jurisdiction.EU);
        enforcer.requireAllow(r1, TenantAbacEnforcer.Actions.MEMBERSHIP_REVOKE);
        enforcer.requireAllow(r2, TenantAbacEnforcer.Actions.TENANT_UPDATE);
        enforcer.requireAllow(r3, TenantAbacEnforcer.Actions.TENANT_UPDATE);
        assertThat(cache.size()).isEqualTo(3);

        int removed = enforcer.invalidateTenant("t1");
        assertThat(removed).isEqualTo(2);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("tenantRequest and membershipRequest factories set privacy class to PRIVATE")
    void factoriesSetPrivate() {
        AbacRequest r = enforcer.membershipRequest(
                "t1", "u1", "viewer", "m1", false, false, Jurisdiction.EU);
        assertThat(r.resourcePrivacyClass()).isEqualTo(PrivacyClass.PRIVATE);
        assertThat(r.resourceType()).isEqualTo("membership");
    }

    /** Stub engine that increments a counter and optionally denies. */
    private static final class CountingEngine implements AbacPolicyEngine {

        final java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        ReasonCode nextReason;

        @Override
        public AbacDecision evaluate(AbacRequest request) {
            calls.incrementAndGet();
            if (nextReason != null) {
                return AbacDecision.deny("test-deny", nextReason);
            }
            return AbacDecision.allow("test-allow", AbacObligation.none());
        }

        @Override
        public String engineId() {
            return "counting/test";
        }
    }
}
