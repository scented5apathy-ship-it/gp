package com.genealogy.platform.services.tenant.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared JSON serialisation helpers for the five event payload
 * schemas under {@code contracts/events/tenant/v1/}. The shape
 * mirrors the Avro field names so the E4.7 relay can substitute
 * the Avro encoder without touching the writer code.
 *
 * <p>Why JSON in E3.2c instead of binary Avro:
 * <ul>
 *   <li>No new runtime dependency in tenant-service (jOOQ +
 *       Flyway + Spring Boot starter only).</li>
 *   <li>The {@code payload} column is {@code BYTEA}; the byte
 *       encoding is opaque to the database.</li>
 *   <li>The relay upgrades to binary Avro without changing the
 *       writer, the outbox table or the schema id reference.</li>
 * </ul>
 */
public final class EventPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static final String SCHEMA_TENANT_CREATED =
            "com.genealogy.platform.events.tenant.v1.TenantCreated";
    public static final String SCHEMA_MEMBERSHIP_INVITED =
            "com.genealogy.platform.events.tenant.v1.MembershipInvited";
    public static final String SCHEMA_MEMBERSHIP_ACTIVATED =
            "com.genealogy.platform.events.tenant.v1.MembershipActivated";
    public static final String SCHEMA_MEMBERSHIP_REVOKED =
            "com.genealogy.platform.events.tenant.v1.MembershipRevoked";
    public static final String SCHEMA_ENTITLEMENT_CHANGED =
            "com.genealogy.platform.events.tenant.v1.EntitlementChanged";

    public static final String EVENT_TYPE_TENANT_CREATED =
            "tenant.tenant.v1.created";
    public static final String EVENT_TYPE_MEMBERSHIP_INVITED =
            "tenant.membership.v1.invited";
    public static final String EVENT_TYPE_MEMBERSHIP_ACTIVATED =
            "tenant.membership.v1.membership_activated";
    public static final String EVENT_TYPE_MEMBERSHIP_REVOKED =
            "tenant.membership.v1.revoked";
    public static final String EVENT_TYPE_ENTITLEMENT_CHANGED =
            "tenant.entitlement.v1.changed";

    private EventPayloads() {
        // utility
    }

    /** Encode an arbitrary payload map as JSON UTF-8 bytes. */
    public static byte[] encode(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to encode event payload", e);
        }
    }

    public static Map<String, Object> tenantCreated(
            String tenantId,
            String slug,
            String displayName,
            String plan,
            String defaultLocale,
            String defaultTimezone,
            String defaultCalendar,
            String actorId,
            Instant createdAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", opaqueId(tenantId));
        m.put("slug", slug);
        m.put("displayName", displayName);
        m.put("plan", plan);
        m.put("defaultLocale", defaultLocale);
        m.put("defaultTimezone", defaultTimezone);
        m.put("defaultCalendar", defaultCalendar);
        m.put("actorId", opaqueId(actorId));
        m.put("createdAt", createdAt.toString());
        return m;
    }

    public static Map<String, Object> membershipInvited(
            String tenantId,
            String membershipId,
            String invitationId,
            String email,
            String role,
            String invitedByUserId,
            String tokenHash,
            Instant expiresAt,
            Instant invitedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", opaqueId(tenantId));
        m.put("membershipId", opaqueId(membershipId));
        m.put("invitationId", opaqueId(invitationId));
        m.put("email", email);
        m.put("role", role);
        m.put("invitedByUserId", opaqueId(invitedByUserId));
        m.put("tokenHash", tokenHash);
        m.put("expiresAt", expiresAt.toString());
        m.put("invitedAt", invitedAt.toString());
        return m;
    }

    public static Map<String, Object> membershipActivated(
            String tenantId,
            String membershipId,
            String userId,
            String role,
            String actorId,
            Instant joinedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", opaqueId(tenantId));
        m.put("membershipId", opaqueId(membershipId));
        m.put("userId", opaqueId(userId));
        m.put("role", role);
        m.put("actorId", opaqueId(actorId));
        m.put("joinedAt", joinedAt.toString());
        return m;
    }

    public static Map<String, Object> membershipRevoked(
            String tenantId,
            String membershipId,
            String userId,
            String previousStatus,
            String reason,
            String actorId,
            Instant revokedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", opaqueId(tenantId));
        m.put("membershipId", opaqueId(membershipId));
        m.put("userId", opaqueId(userId));
        m.put("previousStatus", previousStatus);
        m.put("reason", reason);
        m.put("actorId", opaqueId(actorId));
        m.put("revokedAt", revokedAt.toString());
        return m;
    }

    public static Map<String, Object> entitlementChanged(
            String tenantId,
            String plan,
            Integer memberLimit,
            Integer treeLimit,
            Integer storageLimitMb,
            Integer retentionDays,
            String billingExternalId,
            String actorId,
            Instant changedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", opaqueId(tenantId));
        m.put("plan", plan);
        m.put("memberLimit", memberLimit);
        m.put("treeLimit", treeLimit);
        m.put("storageLimitMb", storageLimitMb);
        m.put("retentionDays", retentionDays);
        m.put("billingExternalId", billingExternalId);
        m.put("actorId", opaqueId(actorId));
        m.put("changedAt", changedAt.toString());
        return m;
    }

    private static Map<String, String> opaqueId(String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("value", value);
        return m;
    }
}
