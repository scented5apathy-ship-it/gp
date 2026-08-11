/**
 * apps/web/src/components/tree-view/route-page.tsx
 *
 * Server-component helper that builds a tree-view page from the
 * `[locale]/trees/[treeId]/[viewKind]` route. The helper exists
 * so the five pages (`pedigree`, `descendant`, `fan`,
 * `hourglass`, `family`) only carry their `viewKind` constant
 * and never drift in the surrounding metadata/redirect logic.
 */
import { notFound } from "next/navigation";
import type { Metadata } from "next";

import type { Locale } from "@/i18n";
import { createTranslator } from "@/i18n";
import type { TreeProjectionViewKind } from "@genealogy/api-client";

import { TreeViewRoute, resolveRootPersonId } from "@/components/tree-view/tree-view-route";

export interface TreeViewPageProps {
  readonly locale: string;
  readonly treeId: string;
  readonly viewKind: TreeProjectionViewKind;
  readonly searchParams: Record<string, string | string[] | undefined> | undefined;
}

function stringifyQuery(params: Record<string, string | string[] | undefined>): string {
  const out = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (Array.isArray(value)) {
      for (const v of value) out.append(key, v);
    } else if (value !== undefined) {
      out.append(key, value);
    }
  }
  return out.toString();
}

export function buildTreeViewMetadata(
  viewKind: TreeProjectionViewKind,
  locale: string,
  treeId: string,
): Metadata {
  const translate = createTranslator(locale as Locale);
  const labelKey =
    viewKind === "pedigree"
      ? "tree.viewKindPedigree"
      : viewKind === "descendant"
        ? "tree.viewKindDescendant"
        : viewKind === "fan"
          ? "tree.viewKindFan"
          : viewKind === "hourglass"
            ? "tree.viewKindHourglass"
            : "tree.viewKindFamily";
  return {
    title: `${translate(labelKey)} · ${treeId}`,
    description: translate("app.tagline"),
  };
}

export function TreeViewPage({
  locale,
  treeId,
  viewKind,
  searchParams,
}: TreeViewPageProps): JSX.Element {
  const translate = createTranslator(locale as Locale);
  if (!translate) notFound();
  const rootPersonId = resolveRootPersonId(
    searchParams ? new URLSearchParams(stringifyQuery(searchParams)) : undefined,
  );
  if (rootPersonId) {
    return (
      <TreeViewRoute
        locale={locale}
        translate={translate}
        treeId={treeId}
        viewKind={viewKind}
        initialRootPersonId={rootPersonId}
      />
    );
  }
  return (
    <TreeViewRoute locale={locale} translate={translate} treeId={treeId} viewKind={viewKind} />
  );
}
