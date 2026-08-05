package com.genealogy.platform.testing;

import java.util.ArrayList;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Aggregates multiple {@link TestcontainersFixture} instances so a
 * test can declare which infra it needs in one place. The order is
 * deterministic: PostgreSQL is registered first so the Spring
 * datasource is bound before the rest of the platform beans come
 * online.
 */
public final class TestcontainersFixtures {

    private final List<TestcontainersFixture> fixtures = new ArrayList<>();

    public static TestcontainersFixtures of(TestcontainersFixture... fixtures) {
        TestcontainersFixtures out = new TestcontainersFixtures();
        for (TestcontainersFixture f : fixtures) {
            out.fixtures.add(f);
        }
        return out;
    }

    public void applyTo(DynamicPropertyRegistry registry) {
        for (TestcontainersFixture f : fixtures) {
            f.overrideProperties(registry);
        }
    }

    public void stop() {
        for (TestcontainersFixture f : fixtures) {
            try {
                f.stop();
            } catch (RuntimeException ex) {
                // best-effort shutdown
            }
        }
    }
}
