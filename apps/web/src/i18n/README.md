# apps/web/src/i18n

Next.js i18n bootstrap. Per `design.md` §10.4 the UI uses ICU
MessageFormat and locale routing.

- `messages/` — locale catalogues consumed at build/render time.
  Currently `en.ts` and `vi.ts` (canonical English + Vietnamese as
  the first additional locale) plus QA-only pseudolocales
  `en-XA.ts` (padded) and `ar-XB.ts` (RTL mirrored) — see
  `scripts/lint-a11y-i18n.mjs` and `docs/e55-a11y-i18n-manual-audit.md`.
- `createTranslator(locale)` — accepts both `Locale` tags and the
  QA-only pseudolocales. Production builds never set the
  `GENEALOGY_PSEUDOLOCALE` env var, so the `Locale` union stays
  the only path through `negotiateLocale`.
- `resolveDirection(locale)` — central `dir="ltr|rtl"` table,
  falls back to the base-locale entry for unknown BCP-47 tags.

Owner: web-app team. Reviewer: i18n, Accessibility.