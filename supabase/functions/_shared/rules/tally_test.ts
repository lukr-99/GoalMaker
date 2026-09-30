// contracts/vectors/tally.json, the same file the Kotlin and C# tests read.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { counts, dayTotals, editorFolder, projectFor, sortSample, tallyDayId, type TallyRule } from "./tally.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const read = async (path: string): Promise<Json> =>
  JSON.parse(await Deno.readTextFile(new URL(`../../../../contracts/${path}`, import.meta.url)));
const file: Json = await read("vectors/tally.json");
const shipped: TallyRule[] = (await read("content/tally-rules.json")).rules;

for (const vector of file.match) {
  Deno.test(`tally.json match: ${vector.name}`, () => {
    const defaults = vector.defaults === "shipped" ? shipped : vector.defaults;
    assertEquals(sortSample(vector.sample, vector.own ?? [], defaults, vector.projects ?? []), vector.expect);
  });
}

for (const vector of file.folder) {
  Deno.test(`tally.json folder: ${vector.name}`, () => {
    assertEquals(editorFolder(vector.app, vector.title), vector.expect);
  });
}

for (const vector of file.project) {
  Deno.test(`tally.json project: ${vector.name}`, () => {
    assertEquals(projectFor(vector.folder, vector.projects), vector.expect);
  });
}

for (const vector of file.idle) {
  Deno.test(`tally.json idle: ${vector.name}`, () => {
    assertEquals(counts(vector.secondsSinceInput, vector.category, vector.locked, vector.asleep), vector.expect);
  });
}

for (const vector of file.days) {
  Deno.test(`tally.json days: ${vector.name}`, () => {
    assertEquals(dayTotals(vector.intervals, vector.startHour), vector.expect);
  });
}

Deno.test("tally.json: every day id", async () => {
  for (const vector of file.ids) {
    assertEquals(
      await tallyDayId(vector.owner, vector.day, vector.device, vector.category, vector.project),
      vector.expect,
    );
  }
});
