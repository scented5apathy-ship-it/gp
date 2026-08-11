# E5.6 — Print / export manual critical-flow audit

This document captures the WCAG 2.2 AA + J-SHARE-3 sign-off matrix
for the print/export surface delivered by E5.6. The audit is the
human counterpart to `scripts/smoke-print-export.mjs` (automated
source-level checks) and `scripts/lint-print-export.mjs` (deep
validator). Both run in CI; this file is the manual sign-off.

## 1. J-SHARE-3 walk-through

J-SHARE-3 ("Print and PDF export") is the persona-driven journey
that E5.6 supports. The table below traces each trigger /
permission / data-visible / failure-path bullet from
`.kiro/specs/genealogy-platform/personas-and-journeys.md` line
256 to the E5.6 component + linter gate that covers it.

| Persona bullet                                          | E5.6 surface                                                                                                                           | Gate                                                                              |
| ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Trigger: Viewer requests printable view                 | `<PrintToolbar>` mounts under `<PersonRoute>` + `<TreeViewRoute>` with `<details>` toggle                                              | `smoke-print-export` check 4; `lint-print-export.checkToolbarHidesOnPrint`        |
| Personas: Viewer, Owner                                 | `<ExportRequestPanel>` is gated by tenant-scoped BFF shim; UI never decides who can export                                             | Server-side enforcement (E11.3) — E5.6 ships the contract only                    |
| Minimum permissions: read on subtree                    | `<PrintToolbar>` only renders when a `rootPersonId` is known; `defaultScope="currentPerson"` for person variant                        | `lint-print-export.checkPanelConsumesClosedSets`                                  |
| PDF generation via Temporal + Gotenberg                 | `lib/print/client.ts` posts to `/api/v1/trees/{treeId}/print-jobs` (E11.3 server)                                                      | `lint-print-export.checkBffShimHeaders`                                           |
| Data visible: same redacted data as on-screen           | `redaction-preview.ts` mirrors server `RedactionSummary`; UI never re-redacts                                                          | `lint-print-export.checkRequiredPrintKeys` (no client-side field-strip code path) |
| Watermark with token hash                               | `resolveDefaultWatermark(privacy, hasShareToken)` covers all four `WATERMARK_MODES`; print stylesheet renders `[data-print-watermark]` | `lint-print-export.checkPolicyBounds`, `checkPrintCss`                            |
| Failure path: Gotenberg unavailable                     | `client.ts` `catch` returns `failed` snapshot with `reasonCode: "bff-unavailable"`; live region announces `print.statusFailed`         | `lint-print-export.checkBffShimHeaders`                                           |
| Failure path: long generation                           | State machine polls with `deadlineMs`; UI shows `print.statusRunning` + `print.statusExpired` for TTL                                  | `export-job.test.ts` exercises the full lifecycle                                 |
| Failure path: expired signed URL                        | `signedUrlTimeRemaining` returns `expired` once TTL elapses; refresh button re-issues                                                  | `signed-url-handle.test.ts` exercises fresh / refreshing / expired transitions    |
| Success metric: report ready p95 ≤ 30 s for ≤ 200 pages | Redaction + page-break limits surfaced; `EXPORT_MAX_NODES_PER_JOB = 1000` cap prevents "200 pages" bombs                               | `lint-print-export.checkPolicyBounds`                                             |

## 2. WCAG 2.2 AA — critical-flow matrix

The audit covers the print/export surface only; the broader
PWA audit is the responsibility of E6 Playwright + axe-core.

