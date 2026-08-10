package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private static PlaceName name(String display, String lang) {
        return new PlaceName(display, lang, null, null);
    }

    private static Coordinates hanoi() {
        return Coordinates.of(21.0285, 105.8542);
    }

    private static Place newBase(
            List<PlaceName> names,
            Coordinates coordinates,
            PlaceAuthority authority,
            List<String> hierarchy) {
        return new Place(
                "place-1", "tenant-1",
                PlaceKind.LOCALITY,
                names, coordinates, authority, hierarchy,
                T, T, "user-1", Certainty.VERIFIED, 1L);
    }

    @Test
    void nameForReturnsExactLocaleMatch() {
        Place place = newBase(
                List.of(
                        name("Hà Nội", "vi"),
                        name("Hanoi", "en")),
                hanoi(), null, List.of("place-country-vn"));
        assertEquals("Hanoi", place.nameFor("en-US").display());
    }

    @Test
    void nameForReturnsExactLanguageMatch() {
        Place place = newBase(
                List.of(
                        name("Hà Nội", "vi"),
                        name("Hanoi", "en")),
                hanoi(), null, List.of("place-country-vn"));
        assertEquals("Hà Nội", place.nameFor("vi").display());
    }

    @Test
    void nameForFallsBackToFirstWhenNoMatch() {
        Place place = newBase(
                List.of(name("Tokyo", "ja")),
                hanoi(), null, List.of());
        assertEquals("Tokyo", place.nameFor("fr-FR").display());
    }

    @Test
    void nameForReturnsNullWhenNoNames() {
        Place place = newBase(List.of(), null, null, List.of());
        assertNull(place.nameFor("en"));
    }

    @Test
    void rejectsBlankDisplayName() {
        assertThrows(IllegalArgumentException.class, () ->
                new PlaceName("  ", "en", null, null));
    }

    @Test
    void rejectsBadBcp47() {
        assertThrows(IllegalArgumentException.class, () ->
                new PlaceName("Hanoi", "english!", null, null));
    }

    @Test
    void rejectsSelfInHierarchy() {
        assertThrows(IllegalArgumentException.class, () ->
                newBase(
                        List.of(name("Hanoi", "en")),
                        hanoi(), null,
                        List.of("place-1")));
    }

    @Test
    void rejectsHierarchyDeeperThanCap() {
        List<String> deep = new ArrayList<>();
        for (int i = 0; i < Place.MAX_HIERARCHY_DEPTH + 1; i += 1) {
            deep.add("ancestor-" + i);
        }
        assertThrows(IllegalArgumentException.class, () ->
                newBase(List.of(name("Hanoi", "en")), hanoi(), null, deep));
    }

    @Test
    void rejectsDuplicateNameLocaleDisplay() {
        assertThrows(IllegalArgumentException.class, () ->
                newBase(
                        List.of(
                                name("Hanoi", "en"),
                                name("HANOI", "EN")),
                        hanoi(), null, List.of()));
    }

    @Test
    void rejectsAuthorityIdWithBadShape() {
        assertThrows(IllegalArgumentException.class, () ->
                new PlaceAuthority(AuthorityKind.WIKIDATA, "Q1?"));
    }

    @Test
    void rejectsCoordinatesOutOfRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Coordinates(
                        new BigDecimal("100.0"),
                        new BigDecimal("0"),
                        CoordinateDatum.WGS84));
        assertThrows(IllegalArgumentException.class, () ->
                new Coordinates(
                        new BigDecimal("0"),
                        new BigDecimal("-181"),
                        CoordinateDatum.WGS84));
    }

    @Test
    void withNamesAdvancesVersion() {
        Place before = newBase(
                List.of(name("Hanoi", "en")),
                hanoi(), null, List.of());
        Place after = before.withNames(
                List.of(name("Hanoi", "en"), name("Hà Nội", "vi")), T);
        assertEquals(before.version() + 1, after.version());
        assertEquals(2, after.names().size());
    }

    @Test
    void withAuthorityAdvancesVersion() {
        Place before = newBase(
                List.of(name("Hanoi", "en")),
                hanoi(), null, List.of());
        Place after = before.withAuthority(
                new PlaceAuthority(AuthorityKind.WIKIDATA, "Q1788"), T);
        assertEquals(before.version() + 1, after.version());
        assertNotNull(after.authority());
    }

    @Test
    void diffDetectsClosedSetFieldPaths() {
        Place before = newBase(
                List.of(name("Hanoi", "en")),
                hanoi(),
                null,
                List.of());
        Place after = before.withNames(
                List.of(name("Hanoi", "en"), name("Hà Nội", "vi")), T)
                .withKind(PlaceKind.REGION, T);
        java.util.LinkedHashSet<String> diff = Place.diff(before, after);
        assertTrue(diff.contains("kind"));
        assertTrue(diff.contains("names[]"));
        assertEquals(2, diff.size());
    }

    @Test
    void localAuthorityLowercasesId() {
        PlaceAuthority auth = PlaceAuthority.local("PLACE-XYZ");
        assertEquals("place-xyz", auth.authorityId());
        assertSame(AuthorityKind.LOCAL, auth.authorityKind());
    }
}
