import { z } from "../deps.ts";
import type { CalendarEvent, EventFields } from "../planner/eventList.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import * as format from "./format.ts";
import type { Tool } from "./tools.ts";

const eventId = z.string().describe("The event's id, from get_calendar or get_today.");
const day = z.string().describe("YYYY-MM-DD.");

/** The event's line, with the names its area needs. */
async function eventText(planner: Planner, event: CalendarEvent): Promise<string> {
  return format.eventLine(event, format.names(await planner.areas(), [], new Map()));
}

/** The area an event is filed under, by name; an empty name clears it, and a new one is created. */
async function areaOf(planner: Planner, name: string | undefined): Promise<string | null | undefined> {
  if (name === undefined) return undefined;
  if (name.trim() === "") return null;
  return (await planner.findOrCreateArea(name)).id;
}

/**
 * The calendar event tools (spec, stories 120 to 123; docs/calendar.md, "Events"; ADR 0019). get_calendar
 * and get_today read events; these add, change and delete them, and undo_change takes any of it back.
 */
export const eventTools: Tool[] = [
  {
    name: "add_event",
    title: "Add an event",
    description:
      "Puts an event on the calendar: something that takes up days rather than gets done, like a trip, a holiday " +
      "or a conference. It runs from its first day to its last (the same day for a one-day event, a year at " +
      "most), shows on each of those days and on Today while it lasts, and is never ticked off. For something " +
      "to do on a day, add a task instead.",
    input: {
      title: z.string().describe("Like Prague trip."),
      from: day.describe("The first day."),
      to: day.optional().describe("The last day; the first day by default."),
      area: z.string().optional().describe("An area's name, like Travel."),
      notes: z.string().optional(),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const event = await planner.events().add({
        title: args.title,
        startsOn: args.from,
        endsOn: args.to,
        areaId: await areaOf(planner, args.area),
        notes: args.notes,
      });
      return ["Added:", await eventText(planner, event)].join("\n");
    },
  },
  {
    name: "update_event",
    title: "Edit an event",
    description:
      "Changes an event's title, days, area or notes. What is left out stays as it was. Moving only the first " +
      "day keeps how many days it has; give to as well to change that. An empty area or notes clears it.",
    input: {
      id: eventId,
      title: z.string().optional(),
      from: day.optional().describe("The new first day."),
      to: day.optional().describe("The new last day."),
      area: z.string().optional().describe("An area's name, or empty to clear it."),
      notes: z.string().optional().describe("New notes, or empty to clear them."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const fields: EventFields = {
        title: args.title,
        startsOn: args.from,
        endsOn: args.to,
        areaId: await areaOf(planner, args.area),
        notes: args.notes,
      };
      if (Object.values(fields).every((value) => value === undefined)) {
        throw new PlannerError("Say what to change: title, from, to, area or notes.");
      }
      const event = await planner.events().update(args.id, fields);
      return ["Updated:", await eventText(planner, event)].join("\n");
    },
  },
  {
    name: "delete_event",
    title: "Delete an event",
    description: "Takes an event off the calendar. undo_change brings it back.",
    input: { id: eventId },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      const event = await planner.events().delete(args.id);
      return `Deleted the event "${event.title}" (${format.eventDays(event)}).`;
    },
  },
];
