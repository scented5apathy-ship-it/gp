# apps/web/src/lib/a11y

Accessibility hooks and helpers for the E5.5 / R18 web shell.
Every module is framework-aware but UI-agnostic so it can be
re-used outside the React tree (e.g. for the Storybook visual
test harness).

| Module | Purpose |
| --- | --- |
| `use-prefers-reduced-motion.ts` | `usePrefersReducedMotion()` — returns the live value of `prefers-reduced-motion: reduce`. SSR-safe. |
| `use-focus-return.ts` | `useFocusReturn()` — saves the focused element on mount and restores it on unmount (WCAG 2.2 SC 2.4.3). |
| `use-live-region-announcer.ts` | `useLiveRegionAnnouncer()` — pushes short status messages into a hidden `aria-live="polite"` region (WCAG 2.2 SC 4.1.3). Coalesces rapid updates. |
| `live-region.tsx` | `LiveRegion` — JSX wrapper that renders the polite live region. Separated from the hook because the runner is `.ts`-only. |
| `sr-only.tsx` | `SrOnly` — visually hide content while keeping it available to assistive technology. |

Owner: web-app team. Reviewer: i18n, Accessibility.