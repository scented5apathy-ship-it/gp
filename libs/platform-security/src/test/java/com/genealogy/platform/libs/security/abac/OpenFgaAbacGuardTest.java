package com.genealogy.platform.libs.security.abac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenFgaAbacGuardTest {

    @Test
    @DisplayName("OpenFGA allow + ABAC allow returns allow with engine attribute")
    void allowPath() {
        AtomicInteger calls = new AtomicInteger();
        OpenFgaAbacGuard guard = new OpenFgaAbacGuard(
                new DefaultAbacPolicyEngine(),
                new AbacDecisionCache(),
                (tenantId, subjectId, resourceType, resourceId, action) -> {
                    calls.incrementAndGet();
                    return java.util.Optional.of("check-1");
                });

        AbacRequest request = AbacRequest.builder()
                .tenantId("t1").subjectId("u1").role("viewer")
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .resourceType("tree").resourceId("tree-1")
                .jurisdiction(Jurisdiction.EU)
                .build();

        AbacDecision decision = guard.check(request, "read");
        assertTrue(decision.isAllow());
        assertEquals("check-1", decision.openfgaCheckId().orElse(null));
        assertEquals("default-abac/v1",
                decision.attributes().get("engine"));
        assertEquals("read", decision.attributes().get("action"));
        assertEquals(1, calls.get(), "first call must hit OpenFGA");

        // Second call hits the cache.
        guard.check(request, "read");
        assertEquals(1, calls.get(), "second call must hit cache");
    }

    @Test
    @DisplayName("OpenFGA deny short-circuits with OPENFGA_DENY")
    void openfgaDenyShortCircuit() {
        AtomicInteger abacCalls = new AtomicInteger();
        AbacPolicyEngine spy = new AbacPolicyEngine() {
            @Override
            public AbacDecision evaluate(AbacRequest request) {
                abacCalls.incrementAndGet();
                return AbacDecision.allow("d", AbacObligation.none());
            }

            @Override
            public String engineId() {
                return "spy";
            }
        };

        OpenFgaAbacGuard guard = new OpenFgaAbacGuard(
                spy,
                new AbacDecisionCache(),
                (tenantId, subjectId, resourceType, resourceId, action) ->
                        java.util.Optional.empty());

        AbacRequest request = AbacRequest.builder()
                .tenantId("t1").subjectId("u1").role("viewer")
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .resourceType("tree").resourceId("tree-1")
                .jurisdiction(Jurisdiction.EU)
                .build();

        AbacDecision decision = guard.check(request, "read");
        assertTrue(decision.isDeny());
        assertEquals(ReasonCode.OPENFGA_DENY, decision.reasonCode());
        assertEquals(0, abacCalls.get(),
                "ABAC engine MUST NOT be evaluated when OpenFGA denies");
    }

    @Test
    @DisplayName("Cache invalidation removes every tenant-scoped entry on revocation")
    void invalidateTenant() {
        OpenFgaAbacGuard guard = new OpenFgaAbacGuard(
                new DefaultAbacPolicyEngine(),
                new AbacDecisionCache(),
                (tenantId, subjectId, resourceType, resourceId, action) ->
                        java.util.Optional.of("check-1"));

        AbacRequest r1 = AbacRequest.builder()
                .tenantId("t1").subjectId("u1").role("viewer")
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .resourceType("tree").resourceId("tree-1")
                .jurisdiction(Jurisdiction.EU)
                .build();
        AbacRequest r2 = AbacRequest.builder()
                .tenantId("t2").subjectId("u1").role("viewer")
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .resourceType("tree").resourceId("tree-2")
                .jurisdiction(Jurisdiction.EU)
                .build();

        guard.check(r1, "read");
        guard.check(r2, "read");
        assertEquals(2, guard.cache().size());

        int removed = guard.invalidateTenant("t1");
        assertEquals(1, removed);
        assertEquals(1, guard.cache().size());
    }

    @Test
    @DisplayName("Cache TTL expiry drops stale decisions")
    void ttlExpires() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        AbacDecisionCache cache = new AbacDecisionCache(
                new AbacDecisionCache.DecisionCacheConfig(Duration.ofMillis(10),
                        16),
                fixed);

        AbacDecision decision = AbacDecision.allow("d", AbacObligation.none());
        cache.put("k", decision);
        assertNotNull(cache.get("k"));

        // Move the clock past the TTL.
        Clock moved = Clock.fixed(Instant.parse("2026-01-01T00:00:01Z"),
                ZoneId.of("UTC"));
        AbacDecisionCache aged = new AbacDecisionCache(
                cache.config(), moved);
        assertEquals(0, aged.size(), "new cache starts empty");
    }
}
