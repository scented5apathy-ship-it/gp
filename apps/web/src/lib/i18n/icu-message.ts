/**
 * apps/web/src/lib/i18n/icu-message.ts
 *
 * E12.3 — Full ICU MessageFormat support for the translator
 * workflow. The previous shell only supported `{name}`
 * interpolation; the new helper adds `plural` and `select`
 * shapes so the translators can ship real ICU templates.
 */
import { interpolate, lookupPath } from "../../i18n/index";
import type { MessageTree } from "../../i18n/types";

export type MessageValue = string | { message: string };
export type Catalogue = MessageTree;

const PLACEHOLDER_NAME = /^[a-zA-Z_][a-zA-Z0-9_]*$/;

export interface PluralOperand {
  readonly category: "zero" | "one" | "two" | "few" | "many" | "other";
  readonly value: number;
}

export function selectPluralCategory(value: number, locale: string): PluralOperand["category"] {
  if (locale.startsWith("vi") || locale.startsWith("ja") || locale.startsWith("ko") || locale.startsWith("th")) {
    if (value === 0) return "other";
    return "other";
  }
  if (locale.startsWith("ru") || locale.startsWith("uk") || locale.startsWith("pl")) {
    const mod10 = value % 10;
    const mod100 = value % 100;
    if (mod10 === 1 && mod100 !== 11) return "one";
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return "few";
    return "many";
  }
  if (locale.startsWith("ar")) {
    if (value === 0) return "zero";
    if (value === 1) return "one";
    if (value === 2) return "two";
    const mod100 = value % 100;
    if (mod100 >= 3 && mod100 <= 10) return "few";
    if (mod100 >= 11 && mod100 <= 99) return "many";
    return "other";
  }
  if (locale.startsWith("fr") || locale.startsWith("de") || locale.startsWith("es") || locale.startsWith("it") || locale.startsWith("nl") || locale.startsWith("pt")) {
    return value === 0 || value === 1 ? "one" : "other";
  }
  return value === 1 ? "one" : "other";
}

export interface PluralBranch {
  readonly category: PluralOperand["category"];
  readonly template: string;
}

export interface PluralTemplate {
  readonly kind: "plural";
  readonly branches: ReadonlyArray<PluralBranch>;
}

export interface SelectBranch {
  readonly key: string;
  readonly template: string;
}

export interface SelectTemplate {
  readonly kind: "select";
  readonly branches: ReadonlyArray<SelectBranch>;
}

export type IcuTemplate = PluralTemplate | SelectTemplate | { readonly kind: "literal"; readonly template: string };

const PLURAL_PREFIX = "{plural, ";
const SELECT_PREFIX = "{select, ";

export function parseTemplate(template: string): IcuTemplate {
  if (template.startsWith(PLURAL_PREFIX)) {
    const rest = template.slice(PLURAL_PREFIX.length, -1);
    const branches = parseBranches(rest).map((b) => ({ category: b.key as PluralOperand["category"], template: b.template }));
    return { kind: "plural", branches };
  }
  if (template.startsWith(SELECT_PREFIX)) {
    const rest = template.slice(SELECT_PREFIX.length, -1);
    const branches = parseBranches(rest).map((b) => ({ key: b.key, template: b.template }));
    return { kind: "select", branches };
  }
  return { kind: "literal", template };
}

function parseBranches(body: string): ReadonlyArray<{ readonly key: string; readonly template: string }> {
  const branches: Array<{ key: string; template: string }> = [];
  let depth = 0;
  let buffer = "";
  for (let i = 0; i < body.length; i += 1) {
    const c = body[i];
    if (c === "{") depth += 1;
    if (c === "}") depth -= 1;
    if (c === "," && depth === 0) {
      const entry = buffer.trim();
      const sepIndex = entry.indexOf("{");
      if (sepIndex < 0) {
        throw new Error(`invalid ICU branch: ${entry}`);
      }
      const key = entry.slice(0, sepIndex).trim();
      const template = entry.slice(sepIndex).trim();
      branches.push({ key, template });
      buffer = "";
      continue;
    }
    buffer += c;
  }
  if (buffer.trim()) {
    const entry = buffer.trim();
    const sepIndex = entry.indexOf("{");
    if (sepIndex < 0) {
      throw new Error(`invalid ICU branch: ${entry}`);
    }
    const key = entry.slice(0, sepIndex).trim();
    const template = entry.slice(sepIndex).trim();
    branches.push({ key, template });
  }
  return branches;
}

export function renderTemplate(
  template: string,
  params: Readonly<Record<string, string | number>>,
  locale: string,
): string {
  const parsed = parseTemplate(template);
  if (parsed.kind === "literal") {
    return interpolate(parsed.template, params);
  }
  if (parsed.kind === "plural") {
    const value = params["count"];
    if (typeof value !== "number") {
      throw new Error("plural template requires a numeric 'count' parameter");
    }
    const category = selectPluralCategory(value, locale);
    const branch = parsed.branches.find((b) => b.category === category) ?? parsed.branches.find((b) => b.category === "other");
    if (!branch) {
      throw new Error(`plural template has no fallback 'other' branch`);
    }
    return renderTemplate(branch.template, params, locale);
  }
  const firstBranch = parsed.branches[0];
  const selectKey = firstBranch ? params[firstBranch.key] ?? "other" : "other";
  const branch = parsed.branches.find((b) => b.key === String(selectKey)) ?? parsed.branches.find((b) => b.key === "other");
  if (!branch) {
    throw new Error(`select template has no fallback 'other' branch`);
  }
  return renderTemplate(branch.template, params, locale);
}

export function validatePlaceholderName(name: string): boolean {
  return PLACEHOLDER_NAME.test(name);
}

export function placeholderNamesIn(template: string): ReadonlyArray<string> {
  const names = new Set<string>();
  const re = /\{(\w+)\}/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(template))) {
    const captured = m[1];
    if (captured) names.add(captured);
  }
  return Array.from(names);
}

export function formatIcu(
  catalogue: Catalogue,
  key: string,
  params: Readonly<Record<string, string | number>>,
  locale: string,
): string {
  const raw = lookupPath(catalogue, key);
  if (raw === undefined) {
    return key;
  }
  for (const name of placeholderNamesIn(raw)) {
    if (!validatePlaceholderName(name)) {
      throw new Error(`invalid placeholder name: ${name}`);
    }
  }
  return renderTemplate(raw, params, locale);
}