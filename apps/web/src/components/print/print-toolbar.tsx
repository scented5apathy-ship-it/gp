/**
 * apps/web/src/components/print/print-toolbar.tsx
 *
 * Lightweight client component that exposes a "Print this view"
 * button + the export panel. The toolbar:
 *
 *   - wraps the button in a `<details>` so the panel collapses by
 *     default (R18 — keep the chrome minimal);
 *   - uses the browser's native print dialog via `window.print()`
 *     so the screen stylesheet + print stylesheet already cover
 *     the page break / watermark / hidden chrome rules;
 *   - exposes the `<ExportRequestPanel>` for jobs that need a
 *     server-side rasterisation (Gotenberg) — the panel runs in
 *     parallel with the browser print dialog and never blocks it;
 *   - carries `data-no-print` so it disappears from the printed
 *     output.
 *
 * The component intentionally uses a native `<details>` (rather
 * than a custom modal) to avoid the focus-management + escape-key
 * surface of an overlay; that decision mirrors the E5.5 list/form
 * toggle button pattern.
 */
"use client";

import { useCallback, useEffect, useId, useState } from "react";

import type { Translator } from "@/i18n";

import { ExportRequestPanel } from "@/components/print/export-request-panel";
import type { PrintScope, PrivacyLevel } from "@/lib/print/print-policy";

export interface PrintToolbarProps {
  readonly locale: string;
  readonly translate: Translator;
  readonly tenantId: string;
  readonly treeId: string;
  readonly rootPersonId: string;
  readonly actorPseudoId: string;
  readonly defaultScope?: PrintScope;
  readonly defaultPrivacy?: PrivacyLevel;
  readonly variant?: "tree" | "person";
}

export function PrintToolbar({
  locale,
  translate,
  tenantId,
  treeId,
  rootPersonId,
  actorPseudoId,
  defaultScope,
  defaultPrivacy,
  variant = "tree",
}: PrintToolbarProps): JSX.Element {
  const summaryId = useId();
  const [open, setOpen] = useState<boolean>(false);
  const [hydrated, setHydrated] = useState<boolean>(false);
  useEffect(() => setHydrated(true), []);

  const handlePrint = useCallback(() => {
    if (typeof window !== "undefined" && typeof window.print === "function") {
      window.print();
    }
  }, []);

  const headingKey = variant === "person" ? "print.toolbarHeadingPerson" : "print.toolbarHeading";

  return (
    <div className="mt-4" data-no-print="true" data-print-toolbar>
      <details
        open={open}
        onToggle={(event) => setOpen((event.target as HTMLDetailsElement).open)}
        className="rounded border border-surface-sunken bg-surface-raised p-3"
      >
        <summary
          id={summaryId}
          className="cursor-pointer text-sm font-medium"
          aria-controls={`${summaryId}-panel`}
        >
          {translate(headingKey)}
        </summary>
        <div id={`${summaryId}-panel`} className="mt-3 flex flex-col gap-3">
          <button
            type="button"
            className="self-start rounded bg-primary px-3 py-1 text-sm text-on-primary"
            onClick={handlePrint}
            data-action="print-this-view"
          >
            {translate("print.printAction")}
          </button>
          <p className="text-xs text-surface-muted">{translate("print.printHelp")}</p>
          {hydrated ? (
            <ExportRequestPanel
              locale={locale}
              translate={translate}
              tenantId={tenantId}
              treeId={treeId}
              rootPersonId={rootPersonId}
              actorPseudoId={actorPseudoId}
              {...(defaultScope !== undefined ? { defaultScope } : {})}
              {...(defaultPrivacy !== undefined ? { defaultPrivacy } : {})}
            />
          ) : null}
        </div>
      </details>
    </div>
  );
}
