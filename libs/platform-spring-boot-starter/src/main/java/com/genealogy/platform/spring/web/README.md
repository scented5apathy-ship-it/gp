# libs/platform-spring-boot-starter/.../spring/web

Web-layer glue for the trusted tenant context.

Classes:

- `TrustedContextFilter` — Servlet filter that extracts the
  pseudonymous labels propagated by Kong (or by the BFF on
  internal hops), validates them against the request signature and
  populates `TrustedTenantContext`.

Privacy: the filter MUST validate that every propagated label is
opaque; raw identifiers (`X-User-Id`, `X-Tenant-Id`) are rejected.

Owner: platform-secondary. Reviewers: Security, Privacy.