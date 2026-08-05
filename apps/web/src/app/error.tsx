"use client";

/**
 * Root error boundary. Required by App Router — when a Server
 * Component throws (e.g. because the BFF returned a 5xx) the
 * framework renders the closest `error.tsx` instead of the 500
 * page so the shell never goes blank.
 *
 * The boundary is intentionally a Client Component because:
 *   1. `reset()` is only callable from the client.
 *   2. We log the error via `console.error` (Kong + the OTel
 *      SDK consume the platform-side log; see E6.3).
 *
 * Privacy: we MUST NOT include the raw `error.message` in the UI
 * because it can carry payload echoes (PII, request URLs with
 * query parameters). The boundary only renders a static copy and
 * hands the technical detail to the structured logger.
 */
import { useEffect } from "react";

import { Button } from "@genealogy/ui";

interface ErrorBoundaryProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function RootErrorBoundary({ error, reset }: ErrorBoundaryProps) {
  useEffect(() => {
    // Structured logging only — never re-throw, never echo to DOM.
    console.error("[web] unhandled error in App Router", {
      name: error.name,
      digest: error.digest,
    });
  }, [error]);

  return (
    <div className="mx-auto flex min-h-[60vh] w-full max-w-xl flex-col gap-4 px-6 py-16">
      <h1 className="text-2xl font-semibold text-surface-foreground">Something went wrong</h1>
      <p className="text-sm text-surface-muted">
        The page could not be rendered. The error has been logged with a correlation id; please
        retry.
      </p>
      <div>
        <Button variant="primary" onClick={reset}>
          Reload the page
        </Button>
      </div>
    </div>
  );
}
