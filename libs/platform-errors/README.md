# libs/platform-errors

Cross-cutting Spring Boot starter that provides the shared error
envelope used by every Java service and BFF.

Per `design.md` §7.1 ("URI versioned `/api/v1/...`, JSON, RFC 9457
Problem Details") and §12 (security/observability), every REST and
gRPC error is shaped consistently so Kong, the web-bff, the public-api
and clients can rely on a single contract. Concretely the starter
ships:

- `ProblemDetailsExceptionHandler` — Spring MVC exception handler
  emitting RFC 9457 Problem Details (`type`, `title`, `status`,
  `detail`, `instance`, `code`, `traceId`, `tenantPseudoId`).
- `ErrorEnvelope` Protobuf message for gRPC trailers (`google.rpc.Status`
  + `gp.platform.v1.ErrorMeta` with pseudonymous IDs only).
- `ErrorCodes` enum — the central registry of platform error codes
  (`TENANT_NOT_FOUND`, `CONSENT_REQUIRED`, `DNA_PAYLOAD_FORBIDDEN`,
  …). New codes require an ADR append.
- `TenantContext` + `RequestId` MDC keys wired into every log line.
- `ExceptionTranslator` — maps domain exceptions to envelope while
  enforcing the redaction list (no raw DNA, no access tokens, no PII).

This directory is intentionally empty in the E1.1 scaffold: the
Gradle module + `package-info.java` for the `com.genealogy.platform.libs`
package already exist; implementation lands in later epics.

Owner: platform-secondary. Reviewers: Security, Privacy (redaction
list).
