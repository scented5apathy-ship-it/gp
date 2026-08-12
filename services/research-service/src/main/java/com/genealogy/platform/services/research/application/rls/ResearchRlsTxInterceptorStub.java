package com.genealogy.platform.services.research.application.rls;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test seam: a no-op {@link ResearchRlsTxInterceptor} that
 * fulfils the constructor contract without binding the
 * PostgreSQL role. Used by unit tests that don't have a real
 * JDBC connection (the consumer tests, the workspace projection
 * stub, etc.).
 *
 * <p>Production code MUST use the real
 * {@link ResearchRlsTxInterceptor} bean — the no-op variant
 * intentionally skips the {@code SET LOCAL ROLE} +
 * {@code SET LOCAL app.tenant_id} binding so the test
 * environment can run without a database.
 */
public class ResearchRlsTxInterceptorStub extends ResearchRlsTxInterceptor {

    public ResearchRlsTxInterceptorStub() {
        super(noopJdbc(), true);
    }

    private static JdbcTemplate noopJdbc() {
        return new JdbcTemplate();
    }

    @Override
    public void bind() {
        // no-op for tests; do NOT propagate to the DB
    }
}
