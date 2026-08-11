/**
 * apps/web/src/components/tree-view/tree-view-route.tsx
 *
 * Client-side route bootstrapper for the five E5.3 view kinds
 * (pedigree, descendant, fan, hourglass, family). The component:
 *
 *   1. Resolves the tenant id + tree id + root person id from the
 *      URL (`[treeId]` + `?rootPersonId=...` + `?viewKind=...`).
 *   2. Constructs a `TreeViewStore` bound to the singleton
 *      `BffClient` (one store per `treeId`+`viewKind` pair so
 *      switches between view kinds preserve their state).
 *   3. Triggers the initial `load(query)` so the projection
 *      arrives server-side-rendered through the BFF — the PWA
 *      NEVER holds the full graph (R6.3).
 *
 * The component is a thin shell; the rendering itself lives in
 * `<TreeView>` so it can be reused for tests.
 */
"use client";

import { useEffect, useMemo, useState } from "react";

import { TREE_PROJECTION_VIEW_KINDS, type TreeProjectionViewKind } from "@genealogy/api-client";

import { TreeView } from "@/components/tree-view";
import { PrintToolbar } from "@/components/print/print-toolbar";
import { getBffClient } from "@/lib/api/client";
import { createBffTreeProjectionFetcher } from "@/lib/tree-view/client";
import { defaultQuery, EMPTY_SNAPSHOT, TreeViewStore } from "@/lib/tree-view/store";
import type { Translator } from "@/i18n";

export interface TreeViewRouteProps {
  readonly locale: string;
  readonly translate: Translator;
  readonly treeId: string;
  readonly viewKind: TreeProjectionViewKind;
  readonly initialRootPersonId?: string;
  readonly tenantId?: string;
}

function resolveViewKind(value: string | undefined): TreeProjectionViewKind {
  if (value && TREE_PROJECTION_VIEW_KINDS.includes(value as TreeProjectionViewKind)) {
    return value as TreeProjectionViewKind;
  }
  return "family";
}

function resolveRootPersonId(searchParams: URLSearchParams | undefined): string {
  if (!searchParams) return "";
  const candidate = searchParams.get("rootPersonId");
  return candidate && /^[A-Za-z0-9._:-]{1,128}$/.test(candidate) ? candidate : "";
}

export function TreeViewRoute(props: TreeViewRouteProps): JSX.Element {
  const { locale, translate, treeId, viewKind, initialRootPersonId, tenantId } = props;
  const rootPersonId = initialRootPersonId ?? "";
  const store = useMemo(() => {
    const client = getBffClient();
    const fetcher = createBffTreeProjectionFetcher({ client });
    return new TreeViewStore(EMPTY_SNAPSHOT, { fetcher });
  }, [treeId, viewKind]);

  useEffect(() => {
    if (!rootPersonId) return;
    const initialQuery = defaultQuery({ treeId, viewKind, rootPersonId });
    void store.load(initialQuery);
  }, [rootPersonId, store, treeId, viewKind]);

  const [hydrated, setHydrated] = useState(false);
  useEffect(() => setHydrated(true), []);

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8">
      <p className="text-xs text-surface-muted" data-tenant={tenantId ?? "unscoped"}>
        {translate("tree.sectionLabel")} · {locale}
      </p>
      {hydrated ? (
        <>
          <TreeView store={store} translate={translate} locale={locale} />
          <PrintToolbar
            locale={locale}
            translate={translate}
            tenantId={tenantId ?? "unscoped"}
            treeId={treeId}
            rootPersonId={rootPersonId || "00000000-0000-4000-8000-000000000000"}
            actorPseudoId="user-pseudo"
            variant="tree"
          />
        </>
      ) : null}
    </div>
  );
}

export { resolveViewKind, resolveRootPersonId };
