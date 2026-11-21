// Runs contracts/vectors/tags.json, which the Kotlin and C# tests run too.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import { offeredTags, type PlacedTag, tagGroups } from "./tagRules.ts";

// deno-lint-ignore no-explicit-any
type Json = any;

const file: Json = JSON.parse(
  await Deno.readTextFile(new URL("../../../../contracts/vectors/tags.json", import.meta.url)),
);

const tags = (rows: Json[]): PlacedTag[] =>
  rows.map((row) => ({ id: row.id, name: row.id, areaId: row.area ?? null, projectId: row.project ?? null }));

Deno.test("tags.json: what a tag picker offers", () => {
  for (const one of file.offered) {
    const offered = offeredTags(
      tags(one.tags),
      new Set(one.areas),
      new Map(Object.entries(one.projects)) as Map<string, string | null>,
      { areaId: one.chosen.area ?? null, projectId: one.chosen.project ?? null },
    );
    assertEquals(offered.map((tag) => tag.id), one.expect, one.name);
  }
});

Deno.test("tags.json: how Settings groups the tags", () => {
  for (const one of file.groups) {
    const groups = tagGroups(tags(one.tags), one.areas, Object.keys(one.projects));
    assertEquals(
      groups.map((group) => ({ place: group.place, tags: group.tags.map((tag) => tag.id) })),
      one.expect,
      one.name,
    );
  }
});
