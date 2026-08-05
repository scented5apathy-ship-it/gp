/**
 * Top-level not-found boundary. The shell uses a Server Component
 * because the content is fully static; client interactivity is
 * unnecessary on a 404.
 *
 * The page intentionally avoids revealing the locale catalogue so
 * that crawlers and unauthenticated visitors cannot enumerate
 * locale metadata through 404 pages.
 */
import Link from "next/link";

import { Button } from "@genealogy/ui";

export default function NotFound() {
  return (
    <html lang="en" dir="ltr">
      <body className="min-h-screen bg-surface text-surface-foreground antialiased">
        <div className="mx-auto flex min-h-screen w-full max-w-xl flex-col items-center justify-center gap-6 px-6 text-center">
          <p className="text-sm font-medium uppercase tracking-widest text-primary">404</p>
          <h1 className="text-3xl font-semibold text-surface-foreground">Page not found</h1>
          <p className="text-base text-surface-muted">
            The URL you requested does not match any known route.
          </p>
          <Link href="/" aria-label="Back to home">
            <Button variant="primary">Back to home</Button>
          </Link>
        </div>
      </body>
    </html>
  );
}
