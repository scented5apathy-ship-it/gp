# apps/web/src/app

Next.js App Router root. Per `design.md` §10.1 ("Next.js App Router,
TypeScript strict. Tailwind CSS + shadcn/ui") the file-based router
is the public/read-heavy entry point; complex editors live in
`src/components/` and run as Client Components.

Special segments:

- `[locale]/` — locale-prefixed routes (e.g. `/vi/...`,
  `/ar/...`). Implements the locale routing required by §10.4.
- `health/` — `/health` liveness/readiness route used by the
  Kubernetes probe.

Top-level files: `layout.tsx`, `page.tsx`, `error.tsx`, `loading.tsx`,
`not-found.tsx`, `manifest.ts`, `robots.ts`, `sitemap.ts`.

Owner: web-app team. Reviewer: i18n (locale segment), Accessibility.