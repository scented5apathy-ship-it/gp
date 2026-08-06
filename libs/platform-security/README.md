# libs/platform-security

Cross-cutting Spring Boot starter for the authentication and
authorization glue shared by every Java service.

Per `design.md` §6, §12 and `ownership-catalog.md` §3 the platform
delegates credential storage to Keycloak, relationship decisions to
OpenFGA, ABAC overlays to the calling service and mTLS to Istio. This
starter is the consistent entry point that wires those components
together so individual services do not reinvent them.

Concretely the starter ships:

- `OidcTokenValidator` — validates access token issuer, audience and
  JWKS once at the edge; BFFs and services trust the resolved
  `TenantContext` carried via gRPC metadata (never via raw JWT).
- `OpenFgaClient` — typed façade with cache, circuit breaker and
  batched `check` for hot reads. Cache invalidation is wired to the
  `gp.platform.v1.PolicyChanged` event.
- `AbacEvaluator` — interface every service implements to overlay
  living/minor status, DNA consent, residency and contextual deny
  on top of an OpenFGA `allow`.
- `SignedUrlSigner` — short-lived HMAC / asymmetric signed URLs for
  media downloads, GEDCOM exports and audit bundles; keys are pulled
  from Vault / cloud KMS, never committed.
- `RedactionFilter` — Logback filter that scrubs PII, DNA and tokens
  from structured logs and OTel attributes before emission.

This directory is intentionally empty in the E1.1 scaffold: the
Gradle module + `package-info.java` for the
`com.genealogy.platform.libs` package already exist; implementation
lands in later epics.

Owner: platform-secondary. Reviewers: Security, Privacy.
