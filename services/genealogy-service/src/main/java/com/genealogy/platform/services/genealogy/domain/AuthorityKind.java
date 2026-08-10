package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set authority provider kind. The authority id itself is
 * opaque and provider-neutral (see {@link PlaceAuthority}); the
 * kind tells the resolver which provider's data plane to hit.
 */
public enum AuthorityKind {
    WIKIDATA,
    GEONAMES,
    NATIONAL_GAZETTEER,
    LOCAL;

    public static AuthorityKind fromWire(String wire) {
        Objects.requireNonNull(wire, "authorityKind");
        return AuthorityKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
