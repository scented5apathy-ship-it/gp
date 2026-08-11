/**
 * apps/web/src/lib/a11y/use-live-region-announcer.ts
 *
 * Push short status messages into a visually hidden, screen-reader
 * live region. WCAG 2.2 SC 4.1.3 (Status Messages) requires that
 * status changes (selection, async completion, conflict) are
 * announced without taking focus. The hook returns both the
 * announce callback and the message string the consumer should
 * render inside a `<LiveRegion>` element (see
 * `./live-region.tsx`).
 *
 * Why split the file: this hook lives in a `.ts` module so the
 * Node `node --test` runner can import it without a TSX loader;
 * the JSX wrapper lives in the sibling `live-region.tsx` file.
 *
 * Messages are coalesced so a rapid succession of `announce("X")`
 * calls within `coalesceMs` collapses to the most recent one —
 * screen-readers do not queue a wall of text.
 */
"use client";

import { useCallback, useEffect, useRef, useState } from "react";

const DEFAULT_COALESCE_MS = 250;

export interface LiveRegionAnnouncer {
  readonly announce: (message: string) => void;
}

export function useLiveRegionAnnouncer(coalesceMs: number = DEFAULT_COALESCE_MS): {
  readonly announcer: LiveRegionAnnouncer;
  readonly message: string;
} {
  const [message, setMessage] = useState<string>("");
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pending = useRef<string>("");

  const announce = useCallback(
    (next: string) => {
      pending.current = next;
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => {
        if (pending.current) setMessage(pending.current);
        pending.current = "";
      }, coalesceMs);
    },
    [coalesceMs],
  );

  useEffect(() => {
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return { announcer: { announce }, message };
}
