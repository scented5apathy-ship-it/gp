package com.genealogy.platform.services.tenant.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spot-check that the JSON payload builders emit a shape compatible
 * with the Avro schemas under {@code contracts/events/tenant/v1/}.
 * The E4.7 relay will swap the byte encoding to binary Avro; until
 * then the JSON shape proves the application service and the Avro
 * schema agree on field names + types.
 */
class EventPayloadsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("tenantCreated payload has the 9 fields of the Avro record")
    void tenantCreatedShape() throws Exception {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        byte[] payload = EventPayloads.encode(EventPayloads.tenantCreated(
                "tenant-aaaa-1111", "smith", "Smith",
                "FAMILY", "en-US", "Europe/Helsinki", "GREGORIAN",
                "kc-user-actor-1111", now));
        JsonNode node = mapper.readTree(payload);
        assertThat(node.get("tenantId").get("value").asText()).isEqualTo("tenant-aaaa-1111");
        assertThat(node.get("slug").asText()).isEqualTo("smith");
        assertThat(node.get("displayName").asText()).isEqualTo("Smith");
        assertThat(node.get("plan").asText()).isEqualTo("FAMILY");
        assertThat(node.get("defaultLocale").asText()).isEqualTo("en-US");
        assertThat(node.get("defaultTimezone").asText()).isEqualTo("Europe/Helsinki");
        assertThat(node.get("defaultCalendar").asText()).isEqualTo("GREGORIAN");
        assertThat(node.get("actorId").get("value").asText()).isEqualTo("kc-user-actor-1111");
        assertThat(node.get("createdAt").asText()).isEqualTo(now.toString());
    }

    @Test
    @DisplayName("membershipInvited payload has the 9 fields of the Avro record")
    void membershipInvitedShape() throws Exception {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        Instant expires = now.plusSeconds(7 * 24 * 3600);
        byte[] payload = EventPayloads.encode(EventPayloads.membershipInvited(
                "tenant-aaaa-1111", "mem-aaaa-1111", "inv-aaaa-1111",
                "alice@example.com", "MEMBER",
                "kc-user-bbbb-2222", "hash-abc", expires, now));
        JsonNode node = mapper.readTree(payload);
        assertThat(node.get("membershipId").get("value").asText()).isEqualTo("mem-aaaa-1111");
        assertThat(node.get("invitationId").get("value").asText()).isEqualTo("inv-aaaa-1111");
        assertThat(node.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(node.get("role").asText()).isEqualTo("MEMBER");
        assertThat(node.get("tokenHash").asText()).isEqualTo("hash-abc");
        assertThat(node.get("invitedByUserId").get("value").asText()).isEqualTo("kc-user-bbbb-2222");
    }

    @Test
    @DisplayName("membershipActivated payload has 6 fields")
    void membershipActivatedShape() throws Exception {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        byte[] payload = EventPayloads.encode(EventPayloads.membershipActivated(
                "tenant-aaaa-1111", "mem-aaaa-1111",
                "kc-user-actual-9999", "MEMBER",
                "kc-user-actual-9999", now));
        JsonNode node = mapper.readTree(payload);
        assertThat(node.get("userId").get("value").asText()).isEqualTo("kc-user-actual-9999");
        assertThat(node.get("role").asText()).isEqualTo("MEMBER");
        assertThat(node.get("actorId").get("value").asText()).isEqualTo("kc-user-actual-9999");
    }

    @Test
    @DisplayName("membershipRevoked payload carries previousStatus + reason")
    void membershipRevokedShape() throws Exception {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        byte[] payload = EventPayloads.encode(EventPayloads.membershipRevoked(
                "tenant-aaaa-1111", "mem-aaaa-1111",
                "kc-user-bbbb-2222", "ACTIVE",
                "left the team", "kc-user-admin-3333", now));
        JsonNode node = mapper.readTree(payload);
        assertThat(node.get("previousStatus").asText()).isEqualTo("ACTIVE");
        assertThat(node.get("reason").asText()).isEqualTo("left the team");
    }

    @Test
    @DisplayName("entitlementChanged payload carries all four quota fields")
    void entitlementChangedShape() throws Exception {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        byte[] payload = EventPayloads.encode(EventPayloads.entitlementChanged(
                "tenant-aaaa-1111", "PRO", 10, 5, 1024, 365,
                "stripe-cust-1234", "kc-user-billing-1111", now));
        JsonNode node = mapper.readTree(payload);
        assertThat(node.get("plan").asText()).isEqualTo("PRO");
        assertThat(node.get("memberLimit").asInt()).isEqualTo(10);
        assertThat(node.get("treeLimit").asInt()).isEqualTo(5);
        assertThat(node.get("storageLimitMb").asInt()).isEqualTo(1024);
        assertThat(node.get("retentionDays").asInt()).isEqualTo(365);
        assertThat(node.get("billingExternalId").asText()).isEqualTo("stripe-cust-1234");
    }
}
