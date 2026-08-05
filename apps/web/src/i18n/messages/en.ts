import type { MessageTree } from "../index";

export const en = {
  app: {
    title: "Genealogy Platform",
    tagline: "Privacy-first family history",
  },
  nav: {
    home: "Home",
    trees: "Trees",
    people: "People",
    sources: "Sources",
    dna: "DNA",
    settings: "Settings",
    skipToContent: "Skip to main content",
  },
  home: {
    headline: "Build your family tree without giving up privacy.",
    subhead:
      "Sovereign storage, transparent consent and offline-friendly PWA — your data stays yours.",
    ctaPrimary: "Create your first tree",
    ctaSecondary: "Learn about consent",
    featuresTitle: "What you get",
    featureOfflineTitle: "Offline-friendly shell",
    featureOfflineBody:
      "The PWA shell keeps the navigation, manifest and design tokens available even when the network drops.",
    featureI18nTitle: "Multilingual + RTL",
    featureI18nBody:
      "Every string flows through the locale catalogue. Right-to-left scripts land in E12.3 alongside ICU.",
    featureContractsTitle: "Typed API client",
    featureContractsBody:
      "Generated from the OpenAPI contracts in `contracts/openapi/` — your IDE knows every endpoint.",
  },
  errors: {
    boundaryTitle: "Something went wrong",
    boundaryBody:
      "The page could not be rendered. The error has been logged with a correlation id; please retry.",
    boundaryAction: "Reload the page",
    notFoundTitle: "Page not found",
    notFoundBody: "The URL you requested does not match any known route.",
    notFoundAction: "Back to home",
    unauthorizedTitle: "Sign-in required",
    unauthorizedBody: "This page is only available to authenticated members.",
    unauthorizedAction: "Sign in",
  },
  loading: {
    page: "Loading page…",
    section: "Loading…",
  },
  footer: {
    rights: "All rights reserved.",
    privacy: "Privacy policy",
    terms: "Terms of service",
  },
} as const satisfies MessageTree;
