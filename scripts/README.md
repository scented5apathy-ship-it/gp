# scripts

Repository-level Node.js scripts invoked by `pnpm` and by CI. Per
`AGENTS.md` §4 these implement every quality gate:

- `format-check`, `lint:eslint`, `lint:yaml`, `lint:openapi`,
  `lint:protobuf`, `lint:events`, `lint:markdown`,
  `lint:ownership`, `lint:generated`.
- `typecheck`, `test:unit`, `test:contract`,
  `check:java`, `check:boundary`, `check:gradle:lock`.
- `check-generated-code`, `check-gradle-lockfile`,
  `check-monorepo-boundaries`, `check-monorepo-lockfile`,
  `check-ownership-coverage`.
- `cosign-sign`, `license-check`, `lint-events`, `lint-helm`,
  `lint-openapi`, `lint-protobuf`, `lint-yaml`.
- `run-gradle`, `security-ci`, `test-contracts`, `test-ts`,
  `ts-loader`.
- `__tests__/` — Vitest tests covering the lint/check scripts
  themselves.

Owner: platform-primary. Reviewers: every team consuming the
quality gates.