# libs/platform-spring-boot-starter/.../resources/META-INF/genealogy

Starter-default YAML loaded by `PlatformProperties`. Per
`AGENTS.md` §2 and `architecture-decisions.md` the only allowed
sources of configuration values are:

- `config/teams.yaml` (canonical team IDs).
- `META-INF/genealogy/platform-spring-boot-defaults.yml` (starter
  defaults).
- Per-service `application.yml` (overrides).

No literal version, no PII, no DNA, no webhook payload may appear
in any of these files.

Owner: platform-secondary. Reviewers: Security, Privacy.