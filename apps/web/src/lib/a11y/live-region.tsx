/**
 * apps/web/src/lib/a11y/live-region.tsx
 *
 * Visual wrapper for the live-region announcer element. Separated
 * from `use-live-region-announcer.ts` because the hook lives in a
 * `.ts` module (so the Node `node --test` runner can import it
 * without a TSX loader) while the element is JSX and must be a
 * `.tsx` module.
 */
import type { LiveRegionAnnouncer } from "./use-live-region-announcer";

export interface LiveRegionProps {
  readonly announcer: LiveRegionAnnouncer;
  readonly message: string;
}

export function LiveRegion({ message }: LiveRegionProps): JSX.Element {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-atomic="true"
      data-a11y-live-region="polite"
      className="sr-only"
    >
      {message}
    </div>
  );
}
