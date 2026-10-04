// The tools the quick chat offers, as function declarations (docs/assistant.md). They are the
// connector's own tools, minus every one that deletes: the chat never deletes (M7 decision).
import type { z } from "../deps.ts";
import { type Tool, tools } from "../tools/tools.ts";
import type { Schema, ToolSpec } from "./chatProvider.ts";

/**
 * True for a tool the chat must not offer: anything marked destructive (deletes, undo, removing a
 * step) and anything that takes a row away by its name, like remove_reminder.
 */
export function deletes(tool: Tool): boolean {
  return tool.destructive || /^(delete|remove)_/.test(tool.name);
}

/**
 * Inputs the chat fills in itself rather than letting the model choose. A task the owner asks for in
 * the chat is the owner's, so `made_by` is never offered.
 */
export const HIDDEN_INPUTS = new Set(["made_by"]);

/**
 * The connector's tools the chat offers: the everyday ones only. Every model round carries all the
 * declarations, so each tool offered costs time on every message (docs/assistant.md, "Speed").
 */
export const CHAT_TOOLS = new Set([
  "get_today",
  "get_tomorrow",
  "get_inbox",
  "get_calendar",
  "get_task",
  "search_tasks",
  "get_completed_tasks",
  "add_task",
  "update_task",
  "complete_task",
  "reopen_task",
  "drop_task",
  "move_task",
  "add_step",
  "check_step",
  "add_reminder",
  "get_habits",
  "check_in_habit",
  "add_habit",
  "get_goals",
  "add_goal",
  "log_goal_amount",
  "get_projects",
  "get_project_board",
  "add_project_item",
  "move_project_item",
  "get_wants",
  "add_want",
  "get_life_goals",
  "add_life_goal",
  "get_activity",
]);

/**
 * The inputs the chat offers for a tool whose full input is long, when the owner's short line covers the
 * rest (docs/composer.md, "Adding on Wants, Habits and Goals"). A tool not named here offers all of its own.
 */
export const CHAT_INPUTS: Record<string, Set<string>> = {
  add_habit: new Set(["line", "emoji", "direction", "show_on_today"]),
  add_goal: new Set(["line", "emoji", "parent"]),
  add_life_goal: new Set(["title", "why", "by"]),
};

/** The connector's tools the chat offers; one that deletes never is, even if listed. */
export const chatTools: Tool[] = tools.filter((tool) => CHAT_TOOLS.has(tool.name) && !deletes(tool));

// deno-lint-ignore no-explicit-any
type AnyZod = z.ZodTypeAny & { _def: any; description?: string };

/** One zod type as a Gemini schema; the description is the nearest one on the way in. */
export function schemaOf(type: AnyZod, description?: string): Schema {
  const said = type.description ?? description;
  const def = type._def;
  switch (def.typeName) {
    case "ZodOptional":
    case "ZodDefault":
      return schemaOf(def.innerType, said);
    case "ZodNullable":
      return { ...schemaOf(def.innerType, said), nullable: true };
    case "ZodString":
      return withDescription({ type: "STRING" }, said);
    case "ZodBoolean":
      return withDescription({ type: "BOOLEAN" }, said);
    case "ZodNumber": {
      // deno-lint-ignore no-explicit-any
      const checks: any[] = def.checks ?? [];
      const schema: Schema = { type: checks.some((check) => check.kind === "int") ? "INTEGER" : "NUMBER" };
      for (const check of checks) {
        if (check.kind === "min") schema.minimum = check.value;
        if (check.kind === "max") schema.maximum = check.value;
      }
      return withDescription(schema, said);
    }
    case "ZodEnum":
      return withDescription({ type: "STRING", enum: [...def.values] }, said);
    case "ZodArray":
      return withDescription({ type: "ARRAY", items: schemaOf(def.type) }, said);
    case "ZodObject":
      return withDescription(objectOf(def.shape() as z.ZodRawShape), said);
    default:
      throw new Error(`No Gemini schema for ${def.typeName}.`);
  }
}

function withDescription(schema: Schema, description: string | undefined): Schema {
  return description === undefined ? schema : { ...schema, description };
}

function objectOf(shape: z.ZodRawShape, offered?: Set<string>): Schema {
  const properties: Record<string, Schema> = {};
  const required: string[] = [];
  for (const [name, type] of Object.entries(shape)) {
    if (HIDDEN_INPUTS.has(name) || (offered !== undefined && !offered.has(name))) continue;
    properties[name] = schemaOf(type as AnyZod);
    if (!(type as AnyZod).isOptional()) required.push(name);
  }
  return required.length === 0 ? { type: "OBJECT", properties } : { type: "OBJECT", properties, required };
}

/** A tool as a function declaration; a tool without input gets no parameters at all. */
export function declarationOf(tool: Tool): ToolSpec {
  const parameters = objectOf(tool.input, CHAT_INPUTS[tool.name]);
  const description = `${tool.title}. ${firstSentence(tool.description)}`;
  return Object.keys(parameters.properties!).length === 0
    ? { name: tool.name, description }
    : { name: tool.name, description, parameters };
}

/** The first sentence of a connector description; the rest is written for Claude and costs tokens here. */
export function firstSentence(text: string): string {
  const end = text.search(/[.!?](\s|$)/);
  return end === -1 ? text : text.slice(0, end + 1);
}

/** The declarations the chat hands the model on every round. */
export const chatDeclarations: ToolSpec[] = chatTools.map(declarationOf);
