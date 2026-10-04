import { z } from "../deps.ts";
import { type LifeGoal, type LifeGoalFields, yearsLater } from "../planner/lifeGoalList.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import type { Day } from "../rules/day.ts";
import { timeLeft } from "../rules/lifeGoals.ts";
import * as format from "./format.ts";
import type { Tool } from "./tools.ts";

const lifeGoalId = z.string().describe("The life goal's id, from get_life_goals.");
const inYears = z.number().int().min(1).max(100).describe(
  'The by date as the same day N years from today, like the apps\' "In 10 years". Instead of by.',
);

/** A life goal's line as it stands today, with the names its area needs. */
async function lifeGoalText(planner: Planner, goal: LifeGoal): Promise<string> {
  const today = (await planner.now()).today;
  const names = format.names(await planner.areas(), [], new Map());
  return format.lifeGoalLine(goal, timeLeft(goal.by, today), names);
}

/** The area a life goal is filed under, by name; an empty name clears it, and a new one is created. */
async function areaOf(planner: Planner, name: string | undefined): Promise<string | null | undefined> {
  if (name === undefined) return undefined;
  if (name.trim() === "") return null;
  return (await planner.findOrCreateArea(name)).id;
}

/**
 * The by date from `by` (a day, or empty to clear it) or `in_years` (counted from the planning day);
 * undefined when neither is given.
 */
export async function byDateOf(
  by: string | undefined,
  years: number | undefined,
  today: () => Promise<Day>,
): Promise<Day | null | undefined> {
  if (by !== undefined && years !== undefined) throw new PlannerError("Give by or in_years, not both.");
  if (years !== undefined) return yearsLater(await today(), years);
  if (by === undefined) return undefined;
  return by.trim() === "" ? null : by.trim();
}

/**
 * The life goals tools (spec, stories 114 to 119; docs/life-goals.md). Pictures are added in the apps
 * only, and deleting a life goal is the owner's, in the apps.
 */
export const lifeGoalTools: Tool[] = [
  {
    name: "get_life_goals",
    title: "Life goals",
    description:
      "The owner's long-run life goals, each with why it matters. Open ones come first in the owner's order, then " +
      "the achieved and dropped ones, the most recently closed first. Each one comes with its time left to its by " +
      'date ("10 years left", "Today", "Past its date"), its area, who made it, how many pictures it has and its ' +
      "why. All of them by default.",
    input: {
      status: z.enum(["open", "closed", "all"]).optional().describe("all by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const today = (await planner.now()).today;
      const wanted: string = args.status ?? "all";
      const all = await planner.lifeGoals().all();
      if (all.length === 0) return "There are no life goals yet. add_life_goal writes one down with why it matters.";
      const names = format.names(await planner.areas(), [], new Map());
      const line = (goal: LifeGoal) => format.lifeGoalLine(goal, timeLeft(goal.by, today), names);
      const lines: string[] = [];
      const open = all.filter((goal) => goal.status === "open");
      const closed = all.filter((goal) => goal.status !== "open");
      if (wanted !== "closed" && open.length > 0) lines.push("Open:", ...open.map(line));
      if (wanted !== "open" && closed.length > 0) lines.push("Achieved and dropped:", ...closed.map(line));
      return lines.length === 0 ? `No ${wanted} life goals.` : lines.join("\n");
    },
  },
  {
    name: "add_life_goal",
    title: "Add a life goal",
    description:
      "Writes down a long-run life goal with why it matters. The why is required: ask the owner for it when they " +
      "have not said it, never make one up. A by date is optional, as a day or as in_years. It is added open, after " +
      "the other open ones. Pictures are added in the apps only.",
    input: {
      title: z.string().describe("Like Run a marathon."),
      why: z.string().optional().describe("In the owner's words. Required; ask if it is missing."),
      by: z.string().optional().describe("YYYY-MM-DD."),
      in_years: inYears.optional(),
      area: z.string().optional().describe("An area's name, like Health."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const goal = await planner.lifeGoals().add({
        title: args.title,
        why: args.why,
        by: await byDateOf(args.by, args.in_years, async () => (await planner.now()).today),
        areaId: await areaOf(planner, args.area),
      });
      return ["Added:", await lifeGoalText(planner, goal)].join("\n");
    },
  },
  {
    name: "update_life_goal",
    title: "Edit a life goal",
    description:
      "Changes a life goal's title, why, by date or area, or marks it achieved, dropped or open again. What is " +
      "left out stays as it was. An empty by or area clears it. Only mark a life goal achieved or dropped when " +
      "the owner says so. Pictures are changed in the apps only, and so is deleting a life goal.",
    input: {
      id: lifeGoalId,
      title: z.string().optional(),
      why: z.string().optional(),
      by: z.string().optional().describe("The new by date as YYYY-MM-DD, or empty to clear it."),
      in_years: inYears.optional(),
      area: z.string().optional().describe("An area's name, or empty to clear it."),
      status: z.enum(["open", "achieved", "dropped"]).optional().describe(
        "achieved or dropped closes it; open reopens a closed one.",
      ),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const lifeGoals = planner.lifeGoals();
      const before = await lifeGoals.lifeGoal(args.id);
      const fields: LifeGoalFields = {
        title: args.title,
        why: args.why,
        by: await byDateOf(args.by, args.in_years, async () => (await planner.now()).today),
        areaId: await areaOf(planner, args.area),
        status: args.status,
      };
      if (Object.values(fields).every((value) => value === undefined)) {
        throw new PlannerError("Say what to change: title, why, by, in_years, area or status.");
      }
      const goal = await lifeGoals.update(args.id, fields);
      const said = goal.status === before.status
        ? "Updated:"
        : goal.status === "open"
        ? "Reopened:"
        : `Marked ${goal.status}:`;
      return [said, await lifeGoalText(planner, goal)].join("\n");
    },
  },
];
