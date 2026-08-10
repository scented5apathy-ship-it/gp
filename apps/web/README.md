# apps/web

Next.js 14 PWA — the primary client surface for end users (genealogy
editor, researcher, public discovery, DNA owner, admin). Per
`design.md` §10.1 ("Next.js App Router, TypeScript strict. Tailwind
CSS + shadcn/ui; design token tách semantic color/spacing/type"):

- App Router under `src/app/` with locale segment `[locale]/` and a
  `health/` route for the Kubernetes probe.
- Feature surfaces under `src/components/` (currently shell:
  `top-bar`, `footer`, `skip-link`).
- Tree placeholder under `components/tree/` per ADR-E0.5-10
  §Consequences (1 K-node canvas cap). The real renderer is
  `DEFERRED` until the prototype benchmark in E5.1 / E5.3
  closes; see `bench/` for the harness.
- Typed REST client under `src/lib/api/generated/` produced by
  `packages/api-client/` from `contracts/openapi/`.
- i18n under `src/i18n/messages/` consuming `@gp/i18n` catalogues.
- Global stylesheet in `src/styles/` (Tailwind entry + tokens).
- Tests in `test/` (`perf-budget.test.ts` for Core Web Vitals +
  `tree-renderer-bench.test.mjs` for the bench harness).
- Helper scripts in `scripts/` (`codegen-openapi-client.mjs`).
- Bench harness under `bench/` (`tree-renderer-bench.mjs`,
  `synthetic-tree.mjs`) — closes ADR-E0.5-10 by comparing
  SVG_VIRTUALIZED / CANVAS_HIERARCHY / HYBRID options on the
  10 K / 100 K synthetic datasets.
- Public assets in `public/` (`icons/`, `manifest.webmanifest`,
  `robots.txt`) and `security.txt` under `.well-known/`.

Owner: web-app team. SLO 99.9 %, LCP p75 < 2.5 s. Runbook:
`runbook/web-app.md`. Accessibility is part of the definition of
done: axe + manual keyboard/screen-reader for every shipped flow.

## Tree renderer benchmark (E5.1)

The benchmark harness in `bench/` is the closure input for
ADR-E0.5-10 (tree layout / render engine, `DEFERRED` until the
prototype benchmark produces a p75 interaction time under
2,5 s on a 10 K-person synthetic tree on mid-tier mobile).

Available scripts:

- `pnpm --filter @genealogy/web bench:treeRenderer` — runs the
  10 K comparison (3 options × 3 repeats).
- `pnpm --filter @genealogy/web bench:treeRenderer:100k` —
  validates the Y3 p99 worst-case (`scale-and-slo.md` §2.2).
- `pnpm --filter @genealogy/web bench:treeRenderer:report` —
  emits a Markdown report at
  `.kiro/specs/genealogy-platform/evidence/benchmark/tree-renderer/10k-report.md`.
- `pnpm --filter @genealogy/web bench:treeRenderer:selfTest`
  — fast self-test used by `test/tree-renderer-bench.test.mjs`.

The contract lives at
`contracts/genealogy/tree-renderer-bench-policy.yaml` (mirror
`platform/helm/genealogy-platform/files/`). Validate it with
`pnpm lint:treeRendererBench` and `pnpm check:treeRendererBench`.

The Node bench measures layout + render time inside a real
`node:worker_threads` worker (Web Worker boundary proxy). The
real on-device profile (Playwright + mid-tier Chromium +
throttled CPU) lands in E5.3 with the tree projection API.
