import Link from "next/link";
import type { Metadata } from "next";

import { createTranslator, type Locale } from "@/i18n";

interface TreesIndexProps {
  params: Promise<{ locale: string }> | { locale: string };
}

const VIEW_KINDS = ["pedigree", "descendant", "fan", "hourglass", "family"] as const;

export async function generateMetadata({ params }: TreesIndexProps): Promise<Metadata> {
  const { locale } = await params;
  const translate = createTranslator(locale as Locale);
  return {
    title: translate("tree.treesListHeading"),
    description: translate("app.tagline"),
  };
}

export default async function TreesIndex({ params }: TreesIndexProps) {
  const { locale: rawLocale } = await params;
  const translate = createTranslator(rawLocale as Locale);
  return (
    <div className="mx-auto w-full max-w-4xl px-6 py-12">
      <h1 className="text-2xl font-semibold text-surface-foreground">
        {translate("tree.treesListHeading")}
      </h1>
      <p className="mt-2 text-sm text-surface-muted">
        {translate("tree.viewKindFootnote", { locale: rawLocale })}
      </p>
      <ul className="mt-6 grid grid-cols-1 gap-3 md:grid-cols-2">
        {VIEW_KINDS.map((viewKind) => (
          <li key={viewKind}>
            <Link
              className="block rounded border border-surface-sunken bg-surface-raised px-4 py-3 text-sm hover:bg-surface-sunken"
              href={`/${rawLocale}/trees/demo/${viewKind}?rootPersonId=00000000-0000-4000-8000-000000000000`}
            >
              {translate(
                viewKind === "pedigree"
                  ? "tree.viewKindPedigree"
                  : viewKind === "descendant"
                    ? "tree.viewKindDescendant"
                    : viewKind === "fan"
                      ? "tree.viewKindFan"
                      : viewKind === "hourglass"
                        ? "tree.viewKindHourglass"
                        : "tree.viewKindFamily",
              )}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
