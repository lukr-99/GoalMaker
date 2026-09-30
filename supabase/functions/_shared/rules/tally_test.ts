// contracts/vectors/tally.json, the same file the Kotlin and C# tests read.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import {
  counts,
  dayTotals,
  defaultCategoryName,
  durationText,
  editorFolder,
  groupTally,
  projectFor,
  sortSample,
  tallyDayId,
  type TallyRow,
  type TallyRule,
} from "./tally.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const read = async (path: string): Promise<Json> =>
  JSON.parse(await Deno.readTextFile(new URL(`../../../../contracts/${path}`, import.meta.url)));
const file: Json = await read("vectors/tally.json");
const defaults: Json = await read("content/tally-rules.json");
const shipped: TallyRule[] = defaults.rules;

for (const vector of file.match) {
  Deno.test(`tally.json match: ${vector.name}`, () => {
    const rules = vector.defaults === "shipped" ? shipped : vector.defaults;
    assertEquals(sortSample(vector.sample, vector.own ?? [], rules, vector.projects ?? []), vector.expect);
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

// The connector names a default category by its key, since it can't read the shipped file once deployed.
Deno.test("a default category's name is its key, capitalized, as the shipped file says", () => {
  for (const category of defaults.categories) assertEquals(defaultCategoryName(category.id), category.name);
});

Deno.test("Tally minutes group by category, project and device, most first and none last", () => {
  const rows: TallyRow[] = [
    { day: "2026-08-17", deviceKind: "pc", category: "coding", projectId: "p-goalmaker", minutes: 120 },
    { day: "2026-08-18", deviceKind: "pc", category: "coding", projectId: null, minutes: 30 },
    { day: "2026-08-18", deviceKind: "phone", category: "video", projectId: null, minutes: 90 },
    { day: "2026-08-19", deviceKind: "phone", category: "coding", projectId: null, minutes: 10 },
  ];
  assertEquals(groupTally(rows, "category"), [{ key: "coding", minutes: 160 }, { key: "video", minutes: 90 }]);
  assertEquals(groupTally(rows, "project"), [{ key: null, minutes: 130 }, { key: "p-goalmaker", minutes: 120 }]);
  assertEquals(groupTally(rows, "device"), [{ key: "pc", minutes: 150 }, { key: "phone", minutes: 100 }]);
  assertEquals(groupTally([{ ...rows[0], projectId: null, minutes: 120 }, rows[0]], "project"), [
    { key: "p-goalmaker", minutes: 120 },
    { key: null, minutes: 120 },
  ]);
  assertEquals([durationText(45), durationText(120), durationText(185)], ["45 min", "2 h", "3 h 5 min"]);
});
