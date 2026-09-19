import { addDays, type Day } from "./day.ts";

/** The hour the planning day starts unless the owner changed it (docs/lists.md). */
export const DEFAULT_START_HOUR = 4;

/**
 * The day plans belong to: the local date shifted back by the hour the day starts (docs/lists.md), so
 * planning at 01:30 still happens on yesterday's day. `local` is a local date-time, `2026-09-18T01:30`.
 */
export function planningDay(local: string, startHour: number = DEFAULT_START_HOUR): Day {
  const day = local.slice(0, 10);
  const hour = Number(local.slice(11, 13));
  return hour < startHour ? addDays(day, -1) : day;
}
