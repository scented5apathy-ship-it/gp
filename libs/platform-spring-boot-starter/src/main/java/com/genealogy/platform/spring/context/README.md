# libs/platform-spring-boot-starter/.../spring/context

Trusted tenant + request context. Per `design.md` §6.1 every service
that handles a request MUST operate inside a trusted context that
carries only pseudonymous IDs (`tenantPseudoId`, `userPseudoId`,
`traceId`, `requestId`, `membershipId`).

Classes:

- `TrustedTenantContext` — request-scoped holder injected into
  services; never carries raw JWTs or Keycloak subject IDs.

Privacy: any new field MUST be opaque + pseudonymous; a raw
identifier is a privacy finding.

Owner: platform-secondary. Reviewers: Security, Privacy.