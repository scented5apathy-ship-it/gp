# packages/ui

Design system primitives and feature components used by every
Next.js surface in `apps/web`. Per `design.md` §10.1 ("Tailwind CSS
+ shadcn/ui; design token tách semantic color/spacing/type"):

- `src/index.ts` — barrel export (`Button`, `Card`, …).
- `src/tokens/` — semantic design tokens (colour, spacing, type,
  radius, shadow, motion).
- `src/button.tsx`, `src/card.tsx` — primitive components consumed
  across the platform.

Public exports: `@gp/ui`, `@gp/ui/tokens`, `@gp/ui/test`.

Owner: web-app team. Reviewers: Design, Accessibility.