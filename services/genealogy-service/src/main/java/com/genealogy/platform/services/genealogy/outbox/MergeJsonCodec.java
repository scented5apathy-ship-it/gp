package com.genealogy.platform.services.genealogy.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Minimal JSON encoder for the merge event payloads.
 * The outbox row carries JSON UTF-8 bytes; the relay
 * (E4.7) upgrades to binary Avro at publish time per
 * ADR-E0.5-08. Using a private {@link ObjectMapper}
 * keeps the dependency surface narrow.
 */
final class MergeJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MergeJsonCodec() {
    }

    static byte[] encode(Object payload) {
        try {
            return MAPPER.writeValueAsBytes(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialise payload", ex);
        }
    }
}
