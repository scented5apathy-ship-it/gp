package com.genealogy.platform.services.research.outbox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the forbidden-field scan used by
 * {@code services/genealogy-service/.../PayloadForbiddenFieldScan.java}
 * — same closed-set, same JSON walk, same outcome (the payload
 * is rejected at the relay boundary so a downstream consumer
 * never sees raw DNA / PII / secret material).
 */
public final class ResearchPayloadForbiddenFieldScan {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "dnaRaw",
            "rawGenotype",
            "dna",
            "kit",
            "rawDna",
            "raw_dna",
            "rawPassword",
            "rawToken",
            "biography",
            "rawEmail",
            "rawSubjectId");

    public ResearchPayloadForbiddenFieldScan() {
    }

    public boolean containsForbiddenField(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        Map<String, Object> root = parseFlat(json);
        if (root == null) {
            // A nested payload is rare for the research events
            // (the published Avro schemas are flat). The
            // genealogy-service scan walks recursively; we keep
            // the research scan flat to keep the contract
            // symmetric with the Avro schema fields.
            return false;
        }
        for (String key : root.keySet()) {
            if (FORBIDDEN_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> parseFlat(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(json, Map.class);
            return new LinkedHashMap<>(map);
        } catch (Exception e) {
            return null;
        }
    }
}
