# apps/web/test

Top-level test directory. Per `AGENTS.md` §4 and `design.md` §15 the
web app must pass unit, accessibility and performance tests before
merge.

Current tests:

- `perf-budget.test.ts` — Core Web Vitals guard (LCP, CLS, INP,
  TBT) tied to the SLO in `scale-and-slo.md` §5.2 (LCP p75 < 2.5 s).

Planned (later epics): Playwright e2e for onboarding, tree edit,
proposal, upload, import/export and privacy modes; axe-core
automated run with manual keyboard/screen-reader critical flows.

Owner: web-app team. Reviewer: SRE, Accessibility.