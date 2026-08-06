# .github

GitHub repository configuration. Per `AGENTS.md` §1 the spec sources
of truth live in `.kiro/specs/genealogy-platform/`; everything in
this directory only orchestrates how the repo behaves on GitHub.

Contents:

- `workflows/` — GitHub Actions workflows. The active workflow is
  `security.yml` which implements the OSS security CI/CD gate and
  supply-chain hardening required by `architecture-decisions.md` and
  `AGENTS.md` §4 (Semgrep, Trivy, Syft/Grype, Gitleaks, ZAP, Checkov,
  Cosign, Renovate).
- `CODEOWNERS`, `dependabot.yml`, `ISSUE_TEMPLATE/`,
  `PULL_REQUEST_TEMPLATE/` (added in later epics) — inherited from
  `OWNERS` (repo root) and `config/teams.yaml`.

Top-level review ownership: `@genealogy/platform` (mirror of
`docs/ownership/OWNERS.md`).