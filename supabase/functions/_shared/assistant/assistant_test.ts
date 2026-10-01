// The quick chat's parts without a database or a model: the declarations, the loop, Gemini's wire form
// and the prompt (docs/assistant.md).
import { assert, assertEquals, assertRejects, assertStringIncludes } from "jsr:@std/assert@1.0.13";
import { tools } from "../tools/tools.ts";
import type { Schema, ToolCall, ToolResult, ToolSpec } from "./chatProvider.ts";
import { ProviderError } from "./chatProvider.ts";
import { CHAT_INPUTS, CHAT_TOOLS, chatDeclarations, chatTools, deletes, firstSentence } from "./chatTools.ts";
import { FakeProvider } from "./fakeProvider.ts";
import { geminiBody, GeminiProvider, replyOf } from "./geminiProvider.ts";
import { systemPrompt } from "./prompt.ts";
import { chooseProvider, onLocalStack } from "./providerChoice.ts";
import { MAX_ROUNDS, runLoop, STOPPED } from "./toolLoop.ts";

const TYPES = new Set(["STRING", "NUMBER", "INTEGER", "BOOLEAN", "ARRAY", "OBJECT"]);
const SCHEMA_KEYS = new Set([
  "type",
  "description",
  "nullable",
  "enum",
  "minimum",
  "maximum",
  "items",
  "properties",
  "required",
]);

/** The problems with a schema under Gemini's rules for function declarations; empty when it is valid. */
function problems(schema: Schema, at: string): string[] {
  const found: string[] = [];
  for (const key of Object.keys(schema)) if (!SCHEMA_KEYS.has(key)) found.push(`${at}: unknown key ${key}`);
  if (!TYPES.has(schema.type)) found.push(`${at}: type ${schema.type}`);
  if (schema.description !== undefined && typeof schema.description !== "string") found.push(`${at}: description`);
  if (schema.enum !== undefined) {
    if (schema.type !== "STRING") found.push(`${at}: enum on ${schema.type}`);
    if (schema.enum.length === 0 || !schema.enum.every((value) => typeof value === "string")) {
      found.push(`${at}: enum values`);
    }
  }
  if ((schema.minimum !== undefined || schema.maximum !== undefined) && !["NUMBER", "INTEGER"].includes(schema.type)) {
    found.push(`${at}: bounds on ${schema.type}`);
  }
  if (schema.type === "ARRAY") {
    if (schema.items === undefined) found.push(`${at}: array without items`);
    else found.push(...problems(schema.items, `${at}[]`));
  } else if (schema.items !== undefined) found.push(`${at}: items on ${schema.type}`);
  if (schema.type === "OBJECT") {
    const properties = schema.properties ?? {};
    for (const [name, property] of Object.entries(properties)) found.push(...problems(property, `${at}.${name}`));
    for (const name of schema.required ?? []) if (!(name in properties)) found.push(`${at}: required ${name} missing`);
  } else if (schema.properties !== undefined || schema.required !== undefined) {
    found.push(`${at}: properties on ${schema.type}`);
  }
  return found;
}

function declarationProblems(declaration: ToolSpec): string[] {
  const found: string[] = [];
  if (!/^[A-Za-z_][A-Za-z0-9_.:-]{0,63}$/.test(declaration.name)) found.push(`${declaration.name}: name`);
  if (declaration.description.trim().length === 0) found.push(`${declaration.name}: no description`);
  if (declaration.parameters !== undefined) {
    if (declaration.parameters.type !== "OBJECT") found.push(`${declaration.name}: parameters aren't an object`);
    if (Object.keys(declaration.parameters.properties ?? {}).length === 0) {
      found.push(`${declaration.name}: empty parameters`);
    }
    found.push(...problems(declaration.parameters, declaration.name));
  }
  return found;
}

Deno.test("every offered tool's input becomes a valid Gemini function declaration", () => {
  assertEquals(chatDeclarations.length, chatTools.length);
  assertEquals(chatDeclarations.flatMap(declarationProblems), []);
  assertEquals(new Set(chatDeclarations.map((declaration) => declaration.name)).size, chatDeclarations.length);
});

Deno.test("the chat offers no tool that deletes", () => {
  const offered = chatDeclarations.map((declaration) => declaration.name);
  for (const name of ["delete_task", "delete_area", "delete_goal", "remove_step", "remove_reminder", "undo_change"]) {
    assert(!offered.includes(name), name);
  }
  assert(tools.filter(deletes).every((tool) => !offered.includes(tool.name)));
  assert(offered.every((name) => !/^delete_/.test(name)));
  for (const name of ["get_today", "add_task", "move_task", "complete_task", "search_tasks"]) {
    assert(offered.includes(name), name);
  }
});

