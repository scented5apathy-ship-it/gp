# .kiro/specs/genealogy-platform

Authoritative spec sources for the genealogy platform. Per
`AGENTS.md` §1 every agent MUST start here before making changes.

Documents in this directory:

| File                            | Purpose                                                                      |
| ------------------------------- | ---------------------------------------------------------------------------- |
| `requirements.md`               | Functional (R1–R18) and non-functional (NFR1–NFR8) requirements.             |
| `design.md`                     | Architectural decisions, service catalogue, data model, API/event contracts. |
| `tasks.md`                      | Epics and subtasks; checkbox flipped to `[x]` only after evidence committed. |
| `ownership-catalog.md`          | Per-service ownership, RACI, sync budget, SLO slices, deprecation windows.   |
| `architecture-decisions.md`     | ADR index + numeric thresholds (parallel of `design.md` §16).                |
| `agent-execution.md`            | How an agent must execute a task end-to-end.                                 |
| `personas-and-journeys.md`      | User journeys and persona-driven acceptance criteria.                        |
| `scale-and-slo.md`              | SLO class definitions, capacity tables, error-budget policy.                 |
| `privacy-and-legal-gate.md`     | Privacy gates (consent, residency, DNA, jurisdiction).                       |
| `glossary-and-policy-matrix.md` | Shared vocabulary and policy matrix.                                         |
| `evidence/`                     | Completion-evidence files required by `AGENTS.md` §4 (`<TASK_ID>.md`).       |
