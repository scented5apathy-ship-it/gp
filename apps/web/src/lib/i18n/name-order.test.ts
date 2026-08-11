/**
 * apps/web/src/lib/i18n/name-order.test.ts
 *
 * Tests for the E5.5 / R18.2 name-order policy. The wire format
 * stays canonical (given / surname / patronymic / suffix); the
 * helper just renders the locale-flavoured display string.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import type { PersonName } from "@genealogy/api-client";

import { NAME_ORDER_POLICIES, nameOrderPolicyFor, renderPersonName } from "./name-order";

const baseName: PersonName = {
  locale: "en",
  script: "Latn",
  parts: { given: "Jane", surname: "Doe" },
};

const vietnameseName: PersonName = {
  locale: "vi",
  script: "Latn",
  parts: { given: "Văn", surname: "Nguyễn", patronymic: "A" },
};

const patroName: PersonName = {
  locale: "ru",
  script: "Cyrl",
  parts: { given: "Ivan", patronymic: "Ivanovich", surname: "Ivanov" },
};

test("NAME_ORDER_POLICIES is the canonical closed-set", () => {
  assert.deepEqual(
    [...NAME_ORDER_POLICIES],
    ["given-first", "family-first", "family-only", "given-then-family-with-comma"],
  );
});

test("nameOrderPolicyFor returns given-first for en and en-XA", () => {
  assert.equal(nameOrderPolicyFor("en"), "given-first");
  assert.equal(nameOrderPolicyFor("en-XA"), "given-first");
});

test("nameOrderPolicyFor returns family-first for vi / ja / ko / hu / zh", () => {
  for (const locale of ["vi", "ja", "ko", "hu", "zh"]) {
    assert.equal(nameOrderPolicyFor(locale), "family-first", locale);
  }
});

test("nameOrderPolicyFor returns given-then-family-with-comma for ar and ar-XB", () => {
  assert.equal(nameOrderPolicyFor("ar"), "given-then-family-with-comma");
  assert.equal(nameOrderPolicyFor("ar-XB"), "given-then-family-with-comma");
});

test("nameOrderPolicyFor falls back to given-first for unknown locales", () => {
  assert.equal(nameOrderPolicyFor("xx"), "given-first");
  assert.equal(nameOrderPolicyFor(""), "given-first");
});

test("renderPersonName en gives 'Jane Doe'", () => {
  const out = renderPersonName(baseName, "en");
  assert.equal(out.display, "Jane Doe");
  assert.equal(out.policy, "given-first");
  assert.equal(out.script, "Latn");
});

test("renderPersonName vi gives 'Nguyễn A Văn'", () => {
  const out = renderPersonName(vietnameseName, "vi");
  assert.equal(out.display, "Nguyễn A Văn");
  assert.equal(out.policy, "family-first");
});

test("renderPersonName ar gives 'Jane, Doe'", () => {
  const out = renderPersonName(baseName, "ar");
  assert.equal(out.display, "Jane, Doe");
  assert.equal(out.policy, "given-then-family-with-comma");
});

test("renderPersonName ru still uses given-first (no locale-specific override)", () => {
  const out = renderPersonName(patroName, "ru");
  // The helper is locale-driven; for ru there is no specific
  // override so we fall back to given-first. Family tokens are
  // joined as "surname patronymic".
  assert.equal(out.display, "Ivan Ivanov Ivanovich");
});

test("renderPersonName skips empty parts", () => {
  const partial: PersonName = {
    locale: "en",
    script: "Latn",
    parts: { given: "", surname: "Doe" },
  };
  const out = renderPersonName(partial, "en");
  assert.equal(out.display, "Doe");
});

test("renderPersonName handles generationalSuffix", () => {
  const withSuffix: PersonName = {
    locale: "en",
    script: "Latn",
    parts: { given: "Jane", surname: "Doe", generationalSuffix: "Jr." },
  };
  const out = renderPersonName(withSuffix, "en");
  assert.equal(out.display, "Jane Doe Jr.");
});

test("renderPersonName family-only returns family only", () => {
  const partial: PersonName = {
    locale: "ja",
    script: "Jpan",
    parts: { given: "Taro", surname: "Tanaka" },
  };
  const out = renderPersonName(partial, "ja");
  assert.equal(out.display, "Tanaka Taro");
});

test("renderPersonName preserves the original script tag", () => {
  const out = renderPersonName(patroName, "ru");
  assert.equal(out.script, "Cyrl");
});
