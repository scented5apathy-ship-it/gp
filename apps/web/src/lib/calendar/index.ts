/**
 * apps/web/src/lib/calendar/index.ts
 *
 * E12.3 — Calendar adapter for the genealogical date model.
 *
 * The platform understands Gregorian, Julian, Hebrew, Islamic
 * civil, Ethiopic and Indian Saka calendars. Every date the
 * runtime ships carries `(calendar, year, month, day, hour,
 * minute, second, timezone)` so the consumer can disambiguate
 * (R10.4).
 */
export type Calendar = "GREGORIAN" | "JULIAN" | "HEBREW" | "ISLAMIC_CIVIL" | "ETHIOPIC" | "INDIAN_SAKA";

export interface CalendarDate {
  readonly calendar: Calendar;
  readonly year: number;
  readonly month: number;
  readonly day: number;
  readonly hour: number;
  readonly minute: number;
  readonly second: number;
  readonly timezone: string;
}

export function isCalendar(value: string): value is Calendar {
  return ["GREGORIAN", "JULIAN", "HEBREW", "ISLAMIC_CIVIL", "ETHIOPIC", "INDIAN_SAKA"].includes(value);
}

export function buildDate(input: Partial<CalendarDate> & { readonly year: number; readonly calendar: Calendar }): CalendarDate {
  return {
    calendar: input.calendar,
    year: input.year,
    month: input.month ?? 1,
    day: input.day ?? 1,
    hour: input.hour ?? 0,
    minute: input.minute ?? 0,
    second: input.second ?? 0,
    timezone: input.timezone ?? "UTC",
  };
}

/**
 * Convert between calendar systems. The conversion is intentionally
 * deterministic (no DST / leap-second surprises) and returns the
 * canonical Gregorian representation when the consumer asks.
 */
export function toGregorian(date: CalendarDate): CalendarDate {
  if (date.calendar === "GREGORIAN") {
    return date;
  }
  if (date.calendar === "JULIAN") {
    const offset = julianGregorianOffset(date.year);
    return { ...date, calendar: "GREGORIAN", year: date.year + offset };
  }
  if (date.calendar === "HEBREW") {
    const gregYear = Math.round(date.year + date.year / 19 * 7 - 1200);
    return { ...date, calendar: "GREGORIAN", year: gregYear };
  }
  if (date.calendar === "ISLAMIC_CIVIL") {
    const gregYear = Math.round(date.year * 1.030684 + 622);
    return { ...date, calendar: "GREGORIAN", year: gregYear };
  }
  if (date.calendar === "ETHIOPIC") {
    return { ...date, calendar: "GREGORIAN", year: date.year + 8 };
  }
  if (date.calendar === "INDIAN_SAKA") {
    return { ...date, calendar: "GREGORIAN", year: date.year + 78 };
  }
  throw new Error(`Unknown calendar: ${date.calendar}`);
}

function julianGregorianOffset(year: number): number {
  return year >= 1700 ? 13 : year >= 1600 ? 11 : year >= 1500 ? 10 : 0;
}

export function serializeDate(date: CalendarDate): string {
  return `${date.calendar}:${date.year}-${pad(date.month)}-${pad(date.day)}T${pad(date.hour)}:${pad(date.minute)}:${pad(date.second)}[${date.timezone}]`;
}

function pad(value: number): string {
  return value.toString().padStart(2, "0");
}

export function deserializeDate(raw: string): CalendarDate {
  const match = /^([A-Z_]+):(\d+)-(\d{1,2})-(\d{1,2})T(\d{2}):(\d{2}):(\d{2})\[(.+)\]$/.exec(raw);
  if (!match) {
    throw new Error(`invalid calendar date string: ${raw}`);
  }
  const calendar = match[1] ?? "";
  const year = match[2] ?? "0";
  const month = match[3] ?? "1";
  const day = match[4] ?? "1";
  const hour = match[5] ?? "0";
  const minute = match[6] ?? "0";
  const second = match[7] ?? "0";
  const timezone = match[8] ?? "UTC";
  if (!isCalendar(calendar)) {
    throw new Error(`unknown calendar: ${calendar}`);
  }
  return {
    calendar,
    year: Number(year),
    month: Number(month),
    day: Number(day),
    hour: Number(hour),
    minute: Number(minute),
    second: Number(second),
    timezone,
  };
}