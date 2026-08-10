package com.genealogy.platform.libs.security.abac;

import java.util.Locale;
import java.util.Objects;

/**
 * Jurisdiction code per {@code privacy-and-legal-gate.md} §3.
 * The closed set matches the residency matrix in that document;
 * adding a new code requires ADR-E0.5-04 sign-off.
 */
public record Jurisdiction(String code) {

    public Jurisdiction {
        Objects.requireNonNull(code, "code");
        if (!code.matches("[A-Z][A-Z0-9\\-]{1,15}")) {
            throw new IllegalArgumentException(
                    "invalid jurisdiction code: " + code);
        }
    }

    /** EU sovereign region (GDPR + national supplements). */
    public static Jurisdiction EU = new Jurisdiction("EU");
    /** UK sovereign region (UK GDPR + DPA 2018). */
    public static Jurisdiction UK = new Jurisdiction("UK");
    /** US region (CCPA/CPRA + state addenda). */
    public static Jurisdiction US = new Jurisdiction("US");
    /** Canadian region with French-first defaults. */
    public static Jurisdiction CA_QC = new Jurisdiction("CA-QC");
    /** Australia / New Zealand region. */
    public static Jurisdiction APAC_ANZ = new Jurisdiction("APAC-ANZ");
    /** Japan region. */
    public static Jurisdiction APAC_JP = new Jurisdiction("APAC-JP");
    /** Singapore region. */
    public static Jurisdiction APAC_SG = new Jurisdiction("APAC-SG");
    /** Other regions (deferred until ADR). */
    public static Jurisdiction ROW = new Jurisdiction("ROW");
    /** Customer-managed on-premise. */
    public static Jurisdiction ONPREM = new Jurisdiction("ONPREM");

    public boolean isGdprLike() {
        String u = code.toUpperCase(Locale.ROOT);
        return u.equals("EU") || u.equals("UK") || u.equals("CA-QC")
                || u.equals("APAC-ANZ") || u.equals("ROW") || u.equals("ONPREM");
    }

    public boolean isGeneticDataRestricted() {
        String u = code.toUpperCase(Locale.ROOT);
        return u.equals("EU") || u.equals("UK") || u.equals("APAC-ANZ")
                || u.equals("APAC-JP") || u.equals("ONPREM");
    }
}
