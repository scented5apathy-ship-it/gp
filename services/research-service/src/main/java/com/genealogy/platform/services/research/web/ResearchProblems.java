package com.genealogy.platform.services.research.web;

import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Factory for RFC 9457 {@link ProblemDetail} responses. Every
 * 4xx / 5xx emitted by the research-service controllers goes
 * through this helper so the {@code type}, {@code title},
 * {@code status}, {@code errorCode} and {@code correlationId}
 * extensions stay uniform.
 *
 * <p>The {@code type} URI uses the
 * {@code https://errors.genealogy-platform.com/<errorCode>}
 * namespace declared in
 * {@code contracts/openapi/common/problem-details.yaml}.
 */
public final class ResearchProblems {

    private static final String TYPE_BASE = "https://errors.genealogy-platform.com/";

    private ResearchProblems() {
        // utility
    }

    public static final String ERR_INVALID_REQUEST = "invalid-request";
    public static final String ERR_INVALID_ETAG = "invalid-etag";
    public static final String ERR_REPOSITORY_NOT_FOUND = "repository-not-found";
    public static final String ERR_SOURCE_NOT_FOUND = "source-not-found";
    public static final String ERR_CITATION_NOT_FOUND = "citation-not-found";
    public static final String ERR_RESEARCH_TASK_NOT_FOUND = "research-task-not-found";
    public static final String ERR_HYPOTHESIS_NOT_FOUND = "hypothesis-not-found";
    public static final String ERR_CONFLICT_NOT_FOUND = "conflict-not-found";
    public static final String ERR_INVALID_TRANSITION = "invalid-transition";
    public static final String ERR_INVARIANT_VIOLATION = "research-invariant-violation";
    public static final String ERR_IDEMPOTENCY_CONFLICT = "idempotency-conflict";
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
