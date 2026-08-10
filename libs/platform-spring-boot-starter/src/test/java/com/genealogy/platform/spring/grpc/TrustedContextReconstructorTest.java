package com.genealogy.platform.spring.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import org.junit.jupiter.api.Test;

class TrustedContextReconstructorTest {

    private final TrustedContextReconstructor reconstructor = new TrustedContextReconstructor();

    @Test
    void reconstructsFromTrustedMetadataAndPeer() {
        TrustedTenantContext ctx = reconstructor.reconstruct(
                new TrustedContextReconstructor.InboundCall(
                        /* contextTenantId = */ null,
                        /* contextActorId = */ null,
                        /* contextActorRole = */ null,
                        "tenant-1",
                        "user-1",
                        "MEMBER",
                        "corr-1",
                        "trace-1",
                        "spiffe://cluster.local/ns/gp-bff/sa/web-bff"));

        assertNotNull(ctx);
        assertEquals("tenant-1", ctx.getTenantId());
        assertEquals("user-1", ctx.getActorId());
        assertEquals("MEMBER", ctx.getActorRole());
        assertEquals("corr-1", ctx.getCorrelationId());
    }

    @Test
    void rejectsClientSuppliedTenantId() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                "tenant-1", null, null,
                                "tenant-1", "user-1", "MEMBER", "corr-1", "trace-1",
                                "spiffe://cluster.local/ns/gp-bff/sa/web-bff")));
        assertSame(TrustedContextViolation.Reason.CLIENT_SUPPLIED_TENANT_ID, v.reason());
    }

    @Test
    void rejectsClientSuppliedActorId() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, "user-1", null,
                                "tenant-1", "user-1", "MEMBER", "corr-1", "trace-1",
                                "spiffe://cluster.local/ns/gp-bff/sa/web-bff")));
        assertSame(TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ID, v.reason());
    }

    @Test
    void rejectsClientSuppliedActorRole() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, null, "OWNER",
                                "tenant-1", "user-1", "MEMBER", "corr-1", "trace-1",
                                "spiffe://cluster.local/ns/gp-bff/sa/web-bff")));
        assertSame(TrustedContextViolation.Reason.CLIENT_SUPPLIED_ACTOR_ROLE, v.reason());
    }

    @Test
    void rejectsMissingSpiffePeer() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, null, null,
                                "tenant-1", "user-1", "MEMBER", "corr-1", "trace-1",
                                /* peerSpiffeId = */ null)));
        assertSame(TrustedContextViolation.Reason.MISSING_SPIFFE_PEER, v.reason());
    }

    @Test
    void rejectsUntrustedSpiffePeer() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, null, null,
                                "tenant-1", "user-1", "MEMBER", "corr-1", "trace-1",
                                "spiffe://cluster.local/ns/gp-services/sa/tenant-service")));
        assertSame(TrustedContextViolation.Reason.UNTRUSTED_SPIFFE_PEER, v.reason());
    }

    @Test
    void rejectsMissingTenantId() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, null, null,
                                /* tenantId = */ null, "user-1", "MEMBER", "corr-1", "trace-1",
                                "spiffe://cluster.local/ns/gp-bff/sa/web-bff")));
        assertSame(TrustedContextViolation.Reason.MISSING_TENANT_ID, v.reason());
    }

    @Test
    void rejectsMissingActorId() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, null, null,
                                "tenant-1", /* actorId = */ null, "MEMBER", "corr-1", "trace-1",
                                "spiffe://cluster.local/ns/gp-bff/sa/web-bff")));
        assertSame(TrustedContextViolation.Reason.MISSING_ACTOR_ID, v.reason());
    }

    @Test
    void rejectsMissingActorRole() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, null, null,
                                "tenant-1", "user-1", /* actorRole = */ null, "corr-1", "trace-1",
                                "spiffe://cluster.local/ns/gp-bff/sa/web-bff")));
        assertSame(TrustedContextViolation.Reason.MISSING_ACTOR_ROLE, v.reason());
    }

    @Test
    void rejectsMissingCorrelationId() {
        TrustedContextViolation v = assertThrows(TrustedContextViolation.class,
                () -> reconstructor.reconstruct(
                        new TrustedContextReconstructor.InboundCall(
                                null, null, null,
                                "tenant-1", "user-1", "MEMBER",
                                /* correlationId = */ null, "trace-1",
                                "spiffe://cluster.local/ns/gp-bff/sa/web-bff")));
        assertSame(TrustedContextViolation.Reason.MISSING_CORRELATION_ID, v.reason());
    }
}
