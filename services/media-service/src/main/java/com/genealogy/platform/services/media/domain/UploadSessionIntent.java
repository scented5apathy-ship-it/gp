package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of upload session intents. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionIntents` (E7.1) + `requirements.md`
 * R9.1. The intent is the closed-set pinned by the contract;
 * adding a new intent requires an ADR supersession.
 */
public enum UploadSessionIntent {
    ATTACHMENT,
    ALBUM,
    PROFILE,
    TREE_MEDIA,
    DOCUMENT_THUMBNAIL,
    OCR_INPUT,
    DELIVERY_THUMBNAIL;

    public static UploadSessionIntent fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return UploadSessionIntent.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown UploadSessionIntent from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
