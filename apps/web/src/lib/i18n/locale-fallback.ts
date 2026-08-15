/**
 * apps/web/src/lib/i18n/locale-fallback.ts
 *
 * E12.3 — Locale fallback chain.
 *
 * Mirrors `localeFallbackChain` in
 * `contracts/pwa/globalization-policy.yaml`. Unknown locales
 * MUST resolve to the defaultLocale (`en`) without throwing.
 * Pseudolocales (en-XA, ar-XB) MUST NOT appear in a production
 * chain — when requested they MUST be blocked and the runtime
 * falls back to defaultLocale.
 */

const DEFAULT_LOCALE = "en";

const FALLBACK: Readonly<Record<string, string>> = {
  "en-US": "en",
  "en-GB": "en",
  "vi-VN": "vi",
  "fr-FR": "fr",
  "de-DE": "de",
  "es-ES": "es",
  "ja-JP": "ja",
  "ar-SA": "ar",
  "he-IL": "he",
};

const PSEUDO = new Set(["en-XA", "ar-XB"]);

export type FallbackOutcome =
  | { readonly outcome: "RESOLVED"; readonly tag: string; readonly chain: ReadonlyArray<string> }
  | { readonly outcome: "PSEUDOLOCALE_BLOCKED"; readonly tag: string }
  | { readonly outcome: "DEFAULT"; readonly tag: string };

export function resolveLocale(tag: string): FallbackOutcome {
  if (PSEUDO.has(tag)) {
    return { outcome: "PSEUDOLOCALE_BLOCKED", tag };
  }
  const chain: Array<string> = [tag];
  let current = tag;
  for (;;) {
    const next = FALLBACK[current];
    if (!next) break;
    if (chain.includes(next)) {
      throw new Error(`fallback cycle detected at ${current}`);
    }
    chain.push(next);
    current = next;
  }
  if (current === tag) {
    return { outcome: "DEFAULT", tag: current };
  }
  return { outcome: "RESOLVED", tag: current, chain };
}

export function defaultLocale(): string {
  return DEFAULT_LOCALE;
}

export function fallbackFor(tag: string): string {
  const outcome = resolveLocale(tag);
  if (outcome.outcome === "PSEUDOLOCALE_BLOCKED") {
    return DEFAULT_LOCALE;
  }
  return outcome.tag;
}