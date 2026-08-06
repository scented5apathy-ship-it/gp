# apps/web/src/components

React components. Per `design.md` §10.1 the design system primitives
(`Button`, `Card`, `Tree`, `Timeline`, `Form`, `Table`, `Toast`,
`PersonCard`, `RelationshipEditor`, `MediaUploader`, `ConsentBanner`)
live in `packages/ui/`; this directory holds:

- App-shell components (`top-bar.tsx`, `footer.tsx`, `skip-link.tsx`)
  that compose primitives from `@gp/ui` with locale + tenant context.
- Feature widgets that depend on route state or BFF composition and
  therefore belong to the web app rather than the design system.

Owner: web-app team. Reviewer: Design, Accessibility.