/**
 * SkipLink — the first focusable element in the document order.
 *
 * WCAG 2.2 SC 2.4.1 requires a keyboard-accessible mechanism to
 * bypass repeated navigation blocks. The skip link is hidden
 * visually until focused; the `.not-sr-only-focus` utility (see
 * `globals.css`) restores the focus styles.
 *
 * The link is rendered as a `<a>` rather than a Next.js `<Link>`
 * because it targets an in-page anchor and the router would
 * otherwise perform a needless navigation.
 */
interface SkipLinkProps {
  label: string;
  target?: string;
}

export function SkipLink({ label, target = "#main-content" }: SkipLinkProps) {
  return (
    <a
      href={target}
      className="not-sr-only-focus fixed left-4 top-4 z-50 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-md focus:outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
    >
      {label}
    </a>
  );
}
