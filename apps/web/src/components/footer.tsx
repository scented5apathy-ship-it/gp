/**
 * Site footer. Static for now — locale-agnostic links to the
 * legal pages land in E15 once the partner surface is signed
 * off. The footer is a Server Component; no client interactivity
 * is required.
 */
interface FooterProps {
  translate: {
    rights: string;
    privacy: string;
    terms: string;
  };
}

export function Footer({ translate }: FooterProps) {
  const year = new Date().getFullYear();
  return (
    <footer className="border-t border-surface-sunken bg-surface-raised">
      <div className="mx-auto flex w-full max-w-6xl flex-col items-start justify-between gap-3 px-6 py-6 text-sm text-surface-muted md:flex-row md:items-center">
        <p>
          © {year} Genealogy Platform. {translate.rights}
        </p>
        <ul className="flex flex-wrap items-center gap-4" role="list">
          <li>
            <a
              href="/privacy"
              className="rounded text-surface-muted hover:text-surface-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              {translate.privacy}
            </a>
          </li>
          <li>
            <a
              href="/terms"
              className="rounded text-surface-muted hover:text-surface-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              {translate.terms}
            </a>
          </li>
        </ul>
      </div>
    </footer>
  );
}
