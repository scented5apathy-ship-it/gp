package com.genealogy.platform.services.research.domain.ids;

/**
 * Port for generating opaque resource ids. The default
 * implementation is a UUID v4 generator (see
 * {@code ApplicationConfig#uuidV4IdGenerator()}); unit tests
 * override the bean with a deterministic counter-based
 * implementation so the assertions stay stable.
 */
@FunctionalInterface
public interface IdGenerator {

    String nextId();
}
