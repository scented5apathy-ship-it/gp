package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PayloadForbiddenFieldScan}.
 * Mirrors `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.payloadForbiddenFields` (E4.7).
 */
class PayloadForbiddenFieldScanTest {

    @Test
    void cleanPayloadPasses() {
        PayloadForbiddenFieldScan.check("{\"mergeId\":\"merge-1\"}".getBytes());
        PayloadForbiddenFieldScan.check("{}".getBytes());
        PayloadForbiddenFieldScan.check("{\"name\":\"ok\"}".getBytes());
    }

    @Test
    void rejectsForbiddenFields() {
        for (String field : PayloadForbiddenFieldScan.FORBIDDEN_FIELDS) {
            byte[] payload = ("{\"" + field + "\":\"x\"}").getBytes();
            try {
                PayloadForbiddenFieldScan.check(payload);
                throw new AssertionError("expected ForbiddenPayload for " + field);
            } catch (KafkaProducerPort.ForbiddenPayload ex) {
                assertTrue(ex.getMessage().contains(field),
                        "expected message to mention " + field + ", got " + ex.getMessage());
            }
        }
    }

    @Test
    void nullPayloadIsRejected() {
        assertThrows(NullPointerException.class,
                () -> PayloadForbiddenFieldScan.check(null));
    }
}
