import { z } from "../deps.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import { type Day, mondayOf } from "../rules/day.ts";
import {
  defaultCategoryName,
  durationText,
  groupTally,
  type TallyGroup,
  type TallyGrouping,
  type TallyRow,
} from "../rules/tally.ts";
import * as format from "./format.ts";
import type { Tool } from "./tools.ts";

const day = z.string().describe('"today", "tomorrow" or a date like 2026-09-21, in the owner\'s planning days.');

/** What a group is called in a sentence: a category's name, a project's, or the kind of device. */
export async function tallyNames(planner: Planner): Promise<(by: TallyGrouping, key: string | null) => string> {
  const own = await planner.tally().categoryNames();
  const projects = new Map((await planner.projects()).map((project) => [project.id, project.name]));
  return (by, key) => {
    if (by === "device") return key === "pc" ? "PC" : "Phone";
    if (by === "project") return key === null ? "No project" : projects.get(key) ?? "A deleted project";
    return key === null ? "Other" : own.get(key) ?? defaultCategoryName(key);
  };
}

/** The rows grouped every way the digest and the tool offer, with names instead of ids. */
export async function tallySummary(planner: Planner, rows: TallyRow[]) {
  const name = await tallyNames(planner);
  const named = (by: TallyGrouping, groups: TallyGroup[]) =>
    groups.map((group) => ({ [by]: name(by, group.key), minutes: group.minutes }));
  return {
    minutes: rows.reduce((sum, row) => sum + row.minutes, 0),
    by_category: named("category", groupTally(rows, "category")),
    by_project: named("project", groupTally(rows, "project")),
    by_device: named("device", groupTally(rows, "device")),
  };
}

async function dayArgument(planner: Planner, text: string | undefined, fallback: Day): Promise<Day> {
  if (text === undefined || text.trim() === "") return fallback;
  const parsed = await planner.day(text);
  if (parsed === null) throw new PlannerError(`"${text}" isn't a day: use today, tomorrow or a date like 2026-09-21.`);
  return parsed;
}

/**
 * Tally through the connector (docs/tally.md, spec story 113): where the owner's time went, from the
 * daily minutes the phone and the PC synced. Apps and windows never reach the server (ADR 0013), so
 * the answer is by category, project or device, never by app.
 */
export const tallyTools: Tool[] = [
  {
    name: "get_time_tally",
    title: "Where time went",
    description:
      "Where the owner's time went on the phone and the PC, from Tally: minutes per category (Coding, Video, " +
      "Social and the owner's own), per project (time in an editor on the PC counts toward the project whose " +
      "folder it is), or per device, over a stretch of days. Only daily totals are kept, never the apps or " +
      "windows themselves, so it can't say which app; a category is as fine as it gets. Tally is off until the " +
      "owner turns it on, so an empty answer may just mean that. Compare it with get_completed_tasks or the " +
      "calendar to see time against the plan.",
    input: {
      from: day.optional().describe("The first day; this week's Monday by default."),
      to: day.optional().describe("The last day; today by default."),
      by: z.enum(["category", "project", "device"]).optional().describe("How to group it; category by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const today = (await planner.now()).today;
      const first = await dayArgument(planner, args.from, mondayOf(today));
      const last = await dayArgument(planner, args.to, today);
      if (last < first) throw new PlannerError("The last day comes before the first one.");
      const by: TallyGrouping = args.by ?? "category";
      const rows = await planner.tally().between(first, last);
      const span = first === last ? format.longDay(first) : `${format.longDay(first)} to ${format.longDay(last)}`;
      const total = rows.reduce((sum, row) => sum + row.minutes, 0);
      if (total === 0) {
        return `No Tally time from ${span}. Tally may be off on the owner's devices; it is switched on in the apps.`;
      }
      const name = await tallyNames(planner);
      const days = new Set(rows.map((row) => row.day)).size;
      const lines = [
        `Time from ${span}, by ${by}: ${durationText(total)} over ${days} ${days === 1 ? "day" : "days"}.`,
      ];
      for (const group of groupTally(rows, by)) {
        const share = Math.round((group.minutes / total) * 100);
        lines.push(`- ${name(by, group.key)} · ${durationText(group.minutes)} · ${share}%`);
      }
      return lines.join("\n");
    },
  },
];
