import type { Metadata } from "next";

import { buildTreeViewMetadata, TreeViewPage } from "@/components/tree-view/route-page";

interface PageProps {
  params: Promise<{ locale: string; treeId: string }> | { locale: string; treeId: string };
  searchParams?:
    | Promise<Record<string, string | string[] | undefined>>
    | Record<string, string | string[] | undefined>;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { locale, treeId } = await params;
  return buildTreeViewMetadata("pedigree", locale, treeId);
}

export default async function PedigreePage({ params, searchParams }: PageProps) {
  const { locale, treeId } = await params;
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  return TreeViewPage({ locale, treeId, viewKind: "pedigree", searchParams: resolvedSearchParams });
}
