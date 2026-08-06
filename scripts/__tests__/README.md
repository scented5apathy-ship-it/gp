# scripts/__tests__

Vitest tests covering the quality-gate scripts under `scripts/`:

- `check-generated-code.test.mjs`
- `check-gradle-lockfile.test.mjs`
- `check-ownership-coverage.test.mjs`
- `lint-events.test.mjs`
- `lint-yaml.test.mjs`
- `test-contracts.test.mjs`

Per `AGENTS.md` §4 the quality gates must fail PR review when
broken; testing the scripts themselves prevents accidental
regressions in the gate logic.

Owner: platform-primary.