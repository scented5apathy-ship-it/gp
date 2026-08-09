package com.genealogy.platform.services.tenant.domain.tenant;

/**
 * Calendar identifier per ADR-E0.5-14. Mirrors the gRPC
 * {@code Calendar} enum and the OpenAPI
 * {@code defaultCalendar} enum. The platform default is
 * {@link #GREGORIAN}; non-Gregorian choices trigger the alternate
 * formatter stack in the genealogy-service.
 *
 * <p>Validation lives here (and in the V2 migration CHECK) so the
 * repository never stores an unknown calendar string.
 */
public enum CalendarType {

    GREGORIAN,
    HEBREW,
    HIJRI,
    ETHIOPIAN,
    CUSTOM;

    public boolean isNonGregorian() {
        return this != GREGORIAN;
    }
}