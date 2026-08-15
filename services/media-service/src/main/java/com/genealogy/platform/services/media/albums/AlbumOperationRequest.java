package com.genealogy.platform.services.media.albums;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Value object describing a {@link AlbumCatalog} operation
 * the caller wants to perform.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.auditRequiredKeys} (E7.5). The compact constructor
 * enforces the contract length caps + the actor / correlation
 * pseudonym requirement; the cross-service references are
 * immutable lists of {@link AlbumReferenceRequest} values,
 * each of which carries only an {@code albumReferenceKind} +
 * an opaque {@code referencePseudoId}.
 */
public record AlbumOperationRequest(
        String albumId,
        String tenantScopeId,
        String actorPseudoId,
        String correlationId,
        Long albumVersion,
        AlbumVisibility visibility,
        AlbumLifecycleState lifecycleState,
        List<AlbumItemRequest> items,
        List<AlbumReferenceRequest> references,
        Map<String, String> tags,
        Map<String, String> captions,
        boolean dnaBucketKey,
        boolean membershipActive,
        boolean quotaAllowancePresent,
        boolean objectLockComplianceAvailable) {

    public AlbumOperationRequest {
        Objects.requireNonNull(albumId, "albumId");
        Objects.requireNonNull(tenantScopeId, "tenantScopeId");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(lifecycleState, "lifecycleState");
        if (albumId.isBlank()) {
            throw new IllegalArgumentException("albumId blank");
        }
        if (tenantScopeId.isBlank()) {
            throw new IllegalArgumentException("tenantScopeId blank");
        }
        if (actorPseudoId.isBlank()) {
            throw new IllegalArgumentException("actorPseudoId blank");
        }
        if (correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId blank");
        }
        if (albumId.length() > AlbumCatalogLimits.ALBUM_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "albumId too long: " + albumId.length());
        }
        if (actorPseudoId.length() > AlbumCatalogLimits.ACTOR_PSEUDO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "actorPseudoId too long: " + actorPseudoId.length());
        }
        if (correlationId.length() > AlbumCatalogLimits.CORRELATION_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "correlationId too long: " + correlationId.length());
        }
        if (albumVersion != null
                && albumVersion < AlbumCatalogLimits.ALBUM_VERSION_FLOOR) {
            throw new IllegalArgumentException(
                    "albumVersion below floor: " + albumVersion);
        }
        items = items == null ? List.of() : List.copyOf(items);
        references = references == null ? List.of() : List.copyOf(references);
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        captions = captions == null ? Map.of() : Map.copyOf(captions);
        for (AlbumItemRequest it : items) {
            Objects.requireNonNull(it, "item");
        }
        for (AlbumReferenceRequest r : references) {
            Objects.requireNonNull(r, "reference");
        }
        if (items.size() > AlbumCatalogLimits.MAX_ITEMS_PER_ALBUM) {
            throw new IllegalArgumentException(
                    "items too many: " + items.size());
        }
        if (references.size()
                > AlbumCatalogLimits.MAX_REFERENCES_PER_ALBUM) {
            throw new IllegalArgumentException(
                    "references too many per call: " + references.size());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String albumId;
        private String tenantScopeId;
        private String actorPseudoId;
        private String correlationId;
        private Long albumVersion;
        private AlbumVisibility visibility = AlbumVisibility.PRIVATE;
        private AlbumLifecycleState lifecycleState = AlbumLifecycleState.ACTIVE;
        private List<AlbumItemRequest> items = List.of();
        private List<AlbumReferenceRequest> references = List.of();
        private Map<String, String> tags = Map.of();
        private Map<String, String> captions = Map.of();
        private boolean dnaBucketKey;
        private boolean membershipActive = true;
        private boolean quotaAllowancePresent = true;
        private boolean objectLockComplianceAvailable;

        public Builder albumId(String v) {
            this.albumId = v;
            return this;
        }

        public Builder tenantScopeId(String v) {
            this.tenantScopeId = v;
            return this;
        }

        public Builder actorPseudoId(String v) {
            this.actorPseudoId = v;
            return this;
        }

        public Builder correlationId(String v) {
            this.correlationId = v;
            return this;
        }

        public Builder albumVersion(Long v) {
            this.albumVersion = v;
            return this;
        }

        public Builder visibility(AlbumVisibility v) {
            this.visibility = v;
            return this;
        }

        public Builder lifecycleState(AlbumLifecycleState v) {
            this.lifecycleState = v;
            return this;
        }

        public Builder items(List<AlbumItemRequest> v) {
            this.items = v == null ? List.of() : v;
            return this;
        }

        public Builder references(List<AlbumReferenceRequest> v) {
            this.references = v == null ? List.of() : v;
            return this;
        }

        public Builder tags(Map<String, String> v) {
            this.tags = v == null ? Map.of() : v;
            return this;
        }

        public Builder captions(Map<String, String> v) {
            this.captions = v == null ? Map.of() : v;
            return this;
        }

        public Builder dnaBucketKey(boolean v) {
            this.dnaBucketKey = v;
            return this;
        }

        public Builder membershipActive(boolean v) {
            this.membershipActive = v;
            return this;
        }

        public Builder quotaAllowancePresent(boolean v) {
            this.quotaAllowancePresent = v;
            return this;
        }

        public Builder objectLockComplianceAvailable(boolean v) {
            this.objectLockComplianceAvailable = v;
            return this;
        }

        public AlbumOperationRequest build() {
            return new AlbumOperationRequest(
                    albumId, tenantScopeId, actorPseudoId, correlationId,
                    albumVersion, visibility, lifecycleState, items,
                    references, tags, captions, dnaBucketKey,
                    membershipActive, quotaAllowancePresent,
                    objectLockComplianceAvailable);
        }
    }
}