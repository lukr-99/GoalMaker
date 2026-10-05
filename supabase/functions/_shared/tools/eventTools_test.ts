// The calendar event tools (docs/calendar.md, "Events"). The planner is a stub that keeps what it was
// asked to save; the event list's own rules run against the database in connector/endpoint_test.ts.
import { assertEquals, assertRejects, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import type { CalendarEvent, EventFields } from "../planner/eventList.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import { eventTools } from "./eventTools.ts";
import { eventDays, eventLine, names } from "./format.ts";

const ID = "00000000-0000-4000-8000-000000000001";
const TRAVEL = "00000000-0000-4000-8000-0000000000a1";

function event(fields: Partial<CalendarEvent>): CalendarEvent {
  return {
    id: ID,
    title: "Prague",
    startsOn: "2026-10-12",
    endsOn: "2026-10-15",
    area: null,
    areaId: null,
    notes: null,
    madeBy: "claude",
    deleted: false,
    ...fields,
  };
}

function stub() {
  const saved: { add?: EventFields; update?: EventFields; deleted?: string } = {};
  const planner = {
    areas: () => Promise.resolve([{ id: TRAVEL, name: "Travel", color: "teal", emoji: null, archived: false }]),
    findOrCreateArea: (name: string) => Promise.resolve({ id: TRAVEL, name }),
    events: () => ({
      add: (fields: EventFields) => {
        saved.add = fields;
        return Promise.resolve(event({
          title: fields.title,
          startsOn: fields.startsOn,
          endsOn: fields.endsOn ?? fields.startsOn,
          areaId: fields.areaId ?? null,
        }));
      },
      update: (_id: string, fields: EventFields) => {
        saved.update = fields;
        return Promise.resolve(event({ title: fields.title ?? "Prague" }));
      },
      delete: (id: string) => {
        saved.deleted = id;
        return Promise.resolve(event({}));
      },
    }),
  };
  return { planner: planner as unknown as Planner, saved };
}

function tool(name: string) {
  return eventTools.find((one) => one.name === name)!;
}

Deno.test("an event's days read the way a person writes them", () => {
  assertEquals(eventDays({ startsOn: "2026-10-12", endsOn: "2026-10-15" }), "12 to 15 October 2026");
  assertEquals(eventDays({ startsOn: "2026-09-30", endsOn: "2026-10-02" }), "30 September to 2 October 2026");
  assertEquals(eventDays({ startsOn: "2026-12-30", endsOn: "2027-01-02" }), "30 December 2026 to 2 January 2027");
  assertEquals(eventDays({ startsOn: "2026-10-12", endsOn: "2026-10-12" }), "12 October 2026");
});

Deno.test("an event's line shows its days, or which day of it this is", () => {
  const areas = names([{ id: TRAVEL, name: "Travel", color: "teal", emoji: null, archived: false }], [], new Map());
  const trip = event({ areaId: TRAVEL, notes: "Hotel by the river" });
  assertEquals(
    eventLine(trip, areas),
    `- Prague · 12 to 15 October 2026 · @Travel · by Claude (event id ${ID})\n  Notes: Hotel by the river`,
  );
  assertEquals(
    eventLine(event({ madeBy: "owner" }), areas, { dayOf: 2, days: 4 }),
    `- Prague · day 2 of 4 (event id ${ID})`,
  );
  assertEquals(eventLine(event({ madeBy: "owner" }), areas, { dayOf: 1, days: 1 }), `- Prague (event id ${ID})`);
});

Deno.test("add_event passes the days and the area on", async () => {
  const { planner, saved } = stub();
  const text = await tool("add_event").run(planner, {
    title: "Prague",
    from: "2026-10-12",
    to: "2026-10-15",
    area: "Travel",
  });
  assertEquals(saved.add, {
    title: "Prague",
    startsOn: "2026-10-12",
    endsOn: "2026-10-15",
    areaId: TRAVEL,
    notes: undefined,
  });
  assertStringIncludes(text, "Prague · 12 to 15 October 2026 · @Travel");
});

Deno.test("update_event needs something to change, and an empty area clears it", async () => {
  const { planner, saved } = stub();
  await assertRejects(() => tool("update_event").run(planner, { id: ID }), PlannerError, "Say what to change");
  await tool("update_event").run(planner, { id: ID, area: "" });
  assertEquals(saved.update?.areaId, null);
});

Deno.test("delete_event says which event went", async () => {
  const { planner, saved } = stub();
  const text = await tool("delete_event").run(planner, { id: ID });
  assertEquals(saved.deleted, ID);
  assertEquals(text, 'Deleted the event "Prague" (12 to 15 October 2026).');
});
