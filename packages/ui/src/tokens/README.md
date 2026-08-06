# packages/ui/src/tokens

Semantic design tokens shared by every Next.js surface in `apps/web`
and by any future native client. Per `design.md` §10.1 the token scale
is separated from raw Tailwind utilities so theming (light, dark,
high-contrast, RTL-aware density) is a single source.

Planned exports:

- `colors.ts` — semantic palette (`bg.canvas`, `bg.surface`,
  `fg.primary`, `fg.muted`, `accent.*`, `danger.*`, `warning.*`,
  `success.*`) with light/dark/high-contrast triples. Raw palette
  primitives are kept in `colors.raw.ts` and not consumed directly.
- `spacing.ts`, `radius.ts`, `shadow.ts`, `motion.ts` — scales aligned
  with the Tailwind config but exposed as JS for non-CSS contexts
  (e.g. canvas/SVG tree rendering in `packages/ui/components/Tree`).
- `typography.ts` — type ramp with locale-aware font stacks; supports
  Latin, CJK, Cyrillic, Arabic/Hebrew (RTL) and Indic shaping.
- `css/` — generated CSS variables consumed by Tailwind presets.

Owner: web-app team with Design DRI. Reviewers: Accessibility (contrast
ratios), i18n (font fallback per locale).
