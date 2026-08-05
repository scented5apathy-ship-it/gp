import type { MetadataRoute } from "next";

/**
 * Robots policy. Public marketing pages may be crawled; any
 * future authenticated or `/api/*` routes are explicitly denied.
 * The sitemap reference mirrors `app/sitemap.ts`.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/api/", "/admin", "/*/admin"],
      },
    ],
    sitemap: `${process.env["NEXT_PUBLIC_SHELL_ORIGIN"] ?? "https://app.genealogy-platform.com"}/sitemap.xml`,
  };
}
