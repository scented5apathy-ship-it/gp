import Link from "next/link";
import type { Metadata } from "next";

import { Button } from "@genealogy/ui";

import { createTranslator, supportedLocales, type Locale } from "@/i18n";

interface HomeProps {
  params: Promise<{ locale: string }> | { locale: string };
}

export async function generateMetadata({ params }: HomeProps): Promise<Metadata> {
  const { locale } = await params;
  const translate = createTranslator(locale as Locale);
  return {
    title: translate("home.headline"),
    description: translate("home.subhead"),
  };
}

export default async function HomePage({ params }: HomeProps) {
  const { locale: rawLocale } = await params;
  if (!supportedLocales.includes(rawLocale as Locale)) {
    return null;
  }
  const locale = rawLocale as Locale;
  const translate = createTranslator(locale);

  return (
    <div className="mx-auto w-full max-w-5xl px-6 py-16">
      <section className="flex flex-col gap-6 text-balance" aria-labelledby="hero">
        <h1
          id="hero"
          className="text-4xl font-semibold tracking-tight text-surface-foreground md:text-5xl"
        >
          {translate("home.headline")}
        </h1>
        <p className="max-w-2xl text-lg text-surface-muted">{translate("home.subhead")}</p>
        <div className="flex flex-wrap gap-3" role="group" aria-label="Primary actions">
          <Button variant="primary" size="lg">
            {translate("home.ctaPrimary")}
          </Button>
          <Button variant="secondary" size="lg">
            {translate("home.ctaSecondary")}
          </Button>
        </div>
      </section>

      <section className="mt-16" aria-labelledby="features">
        <h2 id="features" className="text-2xl font-semibold text-surface-foreground">
          {translate("home.featuresTitle")}
        </h2>
        <ul className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3" role="list">
          <li className="rounded-lg border border-surface-sunken bg-surface-raised p-6">
            <h3 className="text-lg font-semibold text-surface-foreground">
              {translate("home.featureOfflineTitle")}
            </h3>
            <p className="mt-2 text-sm text-surface-muted">
              {translate("home.featureOfflineBody")}
            </p>
          </li>
          <li className="rounded-lg border border-surface-sunken bg-surface-raised p-6">
            <h3 className="text-lg font-semibold text-surface-foreground">
              {translate("home.featureI18nTitle")}
            </h3>
            <p className="mt-2 text-sm text-surface-muted">{translate("home.featureI18nBody")}</p>
          </li>
          <li className="rounded-lg border border-surface-sunken bg-surface-raised p-6">
            <h3 className="text-lg font-semibold text-surface-foreground">
              {translate("home.featureContractsTitle")}
            </h3>
            <p className="mt-2 text-sm text-surface-muted">
              {translate("home.featureContractsBody")}
            </p>
          </li>
        </ul>
      </section>

      <p className="mt-16 text-xs text-surface-muted">
        <Link href="/health" className="underline-offset-2 hover:underline">
          /health
        </Link>
      </p>
    </div>
  );
}
