/**
 * Calendar days as ISO text (`2026-09-18`) and the arithmetic the planning rules need. Days are never
 * JavaScript Dates in local time: the owner's time zone is applied once, in `localNow`.
 */
export type Day = string;

const MS_PER_DAY = 86_400_000;
const DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** Whether `text` is a real calendar day in ISO form. */
export function isDay(text: string): boolean {
  return DAY_PATTERN.test(text) && fromEpochDay(toEpochDay(text)) === text;
}

export function toEpochDay(day: Day): number {
  const [year, month, date] = day.split("-").map(Number);
  return Date.UTC(year, month - 1, date) / MS_PER_DAY;
}

export function fromEpochDay(epochDay: number): Day {
  return new Date(epochDay * MS_PER_DAY).toISOString().slice(0, 10);
}

export function addDays(day: Day, days: number): Day {
  return fromEpochDay(toEpochDay(day) + days);
}

/** Days from `from` to `to`; negative when `to` comes first. */
export function daysBetween(from: Day, to: Day): number {
  return toEpochDay(to) - toEpochDay(from);
}

/** ISO weekday: 1 is Monday, 7 is Sunday. */
export function weekday(day: Day): number {
  const sundayFirst = new Date(toEpochDay(day) * MS_PER_DAY).getUTCDay();
  return sundayFirst === 0 ? 7 : sundayFirst;
}

/** The Monday of `day`'s week. */
export function mondayOf(day: Day): Day {
  return addDays(day, 1 - weekday(day));
}

/**
 * The local date and time at `at` in `timeZone`, as `2026-09-18T14:05`. An unknown zone falls back to
 * UTC, so a bad profile value can't stop the connector.
 */
export function localNow(timeZone: string, at: Date = new Date()): string {
  let format: Intl.DateTimeFormat;
  try {
    format = new Intl.DateTimeFormat("en-CA", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    });
  } catch {
    return localNow("UTC", at);
  }
  const parts = Object.fromEntries(format.formatToParts(at).map((part) => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}
