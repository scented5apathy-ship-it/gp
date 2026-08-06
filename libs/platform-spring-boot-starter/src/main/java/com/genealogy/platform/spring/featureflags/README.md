# libs/platform-spring-boot-starter/.../spring/featureflags

OpenFeature façade used by every Java service. Per `design.md` §13
("OpenFeature SDK có safe default; Flagsmith outage không làm hỏng
critical flow") and `ownership-catalog.md` §5.4 every flag has an
owner, expiry, audit trail and a safe default that does not break
the request path.

Classes:

- `SafeFeatureClient` — typed façade over OpenFeature; falls back
  to the static default from `application.yml` if the Flagsmith
  provider is unavailable.

Privacy gate: flag values MUST NOT contain raw DNA or PII; they
MUST be booleans, opaque IDs or small JSON config blobs.

Owner: platform-secondary. Reviewers: Product, Privacy, Security.