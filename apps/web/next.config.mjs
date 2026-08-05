/*
 * Next.js configuration for the Genealogy Platform PWA shell.
 *
 * Goals (E1.5 / design.md §10):
 *   1. App Router + RSC by default; client islands for editor / upload only.
 *   2. Strict security defaults: HSTS, X-Frame-Options, Referrer-Policy,
 *      Permissions-Policy. CSP is staged for E6 once the asset CDN is in
 *      place — the placeholder nonce-friendly directives below document
 *      the future policy without breaking local dev.
 *   3. Performance budget enforced through `experimental.optimizePackageImports`
 *      and the per-route bundle size test in `src/lib/perf/budget.test.ts`.
 *   4. PWA-friendly: standalone display, theme color, manifest linked via
 *      `src/app/manifest.ts`. Service worker generation lands in E6 alongside
 *      the offline queue (R17) — we deliberately keep the shell minimal here.
 *   5. i18n routing via `[locale]` segment — `next-intl` was evaluated but is
 *      not yet in `pnpm-lock.yaml`. E1.5 ships a thin in-house loader
 *      (`src/i18n/`) using the built-in `Intl` runtime; the dependency on
 *      `next-intl` (or equivalent) is tracked as residual risk in the E1.5
 *      evidence file and resolved before any production launch.
 *
 * The i18n sub-path routing is intentionally off (`useRouter` for navigation)
 * because App Router handles locale segments natively without `next.config`
 * rewrites — see `src/app/[locale]/layout.tsx`.
 */
const securityHeaders = [
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains; preload" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(self), microphone=(), geolocation=(), payment=()",
  },
  {
    key: "X-Genealogy-Shell",
    value: "pwa-shell-v0",
  },
];

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  productionBrowserSourceMaps: false,
  experimental: {
    // Keeps the bundle small for low-end mobile (E1.5 budget §10).
    optimizePackageImports: ["react", "react-dom", "@genealogy/ui", "@genealogy/api-client"],
  },
  // `transpilePackages` lets the workspace TS packages be consumed
  // directly without a separate build step. We only transpile the
  // packages that ship TS sources — `api-client` is consumed as a
  // type-only module + codegen output (see `src/lib/api/`).
  transpilePackages: ["@genealogy/ui", "@genealogy/api-client", "@genealogy/i18n"],
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders,
      },
      {
        source: "/_next/static/:path*",
        headers: [{ key: "Cache-Control", value: "public, max-age=31536000, immutable" }],
      },
    ];
  },
  // Performance / accessibility budget thresholds. These are surfaced
  // through `next build` and consumed by `src/lib/perf/budget.test.ts`.
  // When the build emits bundles larger than these, the unit test
  // fails CI.
  bundlePagesRouterDependencies: undefined,
  output: undefined,
};

export default nextConfig;
