package com.genealogy.platform.services.research.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchWorkspaceProjectionConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Envelope-wrapped TreeVisibilityChanged payload hits rebroadcastVisibility")
    void treeVisibilityChangedEnvelope() throws Exception {
        ResearchWorkspaceProjectionServiceStub service = new ResearchWorkspaceProjectionServiceStub();
        ResearchTenantContextBinder binder = new ResearchTenantContextBinder();
        ResearchTenantContextBinderSpy spy = new ResearchTenantContextBinderSpy(binder);
        ResearchWorkspaceProjectionConsumer consumer = new ResearchWorkspaceProjectionConsumer(
                service, spy, mapper);
        String envelope = mapper.writeValueAsString(java.util.Map.of(
                "eventType", "gp.genealogy.v1.TreeVisibilityChanged",
                "tenantId", "tenant-a",
                "actorPseudoId", "actor-pseudo-1",
                "correlationId", "corr-1",
                "payload", java.util.Map.of(
                        "treeId", "tree-1",
                        "to", "PUBLIC")));
        consumer.onEnvelope(envelope);
        assertThat(service.observedTenantIds).contains("tenant-a");
        assertThat(service.observedVisibility).contains(ResearchWorkspaceProjection.Visibility.PUBLIC);
        assertThat(spy.bindCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("Envelope-wrapped PersonRedacted payload triggers applyRedactionOverlay")
    void personRedactedEnvelope() throws Exception {
        ResearchWorkspaceProjectionServiceStub service = new ResearchWorkspaceProjectionServiceStub();
        ResearchTenantContextBinder binder = new ResearchTenantContextBinder();
        ResearchTenantContextBinderSpy spy = new ResearchTenantContextBinderSpy(binder);
        ResearchWorkspaceProjectionConsumer consumer = new ResearchWorkspaceProjectionConsumer(
                service, spy, mapper);
        String envelope = mapper.writeValueAsString(java.util.Map.of(
                "eventType", "gp.genealogy.v1.PersonRedacted",
                "tenantId", "tenant-a",
                "actorPseudoId", "actor-pseudo-1",
                "correlationId", "corr-1",
                "payload", java.util.Map.of(
                        "personId", "person-1",
                        "reason", "LIVING")));
        consumer.onEnvelope(envelope);
        assertThat(service.observedReasons).contains(
                ResearchWorkspaceProjection.RedactionReason.LIVING);
        assertThat(spy.bindCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("Unknown eventType is logged + skipped, never throws")
    void unknownEventTypeSkipped() throws Exception {
        ResearchWorkspaceProjectionServiceStub service = new ResearchWorkspaceProjectionServiceStub();
        ResearchTenantContextBinder binder = new ResearchTenantContextBinder();
        ResearchTenantContextBinderSpy spy = new ResearchTenantContextBinderSpy(binder);
        ResearchWorkspaceProjectionConsumer consumer = new ResearchWorkspaceProjectionConsumer(
                service, spy, mapper);
        String envelope = mapper.writeValueAsString(java.util.Map.of(
                "eventType", "gp.unknown.v1.Other",
                "tenantId", "tenant-a"));
        consumer.onEnvelope(envelope);
        assertThat(service.observedTenantIds).isEmpty();
    }

    @Test
    @DisplayName("TreeVisibilityChanged envelope missing tenantId is rejected")
    void envelopeMissingTenantIdRejected() throws Exception {
        ResearchWorkspaceProjectionServiceStub service = new ResearchWorkspaceProjectionServiceStub();
        ResearchTenantContextBinder binder = new ResearchTenantContextBinder();
        ResearchTenantContextBinderSpy spy = new ResearchTenantContextBinderSpy(binder);
        ResearchWorkspaceProjectionConsumer consumer = new ResearchWorkspaceProjectionConsumer(
                service, spy, mapper);
        String envelope = mapper.writeValueAsString(java.util.Map.of(
                "eventType", "gp.genealogy.v1.TreeVisibilityChanged",
                "payload", java.util.Map.of("treeId", "tree-1", "to", "PUBLIC")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> consumer.onEnvelope(envelope));
    }

    @Test
    @DisplayName("Unknown visibility value is rejected with a closed-set message")
    void unknownVisibilityRejected() throws Exception {
        ResearchWorkspaceProjectionServiceStub service = new ResearchWorkspaceProjectionServiceStub();
        ResearchTenantContextBinder binder = new ResearchTenantContextBinder();
        ResearchTenantContextBinderSpy spy = new ResearchTenantContextBinderSpy(binder);
        ResearchWorkspaceProjectionConsumer consumer = new ResearchWorkspaceProjectionConsumer(
                service, spy, mapper);
        String envelope = mapper.writeValueAsString(java.util.Map.of(
                "eventType", "gp.genealogy.v1.TreeVisibilityChanged",
                "tenantId", "tenant-a",
                "payload", java.util.Map.of("treeId", "tree-1", "to", "FOOBAR")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> consumer.onEnvelope(envelope));
    }

    static final class ResearchWorkspaceProjectionServiceStub
            extends ResearchWorkspaceProjectionService {
        java.util.List<String> observedTenantIds = new java.util.ArrayList<>();
        java.util.List<ResearchWorkspaceProjection.Visibility> observedVisibility =
                new java.util.ArrayList<>();
        java.util.List<ResearchWorkspaceProjection.RedactionReason> observedReasons =
                new java.util.ArrayList<>();

        ResearchWorkspaceProjectionServiceStub() {
            super(new ResearchJdbcWorkspaceProjectionRepository.InMemory(),
                    new OutboxWriterStub(),
                    new com.genealogy.platform.services.research.application.rls
                            .ResearchRlsTxInterceptorStub(),
                    new com.genealogy.platform.spring.audit.AuditPublisher() {
                        @Override
                        public void publish(com.genealogy.platform.spring.audit.AuditEvent event) {
                            // no-op for consumer tests
                        }
                    },
                    java.time.Clock.fixed(
                            java.time.Instant.parse("2026-08-01T00:00:00Z"),
                            java.time.ZoneOffset.UTC));
        }

        @Override
        public int rebroadcastVisibility(String tenantId, String treeId,
                ResearchWorkspaceProjection.Visibility visibility, String actorPseudoId,
                String correlationId) {
            observedTenantIds.add(tenantId);
            observedVisibility.add(visibility);
            return 1;
        }

        @Override
        public int applyRedactionOverlay(String tenantId, String subjectReference,
                ResearchWorkspaceProjection.RedactionReason reason, String actorPseudoId,
                String correlationId) {
            observedTenantIds.add(tenantId);
            observedReasons.add(reason);
            return 1;
        }
    }

    static final class ResearchTenantContextBinderSpy extends ResearchTenantContextBinder {
        int bindCalls = 0;
        private final ResearchTenantContextBinder delegate;

        ResearchTenantContextBinderSpy(ResearchTenantContextBinder delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T runWith(String tenantId, String actorId, String correlationId,
                java.util.function.Supplier<T> body) {
            bindCalls += 1;
            return delegate.runWith(tenantId, actorId, correlationId, body);
        }
    }

    static final class OutboxWriterStub
            extends com.genealogy.platform.services.research.outbox.ResearchJdbcOutboxWriter {
        OutboxWriterStub() {
            super();
        }

        @Override
        public String enqueue(String aggregateId, String tenantId,
                String eventType, Object payload, String actorPseudoId,
                String correlationId, String traceId, java.time.Instant occurredAt) {
            throw new AssertionError("outbox enqueue must not be called in consumer tests");
        }
    }
}
