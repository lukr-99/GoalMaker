// contracts/vectors/quick-add.json, the same file the Kotlin and C# tests read.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { readGoalLine, readHabitLine, readWantLine } from "./quickAdd.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/quick-add.json", import.meta.url)),
);

function run(group: Json, read: (line: string) => unknown) {
  for (const c of group.cases) {
    assertEquals(read(c.line), { ...group.defaults, ...c.expect }, c.name);
  }
}

Deno.test("quick-add.json: every want line", () => run(file.wants, readWantLine));

Deno.test("quick-add.json: every habit line", () => run(file.habits, readHabitLine));

Deno.test("quick-add.json: every goal line", () => run(file.goals, (line) => readGoalLine(line, file.today)));
