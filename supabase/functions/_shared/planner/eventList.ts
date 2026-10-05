import type { Db } from "../owner.ts";
import { addDays, type Day, daysBetween, isDay } from "../rules/day.ts";
import { type EventItem, orderEvents } from "../rules/events.ts";
import { isUuid, PlannerError } from "./planner.ts";

/** An event as the connector shows it: the rules' item with its notes and who made it. */
export interface CalendarEvent extends EventItem {
  areaId: string | null;
  notes: string | null;
  madeBy: "owner" | "claude";
}

/** What a new event or an edit says. Undefined leaves a field alone; null clears it. */
export interface EventFields {
  title?: string;
  startsOn?: Day;
  endsOn?: Day;
  areaId?: string | null;
  notes?: string | null;
}

const MAX_TITLE = 200;
const MAX_NOTES = 10_000;
const MAX_SPAN = 366;

/**
 * The owner's calendar events (docs/calendar.md, "Events"; ADR 0019), read and changed through the
 * owner's row security, the way the apps' EventList does. Who made one comes from the actor header
 * (stamp_task_maker), and every change goes to the activity log, so undo works as for any other row.
 */
export class EventList {
  constructor(private readonly db: Db) {}

  /** The events that touch the days from `from` to `to`, in the calendar's order. */
  async between(from: Day, to: Day): Promise<CalendarEvent[]> {
    const rows = await this.db`${this.columns()}
      where e.deleted_at is null and e.starts_on <= ${to}::date and e.ends_on >= ${from}::date`;
    return orderEvents(rows.map(toEvent));
  }

  /** The event with this id; it has to be the owner's and not deleted. */
  async event(id: string): Promise<CalendarEvent> {
    if (!isUuid(id)) throw new PlannerError(`No event with id ${id}.`);
    const rows = await this.db`${this.columns()} where e.id = ${id} and e.deleted_at is null`;
    if (rows.length === 0) throw new PlannerError(`No event with id ${id}.`);
    return toEvent(rows[0]);
  }

  /** Adds an event; without a last day it is a one-day event. */
  async add(fields: EventFields): Promise<CalendarEvent> {
    const title = cleanTitle(fields.title);
    if (fields.startsOn === undefined) throw new PlannerError("An event needs its first day.");
    const [startsOn, endsOn] = span(fields.startsOn, fields.endsOn ?? fields.startsOn);
    const id = crypto.randomUUID();
    await this.db`
      insert into public.events (id, title, starts_on, ends_on, area_id, notes)
      values (${id}, ${title}, ${startsOn}, ${endsOn}, ${fields.areaId ?? null}, ${cleanNotes(fields.notes ?? null)})`;
    return await this.event(id);
  }

  /**
   * Changes what the event says, in one change so one undo takes it all back. Moving only the first
   * day keeps how many days it has.
   */
  async update(id: string, fields: EventFields): Promise<CalendarEvent> {
    const event = await this.event(id);
    const title = fields.title === undefined ? event.title : cleanTitle(fields.title);
    const length = daysBetween(event.startsOn, event.endsOn);
    const first = fields.startsOn ?? event.startsOn;
    const last = fields.endsOn ?? (fields.startsOn === undefined ? event.endsOn : addDays(first, length));
    const [startsOn, endsOn] = span(first, last);
    const notes = fields.notes === undefined ? event.notes : cleanNotes(fields.notes);
    await this.db`
      update public.events set title = ${title}, starts_on = ${startsOn}, ends_on = ${endsOn},
        area_id = ${fields.areaId === undefined ? event.areaId : fields.areaId}, notes = ${notes}
      where id = ${id}`;
    return await this.event(id);
  }

  /** Deletes the event; undo_change brings it back. */
  async delete(id: string): Promise<CalendarEvent> {
    const event = await this.event(id);
    await this.db`update public.events set deleted_at = now() where id = ${id}`;
    return event;
  }

  private columns() {
    return this.db`
      select e.id::text, e.title, e.starts_on::text, e.ends_on::text, e.area_id::text, e.notes, e.made_by
      from public.events e`;
  }
}

function cleanTitle(title: string | undefined): string {
  const trimmed = (title ?? "").trim().slice(0, MAX_TITLE);
  if (trimmed.length === 0) throw new PlannerError("An event needs a title.");
  return trimmed;
}

function cleanNotes(notes: string | null): string | null {
  const trimmed = notes?.trim() ?? "";
  return trimmed.length === 0 ? null : trimmed.slice(0, MAX_NOTES);
}

function cleanDay(day: string): Day {
  const trimmed = day.trim();
  if (!isDay(trimmed)) throw new PlannerError(`"${day}" isn't a day: give it as YYYY-MM-DD.`);
  return trimmed;
}

function span(first: string, last: string): [Day, Day] {
  const startsOn = cleanDay(first);
  const endsOn = cleanDay(last);
  if (endsOn < startsOn) throw new PlannerError("An event's last day can't come before its first.");
  if (daysBetween(startsOn, endsOn) > MAX_SPAN) throw new PlannerError("An event can last a year at most.");
  return [startsOn, endsOn];
}

// deno-lint-ignore no-explicit-any
function toEvent(row: any): CalendarEvent {
  return {
    id: row.id,
    title: row.title,
    startsOn: row.starts_on,
    endsOn: row.ends_on,
    area: row.area_id,
    areaId: row.area_id,
    notes: row.notes,
    madeBy: row.made_by,
    deleted: false,
  };
}
