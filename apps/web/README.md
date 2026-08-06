# apps/web

Next.js 14 PWA — the primary client surface for end users (genealogy
editor, researcher, public discovery, DNA owner, admin). Per
`design.md` §10.1 ("Next.js App Router, TypeScript strict. Tailwind
CSS + shadcn/ui; design token tách semantic color/spacing/type"):

- App Router under `src/app/` with locale segment `[locale]/` and a
  `health/` route for the Kubernetes probe.
- Feature surfaces under `src/components/` (currently shell:
  `top-bar`, `footer`, `skip-link`).
- Typed REST client under `src/lib/api/generated/` produced by
  `packages/api-client/` from `contracts/openapi/`.
- i18n under `src/i18n/messages/` consuming `@gp/i18n` catalogues.
- Global stylesheet in `src/styles/` (Tailwind entry + tokens).
- Tests in `test/` (`perf-budget.test.ts` for Core Web Vitals).
- Helper scripts in `scripts/` (`codegen-openapi-client.mjs`).
- Public assets in `public/` (`icons/`, `manifest.webmanifest`,
  `robots.txt`) and `security.txt` under `.well-known/`.

Owner: web-app team. SLO 99.9 %, LCP p75 < 2.5 s. Runbook:
`runbook/web-app.md`. Accessibility is part of the definition of
done: axe + manual keyboard/screen-reader for every shipped flow.