Deno.test("the chat offers only its everyday tools, each a real one, with short descriptions", () => {
  const names = new Set(tools.map((tool) => tool.name));
  assertEquals([...CHAT_TOOLS].filter((name) => !names.has(name)), []);
  assertEquals(chatTools.length, CHAT_TOOLS.size);
  assert(JSON.stringify(chatDeclarations).length < 16_000, "every round carries the declarations; keep them small");
  assertEquals(firstSentence("Adds a task. It lands in the Inbox."), "Adds a task.");
  assertEquals(firstSentence("No end"), "No end");
  assertEquals(firstSentence("Plans for e.g. today. More."), "Plans for e.g.");
});

Deno.test("Gemini thinks as little as it can", () => {
  const body = geminiBody({ system: "", tools: [], allowCalls: true, messages: [{ role: "user", text: "hi" }] });
  assertEquals(body.generationConfig.thinkingConfig, { thinkingLevel: "minimal" });
});

Deno.test("a declaration keeps descriptions, bounds, enums and optional inputs apart", () => {
  const addTask = chatDeclarations.find((declaration) => declaration.name === "add_task")!;
  const properties = addTask.parameters!.properties!;
  assertEquals(addTask.parameters!.required, ["title"]);
  assertEquals(properties.priority.enum?.sort(), ["high", "low", "normal", "urgent"]);
  assertEquals(properties.tags, { type: "ARRAY", items: { type: "STRING" }, description: "Tag names, without #." });
  assert(!("made_by" in properties), "the chat never lets the model say who made a task");
  const today = chatDeclarations.find((declaration) => declaration.name === "get_today")!;
  assertEquals(today.parameters, undefined);
  const activity = chatDeclarations.find((declaration) => declaration.name === "get_activity")!;
  assertEquals(activity.parameters!.properties!.limit.type, "INTEGER");
  assertEquals(activity.parameters!.properties!.limit.maximum, 60);
});

const specs: ToolSpec[] = [{ name: "get_today", description: "Today." }, { name: "add_task", description: "Add." }];

function runner() {
  const calls: ToolCall[] = [];
  const run = (call: ToolCall): Promise<ToolResult> => {
    calls.push(call);
    return Promise.resolve({ id: call.id, name: call.name, text: `ran ${call.name}`, isError: false });
  };
  return { calls, run };
}

Deno.test("the loop runs tool rounds and answers with the model's text", async () => {
  const provider = new FakeProvider([
    { calls: [{ name: "get_today", args: {} }] },
    { calls: [{ name: "add_task", args: { title: "Call the bank" } }] },
    { text: "Done: {{results}}" },
  ]);
  const { calls, run } = runner();
  const answer = await runLoop(provider, "system", [{ role: "user", text: "hi" }], specs, run);
  assertEquals(answer, { text: "Done: ran add_task", rounds: 2 });
  assertEquals(calls.map((call) => call.name), ["get_today", "add_task"]);
  assertEquals(provider.requests.length, 3);
  assertEquals(provider.requests[2].messages.map((message) => message.role), [
    "user",
    "model",
    "tool",
    "model",
    "tool",
  ]);
});

Deno.test("the loop refuses a tool it didn't offer without running it", async () => {
  const provider = new FakeProvider([{ calls: [{ name: "delete_task", args: { id: "x" } }] }, { text: "{{results}}" }]);
  const { calls, run } = runner();
  const answer = await runLoop(provider, "system", [{ role: "user", text: "delete it" }], specs, run);
  assertEquals(calls, []);
  assertStringIncludes(answer.text, "There is no tool called delete_task");
});

Deno.test("the loop stops after its rounds and the last round allows no calls", async () => {
  const turns = Array.from({ length: MAX_ROUNDS + 1 }, () => ({ calls: [{ name: "get_today", args: {} }] }));
  const provider = new FakeProvider(turns);
  const { calls, run } = runner();
  const answer = await runLoop(provider, "system", [{ role: "user", text: "loop" }], specs, run);
  assertEquals(answer, { text: STOPPED, rounds: MAX_ROUNDS });
  assertEquals(calls.length, MAX_ROUNDS);
  assertEquals(provider.requests.map((request) => request.allowCalls).lastIndexOf(true), MAX_ROUNDS - 1);
  assertEquals(provider.requests[MAX_ROUNDS].allowCalls, false);
});

Deno.test("Gemini gets the thread, the declarations and the tool results in its own form", () => {
  const raw = [{ functionCall: { name: "get_today", args: {} }, thoughtSignature: "sig" }];
  const body = geminiBody({
    system: "be brief",
    tools: specs,
    allowCalls: false,
    messages: [
      { role: "user", text: "what's on today?" },
      { role: "model", text: "", calls: [{ name: "get_today", args: {} }], raw },
      { role: "tool", results: [{ id: "c1", name: "get_today", text: "Nothing.", isError: false }] },
    ],
  });
  assertEquals(body.systemInstruction, { parts: [{ text: "be brief" }] });
  assertEquals(body.contents[1], { role: "model", parts: raw });
  assertEquals(body.contents[2], {
    role: "user",
    parts: [{ functionResponse: { id: "c1", name: "get_today", response: { result: "Nothing." } } }],
  });
  assertEquals(body.tools, [{ functionDeclarations: specs }]);
  assertEquals(body.toolConfig.functionCallingConfig.mode, "NONE");
});

