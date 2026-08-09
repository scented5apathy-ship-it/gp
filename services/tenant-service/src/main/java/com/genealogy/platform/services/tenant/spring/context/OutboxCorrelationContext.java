package com.genealogy.platform.services.tenant.spring.context;

import java.util.Objects;
import org.slf4j.MDC;

/**
 * Pulls the correlation id and trace id out of the SLF4J MDC (which
 * is populated by {@code TrustedContextFilter}) so the application
 * services can stamp every outbox row with the same correlation
 * metadata that the audit hook and the OTel exporter use.
 *
 * <p>The MDC is reset by the trusted context filter at the end of
 * the request; the application service only reads, never writes.
 */
public final class OutboxCorrelationContext {

    private OutboxCorrelationContext() {
        // utility
    }

    public static String correlationId() {
        String value = MDC.get("correlation_id");
        return value == null || value.isBlank() ? "n/a" : value;
    }

    public static String traceId() {
        String value = MDC.get("trace_id");
        if (value == null || value.isBlank()) {
            // Without a current span the OTel bridge will not populate
            // "trace_id". The outbox row needs a non-null value (NOT
            // NULL constraint) so we fall back to a stable placeholder.
            return "00-00000000000000000000000000000000-0000000000000000-00";
        }
        return Objects.requireNonNull(value);
    }
}
