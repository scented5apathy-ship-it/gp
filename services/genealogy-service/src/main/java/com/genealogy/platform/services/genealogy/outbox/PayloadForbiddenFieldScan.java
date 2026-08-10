package com.genealogy.platform.services.genealogy.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Forbidden-field scan. Mirrors
 * `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.payloadForbiddenFields` + `design.md` §7.3 +
 * `privacy-and-legal-gate.md` §11.
 *
 * <p>The scan is structural — it inspects the Avro
 * payload's bytes for the closed-set forbidden field
 * names (the wire schema is Avro, so the field names
 * appear as UTF-8 strings inside the encoded payload).
 * It is intentionally conservative: a hit forces the
 * row to DEAD_LETTERED with
 * {@link DlqReason#SERIALIZATION_ERROR}.
 */
public final class PayloadForbiddenFieldScan {

    public static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "dnaRaw",
            "rawGenotype",
            "dna",
            "kit",
            "rawDna",
            "raw_dna",
            "email",
            "phoneNumber",
            "accessToken",
            "refreshToken");

    private PayloadForbiddenFieldScan() {
    }

    public static void check(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        String asString = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        for (String field : FORBIDDEN_FIELDS) {
            if (asString.contains("\"" + field + "\"")) {
                throw new KafkaProducerPort.ForbiddenPayload(
                        "payload contains forbidden field: " + field);
            }
        }
    }
}
