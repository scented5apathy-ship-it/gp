package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateValueTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    @Test
    void exactRequiresPointInterval() {
        DateValue v = new DateValue(
                "10 Aug 2026", CalendarId.GREGORIAN, DateQualifier.EXACT,
                "UTC", NormalizedInterval.point(T), Certainty.VERIFIED, T);
        assertEquals("10 Aug 2026", v.originalExpression());
        assertSame(CalendarId.GREGORIAN, v.calendar());
        assertTrue(v.normalizedInterval().isPoint());
    }

    @Test
    void beforeRequiresSingleUpperBound() {
        DateValue v = new DateValue(
                "before 1900", CalendarId.GREGORIAN, DateQualifier.BEFORE,
                "UTC",
                new NormalizedInterval(null, Instant.parse("1900-01-01T00:00:00Z")),
                Certainty.ASSERTED, T);
        assertNull(v.normalizedInterval().earliest());
        assertEquals(Instant.parse("1900-01-01T00:00:00Z"), v.normalizedInterval().latest());
    }

    @Test
    void afterRequiresSingleLowerBound() {
        DateValue v = new DateValue(
                "after 1950", CalendarId.GREGORIAN, DateQualifier.AFTER,
                "UTC",
                new NormalizedInterval(Instant.parse("1950-01-01T00:00:00Z"), null),
                Certainty.HYPOTHESIS, T);
        assertEquals(Instant.parse("1950-01-01T00:00:00Z"), v.normalizedInterval().earliest());
        assertNull(v.normalizedInterval().latest());
    }

    @Test
    void betweenRequiresTwoBounds() {
        Instant lo = Instant.parse("1900-01-01T00:00:00Z");
        Instant hi = Instant.parse("1910-01-01T00:00:00Z");
        DateValue v = new DateValue(
                "between 1900 and 1910", CalendarId.GREGORIAN, DateQualifier.BETWEEN,
                "UTC",
                new NormalizedInterval(lo, hi),
                Certainty.ASSERTED, T);
        assertTrue(v.normalizedInterval().earliest().isBefore(v.normalizedInterval().latest()));
        assertFalse(v.normalizedInterval().isPoint());
    }

    @Test
    void betweenRejectsPointInterval() {
        assertThrows(IllegalArgumentException.class, () -> new DateValue(
                "point", CalendarId.GREGORIAN, DateQualifier.BETWEEN, "UTC",
                NormalizedInterval.point(T), Certainty.ASSERTED, T));
    }

    @Test
    void exactRejectsNonPointInterval() {
        assertThrows(IllegalArgumentException.class, () -> new DateValue(
                "range", CalendarId.GREGORIAN, DateQualifier.EXACT, "UTC",
                new NormalizedInterval(T, Instant.parse("2026-09-10T10:00:00Z")),
                Certainty.ASSERTED, T));
    }

    @Test
    void rejectsBlankOriginalExpression() {
        assertThrows(IllegalArgumentException.class, () -> new DateValue(
                "  ", CalendarId.GREGORIAN, DateQualifier.EXACT, "UTC",
                NormalizedInterval.point(T), Certainty.VERIFIED, T));
    }

    @Test
    void rejectsOverlongOriginalExpression() {
        String big = "x".repeat(DateValue.MAX_ORIGINAL_EXPRESSION_CHARS + 1);
        assertThrows(IllegalArgumentException.class, () -> new DateValue(
                big, CalendarId.GREGORIAN, DateQualifier.EXACT, "UTC",
                NormalizedInterval.point(T), Certainty.VERIFIED, T));
    }

    @Test
    void rejectsInvalidTimezone() {
        assertThrows(IllegalArgumentException.class, () -> new DateValue(
                "10 Aug 2026", CalendarId.GREGORIAN, DateQualifier.EXACT, "1bad!tz",
                NormalizedInterval.point(T), Certainty.VERIFIED, T));
    }

    @Test
    void lunisolarCalendarIsLunisolar() {
        assertTrue(CalendarId.VIETNAMESE_LUNISOLAR.isLunisolar());
        assertTrue(CalendarId.CHINESE_LUNISOLAR.isLunisolar());
        assertTrue(CalendarId.KOREAN.isLunisolar());
        assertTrue(!CalendarId.GREGORIAN.isLunisolar());
    }

    @Test
    void unknownQualifierRendersAsUnknown() {
        assertSame(DateQualifier.UNKNOWN, DateQualifier.fromWire(null));
        assertSame(DateQualifier.UNKNOWN, DateQualifier.fromWire("unknown"));
        assertSame(DateQualifier.EXACT, DateQualifier.fromWire("exact"));
    }

    @Test
    void displayKeyEndsWithTimezone() {
        DateValue v = new DateValue(
                "10/08/2026", CalendarId.GREGORIAN, DateQualifier.EXACT,
                "Asia/Ho_Chi_Minh", NormalizedInterval.point(T),
                Certainty.VERIFIED, T);
        assertEquals("GREGORIAN:EXACT:Asia/Ho_Chi_Minh", v.displayKey());
    }
}
