/**
 * apps/web/src/i18n/index.test.ts
 *
 * Tests for the catalogue loader — expanded in E5.5 to cover
 * pseudolocale parity and direction resolution.
 *
 * Pseudolocales MUST mirror every key in `en.ts`; the linter
 * (`scripts/lint-a11y-i18n.mjs`) catches drift at CI, but the
 * runtime tests here guard the `createTranslator` fallback
 * behaviour and the `resolveDirection` table.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import {
  createTranslator,
  defaultLocale,
  negotiateLocale,
  pseudolocaleFromEnv,
  pseudoLocales,
  pseudoLocaleDirection,
  resolveDirection,
  supportedLocales,
} from "./index";

test("supportedLocales only contains the production locales", () => {
  assert.deepEqual([...supportedLocales], ["en", "vi"]);
});

test("pseudoLocales exposes the QA-only catalogues", () => {
  assert.deepEqual([...pseudoLocales].sort(), ["ar-XB", "en-XA"]);
});

test("pseudolocaleFromEnv honours GENEALOGY_PSEUDOLOCALE", () => {
  assert.equal(pseudolocaleFromEnv({ GENEALOGY_PSEUDOLOCALE: "en-XA" }), "en-XA");
  assert.equal(pseudolocaleFromEnv({ GENEALOGY_PSEUDOLOCALE: "ar-XB" }), "ar-XB");
  assert.equal(pseudolocaleFromEnv({}), null);
  assert.equal(pseudolocaleFromEnv({ GENEALOGY_PSEUDOLOCALE: "fr-FR" }), null);
});

test("resolveDirection maps production and pseudolocales", () => {
  assert.equal(resolveDirection("en"), "ltr");
  assert.equal(resolveDirection("vi"), "ltr");
  assert.equal(resolveDirection("ar"), "rtl");
  assert.equal(resolveDirection("en-XA"), "ltr");
  assert.equal(resolveDirection("ar-XB"), "rtl");
  assert.equal(resolveDirection("xx-YY"), "ltr");
});

test("createTranslator falls back to en for missing keys", () => {
  const t = createTranslator("vi");
  assert.equal(t("app.title"), "Genealogy Platform");
  assert.equal(t("nav.skipToContent"), "Bỏ qua để đến nội dung chính");
  assert.equal(t("__missing__"), "__missing__");
});

test("createTranslator interpolates named placeholders", () => {
  const t = createTranslator("en");
  assert.equal(t("a11y.selectedAnnounce", { name: "Jane Doe" }), "Selected person Jane Doe");
});

test("createTranslator accepts the en-XA pseudolocale", () => {
  const t = createTranslator("en-XA");
  assert.ok(t("app.title").startsWith("["));
  assert.ok(t("app.title").endsWith("]"));
});

test("createTranslator accepts the ar-XB pseudolocale with RTL marks", () => {
  const t = createTranslator("ar-XB");
  const out = t("app.title");
  assert.ok(out.includes("\u202E"), "expected RLE mark");
  assert.ok(out.includes("\u202C"), "expected PDF mark");
});

test("createTranslator(en-XA) falls back to en for unknown keys", () => {
  const t = createTranslator("en-XA");
  assert.equal(t("__missing__"), "__missing__");
});

test("pseudoLocaleDirection covers every pseudolocale", () => {
  for (const locale of pseudoLocales) {
    assert.ok(locale in pseudoLocaleDirection, `missing dir for ${locale}`);
  }
});

test("negotiateLocale returns default when no Accept-Language", () => {
  assert.equal(negotiateLocale(null), defaultLocale);
  assert.equal(negotiateLocale(undefined), defaultLocale);
  assert.equal(negotiateLocale(""), defaultLocale);
});

test("negotiateLocale never returns a pseudolocale", () => {
  assert.equal(negotiateLocale("en-XA,vi;q=0.9"), "en");
  assert.equal(negotiateLocale("ar-XB"), defaultLocale);
  assert.equal(negotiateLocale("vi;q=0.5"), "vi");
});
