package com.genealogy.platform.services.research.outbox;

/**
 * Closed-set reasons a DLQ row lands in the dead-letter table.
 * Mirrors {@code V3__outbox_and_workspace.sql}.
 *
 * <p>The classification is intentionally narrow — anything
 * outside the closed-set is a programming error and the
 * application throws.
 */
public enum ResearchDlqReason {
    /** Payload could not be serialised to Avro. */
    PAYLOAD_ENCODE_FAILED,
    /** Producer reported a transient error after the retry budget. */
    PRODUCER_RETRY_EXHAUSTED,
    /** Schema-registry reports the payload is incompatible with the registered schema. */
    SCHEMA_INCOMPATIBLE,
    /** Event type is not in the closed-set we publish. */
    UNKNOWN_EVENT_TYPE
}
