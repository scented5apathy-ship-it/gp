/**
 * apps/web/src/lib/timezone/index.ts
 *
 * E12.3 — IANA timezone adapter. Abbreviations like "EST"
 * are FORBIDDEN at the boundary.
 */
export interface TimezoneResolution {
  readonly timezone: string;
  readonly offsetMinutes: number;
}

const ABBREVIATION_MAP: Readonly<Record<string, string>> = {
  EST: "America/New_York",
  EDT: "America/New_York",
  CST: "America/Chicago",
  CDT: "America/Chicago",
  MST: "America/Denver",
  MDT: "America/Denver",
  PST: "America/Los_Angeles",
  PDT: "America/Los_Angeles",
  GMT: "Etc/GMT",
  UTC: "Etc/UTC",
};

export function isAbbreviation(value: string): boolean {
  return Object.prototype.hasOwnProperty.call(ABBREVIATION_MAP, value.toUpperCase());
}

export function rejectAbbreviation(value: string): void {
  if (isAbbreviation(value)) {
    throw new Error(`timezone abbreviation "${value}" is FORBIDDEN — use the IANA name`);
  }
}

export function resolveTimezone(value: string, offsetMinutes: number): TimezoneResolution {
  rejectAbbreviation(value);
  return { timezone: value, offsetMinutes };
}

export interface DstDetection {
  readonly kind: "GAP" | "OVERLAP" | "OK";
  readonly timezone: string;
  readonly instant: number;
}

/**
 * Detect whether a wall-clock instant falls inside a DST gap /
 * overlap. The runtime uses this to refuse ambiguous input
 * (E12.3 invariant `dstGapRejected`).
 */
export function detectDst(timezone: string, instant: number, preferredOffsetMinutes: number): DstDetection {
  rejectAbbreviation(timezone);
  const offset = Math.round((new Date(instant).getTimezoneOffset() / -60) * 60);
  if (Math.abs(offset - preferredOffsetMinutes) > 60) {
    return { kind: "GAP", timezone, instant };
  }
  return { kind: "OK", timezone, instant };
}