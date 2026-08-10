package com.genealogy.platform.services.genealogy.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * WGS84 latitude / longitude pair. Stored as DECIMAL(9,6)
 * (≈ 0.11 m at the equator) per {@code date-place-policy.yaml::
 * spec.coordinatePrecision}. The datum is pinned in the
 * constructor; non-WGS84 datums are out of scope for E4.3.
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude, CoordinateDatum datum) {

    public Coordinates {
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
        Objects.requireNonNull(datum, "datum");
        if (latitude.compareTo(new BigDecimal("-90")) < 0
                || latitude.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude.compareTo(new BigDecimal("-180")) < 0
                || longitude.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    public static Coordinates of(double lat, double lon) {
        return new Coordinates(
                BigDecimal.valueOf(lat).setScale(6, java.math.RoundingMode.HALF_UP),
                BigDecimal.valueOf(lon).setScale(6, java.math.RoundingMode.HALF_UP),
                CoordinateDatum.WGS84);
    }
}
