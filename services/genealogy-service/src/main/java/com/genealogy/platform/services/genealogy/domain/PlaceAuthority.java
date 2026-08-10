package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provider-neutral authority reference. The {@code authorityKind}
 * tells the resolver which provider's data plane to hit; the
 * {@code authorityId} is an opaque id local to that provider. The
 * contract deliberately keeps the link provider-neutral so
 * swapping Wikidata ↔ GeoNames ↔ a national gazetteer is a
 * config-only change per `requirements.md` R4.4.
 */
public record PlaceAuthority(AuthorityKind authorityKind, String authorityId) {

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:\\-]{1,128}$");

    public PlaceAuthority {
        Objects.requireNonNull(authorityKind, "authorityKind");
        Objects.requireNonNull(authorityId, "authorityId");
        if (!ID_PATTERN.matcher(authorityId).matches()) {
            throw new IllegalArgumentException(
                    "authorityId not in opaque id shape: " + authorityId);
        }
    }

    public static PlaceAuthority local(String opaqueId) {
        return new PlaceAuthority(AuthorityKind.LOCAL, opaqueId.toLowerCase(Locale.ROOT));
    }
}
