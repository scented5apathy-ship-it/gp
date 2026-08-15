/**
 * apps/web/src/lib/a11y/live-region.tsx
 *
 * E12.4 — Live region announcer. The runtime wires the
 * announcer into every async operation (selection, save,
 * conflict, error, sync). The component is intentionally
 * visually hidden via `sr-only.tsx` and uses ARIA polite
 * politeness.
 */
"use client";

import { useLiveRegionAnnouncer } from "./use-live-region-announcer";

const SR_ONLY_STYLE: Readonly<Record<string, string>> = {
  position: "absolute",
  width: "1px",
  height: "1px",
  padding: "0",
  margin: "-1px",
  overflow: "hidden",
  clip: "rect(0, 0, 0, 0)",
  whiteSpace: "nowrap",
  borderWidth: "0",
};

export interface LiveRegionProps {
  readonly id?: string;
  readonly ariaLive?: "polite" | "assertive";
  readonly message: string;
  readonly announcer?: { readonly announce: (message: string) => void };
}

export function LiveRegion({ id, ariaLive = "polite", message }: LiveRegionProps) {
  return (
    <div id={id} role="status" aria-live={ariaLive} style={SR_ONLY_STYLE}>
      {message}
    </div>
  );
}

export function useAnnouncer(coalesceMs?: number) {
  return useLiveRegionAnnouncer(coalesceMs);
}