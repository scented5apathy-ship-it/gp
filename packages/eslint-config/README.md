# packages/eslint-config

Shared ESLint configuration applied by every TypeScript package and
app (`apps/web`, `packages/*`, `tools/*`). Per `AGENTS.md` §4
(`pnpm lint`) the same lint rules run in CI and locally; the
configs here are the single source.

Layout:

- `src/` — flat-config entrypoints (`index.ts`, plus per-stack
  presets for Next.js, library and tooling code).

Owner: web-app team. Reviewers: every TS package owner.