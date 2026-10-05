// contracts/vectors/calendar.json, the same file the Kotlin and C# tests read. The bars run only in
// the apps.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { eventDays, type EventItem, ongoingEvents } from "./events.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/calendar.json", import.meta.url)),
);

Deno.test("calendar.json: the events of each day", () => {
  for (const c of file.eventDays) {
    const days = eventDays(c.events as EventItem[], c.from, c.to, c.area ?? null, c.tag ?? null);
    const got = Object.fromEntries([...days].map(([day, events]) => [day, events.map((event) => event.id)]));
    assertEquals(got, c.expect, c.name);
  }
});

Deno.test("calendar.json: the events going on", () => {
  for (const c of file.ongoing) {
    const got = ongoingEvents(c.events as EventItem[], c.day).map((on) => ({
      id: on.event.id,
      dayOf: on.dayOf,
      days: on.days,
    }));
    assertEquals(got, c.expect, c.name);
  }
});
