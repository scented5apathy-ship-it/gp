package com.genealogy.platform.services.genealogy.outbox;

import java.util.Locale;

/**
 * Closed-set reason a row landed on the dead-letter
 * table. Mirrors `contracts/genealogy/outbox-relay-policy
 * .yaml::spec.dlqReasonClosedSet` (E4.7) and is persisted
 * on the outbox row's {@code dlq_reason} column.
 *
 * <ul>
 *   <li>{@link #PUBLISH_TIMEOUT} — Kafka producer timed
 *       out before the broker acknowledged. Retry class =
 *       TRANSIENT (transient at the relay level but we
 *       already exhausted {@code maxAttempts}).
 *   <li>{@link #SERIALIZATION_ERROR} — payload failed the
 *       Avro forbidden-token scan or encoding error.
 *   <li>{@link #SCHEMA_INCOMPATIBLE} — Apicurio rejected
 *       the payload against the registered schema.
 *   <li>{@link #PERMISSION_DENIED} — Kafka ACL denied
 *       the publish (operator must fix the topic ACL).
 *   <li>{@link #TENANT_MISMATCH} — row's tenant_id did
 *       not match the relay's tenant context. Defense in
 *       depth on top of RLS.
 *   <li>{@link #UNKNOWN_TOPIC} — the event type does not
 *       resolve to a known topic. Operator must fix the
 *       event-type → topic mapping.
 * </ul>
 */
public enum DlqReason {
    PUBLISH_TIMEOUT,
    SERIALIZATION_ERROR,
    SCHEMA_INCOMPATIBLE,
    PERMISSION_DENIED,
    TENANT_MISMATCH,
    UNKNOWN_TOPIC;

    public static DlqReason fromWire(String wire) {
        if (wire == null) {
            return SERIALIZATION_ERROR;
        }
        return DlqReason.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
