/**
 * `i18n` translator unit tests. The tests exercise the catalogue
 * loader and ensure the fallback chain degrades gracefully when
 * a key is missing or the locale is unsupported.
 *
 * `node --test` style: no Jest / Vitest dependency keeps the
 * shell aligned with the rest of the repo.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  createTranslator,
  defaultLocale,
  interpolate,
  localeDirection,
  lookupPath,
  negotiateLocale,
  supportedLocales,
} from "./index";

test("supportedLocales contains en and vi", () => {
  assert.ok(supportedLocales.includes("en"));
  assert.ok(supportedLocales.includes("vi"));
});

test("defaultLocale is en", () => {
  assert.equal(defaultLocale, "en");
});

test("lookupPath resolves dotted keys", async () => {
  const enModule = await import("./messages/en");
  const en: unknown = enModule.en;
  const headline = lookupPath(en as never, "home.headline");
  const home = lookupPath(en as never, "nav.home");
  assert.ok(typeof headline === "string");
  assert.equal(headline?.slice(0, 5), "Build");
  assert.equal(home, "Home");
});

test("lookupPath returns undefined for missing keys", async () => {
  const enModule = await import("./messages/en");
  const en: unknown = enModule.en;
  assert.equal(lookupPath(en as never, "missing.key"), undefined);
});

test("interpolate replaces placeholders", () => {
  assert.equal(interpolate("Hello {name}", { name: "world" }), "Hello world");
  assert.equal(interpolate("Hello {name}", {}), "Hello {name}");
});

test("createTranslator falls back to default locale on missing key", () => {
  const translate = createTranslator("vi");
  // The vi catalogue has the same keys as en; this only checks
  // the fallback returns the key itself rather than throwing.
  assert.equal(translate("does.not.exist"), "does.not.exist");
});

test("createTranslator returns the localised value when present", () => {
  const translate = createTranslator("en");
  assert.equal(translate("nav.home"), "Home");
});

test("negotiateLocale picks the highest-q supported locale", () => {
  assert.equal(negotiateLocale("vi-VN,vi;q=0.9,en-US;q=0.8"), "vi");
  assert.equal(negotiateLocale("fr-FR,fr;q=0.9"), "en");
  assert.equal(negotiateLocale(null), "en");
  assert.equal(negotiateLocale(""), "en");
});

test("localeDirection always returns ltr for the supported locales", () => {
  for (const locale of supportedLocales) {
    assert.equal(localeDirection[locale], "ltr");
  }
});
