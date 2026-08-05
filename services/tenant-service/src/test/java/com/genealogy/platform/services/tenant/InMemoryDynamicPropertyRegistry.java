/*
 * Test-local helper: an in-memory {@link DynamicPropertyRegistry}
 * populated before the Spring context starts. The Spring
 * {@code @DynamicPropertySource} contract only works with the
 * one registry Spring passes in, so we capture the values here
 * and re-apply them inside the {@code @DynamicPropertySource}
 * method.
 *
 * <p>Kept package private for the same reason as
 * {@link WireMockRunner}: this is a fixture detail that the
 * E1.4 template needs to bootstrap multiple platform
 * Testcontainers before the Spring context starts.
 */
package com.genealogy.platform.services.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.test.context.DynamicPropertyRegistry;

final class InMemoryDynamicPropertyRegistry implements DynamicPropertyRegistry {

    private final Map<String, Supplier<Object>> values = new HashMap<>();

    @Override
    public void add(String name, Supplier<Object> value) {
        values.put(name, value);
    }

    Map<String, Supplier<Object>> values() {
        return values;
    }
}
