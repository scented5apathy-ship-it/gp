/*
 * Test-local helper: an in-memory {@link DynamicPropertyRegistry}
 * the {@code TenantServiceApplicationIT} populates in
 * {@code @BeforeAll} and re-emits in {@code @DynamicPropertySource}
 * so the Spring context picks up the values once it starts.
 */
package com.genealogy.platform.services.tenant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.test.context.DynamicPropertyRegistry;

final class InMemoryRegistry {

    static final Map<String, Supplier<Object>> REGISTRY = new ConcurrentHashMap<>();

    static class Replay implements DynamicPropertyRegistry {
        @Override
        public void add(String name, Supplier<Object> value) {
            REGISTRY.put(name, value);
        }
    }
}
