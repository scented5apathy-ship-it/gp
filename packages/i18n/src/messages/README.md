# packages/i18n/src/messages

ICU MessageFormat catalogues for every locale supported by the
genealogy platform. Per `design.md` §10.4 the frontend uses ICU
MessageFormat, locale routing, RTL support and timezone/calendar
adapters; nothing in the UI is allowed to hard-code name order,
gender, addresses or Gregorian calendar.

Layout (added in later epics):

- `en.json` — canonical English source. Translation keys are namespaced
  by journey (`onboarding.*`, `tree.*`, `media.*`, `dna.*`,
  `notifications.*`, `admin.*`).
- `<locale>.json` — one file per locale (e.g. `vi.json`, `fr.json`,
  `ar.json`, `he.json`); RTL locales ship with the matching
  bidi-aware formatting helpers in `packages/ui/`.
- `plural-rules.ts` — locale-specific CLDR plural category helpers
  for languages that deviate from English.
- `extract.ts` — script that crawls the Next.js app and surfaces
  missing keys against `en.json`; runs in `pnpm test:unit` and
  blocks release when a translation drift is detected.

Privacy: messages MUST NOT contain PII, raw DNA strings, access
tokens or tenant identifiers. Owner: web-app team with locale DRI
per region in `config/teams.yaml`.
