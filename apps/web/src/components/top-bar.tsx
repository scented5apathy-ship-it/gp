import Link from "next/link";

import type { Locale } from "@/i18n";
import type { Translator } from "@/i18n";

/**
 * Top navigation bar. The bar is a Server Component because the
 * locale prefix is statically known and we want the initial paint
 * to include the correct language.
 *
 * Mobile UX: the navigation collapses into a `<details>` element
 * so we can ship the responsive shell without client JS. The
 * `prefers-reduced-motion` token (E1.5) disables the `open`
 * animation automatically through CSS rather than a JS check.
 */
interface TopBarProps {
  locale: Locale;
  translate: Translator;
  nav: {
    home: string;
    trees: string;
    people: string;
    sources: string;
    dna: string;
    settings: string;
  };
}

export function TopBar({ locale, nav }: TopBarProps) {
  const items: ReadonlyArray<{ href: string; label: string }> = [
    { href: `/${locale}`, label: nav.home },
    { href: `/${locale}/trees`, label: nav.trees },
    { href: `/${locale}/people`, label: nav.people },
    { href: `/${locale}/sources`, label: nav.sources },
    { href: `/${locale}/dna`, label: nav.dna },
    { href: `/${locale}/settings`, label: nav.settings },
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-surface-sunken bg-surface-raised/90 backdrop-blur">
      <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4 px-6 py-3">
        <Link
          href={`/${locale}`}
          className="text-base font-semibold text-surface-foreground focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
          aria-label="Genealogy Platform — home"
        >
          Genealogy
        </Link>

        <details className="relative md:hidden">
          <summary
            className="flex cursor-pointer items-center gap-2 rounded-md border border-surface-sunken bg-surface px-3 py-1.5 text-sm font-medium text-surface-foreground"
            aria-label="Open navigation"
          >
            Menu
          </summary>
          <nav
            aria-label="Primary"
            className="absolute right-0 mt-2 w-56 rounded-md border border-surface-sunken bg-surface-raised p-3 shadow-lg"
          >
            <ul className="flex flex-col gap-1" role="list">
              {items.map((item) => (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    className="block rounded px-3 py-2 text-sm text-surface-foreground hover:bg-surface-sunken focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                  >
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </details>

        <nav aria-label="Primary" className="hidden md:block">
          <ul className="flex items-center gap-1" role="list">
            {items.map((item) => (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className="rounded-md px-3 py-2 text-sm font-medium text-surface-foreground hover:bg-surface-sunken focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </header>
  );
}
