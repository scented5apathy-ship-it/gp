package com.genealogy.platform.services.tenant.domain.ids;

/**
 * Strategy for generating new opaque identifiers. The runtime
 * implementation (E3.2c) wires UUIDv4 to this interface; the unit
 * tests inject a deterministic generator so invariants can be asserted
 * without coupling to the runtime clock or randomness.
 */
@FunctionalInterface
public interface IdGenerator {

    /**
     * @return a fresh opaque id value matching {@link OpaqueId#FORMAT}.
     *         Implementations are expected to generate values of length
     *         32-36 (UUIDv4 hex with or without dashes).
     */
    String nextId();
}