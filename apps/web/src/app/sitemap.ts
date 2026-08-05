import type { MetadataRoute } from "next";

import { supportedLocales } from "@/i18n";

const SHELL_ORIGIN =
  process.env["NEXT_PUBLIC_SHELL_ORIGIN"] ?? "https://app.genealogy-platform.com";

/**
 * Locale-prefixed sitemap. The shell only emits the public,
 * localised URLs (R17/R18 — search engines must see the
 * language-specific entry points). Authenticated routes (trees,
 * people, sources) are excluded by design.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  return supportedLocales.map((locale) => ({
    url: `${SHELL_ORIGIN}/${locale}`,
    lastModified: now,
    changeFrequency: "weekly",
    priority: 0.8,
    alternates: {
      languages: Object.fromEntries(supportedLocales.map((l) => [l, `${SHELL_ORIGIN}/${l}`])),
    },
  }));
}
