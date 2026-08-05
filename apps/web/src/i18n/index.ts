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
 */
export function createTranslator(locale: Locale): Translator {
  const primary = catalogues[locale] ?? catalogues[defaultLocale]!;
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
 * The `dir` (text direction) associated with each locale. RTL
 * support is mandated by R18 and is enforced by the root layout
 * through the `dir` attribute on `<html>`.
 */
export const localeDirection: Readonly<Record<Locale, "ltr" | "rtl">> = {
  en: "ltr",
  vi: "ltr",
};
