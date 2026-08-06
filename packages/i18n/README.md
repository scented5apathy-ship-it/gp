# packages/i18n

i18n catalogues and helpers shared across the platform. Per
`design.md` §10.4 the UI uses ICU MessageFormat, locale routing,
RTL support and timezone/calendar adapters; nothing in the UI is
allowed to hard-code name order, gender, addresses or Gregorian
calendar.

Layout:

- `src/index.ts` — loader + helpers.
- `src/messages/` — locale catalogues (canonical English source,
  plural rules, extract script).

Public exports: `@gp/i18n`.

Owner: web-app team. Reviewers: i18n, Privacy (no PII in messages).