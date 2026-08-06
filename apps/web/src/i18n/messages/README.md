# apps/web/src/i18n/messages

Locale catalogues used by the Next.js app. The full catalogue (all
locales, plural rules, extract script) lives in `packages/i18n/` per
`design.md` §10.4; this directory holds only the per-locale message
files consumed at render time.

Current locales: `en.ts` (canonical English), `vi.ts` (first
additional locale). RTL locales (`ar.ts`, `he.ts`) are added in later
epics together with bidi-aware formatting helpers.

Owner: web-app team. Reviewer: i18n.