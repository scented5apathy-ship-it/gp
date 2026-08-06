# packages/ui

Design system primitives and feature components used by every Next.js
surface in `apps/web`. Per `design.md` §10.1 ("Tailwind CSS +
shadcn/ui; design token tách semantic color/spacing/type"):

- `tokens/` — semantic design tokens (colour, spacing, type scale,
  radius, shadow, motion). Tokens are exported as CSS variables and
  as a typed JS module so the same scale feeds the web app and any
  future native client. Light/dark/high-contrast palettes are defined
  here, never inline.
- `components/` — `shadcn/ui`-based primitives (`Button`, `Dialog`,
  `Tree`, `Timeline`, `Form`, `Table`, `Toast`) plus composed feature
  widgets (`PersonCard`, `RelationshipEditor`, `MediaUploader`,
  `ConsentBanner`).
- `hooks/` — shared React hooks (`useTenantContext`, `useABAC`,
  `useFeatureFlag`, `useOfflineQueue`).
- `accessibility/` — axe-core configuration, focus management
  utilities and RTL smoke-test helpers; required by NFR-a11y.

Public exports (`@gp/ui`, `@gp/ui/tokens`, `@gp/ui/test`).

Owner: web-app team. Reviewers: Design, Accessibility. CI gate:
`pnpm test:unit` (jest + Testing Library + axe).
