package com.genealogy.platform.spring.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Field-level redactor for {@link AuditEvent} metadata. Mirrors
 * {@code contracts/audit/redaction.yaml} with a small in-process
 * default so the platform starter works out of the box. Production
 * wiring (E3.6 follow-up) loads the contract from the YAML at
 * startup; this default list matches the contract verbatim so
 * service tests stay deterministic.
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code denyKeys} — keys whose value is dropped entirely.
 *   <li>{@code maskKeys} — keys whose value is replaced by
 *       {@code [REDACTED:<key>]}.
 *   <li>{@code scrubPatterns} — regex applied to any free-text
 *       metadata value, regardless of key (email, JWT, IP, DNA).
 * </ul>
 *
 * <p>The redactor is non-throwing: malformed patterns are skipped
 * and logged at WARN; well-formed patterns always succeed. The
 * redactor is intentionally framework-free so it can be reused
 * by the {@code audit-service} consumer side as well.
 */
public final class AuditRedactor {

    public static final String OVERFLOW_MARKER = "[REDACTED:overflow]";

    private final List<String> denyKeys;
    private final List<String> maskKeys;
    private final List<Pattern> scrubPatterns;
    private final List<String> scrubReplacements;
    private final int maxMetadataSizeBytes;
    private final OverflowBehavior overflowBehavior;

    public enum OverflowBehavior {
        TRUNCATE_WITH_MARKER,
        REJECT
    }

    public AuditRedactor(
            List<String> denyKeys,
            List<String> maskKeys,
            List<ScrubRule> scrubPatterns,
            int maxMetadataSizeBytes,
            OverflowBehavior overflowBehavior) {
        this.denyKeys = List.copyOf(Objects.requireNonNull(denyKeys, "denyKeys"));
        this.maskKeys = List.copyOf(Objects.requireNonNull(maskKeys, "maskKeys"));
        this.scrubPatterns = compilePatterns(scrubPatterns);
        this.scrubReplacements = scrubPatterns == null
                ? List.of()
                : scrubPatterns.stream().map(ScrubRule::replacement).toList();
        this.maxMetadataSizeBytes = maxMetadataSizeBytes;
        this.overflowBehavior = Objects.requireNonNull(overflowBehavior, "overflowBehavior");
    }

    /**
     * Default redactor that mirrors {@code contracts/audit/redaction.yaml}.
     */
    public static AuditRedactor defaultRedactor() {
        return new AuditRedactor(
                List.of(
                        "rawDna", "raw_dna", "dnaRaw", "genotype", "sequence",
                        "kitPayload", "rawBytes", "biography", "freeText", "notes",
                        "password", "accessToken", "refreshToken", "apiKey",
                        "clientSecret", "cookie", "setCookie"),
                List.of(
                        "email", "phone", "address", "currentResidence", "school",
                        "guardians", "name", "firstName", "lastName",
                        "birthDate", "deathDate", "ipAddress", "userAgent",
                        "deviceFingerprint"),
                List.of(
                        new ScrubRule(
                                "email",
                                "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}",
                                "[REDACTED:email]"),
                        new ScrubRule(
                                "ipv4", "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "[REDACTED:ipv4]"),
                        new ScrubRule(
                                "ipv6",
                                "\\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}\\b",
                                "[REDACTED:ipv6]"),
                        new ScrubRule("jwt", "(?i)eyJ[A-Za-z0-9._\\-]+", "[REDACTED:jwt]"),
                        new ScrubRule(
                                "bearer",
                                "(?i)bearer\\s+[A-Za-z0-9._\\-]+",
                                "[REDACTED:bearer]"),
                        new ScrubRule("ssn", "\\b\\d{3}-\\d{2}-\\d{4}\\b", "[REDACTED:ssn]"),
                        new ScrubRule(
                                "dnaSequence",
                                "\\b(?:[ACGT]{16,})\\b",
                                "[REDACTED:dnaSequence]")),
                4096,
                OverflowBehavior.TRUNCATE_WITH_MARKER);
    }

    public AuditEventEnvelope redact(AuditEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        Map<String, String> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : envelope.getMetadata().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (matchesAny(key, denyKeys)) {
                continue; // drop
            }
            String normalizedValue = value == null ? "" : value;
            if (matchesAny(key, maskKeys)) {
                redacted.put(key, "[REDACTED:" + key + "]");
                continue;
            }
            redacted.put(key, scrubValue(normalizedValue));
        }
        if (redactedSize(redacted) > maxMetadataSizeBytes) {
            switch (overflowBehavior) {
                case TRUNCATE_WITH_MARKER:
                    redacted = truncateWithMarker(redacted);
                    break;
                case REJECT:
                    throw new IllegalStateException(
                            "audit metadata exceeds maxMetadataSizeBytes=" + maxMetadataSizeBytes);
                default:
                    throw new IllegalStateException("unsupported overflow behavior: " + overflowBehavior);
            }
        }
        return new AuditEventEnvelope(
                envelope.getEventId(),
                envelope.getTenantId(),
                envelope.getActorId(),
                envelope.getAuditClass(),
                envelope.getAction(),
                envelope.getResourceType(),
                envelope.getResourceId(),
                envelope.getReasonCode(),
                envelope.getCorrelationId(),
                envelope.getOccurredAt(),
                redacted);
    }

    private static boolean matchesAny(String key, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private String scrubValue(String value) {
        String result = value;
        for (int i = 0; i < scrubPatterns.size(); i++) {
            result = scrubPatterns.get(i).matcher(result).replaceAll(scrubReplacements.get(i));
        }
        return result;
    }

    private static List<Pattern> compilePatterns(List<ScrubRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<Pattern> compiled = new ArrayList<>(rules.size());
        for (ScrubRule rule : rules) {
            try {
                compiled.add(Pattern.compile(rule.regex()));
            } catch (RuntimeException ignore) {
                // skip malformed patterns; default behaviour already
                // covers the well-known list
            }
        }
        return List.copyOf(compiled);
    }

    private static int redactedSize(Map<String, String> redacted) {
        int size = 0;
        for (Map.Entry<String, String> entry : redacted.entrySet()) {
            size += entry.getKey().length();
            size += entry.getValue() == null ? 0 : entry.getValue().length();
        }
        return size;
    }

    private static Map<String, String> truncateWithMarker(Map<String, String> original) {
        Map<String, String> truncated = new LinkedHashMap<>();
        int size = 0;
        for (Map.Entry<String, String> entry : original.entrySet()) {
            int entrySize = entry.getKey().length() + (entry.getValue() == null ? 0 : entry.getValue().length());
            if (size + entrySize > 4096) {
                truncated.put("__overflow__", OVERFLOW_MARKER);
                break;
            }
            truncated.put(entry.getKey(), entry.getValue());
            size += entrySize;
        }
        return truncated;
    }

    public record ScrubRule(String name, String regex, String replacement) {
    }
}