| #   | Criterion                                          | Surface                                                                                                                                        | Evidence                                                            |
| --- | -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| 1   | 1.3.1 Info and Relationships (semantic structure)  | `<ExportRequestPanel>` uses `<fieldset>` + `<legend>` for grouped checkboxes; `<select>` for closed-set enums; live region has `role="status"` | `export-request-panel.tsx` lines 200-260                            |
| 2   | 1.4.3 Contrast (Minimum)                           | Watermark placeholder uses `opacity: 0.18` over white background — non-text decorative content; primary buttons inherit token palette (≥4.5:1) | `print.css` watermark block + `tokens.css` colour tokens            |
| 3   | 2.1.1 Keyboard (all functionality operable)        | Submit / refresh / reset buttons + scope/format/privacy/layout/page-break selects all reachable via Tab; submit gated by `disabled` only       | `export-request-panel.tsx` keyboard interaction                     |
| 4   | 2.3.3 Animation from Interactions (reduced motion) | Print stylesheet explicitly honours `prefers-reduced-motion: reduce` inside `@media print`                                                     | `print.css` reduced-motion block; `lint-print-export.checkPrintCss` |
| 5   | 2.4.3 Focus Order                                  | `<details>` toggle keeps DOM order; submit/refresh/reset follow the inputs left-to-right top-to-bottom                                         | `export-request-panel.tsx` source order                             |
| 6   | 2.4.6 Headings and Labels                          | `<h2 id="…-heading">` describes the panel; every `<select>` carries an explicit `<label>` and `aria-describedby` pointing at helper text       | `export-request-panel.tsx`                                          |
| 7   | 2.5.3 Label in Name                                | All action buttons expose the i18n catalogue string as accessible name; `aria-pressed` carries the toggled state                               | `export-request-panel.tsx`                                          |
| 8   | 3.3.1 Error Identification                         | Live region announces `print.statusQueued / Running / Ready / Failed / Expired`; `data-export-status` attribute lets automated tests assert    | `lint-print-export.checkPanelConsumesClosedSets`                    |
| 9   | 3.3.2 Labels or Instructions                       | Every input has both a label and helper text (e.g. "Watermark" + "Adds the token hash to the watermark obligation (R15 row W).")               | `export-request-panel.tsx`                                          |
| 10  | 4.1.2 Name, Role, Value                            | `<LiveRegion>` carries `role="status" aria-live="polite" aria-atomic="true"`; `PrintToolbar` `<details>` carries `aria-controls`               | `live-region.tsx`, `print-toolbar.tsx`                              |

## 3. Pseudolocale audit

The QA-only catalogues (`en-XA`, `ar-XB`) are mirrored into
every `print.*` key. Pseudolocale regression tests (visual
layout blowups for `en-XA`, RTL drift for `ar-XB`) belong in
E6 Playwright; this audit confirms the source-level parity.

- `print.toolbarHeading`, `print.scopeHelp`, `print.formatHelp`
  and every sub-namespace key exist in `en-XA.ts` and
  `ar-XB.ts` — verified by `lint-print-export.checkRequiredPrintKeys`.
- The `en-XA` wrapping (`[…]` + `~` padding) and `ar-XB`
  wrapping (`‫…‬`) apply to every new key.
- The import gate (`checkImportGate`) forbids
  `apps/web/src/components/print/*` from importing either
  pseudolocale directly.

## 4. Threat-model cross-check

| Threat                                     | Mitigation in E5.6                                                                                                         |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| Print output leaks redacted data           | Redaction summary mirrored from server (`redaction-preview.ts`); UI never re-redacts (R15)                                 |
| Long-running render blocks request         | `submitExportJob` returns immediately with `queued` snapshot; `pollExportJob` enforces `deadlineMs` (default 5 s)          |
| Signed URL persists past audit window      | TTL clamped to [60 s, 24 h] by `clampSignedUrlTtlSeconds`; expired state surfaces a refresh CTA                            |
| Tenant bypass via crafted export           | `X-Tenant-Id` + `Idempotency-Key` headers in the BFF shim; BFF re-enforces tenant boundary per R10 (`design.md` §8.3)      |
| Secret leakage through audit metadata      | `ExportAuditMetadata` carries `actorPseudoId` / `tenant_pseudo_id` only (R15 / glossary §2.2)                              |
| Cross-service coupling via print pipeline  | E5.6 ships the client surface only; `import-export-service` (E11.3) owns the server side; no `services/*` imports          |
| Pseudolocale leaks into production bundle  | `checkImportGate` + `en-XA`/`ar-XB` markers — `apps/web/src/i18n/index.ts` keeps them out of `Locale` / `supportedLocales` |
| Cache poisoning via stale print stylesheet | `print.css` is imported by `globals.css`; tokens bound to URL+tenant (per P-07 mitigation)                                 |

## 5. Sign-off

| Reviewer           | Role                 | Sign-off criteria                                                       | Date | Result |
| ------------------ | -------------------- | ----------------------------------------------------------------------- | ---- | ------ |
| Web lead           | Print UX             | J-SHARE-3 walk-through + §2 WCAG matrix                                 | TBD  | TBD    |
| Performance lead   | NFR2 budget          | `EXPORT_MAX_NODES_PER_JOB` cap honoured; no sync blocking               | TBD  | TBD    |
| AppSec partner     | Watermark obligation | R15 row W + glossary §2.2 obligations table honoured                    | TBD  | TBD    |
| Accessibility lead | WCAG 2.2 AA matrix   | §2 rows 1–10 all satisfied; reduced-motion + keyboard coverage verified | TBD  | TBD    |
| Data Protection    | Tenant isolation     | `X-Tenant-Id` + server-side re-enforcement documented                   | TBD  | TBD    |

> E5.6 ships with the source-level evidence (linter + smoke +
> unit tests) committed. The table above is the **human** sign-off
> matrix; populate it when the next stakeholder review lands.
