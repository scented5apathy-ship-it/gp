package com.genealogy.platform.libs.security.redaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Field-level redactor that drops forbidden payload classes before
 * any sink (Loki, Tempo, analytics warehouse, public projection).
 *
 * <p>The redactor is the runtime side of the
 * {@code no-raw-dna-in-logs} Semgrep rule (E1.6) and the ABAC
 * {@code redact} obligation (E3.4). The {@code DENY} list mirrors
 * the privacy gate §11 forbidden payload classes; the {@code MASK}
 * list replaces values with a deterministic pseudonymous token so
 * cardinality is preserved without leaking the underlying value.
 */
public final class PiiRedactor {

    /** Keys whose value is dropped entirely before logging. */
    public static final Set<String> DENY_KEYS = Set.of(
            "rawDna",
            "raw_dna",
            "dnaRaw",
            "genotype",
            "sequence",
            "kitPayload",
            "rawBytes",
            "biography",
            "freeText",
            "notes");

    /** Keys whose value is replaced by {@code [REDACTED:<key>]}. */
    public static final Set<String> MASK_KEYS = Set.of(
            "email",
            "phone",
            "address",
            "currentResidence",
            "school",
            "guardians",
            "name",
            "firstName",
            "lastName",
            "birthDate",
            "deathDate");

    private static final Pattern EMAIL_REGEX =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern TOKEN_REGEX =
            Pattern.compile("(?i)(bearer\\s+[A-Za-z0-9._\\-]+|eyJ[A-Za-z0-9._\\-]+)");
    private static final Pattern SSN_REGEX =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private final Set<String> denyKeys;
    private final Set<String> maskKeys;
    private final boolean scrubValues;

    public PiiRedactor() {
        this(DENY_KEYS, MASK_KEYS, true);
    }

    public PiiRedactor(Set<String> denyKeys, Set<String> maskKeys, boolean scrubValues) {
        this.denyKeys = Set.copyOf(denyKeys);
        this.maskKeys = Set.copyOf(maskKeys);
        this.scrubValues = scrubValues;
    }

    /**
     * Returns a sanitised copy of the supplied map. Keys on the
     * {@code DENY_KEYS} list are dropped; keys on the
     * {@code MASK_KEYS} list have their value masked; values that
     * look like emails / tokens / SSNs are scrubbed when
     * {@code scrubValues} is true.
     */
    public Map<String, Object> redactMap(Map<String, Object> input) {
        Objects.requireNonNull(input, "input");
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (denyKeys.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                out.put(key, null);
                continue;
            }
            if (maskKeys.contains(key)) {
                out.put(key, "[REDACTED:" + key + "]");
                continue;
            }
            if (scrubValues && value instanceof String s) {
                String scrubbed = scrubString(s);
                if (!scrubbed.equals(s)) {
                    out.put(key, scrubbed);
                    continue;
                }
            }
            out.put(key, value);
        }
        return out;
    }

    /** Returns the supplied string with forbidden patterns scrubbed. */
    public String scrubString(String input) {
        Objects.requireNonNull(input, "input");
        String s = input;
        s = EMAIL_REGEX.matcher(s).replaceAll("[REDACTED:email]");
        s = SSN_REGEX.matcher(s).replaceAll("[REDACTED:ssn]");
        s = TOKEN_REGEX.matcher(s).replaceAll("[REDACTED:token]");
        return s;
    }

    public Set<String> denyKeys() {
        return denyKeys;
    }

    public Set<String> maskKeys() {
        return maskKeys;
    }
}
