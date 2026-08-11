/**
 * apps/web/src/lib/a11y/use-focus-return.ts
 *
 * Save the currently focused element on mount and restore focus to
 * it on unmount. Required by WCAG 2.2 SC 2.4.3 (Focus Order) and
 * SC 1.3.1 (Info and Relationships) for any view that conditionally
 * mounts an editor or detail pane — when the user dismisses the
 * pane focus must return to the trigger so they do not lose their
 * place in the document.
 *
 * Usage:
 *
 *   function PersonEditor({ onClose }: PersonEditorProps) {
 *     useFocusReturn();           // restores on unmount
 *     return <dialog>…</dialog>;
 *   }
 *
 * The hook is SSR-safe: on the server render `document` is
 * undefined so we skip the snapshot.
 */
"use client";

import { useEffect, useRef } from "react";

export function useFocusReturn(): void {
  const previous = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (typeof document === "undefined") return undefined;
    const active = document.activeElement;
    previous.current = active instanceof HTMLElement ? active : null;
    return () => {
      const target = previous.current;
      if (!target || typeof target.focus !== "function") return;
      try {
        target.focus({ preventScroll: false });
      } catch {
        target.focus();
      }
    };
  }, []);
}
