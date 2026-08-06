# apps/web/src/styles

Global stylesheets. Per `design.md` §10.1 the design tokens live in
`packages/ui/src/tokens/`; this directory consumes them.

- `globals.css` — Tailwind base layer + global resets.
- `tokens.css` — emits the CSS variables that mirror the semantic
  tokens (`--gp-color-bg-canvas`, `--gp-color-fg-primary`, …) so
  non-utility styles can reference them.

Owner: web-app team. Reviewer: Design.