Deno.test("Gemini's answer becomes calls or text, without its thinking", () => {
  const parts = [{ text: "thinking", thought: true }, { functionCall: { id: "a", name: "get_today" } }];
  assertEquals(replyOf({ candidates: [{ content: { parts } }] }), {
    kind: "calls",
    calls: [{ id: "a", name: "get_today", args: {} }],
    raw: parts,
  });
  assertEquals(
    replyOf({ candidates: [{ content: { parts: [{ text: "hmm", thought: true }, { text: "All clear." }] } }] }),
    { kind: "text", text: "All clear." },
  );
});

function answering(status: number, body: unknown): typeof fetch {
  return () => Promise.resolve(new Response(JSON.stringify(body), { status }));
}

Deno.test("Gemini's own limit is told apart from other failures", async () => {
  const request = { system: "", messages: [{ role: "user" as const, text: "hi" }], tools: [], allowCalls: true };
  const limited = await assertRejects(
    () => new GeminiProvider("key", answering(429, { error: { status: "RESOURCE_EXHAUSTED" } })).reply(request),
    ProviderError,
  );
  assertEquals(limited.kind, "limit");
  const broken = await assertRejects(
    () => new GeminiProvider("key", answering(400, { error: { status: "INVALID_ARGUMENT" } })).reply(request),
    ProviderError,
  );
  assertEquals(broken.kind, "failed");
  const empty = await assertRejects(
    () => new GeminiProvider("key", answering(200, { promptFeedback: { blockReason: "SAFETY" } })).reply(request),
    ProviderError,
  );
  assertEquals(empty.kind, "failed");
});

Deno.test("the prompt names the planning day, the time zone and GoalMaker's words", () => {
  const prompt = systemPrompt({
    displayName: "Lukas",
    local: "2026-09-30 01:30",
    today: "2026-09-29",
    timeZone: "Europe/Prague",
    dayStartHour: 4,
    areas: ["Health", "Work"],
    tags: [],
    projects: ["GoalMaker"],
  });
  assertStringIncludes(prompt, "Areas: Health, Work. Tags: none. Active projects: GoalMaker.");
  assertStringIncludes(prompt, "today is Tuesday 29 September 2026 (2026-09-29)");
  assertStringIncludes(prompt, "It is 2026-09-30 01:30 in Europe/Prague");
  assertStringIncludes(prompt, "planned day");
  assertStringIncludes(prompt, "You cannot delete anything");
  assertStringIncludes(prompt, "pass the owner's own words as line");
});

Deno.test("the chat adds wants, habits and goals from the owner's line, with few inputs", () => {
  const declared = new Map(chatDeclarations.map((declaration) => [declaration.name, declaration]));
  for (const name of ["add_want", "add_habit", "add_goal"]) {
    const properties = declared.get(name)?.parameters?.properties ?? {};
    assert("line" in properties, `${name} takes a line`);
    assertEquals(declared.get(name)?.parameters?.required, undefined, `${name} needs nothing but the line`);
  }
  for (const [name, offered] of Object.entries(CHAT_INPUTS)) {
    const tool = tools.find((one) => one.name === name)!;
    assert(CHAT_TOOLS.has(name), `${name} is offered`);
    assertEquals([...offered].filter((input) => !(input in tool.input)), [], `${name} offers only its own inputs`);
    assertEquals(Object.keys(declared.get(name)!.parameters!.properties!).sort(), [...offered].sort());
  }
});

Deno.test("the fake provider is picked only on the local stack, and no key means unavailable", () => {
  const script = JSON.stringify([{ text: "hi" }]);
  const local = (key?: string) => (name: string) =>
    ({ SUPABASE_URL: "http://kong:8000", GEMINI_API_KEY: key } as Record<string, string | undefined>)[name];
  const hosted = (key?: string) => (name: string) =>
    ({ SUPABASE_URL: "https://abcdefgh.supabase.co", GEMINI_API_KEY: key } as Record<string, string | undefined>)[name];

  const fake = chooseProvider(script, local());
  assert(fake.kind === "provider" && fake.provider instanceof FakeProvider);
  assertEquals(chooseProvider("{not json", local()).kind, "bad_script");
  assertEquals(chooseProvider(null, local()).kind, "unavailable");
  assertEquals(chooseProvider(script, hosted()).kind, "unavailable");
  const gemini = chooseProvider(script, hosted("key"));
  assert(gemini.kind === "provider" && gemini.provider instanceof GeminiProvider);
  const real = chooseProvider(null, local("key"));
  assert(real.kind === "provider" && real.provider instanceof GeminiProvider);

  assert(onLocalStack("http://kong:8000"));
  assert(!onLocalStack("https://kong:8000"));
  assert(!onLocalStack("https://abcdefgh.supabase.co"));
  assert(!onLocalStack(undefined));
});
