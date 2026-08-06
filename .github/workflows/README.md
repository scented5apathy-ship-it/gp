# .github/workflows

GitHub Actions workflow definitions consumed by `.github/README.md`.
Per `AGENTS.md` §4 every PR must pass the platform's quality gates
(`pnpm format:check`, `pnpm lint`, `pnpm typecheck`, `pnpm test:unit`,
`pnpm test:contract`, `pnpm check:java`, `pnpm check:boundary`,
`pnpm check:gradle:lock`); these are implemented as reusable
workflows here.

Active workflow:

- `security.yml` — OSS security CI/CD gate and supply-chain
  hardening. Runs Semgrep, Trivy, Syft/Grype, Gitleaks, Checkov and
  Cosign; signs and verifies images per `design.md` §12.

Planned workflows (later epics):

- `ci.yml` — pnpm + turbo + Gradle matrix on every PR.
- `release.yml` — semantic-release + container image promotion to the
  registry consumed by Argo CD (`platform/helm/`).
- `renovate.yml` — weekly dependency updates per
  `ownership-catalog.md` §5.5.

Top-level review ownership: `@genealogy/platform`.