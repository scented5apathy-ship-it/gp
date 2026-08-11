# apps/web/src/lib/i18n

Locale-aware rendering helpers for the E5.5 / R18 web shell.

| Module | Purpose |
| --- | --- |
| `name-order.ts` | `renderPersonName(name, locale)` — locale-aware display ordering for personal names. Closed-set policies: `given-first` (en), `family-first` (vi / ja / ko / hu / zh), `family-only`, `given-then-family-with-comma` (ar). |

Owner: web-app team. Reviewer: i18n.