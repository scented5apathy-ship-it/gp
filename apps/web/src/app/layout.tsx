import type { ReactNode } from "react";
import type { Metadata, Viewport } from "next";

import "@/styles/globals.css";

/**
 * Root layout — wraps every locale layout. The locale layout
 * (under `app/[locale]/layout.tsx`) handles the actual `<html>`
 * element so the `lang`/`dir` attributes match the negotiated
 * locale. We deliberately do not emit `<html>` here because
 * nested `<html>` elements would invalidate the locale layout.
 *
 * Metadata here is the platform default; locale layouts override
 * `metadataBase` and `alternates.languages` with their catalogue.
 */
export const metadata: Metadata = {
  metadataBase: new URL(
    process.env["NEXT_PUBLIC_SHELL_ORIGIN"] ?? "https://app.genealogy-platform.com",
  ),
  applicationName: "Genealogy Platform",
  title: {
    default: "Genealogy Platform",
    template: "%s · Genealogy Platform",
  },
  description: "Privacy-first family history platform — trees, sources and DNA.",
  manifest: "/manifest.webmanifest",
  icons: {
    icon: [
      { url: "/icons/icon-192.svg", type: "image/svg+xml", sizes: "192x192" },
      { url: "/icons/icon-512.svg", type: "image/svg+xml", sizes: "512x512" },
    ],
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "Genealogy",
  },
  formatDetection: {
    telephone: false,
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#1f3a5f" },
    { media: "(prefers-color-scheme: dark)", color: "#10141a" },
  ],
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return children;
}
