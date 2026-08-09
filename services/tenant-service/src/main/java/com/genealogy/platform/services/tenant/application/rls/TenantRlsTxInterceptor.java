package com.genealogy.platform.services.tenant.application.rls;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Binds the runtime PostgreSQL role and {@code app.tenant_id} on the
 * active JDBC connection inside the current transaction.
 *
 * <p>This is the runtime counterpart of the database
 * {@code FORCE ROW LEVEL SECURITY} setting (E3.2a). Each command
 * service method calls {@link #bind()} as its first line — the call
 * must happen inside a {@code @Transactional} method so the
 * {@code SET LOCAL} bindings live exactly as long as the
 * transaction.
 *
 * <p>The bean refuses to run if the trusted tenant context is
 * missing — every command MUST come from an authenticated request
 * with a non-empty {@code X-Tenant-Id} header. When
 * {@code platform.tenant.header-required} is {@code false}, the
 * fallback uses the placeholder {@code tenant-missing} so the RLS
 * policy still matches zero rows (defense-in-depth per
 * design.md §5.1).
 *
 * <p>Why not an AOP @Around: the {@code @Transactional} advice runs
 * at {@code LOWEST_PRECEDENCE}, which means any aspect with a higher
 * precedence (including ours) executes BEFORE the transaction is
 * opened. Pre-proceed JDBC calls therefore happen on an autocommit
 * connection — the {@code SET LOCAL} is meaningless there. Calling
 * {@code bind()} as the first line of every command method keeps the
 * binding inside the open transaction where it actually takes effect.
 */
@Component
public class TenantRlsTxInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(TenantRlsTxInterceptor.class);

    private static final String PLACEHOLDER_TENANT_ID = "tenant-missing";

    private final JdbcTemplate jdbc;
    private final boolean headerRequired;

    public TenantRlsTxInterceptor(
            JdbcTemplate jdbc,
            @Value("${platform.tenant.header-required:true}") boolean headerRequired) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.headerRequired = headerRequired;
    }

    /**
     * Bind the PostgreSQL role + {@code app.tenant_id} on the
     * current transaction's JDBC connection. MUST be called as the
     * first statement inside a {@code @Transactional} method.
     *
     * @throws IllegalStateException when called outside a transaction
     * @throws MissingTenantContextException when the trusted context
     *         is empty AND {@code platform.tenant.header-required} is true
     */
    public void bind() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "TenantRlsTxInterceptor.bind() called outside a transaction; "
                            + "annotate the enclosing method with @Transactional");
        }
        String tenantId = currentTenantId();
        final String sql = "SET LOCAL ROLE tenant_service_app; "
                + "SET LOCAL app.tenant_id = '" + sanitize(tenantId) + "'";
        jdbc.execute(sql);

        // Register an afterCompletion listener so the binding is
        // observable in DEBUG logs without affecting behaviour.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("tenant RLS binding completed: tenantId={} status={}",
                                    tenantId, status);
                        }
                    }
                });
    }

    private String currentTenantId() {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        if (ctx.isAuthenticated()) {
            return ctx.getTenantId();
        }
        if (headerRequired) {
            throw new MissingTenantContextException(
                    "trusted tenant context is required to run a tenant-scoped command");
        }
        return PLACEHOLDER_TENANT_ID;
    }

    /**
     * Defensive escaping — the value comes from a validated JWT
     * subject or the {@code X-Tenant-Id} header that the trusted
     * context filter has already validated against the opaque-id
     * regex. The {@code sanitize} method is a belt-and-braces
     * guarantee against a future change in the validation pipeline.
     */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return PLACEHOLDER_TENANT_ID;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    public static class MissingTenantContextException extends RuntimeException {
        public MissingTenantContextException(String message) {
            super(message);
        }
    }
}
