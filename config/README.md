# config

Single source of truth for cross-cutting repo configuration. Per
`AGENTS.md` §2 every value here is mirrored into the per-service
`OWNERS` and CODEOWNERS files; nothing else in the repo should
duplicate these values.

- `teams.yaml` — canonical team IDs used by `OWNERS`, CODEOWNERS,
  Helm chart labels and OTel resource attributes. Required by
  `ownership-catalog.md` §6 (RACI team names reference these IDs).
- `checkstyle/checkstyle.xml` — Checkstyle rules applied by the
  `platform-build-logic/java-conventions` Gradle plugin.
- `spectral.yaml` — Spectral lint rules for the OpenAPI specs in
  `contracts/openapi/`.

Owner: platform-primary. Reviewers: SRE, Security.