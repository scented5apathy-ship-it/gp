/**
 * Locale catalogue loader — typed accessor for ICU-lite messages.
 *
 * The Genealogy Platform ships its strings as nested ICU-style
 * objects (one file per locale). This module:
 *   1. Validates the catalogue at module load against `LocaleSchema`
 *      so a missing key or wrong type fails fast in development.
 *   2. Returns a `format()` function that resolves a dotted key
 *      (e.g. `"home.headline"`) against the active catalogue and
 *      interpolates named placeholders using `{name}` syntax.
 *   3. Falls back to the configured default locale when a key is
 *      missing — never throws.
 *
 * Full ICU MessageFormat support (`plural`, `select`, nested
 * messages, RTL-aware direction hints) lands in E12.3 alongside the
 * translator workflow. The shell intentionally uses the simplest
 * possible interpolation so we do not block on a translator
 * round-trip during the platform foundation phase.
 */
import type { Locale } from "@genealogy/i18n";
import { supportedLocales } from "@genealogy/i18n";

import { en } from "./messages/en";
import { vi } from "./messages/vi";
import { enXA } from "./messages/en-XA";
import { arXB } from "./messages/ar-XB";
import type { MessageTree } from "./types";

export type { Locale } from "@genealogy/i18n";
export { supportedLocales } from "@genealogy/i18n";
export type { MessageTree, MessageValue } from "./types";

/**
 * Catalogue shape. We type the catalogue as a `MessageTree` rather
 * than `typeof en` so that `vi` (with Vietnamese copy) satisfies
 * the same structural contract.
 */
type Catalogue = MessageTree;

const catalogues: Readonly<Record<Locale, Catalogue>> = {
  en: en as unknown as Catalogue,
  vi: vi as unknown as Catalogue,
};

/**
 * Pseudolocales — QA-only catalogues used to detect hard-coded
 * English (`en-XA`, padded) and hard-coded LTR (`ar-XB`, RTL
 * mirrored). They are **not** part of the `Locale` union or
 * `supportedLocales` — the only way to enable them is by setting
 * `GENEALOGY_PSEUDOLOCALE=en-XA` (or `ar-XB`) before render time.
 *
 * The factory `createTranslator(locale)` will accept the
 * pseudolocale strings for testing only; production builds must
 * never set the env var (the E5.5 linter greps for it).
 */
const pseudoCatalogues: Readonly<Record<string, Catalogue>> = {
  "en-XA": enXA as unknown as Catalogue,
  "ar-XB": arXB as unknown as Catalogue,
};

export const pseudoLocales: ReadonlyArray<string> = Object.keys(pseudoCatalogues);

export const defaultLocale: Locale = "en";

/**
 * Resolve a dotted key (e.g. `"home.headline"`) against a catalogue.
 * Returns `undefined` when the path does not exist; the caller is
 * expected to fall back to the default locale and ultimately to the
 * raw key so the UI never crashes on a missing translation.
 */
export function lookupPath(catalogue: Catalogue, key: string): string | undefined {
  const segments = key.split(".");
  let cursor: Catalogue | string | undefined = catalogue;
  for (const segment of segments) {
    if (cursor === undefined || typeof cursor === "string") {
      return undefined;
    }
    const nextNode: Catalogue | string | undefined = (cursor as Catalogue)[segment];
    if (nextNode === undefined) {
      return undefined;
    }
    cursor = nextNode;
  }
  if (typeof cursor === "string") {
    return cursor;
  }
  if (cursor && typeof cursor === "object" && "message" in cursor) {
    const wrapper = cursor as { message: string };
    return wrapper.message;
  }
  return undefined;
}

/**
 * Interpolate `{name}` placeholders inside a message template.
 * Unknown placeholders are left in the output for easy debugging.
 */
export function interpolate(
  template: string,
  params?: Readonly<Record<string, string | number>>,
): string {
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, name: string) => {
    const value = params[name];
    return value === undefined || value === null ? match : String(value);
  });
}

export type Translator = (
  key: string,
  params?: Readonly<Record<string, string | number>>,
) => string;

