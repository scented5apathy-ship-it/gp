# security/trivy

Trivy scanner configuration for filesystem and secret scanning. Per
`AGENTS.md` §4 the security CI gate runs Trivy on every PR.

Files:

- `trivy.yaml` — main filesystem + IaC + image scanner config.
- `trivy-secret.yaml` — secret-detection ruleset, complements
  Gitleaks (`gitleaks.toml`).

Owner: Security Engineering team.