# libs/platform-{errors,feature-flags,security,telemetry}

Placeholder marker for the shared Java platform libraries. The four
sub-modules each ship a Spring Boot starter consumed by every domain
service, BFF, public-api and worker:

| Library              | Purpose                                                                          |
| -------------------- | -------------------------------------------------------------------------------- |
| `platform-errors`    | RFC 9457 Problem Details + gRPC `ErrorEnvelope`, central `ErrorCodes` registry.  |
| `platform-feature-flags` | OpenFeature / Flagsmith façade with safe fallback, taxonomy and audit event. |
| `platform-security`  | OIDC validation, OpenFGA client, ABAC interface, signed URL signer, redaction.   |
| `platform-telemetry` | OTel SDK bootstrap, redaction processor, structured logging, RED metrics.        |

Per `design.md` §14 ("Không tạo shared domain model Java xuyên
service. Shared library chỉ dành cho cross-cutting ổn định như
telemetry, error envelope và test fixtures") these libraries are the
only ones allowed under `libs/`; aggregate roots MUST stay inside
`services/<svc>/domain/` and never be imported across service
boundaries (ArchUnit guard).

See the per-library `README.md` for detailed ownership, ADR links and
reviewer requirements.
