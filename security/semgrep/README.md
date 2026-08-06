# security/semgrep

Custom Semgrep rules layered on top of the OSS ruleset. Per
`AGENTS.md` §4 the platform's security CI gate runs Semgrep on every
PR; this directory holds the local rules that capture platform-
specific concerns (e.g. "no raw PII/DNA in event payload",
"OpenFGA tuple must include resource type").

Files:

- `semgrep.local.yaml` — rule definitions.

Owner: Security Engineering team. Reviewers: Privacy, AppSec.