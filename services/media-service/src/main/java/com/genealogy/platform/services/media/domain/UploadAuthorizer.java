package com.genealogy.platform.services.media.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Upload authorizer. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionScopes + uploadSessionIntents +
 * uploadGuardDenyReasons + dnaBucketAccess +
 * uploadSessionIntentNeverRoutesToDnaBucket` (E7.1) +
 * `requirements.md` R9.2 + `design.md` §8.2 + §8.2.
 *
 * <p>The authorizer is a pure function; it does not touch
 * the persistence layer. OpenFGA + ABAC are kept behind the
 * {@link UploadAuthorizationPort}.
 */
public final class UploadAuthorizer {

    private final Set<String> permittedIntents;
    private final Set<String> permittedMediaCategories;
    private final boolean dnaBucketForbidden;

    public UploadAuthorizer(
            Set<String> permittedIntents,
            Set<String> permittedMediaCategories,
            boolean dnaBucketForbidden) {
        Objects.requireNonNull(permittedIntents, "permittedIntents");
        Objects.requireNonNull(permittedMediaCategories, "permittedMediaCategories");
        if (permittedIntents.isEmpty()) {
            throw new IllegalArgumentException("permittedIntents must not be empty");
        }
        if (permittedMediaCategories.isEmpty()) {
            throw new IllegalArgumentException(
                    "permittedMediaCategories must not be empty");
        }
        this.permittedIntents = Set.copyOf(permittedIntents);
        this.permittedMediaCategories = Set.copyOf(permittedMediaCategories);
        this.dnaBucketForbidden = dnaBucketForbidden;
    }

    public UploadAuthorizationDecision authorizeCreate(
            UploadSessionIntent intent, MediaCategory category) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(category, "category");
        if (!permittedIntents.contains(intent.wire())) {
            return UploadAuthorizationDecision.deny(
                    "MEDIA_UPLOAD_INTENT_NOT_PERMITTED");
        }
        if (!permittedMediaCategories.contains(category.wire())) {
            return UploadAuthorizationDecision.deny(
                    "MEDIA_UPLOAD_MEDIA_CATEGORY_NOT_PERMITTED");
        }
        if (dnaBucketForbidden && category == MediaCategory.DNA_FASTQ) {
            return UploadAuthorizationDecision.abacDeny(
                    "MEDIA_UPLOAD_DNA_BUCKET_FORBIDDEN");
        }
        return UploadAuthorizationDecision.allow("MEDIA_UPLOAD_AUTHORIZED");
    }

    public UploadAuthorizationDecision authorizeRoutedObjectKey(
            String objectKey, UploadSessionIntent intent) {
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(intent, "intent");
        if (dnaBucketForbidden && routesToDnaBucket(objectKey)) {
            return UploadAuthorizationDecision.abacDeny(
                    "MEDIA_PAYLOAD_DNA_BUCKET_FORBIDDEN");
        }
        if (intent == UploadSessionIntent.OCR_INPUT
                && objectKey.startsWith("dna/")) {
            return UploadAuthorizationDecision.abacDeny(
                    "MEDIA_PAYLOAD_DNA_BUCKET_FORBIDDEN");
        }
        return UploadAuthorizationDecision.allow("MEDIA_OBJECT_KEY_AUTHORIZED");
    }

    public boolean routesToDnaBucket(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey");
        return objectKey.startsWith("dna/raw/")
                || objectKey.startsWith("dna/match/")
                || objectKey.startsWith("dna/consent/")
                || objectKey.equals("dna/raw")
                || objectKey.equals("dna/match")
                || objectKey.equals("dna/consent");
    }

    public Set<String> permittedIntents() {
        return permittedIntents;
    }

    public Set<String> permittedMediaCategories() {
        return permittedMediaCategories;
    }
}
