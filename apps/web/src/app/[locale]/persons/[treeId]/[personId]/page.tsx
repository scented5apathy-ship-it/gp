import type { Metadata } from "next";

import { createTranslator, type Locale } from "@/i18n";
import { PersonRoute, resolvePersonId } from "@/components/profile/person-route";

interface PageProps {
  params:
    | Promise<{ locale: string; treeId: string; personId: string }>
    | {
        locale: string;
        treeId: string;
        personId: string;
      };
  searchParams?:
    | Promise<Record<string, string | string[] | undefined>>
    | Record<string, string | string[] | undefined>;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale, personId } = await params;
  const translate = createTranslator(locale as Locale);
  return {
    title: `${translate("profile.heading")} · ${personId}`,
    description: translate("app.tagline"),
  };
}

function pickSearchParams(
  searchParams:
    | Promise<Record<string, string | string[] | undefined>>
    | Record<string, string | string[] | undefined>
    | undefined,
): Record<string, string | string[] | undefined> | undefined {
  if (!searchParams) return undefined;
  return searchParams instanceof Promise ? undefined : searchParams;
}

export default async function PersonProfilePage({ params, searchParams }: PageProps) {
  const { locale, treeId, personId } = await params;
  const resolved = pickSearchParams(searchParams);
  const validatedPersonId = resolvePersonId(personId);
  if (!validatedPersonId) {
    return null;
  }
  void resolved; // query params intentionally ignored for E5.4
  return (
    <PersonRoute
      locale={locale}
      translate={createTranslator(locale as Locale)}
      treeId={treeId}
      personId={validatedPersonId}
    />
  );
}
