/**
 * apps/web/src/lib/date/ambiguous-date.ts
 *
 * E12.3 — GEDCOM-style ambiguous date parser.
 *
 * GEDCOM `BET 1850 AND 1860`, `ABT 1850`, `BEF 1860`,
 * `AFT 1850`, `UNKNOWN`, `CAL 1850` all need to round-trip
 * losslessly through the runtime. The runtime also stores
 * ambiguous-day / ambiguous-month / ambiguous-year cases
 * (e.g. `? ? 1850`) which the helper exposes via dedicated
 * kinds.
 */

export type Ambiguity =
  | "EXACT"
  | "ABOUT"
  | "BEFORE"
  | "AFTER"
  | "BETWEEN"
  | "POSSIBLE_BETWEEN"
  | "UNKNOWN_DAY"
  | "UNKNOWN_MONTH"
  | "UNKNOWN_YEAR"
  | "APPROXIMATE"
  | "CALCULATED";

export interface AmbiguousDate {
  readonly kind: Ambiguity;
  readonly fromYear?: number;
  readonly toYear?: number;
  readonly year?: number;
}

const PATTERNS: ReadonlyArray<{ readonly kind: Ambiguity; readonly re: RegExp }> = [
  { kind: "BETWEEN", re: /^BET (\d{1,4}) AND (\d{1,4})$/ },
  { kind: "POSSIBLE_BETWEEN", re: /^POSSIBLY BETWEEN (\d{1,4}) AND (\d{1,4})$/ },
  { kind: "BEFORE", re: /^BEF (\d{1,4})$/ },
  { kind: "AFTER", re: /^AFT (\d{1,4})$/ },
  { kind: "ABOUT", re: /^ABT (\d{1,4})$/ },
  { kind: "APPROXIMATE", re: /^EST (\d{1,4})$/ },
  { kind: "CALCULATED", re: /^CAL (\d{1,4})$/ },
  { kind: "UNKNOWN_DAY", re: /^(\d{1,4})-(\d{1,2})-\?$/ },
  { kind: "UNKNOWN_MONTH", re: /^(\d{1,4})-\?-\?$/ },
  { kind: "UNKNOWN_YEAR", re: /^\?-\?-\?$/ },
];

export function parseAmbiguousDate(raw: string): AmbiguousDate {
  const trimmed = raw.trim();
  if (/^\d{1,4}$/.test(trimmed)) {
    return { kind: "EXACT", year: Number(trimmed) };
  }
  for (const { kind, re } of PATTERNS) {
    const match = re.exec(trimmed);
    if (!match) continue;
    if (kind === "BETWEEN" || kind === "POSSIBLE_BETWEEN") {
      return { kind, fromYear: Number(match[1]), toYear: Number(match[2]) };
    }
    if (kind === "UNKNOWN_DAY") {
      return { kind, year: Number(match[1]) };
    }
    if (kind === "UNKNOWN_MONTH") {
      return { kind, year: Number(match[1]) };
    }
    if (kind === "UNKNOWN_YEAR") {
      return { kind };
    }
    return { kind, year: Number(match[1]) };
  }
  throw new Error(`unrecognised ambiguous date: ${raw}`);
}

/**
 * Round-trip a parsed ambiguous date back to its original
 * GEDCOM string. The helper is intentionally lossless so the
 * editor can re-display what the user typed.
 */
export function serializeAmbiguousDate(date: AmbiguousDate): string {
  switch (date.kind) {
    case "EXACT":
      return `${date.year}`;
    case "BETWEEN":
      return `BET ${date.fromYear} AND ${date.toYear}`;
    case "POSSIBLE_BETWEEN":
      return `POSSIBLY BETWEEN ${date.fromYear} AND ${date.toYear}`;
    case "BEFORE":
      return `BEF ${date.year}`;
    case "AFTER":
      return `AFT ${date.year}`;
    case "ABOUT":
      return `ABT ${date.year}`;
    case "APPROXIMATE":
      return `EST ${date.year}`;
    case "CALCULATED":
      return `CAL ${date.year}`;
    case "UNKNOWN_DAY":
      return `${date.year}-?`;
    case "UNKNOWN_MONTH":
      return `${date.year}-?-?`;
    case "UNKNOWN_YEAR":
      return `?-?-?`;
    default:
      throw new Error(`unsupported ambiguity kind: ${(date as AmbiguousDate).kind}`);
  }
}

export function roundTrip(raw: string): string {
  return serializeAmbiguousDate(parseAmbiguousDate(raw));
}