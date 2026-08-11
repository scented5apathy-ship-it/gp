package com.genealogy.platform.services.research.domain;

import java.util.Objects;

/**
 * Reference to an attachment (media-service object) attached to
 * a {@code Source} or {@code Citation}. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.attachmentRefs` (E6.1) + `requirements.md` R8.1
 * (attachment reference) + R7.5 (object delivery through
 * media-service, signed URL + watermark).
 *
 * <p>The {@code mediaObjectId} is the opaque id assigned by the
 * media-service when the upload completed. The research domain
 * never owns the underlying blob — it only carries the
 * indirection so the source can be indexed without leaking the
 * object store identifier.
 */
public record AttachmentRef(
        AttachmentKind kind,
        String mediaObjectId,
        String canonicalUrl,
        String caption,
        String locale) {

    public AttachmentRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(mediaObjectId, "mediaObjectId");
        if (mediaObjectId.isBlank()) {
            throw new IllegalArgumentException("mediaObjectId must not be blank");
        }
        if (mediaObjectId.length() > 128) {
            throw new IllegalArgumentException(
                    "mediaObjectId exceeds 128 characters");
        }
        if (canonicalUrl != null && canonicalUrl.length() > 2048) {
            throw new IllegalArgumentException(
                    "canonicalUrl exceeds 2048 characters");
        }
        if (canonicalUrl != null && canonicalUrl.isBlank()) {
            canonicalUrl = null;
        }
        if (caption != null && caption.length() > 1024) {
            throw new IllegalArgumentException(
                    "caption exceeds 1024 characters");
        }
        if (caption != null && caption.isBlank()) {
            caption = null;
        }
        if (locale != null && !locale.isBlank()) {
            if (!locale.matches("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?")) {
                throw new IllegalArgumentException(
                        "locale must match BCP-47 tag: " + locale);
            }
        } else {
            locale = null;
        }
    }

    public boolean hasCanonicalUrl() {
        return canonicalUrl != null && !canonicalUrl.isBlank();
    }
}
