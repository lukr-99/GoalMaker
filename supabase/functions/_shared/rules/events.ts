import { addDays, type Day, daysBetween } from "./day.ts";

/**
 * Calendar events (docs/calendar.md, contracts/vectors/calendar.json), the parts the connector shows:
 * the events a day holds and the ones going on. The bars a grid draws run only in the apps.
 */
export interface EventItem {
  id: string;
  title: string;
  startsOn: Day;
  endsOn: Day;
  area: string | null;
  deleted: boolean;
}

/** An event the day falls inside: which of its days it is and how many it has. */
export interface Ongoing<T> {
  event: T;
  dayOf: number;
  days: number;
}

/** Earliest first day first, then the longest, then by title, then by id; deleted ones left out. */
export function orderEvents<T extends EventItem>(events: readonly T[]): T[] {
  return events.filter((event) => !event.deleted).sort((a, b) =>
    compareText(a.startsOn, b.startsOn) ||
    daysBetween(b.startsOn, b.endsOn) - daysBetween(a.startsOn, a.endsOn) ||
    compareText(a.title, b.title) ||
    compareText(a.id, b.id)
  );
}

/**
 * The events each day from `from` to `to` holds, in order. An `area` keeps that area's events; a
 * `tag` keeps none, since events have no tags.
 */
export function eventDays<T extends EventItem>(
  events: readonly T[],
  from: Day,
  to: Day,
  area: string | null = null,
  tag: string | null = null,
): Map<Day, T[]> {
  const kept = tag !== null ? [] : orderEvents(events).filter((event) => area === null || event.area === area);
  const days = new Map<Day, T[]>();
  for (let day = from; day <= to; day = addDays(day, 1)) {
    days.set(day, kept.filter((event) => event.startsOn <= day && event.endsOn >= day));
  }
  return days;
}

/** The events `day` falls inside, in order, with which of their days it is. */
export function ongoingEvents<T extends EventItem>(events: readonly T[], day: Day): Ongoing<T>[] {
  return orderEvents(events)
    .filter((event) => event.startsOn <= day && event.endsOn >= day)
    .map((event) => ({
      event,
      dayOf: daysBetween(event.startsOn, day) + 1,
      days: daysBetween(event.startsOn, event.endsOn) + 1,
    }));
}

function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}
