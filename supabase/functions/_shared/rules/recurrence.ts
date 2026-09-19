import { addDays, type Day, daysBetween, mondayOf, weekday } from "./day.ts";

/**
 * A repeat rule the apps can follow (docs/repeating.md): the RRULE subset the composer writes.
 * `days` (ISO weekdays) and `monthDay` are null when the rule takes them from the anchor.
 */
export interface Recurrence {
  frequency: "DAILY" | "WEEKLY" | "MONTHLY";
  interval: number;
  days: Set<number> | null;
  monthDay: number | null;
}

// Ten years of days: far past any rule's next match; a rule that finds nothing in it has none.
const SEARCH_DAYS = 3700;
const PARTS = new Set(["FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY"]);
const WEEKDAYS: Record<string, number> = { MO: 1, TU: 2, WE: 3, TH: 4, FR: 5, SA: 6, SU: 7 };
const MAX_INT = 2_147_483_647;

/** The rule in `text`, or null when it isn't one the apps can follow. */
export function parseRecurrence(text: string | null | undefined): Recurrence | null {
  if (!text || text.trim() === "") return null;
  const parts = new Map<string, string>();
  for (const part of text.trim().toUpperCase().split(";")) {
    const at = part.indexOf("=");
    if (at < 0) return null;
    const key = part.slice(0, at);
    if (!PARTS.has(key) || parts.has(key)) return null;
    parts.set(key, part.slice(at + 1));
  }
  const frequency = parts.get("FREQ");
  if (frequency !== "DAILY" && frequency !== "WEEKLY" && frequency !== "MONTHLY") return null;

  let interval = 1;
  if (parts.has("INTERVAL")) {
    const value = number(parts.get("INTERVAL")!);
    if (value === null) return null;
    interval = value;
  }
  if (interval < 1) return null;

  let days: Set<number> | null = null;
  if (parts.has("BYDAY")) {
    if (frequency !== "WEEKLY") return null;
    days = new Set();
    for (const name of parts.get("BYDAY")!.split(",")) {
      const day = WEEKDAYS[name];
      if (day === undefined) return null;
      days.add(day);
    }
  }

  let monthDay: number | null = null;
  if (parts.has("BYMONTHDAY")) {
    if (frequency !== "MONTHLY") return null;
    const value = number(parts.get("BYMONTHDAY")!);
    if (value === null || value < 1 || value > 31) return null;
    monthDay = value;
  }
  return { frequency, interval, days, monthDay };
}

/**
 * The next occurrence's day: the first match after the later of `planned` and `today`, counting from
 * `planned` (today when there is none). Null when the rule never matches in ten years.
 */
export function nextOccurrence(rule: Recurrence, planned: Day | null, today: Day): Day | null {
  const anchor = planned ?? today;
  let day = anchor > today ? anchor : today;
  for (let step = 0; step < SEARCH_DAYS; step++) {
    day = addDays(day, 1);
    if (matches(rule, day, anchor)) return day;
  }
  return null;
}

function matches(rule: Recurrence, day: Day, anchor: Day): boolean {
  switch (rule.frequency) {
    case "DAILY":
      return daysBetween(anchor, day) % rule.interval === 0;
    case "WEEKLY": {
      const weeks = daysBetween(mondayOf(anchor), mondayOf(day)) / 7;
      return weeks % rule.interval === 0 && (rule.days ?? new Set([weekday(anchor)])).has(weekday(day));
    }
    case "MONTHLY": {
      const months = (year(day) - year(anchor)) * 12 + month(day) - month(anchor);
      return months % rule.interval === 0 && date(day) === (rule.monthDay ?? date(anchor));
    }
  }
}

function number(text: string): number | null {
  if (!/^\d+$/.test(text)) return null;
  const value = Number(text);
  return value > MAX_INT ? null : value;
}

const year = (day: Day) => Number(day.slice(0, 4));
const month = (day: Day) => Number(day.slice(5, 7));
const date = (day: Day) => Number(day.slice(8, 10));
