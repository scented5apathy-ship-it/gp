package com.genealogy.platform.services.tenant.web;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Factory for RFC 9457 {@link ProblemDetail} responses. Every 4xx / 5xx
 * emitted by the tenant-service controllers goes through this
 * helper so the {@code type}, {@code title}, {@code status},
 * {@code errorCode} and {@code correlationId} extensions stay
 * uniform.
 *
 * <p>The {@code type} URI uses the
 * {@code https://errors.genealogy-platform.com/<errorCode>} namespace
 * declared in {@code contracts/openapi/common/problem-details.yaml}.
 */
public final class TenantProblems {

    private static final String TYPE_BASE = "https://errors.genealogy-platform.com/";

    private TenantProblems() {
        // utility
    }

    public static final String ERR_INVALID_REQUEST = "invalid-request";
    public static final String ERR_TENANT_NOT_FOUND = "tenant-not-found";
    public static final String ERR_MEMBERSHIP_NOT_FOUND = "membership-not-found";
    public static final String ERR_ENTITLEMENT_NOT_FOUND = "entitlement-not-found";
    public static final String ERR_INVALID_ETAG = "invalid-etag";
    public static final String ERR_SLUG_CONFLICT = "slug-conflict";
    public static final String ERR_IDEMPOTENCY_CONFLICT = "idempotency-conflict";
    public static final String ERR_INVALID_INVITE = "invalid-invite-token";
    public static final String ERR_CROSS_TENANT = "cross-tenant-access-denied";

    public static ProblemDetail of(HttpStatus status, String errorCode, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail == null ? "" : detail);
        pd.setType(URI.create(TYPE_BASE + errorCode));
        pd.setTitle(humanise(errorCode));
        if (errorCode != null) {
            pd.setProperty("errorCode", errorCode);
        }
        String correlationId = TrustedTenantContext.current().getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            pd.setProperty("correlationId", correlationId);
        }
        String tenantId = TrustedTenantContext.current().getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            pd.setProperty("xTenantId", tenantId);
        }
        return pd;
    }

    public static Map<String, Object> body(HttpStatus status, String errorCode, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_BASE + errorCode);
        body.put("title", humanise(errorCode));
        body.put("status", status.value());
        if (detail != null) {
            body.put("detail", detail);
        }
        body.put("errorCode", errorCode);
        String correlationId = TrustedTenantContext.current().getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            body.put("correlationId", correlationId);
        }
        return body;
    }

    public static MediaType contentType() {
        return MediaType.APPLICATION_PROBLEM_JSON;
    }

    private static String humanise(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "Request failed";
        }
        StringBuilder sb = new StringBuilder(errorCode.length());
        boolean upperNext = true;
        for (int i = 0; i < errorCode.length(); i++) {
            char c = errorCode.charAt(i);
            if (c == '-' || c == '_') {
                sb.append(' ');
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
