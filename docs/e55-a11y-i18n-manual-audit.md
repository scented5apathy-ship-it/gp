# E5.5 Accessibility & i18n — Manual Critical-Flow Audit Checklist

> Per `tasks.md` E5.5 ("Chạy axe tự động và manual critical-flow audit").
> The automated axe-cli smoke lives in `scripts/smoke-a11y.mjs` (source-level
> static checks). This document is the **manual** complement the QA team must
> run before signing off E5.5. It mirrors the WCAG 2.2 AA critical-flow matrix
> that the E6 Playwright + axe-core CI job will eventually automate; until then
> this checklist is a reviewer gate.

## Scope

- Tree view keyboard alternative (`/en/trees/[treeId]` and `/vi/trees/[treeId]`)
- Person profile + editor (`/en/persons/[treeId]/[personId]`)
- Timeline (`/en/persons/[treeId]/[personId]`)
- Place lookup (`/en/persons/[treeId]/[personId]`)
- Locale prefix routing (`en`, `vi`, `ar-XB` via `?pseudo=1`)
- Top navigation + skip link

## Test matrix

| # | Flow | Steps | WCAG SC | Pass criteria |
| --- | --- | --- | --- | --- |
| 1 | **Skip link** | Load any page → press Tab once | 2.4.1 Bypass Blocks | The skip link becomes visible and moves focus to `<main id="main-content">`. |
| 2 | **Keyboard tree navigation** | Open tree view → press Tab until the keyboard list is focused → press Arrow keys, Home, End, PageUp, PageDown, Enter | 2.1.1 Keyboard; 2.4.7 Focus Visible | Each key changes the selected person. `aria-selected="true"` updates. The list announces the new selection through the live region. |
| 3 | **Person profile read-only** | Navigate to a person → confirm read-only view | 1.3.1 Info & Relationships | Display name, living status, privacy, biography render in semantic `<dl>` with explicit labels. |
| 4 | **Person profile list view** | Click "Switch to list view" button (top-right of the editor) | 1.3.1; 4.1.2 Name, Role, Value | A `<table>` with `<caption>`, `scope="col"`, `scope="row"` appears. Tab order is row-major. |
| 5 | **Person profile edit** | Click "Edit" → type a new display name → click Save | 2.4.6 Headings & Labels; 3.3.1 Error ID | The input has an associated `<label>` and live announces "Saving…" then the success state. |
| 6 | **Conflict UX** | Edit a person in two tabs → save in the first, then save in the second | 4.1.3 Status Messages; 3.3.4 Error Prevention | The conflict banner appears with `role="alert"` `aria-live="assertive"`. The form shows the server body and the unsaved draft. |
| 7 | **Permission gate** | Load a person without edit rights | 1.3.1; 4.1.2 | All fields render but inputs carry `aria-readonly="true"` and `readOnly`. An inline notice points at the ABAC reason. |
| 8 | **Place map** | Type "Paris" → click Search | 1.3.1; 2.1.1 | Results render in a semantic `<ul>`; each `<li>` contains a `<button>` to pick the place. Keyboard users can navigate with Tab / Arrow. |
| 9 | **Place map degraded** | Stub the BFF `lookupPlace` to throw | 4.1.3 | The `degraded` banner appears with `role="status"` and announces through the live region. The picker disables submission. |
| 10 | **Timeline** | Pick a year range > 500y | 3.3.1 | The Load button disables; an inline cap notice appears. Submitting is blocked. |
| 11 | **Reduced motion** | Toggle `prefers-reduced-motion: reduce` in DevTools → reload | 2.3.3 Animation from Interactions | The `--motion-*` tokens collapse to `0.001ms`. The store data-attribute `data-reduced-motion="true"` reflects the preference. |
| 12 | **RTL pseudolocale** | `?pseudo=ar-XB` (dev-only) → reload | 1.3.2 Meaningful Sequence | `<html dir="rtl">` is set; every catalogue string is wrapped in `‫…‬`. The list/table re-flows correctly. |
| 13 | **Padded pseudolocale** | `?pseudo=en-XA` (dev-only) → reload | 1.4.5 Images of Text (n/a) | Every string is padded with `[…~…]`. Any hard-coded English surfaces as truncated padding. |
| 14 | **Vietnamese copy** | Set browser locale to `vi` → reload | 3.1.1 Language of Page | `<html lang="vi">` is set. Name-order policy uses `family-first`. Personal name renders as "Nguyễn Văn A". |
| 15 | **Name-order policy** | Open a person with `vi` locale | 1.3.1; 3.1.1 | The display name is in the locale-flavoured order. The `nameOrderPolicyFor("vi")` returns `family-first`. |
| 16 | **Live region announcements** | Toggle the list view | 4.1.3 | The polite live region announces "Switch to list view" / "Switch to form view" without taking focus. |
| 17 | **Focus return** | Open the edit form → click Cancel | 2.4.3 Focus Order | Focus returns to the Edit button that triggered the form. |
| 18 | **axe-core automated** | Run axe DevTools on each route | 1.x / 2.x / 4.x | No critical or serious violations. (E6 CI will automate this.) |

## Reviewer sign-off

| Reviewer | Date | Pass? | Notes |
| --- | --- | --- | --- |
| A11y lead | | | |
| Web lead | | | |
| Product | | | |

## References

- `.kiro/specs/genealogy-platform/requirements.md` R18 (Quốc tế hóa và khả năng tiếp cận)
- `.kiro/specs/genealogy-platform/design.md` §10.4 (Accessibility/i18n)
- `.kiro/specs/genealogy-platform/design.md` §15 (Kiểm thử → Accessibility)
- WCAG 2.2 AA — <https://www.w3.org/TR/WCAG22/>
- CLDR pseudolocales — <https://cldr.unicode.org/development/development-process/design-proposals/x-bidi-and-x-Accent-pseudo-locales>
- ADR-E0.5-14 (Place provider) — accepted; this audit confirms the vendor-free UX holds when the BFF stub throws.