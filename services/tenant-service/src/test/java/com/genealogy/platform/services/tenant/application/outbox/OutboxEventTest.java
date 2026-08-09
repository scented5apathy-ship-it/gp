package com.genealogy.platform.services.tenant.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the immutable {@link OutboxEvent} record.
 */
class OutboxEventTest {

    @Test
    @DisplayName("constructor rejects null fields")
    void rejectsNulls() {
        TenantId tenantId = new TenantId("tenant-aaaa-1111");
        byte[] payload = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new OutboxEvent(
                null, "tenant", "agg-1", "ev", "schema", payload, "corr", "trace", Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("constructor rejects empty payload")
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> new OutboxEvent(
                new TenantId("tenant-aaaa-1111"), "tenant", "agg-1", "ev",
                "schema", new byte[0], "corr", "trace", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    @DisplayName("constructor copies metadata defensively")
    void defensiveMetadataCopy() {
        TenantId tenantId = new TenantId("tenant-aaaa-1111");
        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("k", "v");
        OutboxEvent event = new OutboxEvent(
                tenantId, "tenant", "agg-1", "ev", "schema",
                "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "corr", "trace", metadata);

        // Mutate the source map after construction.
        metadata.put("k2", "v2");
        assertThat(event.metadata()).containsOnlyKeys("k");
    }

    @Test
    @DisplayName("payload is non-null and non-empty")
    void payloadAccessors() {
        byte[] payload = "{\"hello\":\"world\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        OutboxEvent event = new OutboxEvent(
                new TenantId("tenant-aaaa-1111"), "tenant", "agg-1", "ev",
                EventPayloads.SCHEMA_TENANT_CREATED,
                payload, "corr", "trace", Map.of());

        assertThat(event.tenantId().getValue()).isEqualTo("tenant-aaaa-1111");
        assertThat(event.aggregateType()).isEqualTo("tenant");
        assertThat(event.schemaId()).isEqualTo(
                "com.genealogy.platform.events.tenant.v1.TenantCreated");
        assertThat(event.payload()).isEqualTo(payload);
    }
}
