// Finding the project a reference points at (docs/projects.md, spec story 76), and the item ids'
// 'itemKeys' vectors. The board rules themselves run the contract vectors in rules_test.ts.
import { assertEquals } from "jsr:@std/assert@1.0.13";
import {
  folderKey,
  formatItemId,
  isItemKey,
  matchProject,
  parseItemId,
  type ProjectItem,
  repositoryKey,
  suggestItemKey,
} from "./projects.ts";

function project(fields: Partial<ProjectItem> & { id: string; name: string }): ProjectItem {
  return {
    description: "",
    areaId: null,
    status: "active",
    repositoryUrl: null,
    localFolder: null,
    notes: "",
    position: 0,
    deleted: false,
    ...fields,
  };
}

const goalmaker = project({
  id: "11111111-1111-4111-8111-111111111111",
  name: "GoalMaker",
  repositoryUrl: "https://github.com/Owner/GoalMaker.git",
  localFolder: "F:\\GoalMaker",
});
const widget = project({
  id: "22222222-2222-4222-8222-222222222222",
  name: "Widget",
  repositoryUrl: "git@github.com:owner/widget",
  localFolder: "F:/GoalMaker/widget",
});
const gone = project({
  id: "33333333-3333-4333-8333-333333333333",
  name: "Old",
  localFolder: "F:\\Old",
  deleted: true,
});
const all = [goalmaker, widget, gone];

Deno.test("a repository URL is the same project however it is written", () => {
  const key = "github.com/owner/goalmaker";
  for (
    const url of [
      "https://github.com/Owner/GoalMaker.git",
      "https://github.com/owner/goalmaker/",
      "git@github.com:Owner/GoalMaker.git",
      "ssh://git@github.com/owner/goalmaker",
      "https://user@github.com/owner/goalmaker.git/",
    ]
  ) {
    assertEquals(repositoryKey(url), key, url);
  }
  assertEquals(repositoryKey(null), null);
  assertEquals(repositoryKey("  "), null);
});

Deno.test("a folder is the same folder with either slash and any trailing one", () => {
  assertEquals(folderKey("F:\\GoalMaker\\"), "f:/goalmaker");
  assertEquals(folderKey("F:/GoalMaker"), "f:/goalmaker");
  assertEquals(folderKey("/home/me/goalmaker/"), "/home/me/goalmaker");
  assertEquals(folderKey(null), null);
});

Deno.test("a reference finds the project by id, repository, folder or name", () => {
  assertEquals(matchProject(all, goalmaker.id), goalmaker);
  assertEquals(matchProject(all, "git@github.com:owner/goalmaker.git"), goalmaker);
  assertEquals(matchProject(all, "F:\\GoalMaker"), goalmaker);
  assertEquals(matchProject(all, "goalmaker"), goalmaker);
  assertEquals(matchProject(all, "  GoalMaker  "), goalmaker);
});

Deno.test("a folder inside a project belongs to it, and the deepest project wins", () => {
  assertEquals(matchProject(all, "F:\\GoalMaker\\android\\app"), goalmaker);
  assertEquals(matchProject(all, "F:\\GoalMaker\\widget\\src"), widget, "the project inside the other one");
  assertEquals(matchProject(all, "F:\\GoalMakerOther"), null, "not a folder inside it");
});

Deno.test("a deleted project and an unknown reference match nothing", () => {
  assertEquals(matchProject(all, "F:\\Old"), null);
  assertEquals(matchProject(all, gone.id), null);
  assertEquals(matchProject(all, "something else"), null);
  assertEquals(matchProject(all, "   "), null);
});

// deno-lint-ignore no-explicit-any
type Json = any;

async function itemKeys(): Promise<Json> {
  const file = new URL("../../../../contracts/vectors/projects.json", import.meta.url);
  return JSON.parse(await Deno.readTextFile(file)).itemKeys;
}

Deno.test("projects.json itemKeys: a key is suggested from the name", async () => {
  for (const vector of (await itemKeys()).suggest) {
    assertEquals(suggestItemKey(vector.project, vector.taken), vector.expect, vector.name);
  }
});

Deno.test("projects.json itemKeys: which keys are kept", async () => {
  for (const vector of (await itemKeys()).valid) {
    assertEquals(isItemKey(vector.key), vector.expect, vector.key);
  }
});

Deno.test("projects.json itemKeys: an item id is written and read back", async () => {
  const vectors = await itemKeys();
  for (const vector of vectors.format) {
    assertEquals(formatItemId(vector.key, vector.number), vector.expect, vector.expect);
  }
  for (const vector of vectors.parse) {
    assertEquals(parseItemId(vector.text), vector.expect, vector.text);
  }
});
