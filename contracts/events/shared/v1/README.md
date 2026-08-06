# contracts/events/shared/v1

Current version of the shared event value objects. Per
`ownership-catalog.md` §5.3 these schemas follow BACKWARD
compatibility with Apicurio (ADR-E0.5-08).

Schemas:

- `identifiers.avsc` — opaque pseudonymous IDs.
- `visibility.avsc` — tree/resource visibility (`PRIVATE |
  UNLISTED | PUBLIC`) per `design.md` §6.3.

Owner: contract-first platform team. Reviewers: Privacy, Security.