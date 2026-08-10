package com.genealogy.platform.services.genealogy.domain;

import java.util.Base64;

/**
 * URL-safe base64url encoder without padding (RFC 4648 §5). Used
 * to serialise the random UNLISTED token bytes. No third-party
 * dependency required.
 */
public final class Base64UrlEncoder {

    private Base64UrlEncoder() {
    }

    public static String encode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
