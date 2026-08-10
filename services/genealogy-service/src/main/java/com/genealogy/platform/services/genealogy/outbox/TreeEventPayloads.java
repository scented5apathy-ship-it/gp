package com.genealogy.platform.services.genealogy.outbox;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.platform.services.genealogy.domain.LifecycleState;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.UnlistedToken;
import com.genealogy.platform.services.genealogy.domain.Visibility;

import java.time.Instant;

/**
 * Wire-format payloads for every event the tree-service publishes
 * on the transactional outbox. The outbox publisher relays them
 * to Kafka via the Apicurio-registered schemas under
 * {@code contracts/events/genealogy/v1/}.
 *
 * <p>Each record mirrors the Avro schema field-for-field. JSON is
 * the intermediate encoding used by the outbox row; the relay
 * converts JSON → Avro at publish time (and the platform
 * {@code spring-grpc} client serialises via Apicurio).
 *
 * <p>NO raw DNA, biography, file content, access token or PII is
 * ever placed in the payload. {@code design.md} §7.3 forbids it.
 */
public final class TreeEventPayloads {

    private TreeEventPayloads() {
    }

    public static final String EVENT_TREE_CREATED = "gp.genealogy.v1.TreeCreated";
    public static final String EVENT_TREE_VISIBILITY_CHANGED = "gp.genealogy.v1.TreeVisibilityChanged";
    public static final String EVENT_TREE_ARCHIVED = "gp.genealogy.v1.TreeArchived";
    public static final String EVENT_TREE_RESTORED = "gp.genealogy.v1.TreeRestored";
    public static final String EVENT_TREE_TRANSFERRED = "gp.genealogy.v1.TreeTransferred";
    public static final String EVENT_TREE_DELETED = "gp.genealogy.v1.TreeDeleted";
    public static final String EVENT_UNLISTED_TOKEN_ISSUED = "gp.genealogy.v1.UnlistedTokenIssued";
    public static final String EVENT_UNLISTED_TOKEN_REVOKED = "gp.genealogy.v1.UnlistedTokenRevoked";

    public record TreeCreatedEvent(
            @JsonProperty("treeId") String treeId,
            @JsonProperty("slug") String slug,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("visibility") String visibility,
            @JsonProperty("defaultLocale") String defaultLocale,
            @JsonProperty("defaultTimezone") String defaultTimezone,
            @JsonProperty("createdAt") Instant createdAt) {
        public static TreeCreatedEvent fromTree(Tree tree) {
            return new TreeCreatedEvent(
                    tree.treeId(),
                    tree.slug(),
                    tree.displayName(),
                    tree.visibility().wire(),
                    tree.defaultLocale(),
                    tree.defaultTimezone(),
                    tree.createdAt());
        }
    }

    public record TreeVisibilityChangedEvent(
            @JsonProperty("treeId") String treeId,
            @JsonProperty("from") String from,
            @JsonProperty("to") String to,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("changedAt") Instant changedAt) {
    }

    public record TreeArchivedEvent(
            @JsonProperty("treeId") String treeId,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("archivedAt") Instant archivedAt) {
    }

    public record TreeRestoredEvent(
            @JsonProperty("treeId") String treeId,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("restoredAt") Instant restoredAt) {
    }

    public record TreeTransferredEvent(
            @JsonProperty("treeId") String treeId,
            @JsonProperty("fromTenantId") String fromTenantId,
            @JsonProperty("toTenantId") String toTenantId,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("transferredAt") Instant transferredAt) {
    }

    public record TreeDeletedEvent(
            @JsonProperty("treeId") String treeId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("reason") String reason,
            @JsonProperty("deletedAt") Instant deletedAt) {
    }

    public record UnlistedTokenIssuedEvent(
            @JsonProperty("tokenId") String tokenId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("fingerprint") String fingerprint,
            @JsonProperty("scope") String scope,
            @JsonProperty("issuedBy") String issuedBy,
            @JsonProperty("expiresAt") Instant expiresAt,
            @JsonProperty("issuedAt") Instant issuedAt) {
        public static UnlistedTokenIssuedEvent fromToken(UnlistedToken token) {
            return new UnlistedTokenIssuedEvent(
                    token.tokenId(),
                    token.treeId(),
                    token.fingerprint(),
                    token.scope().wire(),
                    token.issuedBy(),
                    token.expiresAt(),
                    token.issuedAt());
        }
    }

    public record UnlistedTokenRevokedEvent(
            @JsonProperty("tokenId") String tokenId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("fingerprint") String fingerprint,
            @JsonProperty("revokedBy") String revokedBy,
            @JsonProperty("reason") String reason,
            @JsonProperty("revokedAt") Instant revokedAt) {
    }

    public static String lifecycleToWire(LifecycleState state) {
        return state == null ? null : state.wire();
    }

    public static String visibilityToWire(Visibility visibility) {
        return visibility == null ? null : visibility.wire();
    }
}
