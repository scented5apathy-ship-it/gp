# apps/web/src/app/[locale]

Locale-prefixed routes. Per `design.md` §10.4 the platform supports
locale routing, RTL, ICU MessageFormat and timezone/calendar
adapters; nothing in the UI may hard-code name order, gender,
addresses or Gregorian calendar.

- `layout.tsx` — locale-aware layout: loads the matching catalogue
  from `@gp/i18n`, sets `<html lang>` and `dir`, wires the
  `<SkipLink>`, `<TopBar>` and `<Footer>`.
- `page.tsx` — landing page translated per locale.

Owner: web-app team. Reviewers: i18n, Accessibility (RTL mirror).