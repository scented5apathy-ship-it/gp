package com.genealogy.platform.spring.grpc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TrustedContextFieldGuardTest {

    @Test
    void acceptsEmptyContext() {
        TrustedContextFieldGuard.ContextView view1 =
                TrustedContextFieldGuard.ContextView.of(null, null, null);
        assertDoesNotThrow(() -> TrustedContextFieldGuard.enforce(view1));
        TrustedContextFieldGuard.ContextView view2 =
                TrustedContextFieldGuard.ContextView.of("", "", "");
        assertDoesNotThrow(() -> TrustedContextFieldGuard.enforce(view2));
    }

    @Test
    void rejectsClientSuppliedTenantId() {
        TrustedContextFieldGuard.ContextView view =
                TrustedContextFieldGuard.ContextView.of("tenant-1", null, null);
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> TrustedContextFieldGuard.enforce(view));
        assertSame(TrustedContextViolation.Reason.CLIENT_SUPPLIED_TENANT_ID, v.reason());
    }

    @Test
    void rejectsClientSuppliedActorId() {
        TrustedContextFieldGuard.ContextView view =
                TrustedContextFieldGuard.ContextView.of(null, "user-1", null);
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> TrustedContextFieldGuard.enforce(view));
        assertSame(TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ID, v.reason());
    }

    @Test
    void rejectsClientSuppliedActorRole() {
        TrustedContextFieldGuard.ContextView view =
                TrustedContextFieldGuard.ContextView.of(null, null, "OWNER");
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> TrustedContextFieldGuard.enforce(view));
        assertSame(TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ROLE, v.reason());
    }
}