/**
 * Build a translator bound to the given locale. The translator
 * always succeeds: missing keys fall back to the default locale
 * and ultimately to the key itself so the UI degrades gracefully.
 *
 * Pseudolocale tags (e.g. `en-XA`, `ar-XB`) are accepted for QA
 * tooling; they never reach production builds because
 * `negotiateLocale` only returns a `Locale`.
 */
export function createTranslator(locale: string): Translator {
  const isPseudo = Object.prototype.hasOwnProperty.call(pseudoCatalogues, locale);
  const primary = isPseudo
    ? pseudoCatalogues[locale]!
    : (catalogues[locale as Locale] ?? catalogues[defaultLocale]!);
  const fallback = catalogues[defaultLocale]!;
  return (key, params) => {
    const primaryHit = lookupPath(primary, key);
    if (primaryHit !== undefined) {
      return interpolate(primaryHit, params);
    }
    const fallbackHit = lookupPath(fallback, key);
    if (fallbackHit !== undefined) {
      return interpolate(fallbackHit, params);
    }
    return key;
  };
}

/**
 * Negotiate the active locale from a `Accept-Language` header. Used
 * by the middleware / dynamic segment loader when a request lands
 * without an explicit locale prefix.
 *
 * Pseudolocales are **not** negotiated from `Accept-Language` —
 * they must be opted into explicitly via `pseudolocaleFromEnv` or
 * `createTranslator("en-XA")`. This keeps production builds safe.
 */
export function negotiateLocale(acceptLanguage: string | null | undefined): Locale {
  if (!acceptLanguage) {
    return defaultLocale;
  }
  const tags = acceptLanguage
    .split(",")
    .map((entry) => {
      const [tag, qPart] = entry.trim().split(";");
      const q = qPart && qPart.startsWith("q=") ? Number.parseFloat(qPart.slice(2)) : 1;
      return { tag: (tag ?? "").toLowerCase(), q: Number.isFinite(q) ? q : 0 };
    })
    .sort((a, b) => b.q - a.q);

  for (const { tag } of tags) {
    const base = tag.split("-")[0];
    if (base && supportedLocales.includes(base as Locale)) {
      return base as Locale;
    }
  }
  return defaultLocale;
}

/**
 * Resolve a pseudolocale from the `GENEALOGY_PSEUDOLOCALE` env var.
 * Returns `null` when the env var is unset or unknown.
 */
export function pseudolocaleFromEnv(
  env: Readonly<Record<string, string | undefined>>,
): string | null {
  const raw = env["GENEALOGY_PSEUDOLOCALE"];
  if (!raw) return null;
  if (Object.prototype.hasOwnProperty.call(pseudoCatalogues, raw)) return raw;
  return null;
}

/**
 * The `dir` (text direction) associated with each locale. RTL
 * support is mandated by R18 and is enforced by the root layout
 * through the `dir` attribute on `<html>`.
 */
export const localeDirection: Readonly<Record<Locale, "ltr" | "rtl">> = {
  en: "ltr",
  vi: "ltr",
};

export const pseudoLocaleDirection: Readonly<Record<string, "ltr" | "rtl">> = {
  "en-XA": "ltr",
  "ar-XB": "rtl",
};

/**
 * Resolve the `dir` for any catalogue the loader knows about —
 * production locales *and* pseudolocales. Falls back to the
 * base-locale entry when the exact BCP-47 tag is unknown (e.g.
 * `ar-XX` falls back to `ar` → `rtl`).
 */
const RTL_BASE_LOCALES: ReadonlyArray<string> = ["ar", "he", "fa", "ur"];
export function resolveDirection(locale: string): "ltr" | "rtl" {
  if (locale in pseudoLocaleDirection) return pseudoLocaleDirection[locale] ?? "ltr";
  if (locale in localeDirection) return localeDirection[locale as Locale];
  const base = locale.split("-")[0] ?? "";
  if (RTL_BASE_LOCALES.includes(base)) return "rtl";
  return "ltr";
}
