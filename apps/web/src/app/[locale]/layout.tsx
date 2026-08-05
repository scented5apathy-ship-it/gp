import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import type { Metadata } from "next";

import { Footer } from "@/components/footer";
import { SkipLink } from "@/components/skip-link";
import { TopBar } from "@/components/top-bar";
import { createTranslator, localeDirection, supportedLocales, type Locale } from "@/i18n";

/**
 * Locale-segment layout. The dynamic `[locale]` segment enforces
 * server-side validation via `notFound()` for unknown locale
 * prefixes so attackers cannot poison the cache with arbitrary
 * routes.
 *
 * The layout is a Server Component — every locale-specific string
 * flows through the `createTranslator` so the initial HTML is
 * already localised (no client-side hydration mismatch on first
 * paint).
 */

interface LocaleLayoutProps {
  children: ReactNode;
  params: Promise<{ locale: string }> | { locale: string };
}

export async function generateStaticParams(): Promise<Array<{ locale: Locale }>> {
  return supportedLocales.map((locale) => ({ locale }));
}

export async function generateMetadata({ params }: LocaleLayoutProps): Promise<Metadata> {
  const { locale } = await params;
  const translate = createTranslator(locale as Locale);
  return {
    title: {
      default: translate("app.title"),
      template: `%s · ${translate("app.title")}`,
    },
    description: translate("app.tagline"),
    alternates: {
      languages: Object.fromEntries(supportedLocales.map((l) => [l, `/${l}`])),
    },
  };
}

export default async function LocaleLayout({ children, params }: LocaleLayoutProps) {
  const { locale: rawLocale } = await params;
  if (!supportedLocales.includes(rawLocale as Locale)) {
    notFound();
  }
  const locale = rawLocale as Locale;
  const translate = createTranslator(locale);
  const dir = localeDirection[locale];

  return (
    <html lang={locale} dir={dir} suppressHydrationWarning>
      <body className="min-h-screen bg-surface text-surface-foreground antialiased">
        <SkipLink label={translate("nav.skipToContent")} />
        <div className="flex min-h-screen flex-col">
          <TopBar
            locale={locale}
            translate={translate}
            nav={{
              home: translate("nav.home"),
              trees: translate("nav.trees"),
              people: translate("nav.people"),
              sources: translate("nav.sources"),
              dna: translate("nav.dna"),
              settings: translate("nav.settings"),
            }}
          />
          <main id="main-content" tabIndex={-1} className="flex-1 focus:outline-none">
            {children}
          </main>
          <Footer
            translate={{
              rights: translate("footer.rights"),
              privacy: translate("footer.privacy"),
              terms: translate("footer.terms"),
            }}
          />
        </div>
      </body>
    </html>
  );
}
