import type { MetadataRoute } from "next";

import { defaultLocale, supportedLocales } from "@/i18n";

/**
 * PWA manifest source. The Next.js metadata route renders a JSON
 * payload from this object and links it via the `<link rel="manifest">`
 * tag emitted by `app/layout.tsx`.
 *
 * `display_override` advertises the modern progressive enhancement
 * path (window-controls-overlay) while still falling back to the
 * classic `standalone` and `browser` modes for browsers that do
 * not yet implement the override.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Genealogy Platform",
    short_name: "Genealogy",
    description: "Privacy-first family history platform — trees, sources and DNA.",
    id: "/",
    start_url: "/",
    scope: "/",
    display: "standalone",
    display_override: ["window-controls-overlay", "standalone", "browser"],
    orientation: "any",
    background_color: "#f7f5f0",
    theme_color: "#1f3a5f",
    lang: defaultLocale,
    dir: "ltr",
    categories: ["productivity", "education", "lifestyle"],
    icons: [
      { src: "/icons/icon-192.svg", type: "image/svg+xml", sizes: "192x192", purpose: "any" },
      { src: "/icons/icon-512.svg", type: "image/svg+xml", sizes: "512x512", purpose: "any" },
      {
        src: "/icons/maskable-512.svg",
        type: "image/svg+xml",
        sizes: "512x512",
        purpose: "maskable",
      },
    ],
    shortcuts: supportedLocales.map((locale) => ({
      name: "Trees",
      short_name: "Trees",
      url: `/${locale}/trees`,
      description: "Open the trees overview",
    })),
    prefer_related_applications: false,
  };
}
