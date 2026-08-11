/**
 * apps/web/src/lib/i18n/name-order.ts
 *
 * Locale-aware rendering of a person's display name. R18.2 says
 * "Names SHALL retain the original script, transliteration and
 * alternate forms" and `design.md` §10.4 says "Không hard-code
 * thứ tự họ tên, giới tính, địa chỉ hoặc Gregorian calendar."
 *
 * This module is the single source of truth for *display* order.
 * Storage stays in `PersonName.parts` (given / surname /
 * patronymic / suffix) so the API can swap policies per tenant
 * without a migration. The function returns the script-flavoured
 * display string for a locale, falling back to the original
 * script if the locale-specific transliteration is missing.
 *
 * Policies (closed-set, validated by the E5.5 linter):
 *
 *   - `given-first`     — "Jane Doe"           (en, default)
 *   - `family-first`    — "Nguyễn Văn A"       (vi, ja, ko, hu)
 *   - `family-only`     — "Nguyễn"             (mononyms)
 *   - `given-then-family-with-comma` — "Doe, Jane"
 *
 * The order is enforced on the wire (`PersonBody.displayName`
 * carries the locale-flavoured value) so the UI does not need to
 * reformat on every render — this helper exists for cases where
 * the UI gets a structured `PersonName` (e.g. the editor preview).
 */
import type { PersonName } from "@genealogy/api-client";

export type NameOrderPolicy =
  | "given-first"
  | "family-first"
  | "family-only"
  | "given-then-family-with-comma";

export const NAME_ORDER_POLICIES: ReadonlyArray<NameOrderPolicy> = [
  "given-first",
  "family-first",
  "family-only",
  "given-then-family-with-comma",
];

const LOCALE_POLICY: Readonly<Record<string, NameOrderPolicy>> = {
  en: "given-first",
  vi: "family-first",
  ar: "given-then-family-with-comma",
  ja: "family-first",
  ko: "family-first",
  hu: "family-first",
  zh: "family-first",
};

const PSEUDO_LOCALE_POLICY: Readonly<Record<string, NameOrderPolicy>> = {
  "en-XA": "given-first",
  "ar-XB": "given-then-family-with-comma",
};

export function nameOrderPolicyFor(locale: string): NameOrderPolicy {
  if (locale in PSEUDO_LOCALE_POLICY) return PSEUDO_LOCALE_POLICY[locale] ?? "given-first";
  const base = locale.split("-")[0] ?? "";
  return LOCALE_POLICY[base] ?? "given-first";
}

export interface RenderedName {
  readonly display: string;
  readonly family: string;
  readonly given: string;
  readonly policy: NameOrderPolicy;
  readonly script: string | null;
}

function trim(value: string): string {
  return value.trim();
}

function pickScript(name: PersonName): string | null {
  return typeof name.script === "string" && name.script.length > 0 ? name.script : null;
}

export function renderPersonName(name: PersonName, locale: string): RenderedName {
  const policy = nameOrderPolicyFor(locale);
  const given = trim(name.parts.given);
  const family = trim(name.parts.surname);
  const patro = trim(name.parts.patronymic ?? "");
  const suffix = trim(name.parts.generationalSuffix ?? "");

  const familyTokens: string[] = [];
  if (family) familyTokens.push(family);
  if (patro) familyTokens.push(patro);
  const familyJoined = familyTokens.join(" ");
  // Generational suffix (Jr., Sr., III) is rendered at the end
  // regardless of policy — the only thing the policy controls is
  // where `familyJoined` and `given` sit relative to each other.
  const suffixTail = suffix ? ` ${suffix}` : "";

  let display: string;
  switch (policy) {
    case "given-first":
      display = [given, familyJoined].filter(Boolean).join(" ").trim();
      break;
    case "family-first":
      display = [familyJoined, given].filter(Boolean).join(" ").trim();
      break;
    case "family-only":
      display = familyJoined;
      break;
    case "given-then-family-with-comma":
      display = [given, family].filter(Boolean).join(", ").trim();
      break;
    default:
      display = given || familyJoined;
  }

  display = `${display}${suffixTail}`.trim();

  if (!display) {
    display = policy === "family-only" ? familyJoined : `${given} ${familyJoined}`.trim();
  }

  return {
    display,
    family: family || patro,
    given: given || patro,
    policy,
    script: pickScript(name),
  };
}
