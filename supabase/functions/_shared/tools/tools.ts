import { z } from "../deps.ts";
import {
  type Area,
  type Change,
  cleanColumn,
  type GoalFields,
  type Habit,
  type HabitFields,
  type Planner,
  PlannerError,
  type TaskFields,
} from "../planner/planner.ts";
import { AREA_COLORS } from "../planner/palette.ts";
import { fold, searchArchive } from "../rules/archiveRules.ts";
import { addDays, type Day, mondayOf } from "../rules/day.ts";
import {
  type GoalHorizon,
  type GoalItem,
  goalProgress,
  periodEnd as goalPeriodEnd,
  periodStart as goalPeriodStart,
} from "../rules/goals.ts";
import { goalAmounts, habitPeriodStart, habitState, ring, streak } from "../rules/habits.ts";
import { lists } from "../rules/listRules.ts";
import { board, COLUMNS, PRIORITIES, type ProjectItem } from "../rules/projects.ts";
import { seriesOf } from "../rules/occurrences.ts";
import { nextOccurrence, parseRecurrence } from "../rules/recurrence.ts";
import { byCreation, byTime, type TaskItem } from "../rules/task.ts";
import * as format from "./format.ts";

/**
 * One tool: what Claude sees (name, description, input) and what it does with the owner's planner.
 * The connector serves these over MCP; the quick chat (M7) will call the same ones.
 */
export interface Tool {
  name: string;
  title: string;
  description: string;
  input: z.ZodRawShape;
  readOnly: boolean;
  destructive: boolean;
  // deno-lint-ignore no-explicit-any
  run(planner: Planner, args: any): Promise<string>;
}

const id = z.string().describe("The task's id, from a list or a search.");
const day = z.string().describe('"today", "tomorrow" or a date like 2026-09-21, in the owner\'s planning days.');
const repeat = z.string().describe(
  "A repeat rule: FREQ=DAILY, FREQ=WEEKLY or FREQ=MONTHLY, with optional INTERVAL=n, BYDAY=MO,WE,FR (weekly) " +
    "or BYMONTHDAY=15 (monthly). Examples: FREQ=DAILY, FREQ=WEEKLY;BYDAY=MO,TH, FREQ=MONTHLY;INTERVAL=2.",
);

async function dayFrom(planner: Planner, text: string | null | undefined): Promise<Day | null | undefined> {
  if (text === undefined) return undefined;
  if (text === null || text.trim() === "") return null;
  const parsed = await planner.day(text);
  if (parsed === null) throw new PlannerError(`"${text}" isn't a day: use today, tomorrow or a date like 2026-09-21.`);
  return parsed;
}

async function namesOf(planner: Planner): Promise<format.Names> {
  return format.names(await planner.areas(), await planner.tags(), await planner.tagLinks(), await planner.projects());
}

async function taskFields(planner: Planner, args: Record<string, unknown>): Promise<TaskFields> {
  return {
    title: args.title as string | undefined,
    notes: args.notes as string | undefined,
    day: await dayFrom(planner, args.day as string | null | undefined),
    time: args.time as string | null | undefined,
    deadline: await dayFrom(planner, args.deadline as string | null | undefined),
    area: args.area as string | null | undefined,
    tags: args.tags as string[] | undefined,
    topPriority: args.top_priority as boolean | undefined,
    repeat: args.repeat as string | null | undefined,
    priority: args.priority as string | undefined,
  };
}

const projectRef = z.string().describe(
  "The project: its id, its repository URL, a folder inside it (the one you are working in), or its name.",
);
const itemId = z.string().describe("The item's id, from a board or a search; a project item is an ordinary task.");
const itemType = z.enum(["task", "idea", "bug"]).describe("What kind of item it is. A new idea lands in the backlog.");
const priority = z.enum(PRIORITIES as [string, ...string[]]).describe(
  "How important it is: urgent, high, normal or low. Items sit in a column in this order.",
);
const column = z.enum(COLUMNS as [string, ...string[]]).describe("A board column: backlog, todo, doing or done.");
const projectStatus = z.enum(["active", "paused", "done"]).describe(
  "Where the project stands: active, paused or done.",
);

/** What a board column is called in a sentence. */
function columnName(column: string | null | undefined): string {
  return format.COLUMN_NAMES[column ?? ""] ?? column ?? "no column";
}

/** A project's milestones, for the board and for the item lines. */
async function milestonesOf(planner: Planner, project: ProjectItem) {
  return (await planner.milestones()).filter((milestone) => milestone.projectId === project.id);
}

const areaId = z.string().describe("The area's id, from list_areas_and_tags.");
const tagId = z.string().describe("The tag's id, from list_areas_and_tags.");
const stepId = z.string().describe("The step's id, from get_task.");
const milestoneId = z.string().describe("The milestone's id, from get_project_board or find_project.");
const areaColor = z.enum(AREA_COLORS as [string, ...string[]]).describe("The area's color, from the palette.");
const cadence = z.enum(["daily", "weekdays", "per_week", "per_month"]).describe(
  "How often it runs: every day, on chosen weekdays, or so many times a week or a month.",
);
const measure = z.enum(["check", "count", "amount"]).describe(
  "How a day is measured: ticked off, counted, or an amount against a target.",
);
const direction = z.enum(["at_least", "at_most"]).describe(
  "at_least reaches the target; at_most keeps at or under it, which is a habit to keep down.",
);
const weekday = z.enum(["mon", "tue", "wed", "thu", "fri", "sat", "sun"]);

const WEEKDAY_BITS: Record<string, number> = { mon: 1, tue: 2, wed: 4, thu: 8, fri: 16, sat: 32, sun: 64 };

/** Weekday names as the bitmask the habits table keeps: Monday 1, Tuesday 2 ... Sunday 64. */
function weekdayMask(days: string[] | undefined): number | undefined {
  if (days === undefined) return undefined;
  let mask = 0;
  for (const name of days) mask |= WEEKDAY_BITS[name] ?? 0;
  return mask;
}

// A month grid is 42 days, so a repeat is followed at most this many times inside a range.
const MAX_REPEATS = 60;

/**
 * The days each repeating task would come round to inside a range, by day. Only the current
 * occurrence exists as a row, so these are worked out from the task's rule: an open task only, never
 * the day it is already planned for, and never a day another occurrence of its series holds
 * (docs/calendar.md, the same walk CalendarRules does).
 */
function projectedRepeats(tasks: TaskItem[], from: Day, to: Day): Map<Day, TaskItem[]> {
  const taken = new Map<string, Set<Day>>();
  for (const task of tasks.filter((task) => task.plannedDate !== null)) {
    const series = seriesOf(task);
    taken.set(series, (taken.get(series) ?? new Set<Day>()).add(task.plannedDate!));
  }
  const found = new Map<Day, TaskItem[]>();
  for (const task of tasks) {
    if (task.state !== "open" || task.recurrence === null || task.plannedDate === null) continue;
    const rule = parseRecurrence(task.recurrence);
    if (rule === null) continue;
    const series = taken.get(seriesOf(task)) ?? new Set<Day>();
    let day: Day | null = task.plannedDate;
    for (let step = 0; step < MAX_REPEATS; step++) {
      day = nextOccurrence(rule, day, day);
      if (day === null || day > to) break;
      if (day >= from && !series.has(day)) found.set(day, [...(found.get(day) ?? []), task]);
    }
  }
  return found;
}

/** An area as one line: its name, color, emoji and whether it is put away. */
function areaText(area: Area): string {
  const parts = [`@${area.name}`, area.color];
  if (area.emoji) parts.push(area.emoji);
  if (area.archived) parts.push("archived");
  return `${parts.join(" · ")} (area id ${area.id})`;
}

/** One entry of the activity log as one line, the way the Activity screen reads it. */
function changeText(change: Change): string {
  const who = change.actor === "claude" ? "Claude" : change.actor === "system" ? "GoalMaker" : "the owner";
  const what = change.label === null ? change.entity : `${change.label} (${change.entity})`;
  const undone = change.undone ? " · undone" : "";
  return `- ${change.action} ${what}, by ${who} at ${change.at}${undone} (change id ${change.id})`;
}

/** A habit's fields from a tool's arguments, with the weekdays turned into the mask they are kept as. */
async function habitFields(planner: Planner, args: Record<string, unknown>): Promise<HabitFields> {
  return {
    name: args.name as string | undefined,
    emoji: args.emoji as string | null | undefined,
    cadence: args.cadence as HabitFields["cadence"],
    weekdays: weekdayMask(args.weekdays as string[] | undefined),
    times: args.times as number | undefined,
    measure: args.measure as HabitFields["measure"],
    target: args.target as number | undefined,
    unit: args.unit as string | null | undefined,
    direction: args.direction as HabitFields["direction"],
    goal: args.goal as string | null | undefined,
    startsOn: (await dayFrom(planner, args.starts_on as string | undefined)) ?? undefined,
    archived: args.archived as boolean | undefined,
  };
}

/** One habit as the Habits screen shows it today, for the line after a change. */
async function habitLineFor(planner: Planner, habit: Habit): Promise<string> {
  const today = (await planner.now()).today;
  const own = (await planner.checkins()).filter((checkin) => checkin.habitId === habit.id);
  const rests = (await planner.pauses()).filter((pause) => pause.habitId === habit.id);
  const state = habitState(habit, habitPeriodStart(habit, today), today, own, rests);
  const done = (ring(habit, today, own) ?? 0) * (habit.measure === "check" ? 1 : habit.target ?? 1);
  return format.habitLine(habit, state, done, streak(habit, today, own, rests));
}

const goalId = z.string().describe("The goal's id, from get_goals.");
const habitId = z.string().describe("The habit's id, from get_habits.");
const horizon = z.enum(["year", "month", "week", "day"]).describe(
  "How long the goal runs: year, month, week or day. A week starts on Monday, a month on the 1st.",
);

/** Where each goal stands, from the tasks that serve it and what was logged or checked in on it. */
async function progressOf(planner: Planner) {
  const tasks = await planner.tasks();
  const entries = await planner.goalEntries();
  const habits = await planner.habits();
  const checkins = await planner.checkins();
  return (goal: GoalItem) =>
    goalProgress(
      goal.mode,
      goal.status,
      goal.target,
      tasks.filter((task) => task.goalId === goal.id && !task.deleted),
      [
        ...(entries.get(goal.id) ?? []),
        ...(goal.mode === "number"
          ? goalAmounts(goal, habits, checkins).map((amount) => ({ amount, deleted: false }))
          : []),
      ],
    );
}

// "14 to 20 September 2026" for a week, "September 2026" for a month, "2026" for a year.
function periodText(goal: GoalItem): string {
  const end = goalPeriodEnd(goal.horizon, goal.periodStart);
  if (goal.horizon === "year") return goal.periodStart.slice(0, 4);
  if (goal.horizon === "month") return format.longDay(goal.periodStart).split(" ").slice(2).join(" ");
  if (goal.horizon === "day") return format.longDay(goal.periodStart);
  return `${goal.periodStart} to ${end}`;
}

async function line(planner: Planner, taskId: string): Promise<string> {
  const task = await planner.task(taskId);
  return task === null ? "" : format.taskLine(task, await namesOf(planner), { showDay: true });
}

export const tools: Tool[] = [
  {
    name: "get_today",
    title: "Today",
    description:
      "The owner's Today list as GoalMaker shows it: top priorities, scheduled tasks by time, more, and overdue " +
      "tasks from earlier days, with how many of today's tasks are done. Today is the owner's planning day, " +
      "which starts at their day-start hour, not midnight.",
    input: {},
    readOnly: true,
    destructive: false,
    run: async (planner) => {
      const { today } = await planner.now();
      return format.today(lists(await planner.tasks(), today), await namesOf(planner));
    },
  },
  {
    name: "get_tomorrow",
    title: "Tomorrow",
    description: "Tomorrow's open tasks, top priorities first, then by time.",
    input: {},
    readOnly: true,
    destructive: false,
    run: async (planner) => {
      const { today } = await planner.now();
      return format.tomorrow(lists(await planner.tasks(), today), await namesOf(planner), addDays(today, 1));
    },
  },
  {
    name: "get_inbox",
    title: "Inbox",
    description: "Open tasks with no day and no area, oldest first: what still needs sorting.",
    input: {},
    readOnly: true,
    destructive: false,
    run: async (planner) => {
      const { today } = await planner.now();
      return format.inbox(lists(await planner.tasks(), today), await namesOf(planner));
    },
  },
  {
    name: "get_task",
    title: "One task",
    description: "One task in full: its day, time, area, tags, repeat, deadline, notes, steps and reminders.",
    input: { id },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const task = await planner.task(args.id);
      if (task === null) throw new PlannerError(`No task with id ${args.id}.`);
      return format.taskDetail(
        task,
        await namesOf(planner),
        await planner.steps(task.id),
        await planner.reminders(task.id),
        await planner.milestones(),
      );
    },
  },
  {
    name: "list_areas_and_tags",
    title: "Areas and tags",
    description: "The owner's areas (life areas like Health or Work; archived ones marked) and tags.",
    input: {},
    readOnly: true,
    destructive: false,
    run: async (planner) => {
      const areas = await planner.areas();
      const tags = await planner.tags();
      return [
        areas.length === 0 ? "No areas yet." : "Areas:",
        ...areas.map((area) =>
          `- ${area.emoji ? `${area.emoji} ` : ""}${area.name}${area.archived ? " (archived)" : ""}`
        ),
        tags.length === 0 ? "No tags yet." : "Tags:",
        ...tags.map((tag) => `- #${tag.name}`),
      ].join("\n");
    },
  },
  {
    name: "add_area",
    title: "Add an area",
    description:
      "Adds an area, one of the few parts of life the owner sorts tasks by, with a color from the palette and an " +
      "emoji. Naming an area that is already there brings that one back rather than making a second.",
    input: {
      name: z.string().describe("The area's name, like Work."),
      color: areaColor.optional(),
      emoji: z.string().optional().describe("One emoji for the area."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => `Area ${areaText(await planner.addArea(args))}.`,
  },
  {
    name: "update_area",
    title: "Edit an area",
    description:
      "Renames an area, recolors it, gives it an emoji, or archives it and brings it back. An archived area keeps " +
      "its tasks and simply drops out of the lists that offer areas.",
    input: {
      id: areaId,
      name: z.string().optional().describe("A new name."),
      color: areaColor.optional(),
      emoji: z.string().optional().describe("One emoji, or empty for none."),
      archived: z.boolean().optional().describe("true puts it away, false brings it back."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => `Area ${areaText(await planner.updateArea(args.id, { ...args, id: undefined }))}.`,
  },
  {
    name: "delete_area",
    title: "Delete an area",
    description:
      "Deletes an area. The tasks and projects in it keep everything else and simply have no area, so nothing is " +
      "lost with it. Ask the owner first. To put one aside instead, archive it with update_area.",
    input: { id: areaId },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      const area = await planner.deleteArea(args.id);
      return `Deleted the area @${area.name}. What was in it kept everything but the area.`;
    },
  },
  {
    name: "add_tag",
    title: "Add a tag",
    description:
      "Adds a tag. Naming one that is already there brings that one back, so tags never double up. Tags are also " +
      "made by naming them on a task.",
    input: { name: z.string().describe("The tag's name, without #.") },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const tag = await planner.findOrCreateTag(args.name);
      return `Tag #${tag.name} (tag id ${tag.id}).`;
    },
  },
  {
    name: "update_tag",
    title: "Rename a tag",
    description: "Renames a tag everywhere it is used, so every task that carries it reads the new name.",
    input: { id: tagId, name: z.string().describe("The new name, without #.") },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const tag = await planner.updateTag(args.id, args.name);
      return `Tag #${tag.name} (tag id ${tag.id}).`;
    },
  },
  {
    name: "delete_tag",
    title: "Delete a tag",
    description:
      "Deletes a tag and takes it off every task that carried it. The tasks themselves stay as they are. Ask the " +
      "owner first.",
    input: { id: tagId },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      const tag = await planner.deleteTag(args.id);
      return `Deleted the tag #${tag.name} and took it off everything that carried it.`;
    },
  },
  {
    name: "get_completed_tasks",
    title: "Completed tasks",
    description:
      "The tasks the owner completed from one planning day to another, in the order they were done: this week " +
      "(Monday to today) unless told otherwise. For reviews and summaries.",
    input: {
      from: day.optional().describe("The first day; this week's Monday by default."),
      to: day.optional().describe("The last day; today by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const { today } = await planner.now();
      const first = (await dayFrom(planner, args.from)) ?? mondayOf(today);
      const last = (await dayFrom(planner, args.to)) ?? today;
      const done = await planner.completedBetween(await planner.tasks(), first, last);
      const names = await namesOf(planner);
      const period = `${format.longDay(first)} to ${format.longDay(last)}`;
      return done.length === 0 ? `Nothing was completed from ${period}.` : [
        `Completed from ${period}: ${done.length}`,
        ...done.map((task) => format.taskLine(task, names, { showDay: true })),
      ]
        .join("\n");
    },
  },
  {
    name: "search_tasks",
    title: "Search",
    description:
      "Finds tasks whose title or notes contain every word of the query, ignoring case and accents: open tasks, " +
      "and done tasks from the archive (most recently completed first).",
    input: {
      query: z.string().describe("Words to look for."),
      include_done: z.boolean().optional().describe("Also search done tasks in the archive (default true)."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const tasks = await planner.tasks();
      const words = String(args.query).split(" ").map(fold).filter((word) => word.length > 0);
      const open = tasks
        .filter((task) => task.state === "open")
        .filter((task) => words.every((word) => `${fold(task.title)}\n${fold(task.notes)}`.includes(word)))
        .sort(byCreation);
      const done = args.include_done === false ? [] : searchArchive(tasks, String(args.query));
      const names = await namesOf(planner);
      const lines = [
        ...(open.length > 0
          ? ["Open:", ...open.slice(0, 30).map((task) => format.taskLine(task, names, { showDay: true }))]
          : []),
        ...(done.length > 0
          ? ["Done:", ...done.slice(0, 30).map((task) => format.taskLine(task, names, { showDay: true }))]
          : []),
      ];
      return lines.length === 0 ? `Nothing matches "${args.query}".` : lines.join("\n");
    },
  },
  {
    name: "add_task",
    title: "Add a task",
    description:
      "Adds a task. Without a day and an area it lands in the Inbox. An area or tag that doesn't exist yet is " +
      "created (an archived area comes back). A time needs a day.",
    input: {
      title: z.string().describe("What to do."),
      day: day.optional(),
      time: z.string().optional().describe("A time of day like 17:30; needs a day."),
      deadline: day.optional().describe("The day it must be done by, if any."),
      area: z.string().optional().describe("An area's name, like Health."),
      tags: z.array(z.string()).optional().describe("Tag names, without #."),
      top_priority: z.boolean().optional().describe("One of the day's top priorities."),
      notes: z.string().optional().describe("Notes; light Markdown: **bold**, *italic*, - lists, links."),
      repeat: repeat.optional(),
      priority: priority.optional().describe("low, normal, high or urgent; normal by default."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const task = await planner.addTask(await taskFields(planner, args));
      return `Added:\n${await line(planner, task.id)}`;
    },
  },
  {
    name: "update_task",
    title: "Edit a task",
    description:
      "Changes the fields you give and leaves the rest. An empty day, time, deadline, area or repeat clears it; " +
      "tags replace the task's tags.",
    input: {
      id,
      title: z.string().optional(),
      day: day.optional(),
      time: z.string().optional().describe("Like 17:30, or empty for no time."),
      deadline: day.optional(),
      area: z.string().optional().describe("An area's name, or empty for none."),
      tags: z.array(z.string()).optional(),
      top_priority: z.boolean().optional(),
      notes: z.string().optional(),
      repeat: repeat.optional(),
      priority: priority.optional().describe("low, normal, high or urgent."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const fields = await taskFields(planner, args);
      for (const key of ["time", "area", "repeat"] as const) {
        if (typeof fields[key] === "string" && (fields[key] as string).trim() === "") fields[key] = null;
      }
      await planner.updateTask(args.id, fields);
      return `Updated:\n${await line(planner, args.id)}`;
    },
  },
  {
    name: "complete_task",
    title: "Complete a task",
    description: "Marks a task done. A repeating task moves on: its next occurrence is planned by the rule.",
    input: { id },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const { next } = await planner.finish(args.id, "done");
      const lines = [`Done:\n${await line(planner, args.id)}`];
      if (next !== null) lines.push(`Next occurrence:\n${await line(planner, next.id)}`);
      return lines.join("\n");
    },
  },
  {
    name: "drop_task",
    title: "Drop a task",
    description: "Drops a task: it leaves every list but stays in the history. A repeating task moves on.",
    input: { id },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const { next } = await planner.finish(args.id, "dropped");
      const lines = [`Dropped:\n${await line(planner, args.id)}`];
      if (next !== null) lines.push(`Next occurrence:\n${await line(planner, next.id)}`);
      return lines.join("\n");
    },
  },
  {
    name: "reopen_task",
    title: "Reopen a task",
    description: "Opens a done or dropped task again; a repeating task takes back the occurrence it made.",
    input: { id },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      await planner.reopen(args.id);
      return `Open again:\n${await line(planner, args.id)}`;
    },
  },
  {
    name: "move_task",
    title: "Move a task to a day",
    description:
      "Plans a task for a day, keeping its time, or takes its day away when day is empty (it then waits in its " +
      "area, or the Inbox without one). A done or dropped task opens again.",
    input: { id, day: z.string().describe('"today", "tomorrow", a date like 2026-09-21, or empty for no day.') },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      await planner.reopen(args.id, (await dayFrom(planner, args.day)) ?? null);
      return `Moved:\n${await line(planner, args.id)}`;
    },
  },
  {
    name: "delete_task",
    title: "Delete a task",
    description:
      "Deletes a task softly (restore_task brings it back). Before calling this, tell the owner which task you " +
      "are about to delete and wait for their yes; then call it with confirmed set to true.",
    input: {
      id,
      confirmed: z.boolean().describe("True only after the owner said yes to deleting this task in the conversation."),
    },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      if (args.confirmed !== true) {
        const task = await planner.task(args.id);
        throw new PlannerError(
          `Nothing deleted. Ask the owner first${task ? ` whether to delete "${task.title}"` : ""}, then call again ` +
            "with confirmed: true.",
        );
      }
      const task = await planner.delete(args.id);
      return `Deleted "${task.title}". restore_task with id ${task.id} brings it back.`;
    },
  },
  {
    name: "restore_task",
    title: "Restore a deleted task",
    description: "Brings back a deleted task.",
    input: { id },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      await planner.restore(args.id);
      return `Restored:\n${await line(planner, args.id)}`;
    },
  },
  {
    name: "add_reminder",
    title: "Set a reminder",
    description:
      "Sets a reminder on a task: at a local time in the owner's time zone, or a number of minutes before the " +
      "task's planned time (the task needs a day and a time). Both of the owner's devices ring it.",
    input: {
      task_id: id,
      at: z.string().optional().describe("Local date and time, like 2026-09-18 17:30."),
      minutes_before: z.number().int().optional().describe("Minutes before the task's time, like 15."),
      important: z.boolean().optional().describe("Rings through quiet hours until handled."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      if ((args.at === undefined) === (args.minutes_before === undefined)) {
        throw new PlannerError("Give either at or minutes_before.");
      }
      const reminder = await planner.addReminder(args.task_id, {
        at: args.at,
        minutesBefore: args.minutes_before,
        important: args.important,
      });
      return `Reminder set ${
        reminder.at ? `for ${reminder.at}` : `${reminder.minutesBefore} min before`
      } on:\n${await line(
        planner,
        args.task_id,
      )}\n(reminder id ${reminder.id})`;
    },
  },
  {
    name: "remove_reminder",
    title: "Remove a reminder",
    description: "Takes a reminder away. get_task lists a task's reminders with their ids.",
    input: { reminder_id: z.string() },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      await planner.removeReminder(args.reminder_id);
      return "Reminder removed.";
    },
  },
  {
    name: "add_step",
    title: "Add a checklist step",
    description: "Adds a step to a task's checklist.",
    input: { task_id: id, title: z.string() },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const step = await planner.addStep(args.task_id, args.title);
      return `Step added: ${step.title} (step id ${step.id})`;
    },
  },
  {
    name: "check_step",
    title: "Check off a step",
    description: "Marks a checklist step done, or not done again. get_task lists the steps with their ids.",
    input: { step_id: z.string(), done: z.boolean() },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const step = await planner.setStepDone(args.step_id, args.done);
      return `${step.done ? "Checked" : "Unchecked"}: ${step.title}`;
    },
  },
  {
    name: "update_step",
    title: "Rename a step",
    description: "Changes what a step of a task's checklist says. check_step is what ticks one off.",
    input: { step_id: stepId, title: z.string().describe("What the step should say.") },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const step = await planner.renameStep(args.step_id, args.title);
      return `${step.done ? "[x]" : "[ ]"} ${step.title}`;
    },
  },
  {
    name: "remove_step",
    title: "Remove a step",
    description: "Takes a step off a task's checklist. The task itself stays as it is.",
    input: { step_id: stepId },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => `Removed the step "${(await planner.removeStep(args.step_id)).title}".`,
  },
  {
    name: "finish_plan_tomorrow",
    title: "Record Plan tomorrow as done",
    description:
      "Records that the owner did the Plan tomorrow ritual today, so GoalMaker's evening reminder stays quiet on " +
      "their devices. Call it at the end of the plan_tomorrow prompt's steps.",
    input: {},
    readOnly: false,
    destructive: false,
    run: async (planner) => {
      const { today } = await planner.now();
      await planner.recordRitual("plan_tomorrow", today);
      return `Plan tomorrow is recorded for ${format.longDay(today)}; the evening reminder stays quiet.`;
    },
  },
  {
    name: "finish_review",
    title: "Finish a review",
    description:
      "Records that the weekly or monthly review was done, which quiets the reminder for it on both devices the " +
      "way finish_plan_tomorrow does for the evening ritual. save_review_summary is what writes down what came " +
      "out of it.",
    input: {
      kind: z.enum(["weekly", "monthly"]).describe("weekly or monthly."),
      day: day.optional().describe("A day in the period reviewed; today by default."),
      skipped: z.boolean().optional().describe("true records that the owner passed on it this time."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const on = (await dayFrom(planner, args.day)) ?? (await planner.now()).today;
      const ritual = args.kind === "weekly" ? "weekly_review" : "monthly_review";
      await planner.recordRitual(ritual, on, args.skipped === true);
      const what = `${args.kind} review`;
      return args.skipped === true
        ? `Noted that the ${what} was passed on for ${format.longDay(on)}.`
        : `The ${what} for ${format.longDay(on)} is done.`;
    },
  },
  {
    name: "get_goals",
    title: "Goals",
    description:
      "The owner's goals with where each one stands, the way the Goals screen shows them: this week, this month " +
      "and this year by default, or the periods a day falls in. A goal counts what its tasks and its habits did.",
    input: {
      day: z.string().optional().describe(
        'Which periods to show, as "today", "tomorrow" or a date like 2026-09-21. Today by default.',
      ),
      include_past: z.boolean().optional().describe("Also show goals of earlier periods. Off by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const day = (await dayFrom(planner, args.day)) ?? (await planner.now()).today;
      const progress = await progressOf(planner);
      const goals = (await planner.goals()).filter((goal) =>
        args.include_past === true || goal.periodStart === goalPeriodStart(goal.horizon, day)
      );
      if (goals.length === 0) {
        return args.include_past === true
          ? "There are no goals yet. add_goal sets one for a week, a month or a year."
          : `No goals for the periods ${day} falls in. add_goal sets one.`;
      }
      const order: GoalHorizon[] = ["year", "month", "week", "day"];
      const lines = [];
      for (const only of order) {
        const rows = goals.filter((goal) => goal.horizon === only);
        if (rows.length === 0) continue;
        lines.push(`${only[0].toUpperCase()}${only.slice(1)} goals:`);
        for (const goal of rows) lines.push(format.goalLine(goal, progress(goal), periodText(goal)));
      }
      return lines.join("\n");
    },
  },
  {
    name: "add_goal",
    title: "Add a goal",
    description:
      "Sets a goal for a week, a month or a year. A goal is met by being marked done, by the tasks that serve it, " +
      "or by a number it counts (a target and a unit, like 80 km), which habits and log_goal_amount add to.",
    input: {
      title: z.string().describe("What the goal is, in the owner's words."),
      horizon: horizon.optional().describe("week by default."),
      day: z.string().optional().describe("Any day in the period the goal belongs to. Today by default."),
      emoji: z.string().optional().describe("One emoji for the goal."),
      target: z.number().optional().describe("The number to reach, for a goal that counts something."),
      unit: z.string().optional().describe('What the number counts, like "km" or "books".'),
      counts_tasks: z.boolean().optional().describe("Met when every task that serves it is done."),
      parent: z.string().optional().describe(
        "A wider goal this one sits under, by id, like a month goal over a week one.",
      ),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const fields: GoalFields = {
        title: args.title,
        horizon: args.horizon,
        day: (await dayFrom(planner, args.day)) ?? undefined,
        emoji: args.emoji,
        target: args.target,
        unit: args.unit,
        mode: args.counts_tasks === true ? "tasks" : args.target === undefined ? "done" : "number",
        parent: args.parent,
      };
      const goal = await planner.addGoal(fields);
      const progress = await progressOf(planner);
      return ["Goal set.", format.goalLine(goal, progress(goal), periodText(goal))].join("\n");
    },
  },
  {
    name: "update_goal",
    title: "Change a goal",
    description:
      "Changes a goal's title, emoji, target, unit, or the wider goal it sits under. What is left out stays " +
      "as it was.",
    input: {
      id: goalId,
      title: z.string().optional(),
      emoji: z.string().nullable().optional(),
      target: z.number().optional().describe("The new target of a goal that counts a number."),
      unit: z.string().nullable().optional(),
      parent: z.string().nullable().optional().describe(
        "A wider goal this one sits under, by id; empty to stand on its own.",
      ),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const goal = await planner.updateGoal(args.id, {
        title: args.title,
        emoji: args.emoji,
        target: args.target,
        unit: args.unit,
        parent: args.parent,
      });
      const progress = await progressOf(planner);
      return ["Goal changed.", format.goalLine(goal, progress(goal), periodText(goal))].join("\n");
    },
  },
  {
    name: "set_goal_status",
    title: "Mark a goal",
    description:
      "Marks a goal done when it is reached, dropped when it is let go, or open again. Dropping keeps the goal " +
      "and its history; it is not a delete.",
    input: { id: goalId, status: z.enum(["open", "done", "dropped"]) },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const goal = await planner.setGoalStatus(args.id, args.status);
      const progress = await progressOf(planner);
      return [`Goal marked ${args.status}.`, format.goalLine(goal, progress(goal), periodText(goal))].join("\n");
    },
  },
  {
    name: "log_goal_amount",
    title: "Log an amount",
    description:
      'Logs an amount on a goal that counts a number, like "+5 km". A negative amount takes one back. Amounts ' +
      "a habit checked in are counted already and must not be logged again.",
    input: {
      id: goalId,
      amount: z.number().describe("How much to add, in the goal's unit. Negative takes an amount back."),
      day: z.string().optional().describe("The day it belongs to. Today by default."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const day = (await dayFrom(planner, args.day)) ?? (await planner.now()).today;
      const goal = await planner.logAmount(args.id, day, args.amount);
      const progress = await progressOf(planner);
      return ["Amount logged.", format.goalLine(goal, progress(goal), periodText(goal))].join("\n");
    },
  },
  {
    name: "delete_goal",
    title: "Delete a goal",
    description:
      "Deletes a goal. The tasks and habits that served it, and any goals under it, simply stop serving one and " +
      "are otherwise untouched. Ask the owner first. To close one off instead, set_goal_status marks it done or " +
      "dropped and keeps it in the period's record.",
    input: { id: goalId },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      const goal = await planner.deleteGoal(args.id);
      return `Deleted the goal "${goal.title}". What served it kept everything but the goal.`;
    },
  },
  {
    name: "get_habits",
    title: "Habits",
    description:
      "The owner's habits as the Habits screen shows them: what each one asks of the day, whether its period is " +
      "met, how far today has got, and the streak it is on. Archived habits are left out.",
    input: {
      day: z.string().optional().describe('The day to look at, as "today" or a date. Today by default.'),
      include_archived: z.boolean().optional().describe("Also show habits put away. Off by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const day = (await dayFrom(planner, args.day)) ?? (await planner.now()).today;
      const habits = (await planner.habits()).filter((habit) => args.include_archived === true || !habit.archived);
      if (habits.length === 0) return "There are no habits yet.";
      const checkins = await planner.checkins();
      const pauses = await planner.pauses();
      const lines = [`Habits on ${format.longDay(day)}:`];
      for (const habit of habits) {
        const own = checkins.filter((checkin) => checkin.habitId === habit.id);
        const rests = pauses.filter((pause) => pause.habitId === habit.id);
        const state = habitState(habit, habitPeriodStart(habit, day), day, own, rests);
        const done = (ring(habit, day, own) ?? 0) * (habit.measure === "check" ? 1 : habit.target ?? 1);
        lines.push(format.habitLine(habit, state, done, streak(habit, day, own, rests)));
      }
      return lines.join("\n");
    },
  },
  {
    name: "check_in_habit",
    title: "Check a habit in",
    description:
      "Checks a habit in on a day, the way tapping its ring does. A habit that counts or measures adds the amount " +
      "to whatever the day already had; leave the amount out for a plain check.",
    input: {
      id: habitId,
      day: z.string().optional().describe("The day to check in on. Today by default."),
      amount: z.number().optional().describe("How much, for a habit that counts or measures something."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const day = (await dayFrom(planner, args.day)) ?? (await planner.now()).today;
      const { habit, value } = await planner.checkIn(args.id, day, args.amount ?? 1);
      const own = (await planner.checkins()).filter((checkin) => checkin.habitId === habit.id);
      const rests = (await planner.pauses()).filter((pause) => pause.habitId === habit.id);
      const state = habitState(habit, habitPeriodStart(habit, day), day, own, rests);
      const amount = habit.measure === "check" ? "" : ` at ${format.round(value)}${habit.unit ? ` ${habit.unit}` : ""}`;
      return [
        `${habit.name} checked in for ${day}${amount}.`,
        format.habitLine(habit, state, value, streak(habit, day, own, rests)),
      ].join("\n");
    },
  },
  {
    name: "skip_habit",
    title: "Skip a habit",
    description:
      "Skips a habit's period, which neither meets it nor breaks its streak: what the owner does on a day off. " +
      "Set skipped to false to take the skip back.",
    input: {
      id: habitId,
      day: z.string().optional().describe("A day in the period to skip. Today by default."),
      skipped: z.boolean().optional().describe("True by default."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const day = (await dayFrom(planner, args.day)) ?? (await planner.now()).today;
      const skipped = args.skipped !== false;
      const habit = await planner.skipHabit(args.id, day, skipped);
      return skipped ? `${habit.name} skipped for ${day}.` : `${habit.name} is no longer skipped on ${day}.`;
    },
  },
  {
    name: "add_habit",
    title: "Add a habit",
    description:
      "Adds a habit: what it asks of a day and how often. It runs daily, on chosen weekdays, or so many times a " +
      "week or a month, and is measured as a check, a count or an amount against a target. A limit habit (at_most) " +
      "is something to keep down, and only a daily or weekdays habit can be one. A habit may serve a numeric goal, " +
      "and its check-ins then count toward that goal.",
    input: {
      name: z.string().describe("What the habit is, in the owner's words."),
      emoji: z.string().optional().describe("One emoji for the habit."),
      cadence: cadence.optional().describe("daily by default."),
      weekdays: z.array(weekday).optional().describe("For the weekdays cadence: which days, like [mon, wed, fri]."),
      times: z.number().int().optional().describe("For per_week (1 to 7) or per_month (1 to 31): how many days."),
      measure: measure.optional().describe("check by default."),
      target: z.number().optional().describe("What a day needs, for a count or an amount."),
      unit: z.string().optional().describe('What the amount counts, like "km" or "pages".'),
      direction: direction.optional().describe("at_least by default; at_most makes the target a limit."),
      goal: z.string().optional().describe("A numeric goal's id, for a habit that feeds one."),
      starts_on: day.optional().describe(
        "The first day it counts from; today by default. Streaks never reach back past it.",
      ),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const habit = await planner.addHabit(await habitFields(planner, args));
      return ["Habit added.", await habitLineFor(planner, habit)].join("\n");
    },
  },
  {
    name: "update_habit",
    title: "Edit a habit",
    description:
      "Changes a habit's name, emoji, cadence, measure, target, unit, direction, the goal it serves or the day it " +
      "starts from, and archives it or brings it back. What is left out stays as it was. Its check-ins are kept.",
    input: {
      id: habitId,
      name: z.string().optional(),
      emoji: z.string().optional().describe("One emoji, or empty for none."),
      cadence: cadence.optional(),
      weekdays: z.array(weekday).optional().describe("For the weekdays cadence: which days, like [mon, wed, fri]."),
      times: z.number().int().optional(),
      measure: measure.optional(),
      target: z.number().optional(),
      unit: z.string().optional(),
      direction: direction.optional(),
      goal: z.string().optional().describe("A numeric goal's id, or empty to serve none."),
      starts_on: day.optional(),
      archived: z.boolean().optional().describe("true puts it away, false brings it back."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const habit = await planner.updateHabit(args.id, await habitFields(planner, args));
      return ["Habit updated.", await habitLineFor(planner, habit)].join("\n");
    },
  },
  {
    name: "delete_habit",
    title: "Delete a habit",
    description:
      "Deletes a habit with its check-ins and its pauses, so its history goes with it. Ask the owner first. To " +
      "stop one without losing what it recorded, archive it with update_habit.",
    input: { id: habitId },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      const habit = await planner.deleteHabit(args.id);
      return `Deleted the habit "${habit.name}" with its check-ins and pauses.`;
    },
  },
  {
    name: "pause_habit",
    title: "Pause a habit",
    description:
      "Pauses a habit over a stretch of days, for a holiday or an injury. Paused days neither break a streak nor " +
      "count toward one, and the pause stays on the record afterwards so old streaks still read right. Leave the " +
      "last day out for a pause with no end yet, and resume_habit ends it. A habit already paused over those " +
      "days has to be resumed first. skip_habit is for a single period.",
    input: {
      id: habitId,
      from: day.optional().describe("The first paused day; today by default."),
      until: day.optional().describe("The last paused day; leave it out for no end yet."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const from = (await dayFrom(planner, args.from)) ?? (await planner.now()).today;
      const until = (await dayFrom(planner, args.until)) ?? null;
      const habit = await planner.habit(args.id);
      await planner.pauseHabit(args.id, from, until);
      return until === null
        ? `"${habit.name}" is paused from ${format.longDay(from)} until it is resumed.`
        : `"${habit.name}" is paused from ${format.longDay(from)} to ${format.longDay(until)}.`;
    },
  },
  {
    name: "resume_habit",
    title: "Resume a habit",
    description:
      "Brings a habit back on a day: the pause covering it ends the day before, or goes away when it started " +
      "that day, so the habit counts again from that day on.",
    input: {
      id: habitId,
      day: day.optional().describe("The day it counts again from; today by default."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const on = (await dayFrom(planner, args.day)) ?? (await planner.now()).today;
      const habit = await planner.resumeHabit(args.id, on);
      return `"${habit.name}" counts again from ${format.longDay(on)}.`;
    },
  },
  {
    name: "get_projects",
    title: "Projects",
    description:
      "The owner's projects as the Projects screen lists them: name, status, area, how much is still open, and the " +
      "repository and folder each one lives in. Paused and done projects come last.",
    input: {
      include_done: z.boolean().optional().describe("Also show projects that are paused or done. On by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const projects = (await planner.projects()).filter((project) =>
        args.include_done !== false || project.status === "active"
      );
      if (projects.length === 0) return "There are no projects yet.";
      const names = await namesOf(planner);
      const tasks = await planner.tasks();
      return ["Projects:", ...projects.map((project) => format.projectLine(project, names, tasks))].join("\n");
    },
  },
  {
    name: "get_project_board",
    title: "A project's board",
    description:
      "One project's board as the apps show it: Backlog, To do, Doing and Done, each with its items in the order " +
      "the board puts them (priority first, then where they were dragged), plus the project's milestones and notes.",
    input: { project: projectRef },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const project = await planner.findProject(args.project);
      const items = (await planner.tasks()).filter((task) => task.projectId === project.id);
      return format.board(project, board(items), await namesOf(planner), await milestonesOf(planner, project));
    },
  },
  {
    name: "find_project",
    title: "Find a project",
    description:
      "The project a repository URL or a folder belongs to, so a tool working in a checkout can drop an idea or a " +
      "bug into the right backlog without asking. A folder inside the project's folder counts, and a repository " +
      "matches however it is written (https, ssh, with or without .git).",
    input: {
      repository: z.string().optional().describe("The repository URL, like https://github.com/me/goalmaker."),
      folder: z.string().optional().describe("The folder being worked in, like F:\\GoalMaker."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const reference = (args.repository as string | undefined) ?? (args.folder as string | undefined) ?? "";
      if (reference.trim() === "") throw new PlannerError("Give a repository URL or a folder to look for.");
      const project = await planner.findProject(reference);
      const milestones = await milestonesOf(planner, project);
      return [
        format.projectLine(project, await namesOf(planner), await planner.tasks()),
        milestones.length === 0
          ? "It has no milestones."
          : `Milestones: ${
            milestones.map((milestone) => `${milestone.name} (milestone id ${milestone.id})`).join(", ")
          }.`,
      ].join("\n");
    },
  },
  {
    name: "create_project",
    title: "Create a project",
    description: "Makes a project with a board of its own, the way the Projects screen's new-project form does. The " +
      "repository and the folder are worth filling in: they are what lets find_project reach this project later " +
      "from the checkout being worked in. Items go on the board afterwards with add_project_item.",
    input: {
      name: z.string().describe("What the project is called, like GoalMaker."),
      description: z.string().optional().describe("A sentence on what it is."),
      area: z.string().optional().describe("An area's name, like Work."),
      status: projectStatus.optional().describe("active by default."),
      repository: z.string().optional().describe("The repository URL, like https://github.com/me/goalmaker."),
      folder: z.string().optional().describe("The folder it lives in, like F:\\GoalMaker."),
      notes: z.string().optional().describe("Notes; light Markdown."),
      milestones: z.array(z.string()).optional().describe("Milestone names in the order they come, like M0, M1."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const project = await planner.addProject({
        name: args.name,
        description: args.description,
        area: args.area,
        status: args.status,
        repository: args.repository,
        folder: args.folder,
        notes: args.notes,
      });
      for (const name of (args.milestones ?? []) as string[]) await planner.addMilestone(project.id, name);
      const milestones = await milestonesOf(planner, project);
      return [
        "Created:",
        format.projectLine(project, await namesOf(planner), await planner.tasks()),
        milestones.length === 0
          ? "It has no milestones yet."
          : `Milestones: ${
            milestones.map((milestone) => `${milestone.name} (milestone id ${milestone.id})`).join(", ")
          }.`,
      ].join("\n");
    },
  },
  {
    name: "create_milestone",
    title: "Create a milestone",
    description:
      "Adds a milestone at the end of a project's list. Milestones are the project's own groupings, like M0 to " +
      "M6, and an item may carry one.",
    input: {
      project: projectRef,
      name: z.string().describe("What the milestone is called, like M2."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const project = await planner.findProject(args.project);
      const milestone = await planner.addMilestone(project.id, args.name);
      return `Added ${milestone.name} to ${project.name} (milestone id ${milestone.id}).`;
    },
  },
  {
    name: "add_project_item",
    title: "Add a project item",
    description:
      "Adds an item to a project's board. An idea lands in the backlog, a task or a bug in To do. An item is an " +
      "ordinary task, so it can have a day, a time, an area, tags and a deadline, and a planned day puts it in " +
      "Today next to everything else.",
    input: {
      project: projectRef,
      title: z.string().describe("What the item is."),
      type: itemType.optional().describe("task, idea or bug; task by default."),
      priority: priority.optional().describe("normal by default."),
      milestone: z.string().optional().describe("A milestone of this project, by name or id."),
      column: column.optional().describe("Where to put it; the type decides by default."),
      day: day.optional(),
      time: z.string().optional().describe("A time of day like 17:30; needs a day."),
      deadline: day.optional(),
      area: z.string().optional().describe("An area's name, like Work."),
      tags: z.array(z.string()).optional().describe("Tag names, without #."),
      notes: z.string().optional().describe("Notes; light Markdown. A link the idea came from belongs here."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const project = await planner.findProject(args.project);
      const milestone = await planner.findMilestone(project.id, args.milestone ?? null);
      const item = await planner.addTask({
        ...(await taskFields(planner, args)),
        projectId: project.id,
        itemType: args.type ?? "task",
        milestoneId: milestone?.id ?? null,
      });
      if (args.column !== undefined && args.column !== item.boardColumn) {
        await planner.moveItem(item.id, cleanColumn(args.column));
      }
      const placed = (await planner.task(item.id))!;
      return [
        `Added to ${project.name}, ${columnName(placed.boardColumn)}:`,
        format.itemLine(placed, await namesOf(planner), await milestonesOf(planner, project)),
      ].join("\n");
    },
  },
  {
    name: "update_project_item",
    title: "Edit a project item",
    description:
      "Changes what an item is and where it belongs: its type, its priority, its milestone, or the project it is " +
      "in. Everything else about it is edited with update_task, and move_project_item moves it between columns. " +
      "An empty milestone takes it off one; an empty project makes it a plain task again.",
    input: {
      id: itemId,
      type: itemType.optional(),
      priority: priority.optional(),
      milestone: z.string().optional().describe("A milestone of its project, by name or id; empty for none."),
      project: z.string().optional().describe("Move it to this project, or empty to take it out of one."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const task = await planner.task(args.id);
      if (task === null || task.deleted) throw new PlannerError(`No task with id ${args.id}.`);
      if (args.project === undefined && !task.projectId) {
        throw new PlannerError(
          `"${task.title}" is not in a project. Name a project to put it in, or use update_task for a plain task.`,
        );
      }
      const wanted = args.project === undefined ? task.projectId! : (args.project as string).trim();
      const project = wanted === "" ? null : await planner.findProject(wanted);
      const milestone = args.milestone === undefined || project === null
        ? undefined
        : (await planner.findMilestone(project.id, (args.milestone as string).trim() === "" ? null : args.milestone))
          ?.id ?? null;
      await planner.updateTask(args.id, {
        projectId: project?.id ?? null,
        itemType: args.type,
        priority: args.priority,
        milestoneId: milestone,
      });
      const item = (await planner.task(args.id))!;
      const names = await namesOf(planner);
      if (project === null) return `Out of its project, a plain task again:\n${format.taskLine(item, names)}`;
      return [
        `Updated in ${project.name}, ${columnName(item.boardColumn)}:`,
        format.itemLine(item, names, await milestonesOf(planner, project)),
      ].join("\n");
    },
  },
  {
    name: "move_project_item",
    title: "Move an item on the board",
    description:
      "Moves an item to another column, the way dragging its card does: Done completes the task (a repeating one " +
      "moves on), any other column reopens a done item, and a dropped item keeps its state wherever it sits.",
    input: { id: itemId, column },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const { task, next } = await planner.moveItem(args.id, cleanColumn(args.column));
      const project = await planner.findProject(task.projectId ?? "");
      const names = await namesOf(planner);
      const lines = [
        `Moved to ${columnName(task.boardColumn)} in ${project.name}:`,
        format.itemLine(task, names, await milestonesOf(planner, project)),
      ];
      if (next !== null) lines.push("Next occurrence:", format.taskLine(next, names, { showDay: true }));
      return lines.join("\n");
    },
  },
  {
    name: "update_project",
    title: "Edit a project",
    description:
      "Changes a project's name, description, area, status, repository, folder or notes. What is left out stays " +
      "as it was. Marking it paused or done is what takes it off the working list without losing the board. A new " +
      "name, repository or folder has to be free, for the same reason create_project asks.",
    input: {
      project: projectRef,
      name: z.string().optional().describe("A new name."),
      description: z.string().optional(),
      area: z.string().optional().describe("An area's name, or empty for none."),
      status: projectStatus.optional(),
      repository: z.string().optional().describe("The repository URL, or empty for none."),
      folder: z.string().optional().describe("The folder it lives in, or empty for none."),
      notes: z.string().optional(),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const found = await planner.findProject(args.project);
      const project = await planner.updateProject(found.id, { ...args, project: undefined });
      return ["Updated:", format.projectLine(project, await namesOf(planner), await planner.tasks())].join("\n");
    },
  },
  {
    name: "delete_project",
    title: "Delete a project",
    description:
      "Deletes a project with its milestones. Its items stay behind as plain tasks rather than going with it, so " +
      "no work is lost. Ask the owner first. To put one aside instead, update_project marks it paused or done.",
    input: { project: projectRef },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      const found = await planner.findProject(args.project);
      const items = (await planner.tasks()).filter((task) => task.projectId === found.id && !task.deleted).length;
      await planner.deleteProject(found.id);
      return items === 0
        ? `Deleted the project ${found.name}.`
        : `Deleted the project ${found.name}. Its ${items} item(s) stayed as plain tasks.`;
    },
  },
  {
    name: "update_milestone",
    title: "Rename a milestone",
    description: "Renames one of a project's milestones. Its items keep it.",
    input: { id: milestoneId, name: z.string().describe("The new name, like M2.") },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const milestone = await planner.renameMilestone(args.id, args.name);
      return `Milestone ${milestone.name} (milestone id ${milestone.id}).`;
    },
  },
  {
    name: "delete_milestone",
    title: "Delete a milestone",
    description:
      "Removes a milestone from its project. The items that carried it stay on the board without one. Ask the " +
      "owner first.",
    input: { id: milestoneId },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => {
      const milestone = await planner.removeMilestone(args.id);
      return `Removed the milestone ${milestone.name}. Its items kept their place on the board.`;
    },
  },
  {
    name: "get_activity",
    title: "Recent changes",
    description:
      "The latest changes to the owner's rows, newest first, with who made each one (the owner, Claude through " +
      "this connector, or GoalMaker itself) and whether it was already undone. This is what the apps' Activity " +
      "screen shows, and undo_change is what takes one back.",
    input: {
      limit: z.number().int().min(1).max(60).optional().describe("How many; 20 by default, 60 at most."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const changes = await planner.changes(args.limit ?? 20);
      if (changes.length === 0) return "Nothing has changed yet.";
      return ["Recent changes, newest first:", ...changes.map(changeText)].join("\n");
    },
  },
  {
    name: "undo_change",
    title: "Undo a change",
    description: "Puts a row back the way a change found it: an edit gets its old values, a deletion comes back, and " +
      "something added is deleted softly. Only the latest change to a row can be undone, and the server refuses " +
      "one the row has moved on from, so later work is never thrown away. The undo is itself a change, so undoing " +
      "it again takes a mistaken undo back. Ask the owner before undoing anything they did themselves.",
    input: { id: z.string().describe("The change's id, from get_activity.") },
    readOnly: false,
    destructive: true,
    run: async (planner, args) => ["Undone:", changeText(await planner.undo(args.id))].join("\n"),
  },
  {
    name: "get_settings",
    title: "The owner's settings",
    description:
      "The name the owner goes by, their time zone, and the hour a planning day starts. Every day this connector " +
      "talks about is worked out from these two: at 01:30 with a 04:00 start it is still yesterday.",
    input: {},
    readOnly: true,
    destructive: false,
    run: async (planner) => {
      const settings = await planner.settings();
      const now = await planner.now();
      return [
        settings.displayName === null ? "No display name set." : `Display name: ${settings.displayName}.`,
        `Time zone: ${settings.timeZone}.`,
        `A planning day starts at ${String(settings.dayStartHour).padStart(2, "0")}:00.`,
        `It is ${now.local} there, so today is ${format.longDay(now.today)}.`,
      ].join("\n");
    },
  },
  {
    name: "update_settings",
    title: "Change the settings",
    description:
      "Changes the name the owner goes by, their time zone, or the hour a planning day starts. The last two move " +
      "what every list means by today on both devices, so check with the owner before changing them.",
    input: {
      display_name: z.string().optional().describe("The name to go by, or empty for none."),
      time_zone: z.string().optional().describe("A time zone like Europe/Prague."),
      day_start_hour: z.number().int().min(0).max(23).optional().describe("The hour a planning day starts, 0 to 23."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const settings = await planner.updateSettings({
        displayName: args.display_name,
        timeZone: args.time_zone,
        dayStartHour: args.day_start_hour,
      });
      const today = (await planner.now()).today;
      return [
        `Time zone ${settings.timeZone}, a planning day starts at ${
          String(settings.dayStartHour).padStart(2, "0")
        }:00.`,
        `Today is now ${format.longDay(today)}.`,
      ].join("\n");
    },
  },
  {
    name: "get_calendar",
    title: "The calendar",
    description:
      "The plan across a stretch of days: what is planned on each one, what is due then, where a repeating task " +
      "would come round to, and which of them carry a reminder. A week or a month at a time reads best. Each day " +
      "runs earliest time first with untimed tasks after, the same order Today uses. A repeat has no row of its " +
      "own yet, so it is worked out from the task's rule.",
    input: {
      from: day.optional().describe("The first day; today by default."),
      to: day.optional().describe("The last day; six days after the first by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const first = (await dayFrom(planner, args.from)) ?? (await planner.now()).today;
      const last = (await dayFrom(planner, args.to)) ?? addDays(first, 6);
      if (last < first) throw new PlannerError("The last day comes before the first one.");
      const names = await namesOf(planner);
      const reminded = await planner.remindedTasks();
      const live = await planner.tasks();
      // A dropped task leaves the calendar; a done one still sits on the day it was planned for.
      const planned = live.filter((task) => task.state !== "dropped" && task.plannedDate !== null);
      const due = live.filter((task) => task.state === "open" && task.deadline !== null);
      const repeats = projectedRepeats(live, first, last);
      const span = `${format.longDay(first)} to ${format.longDay(last)}`;
      const lines: string[] = [];
      for (let date = first; date <= last; date = addDays(date, 1)) {
        const onDay = planned.filter((task) => task.plannedDate === date).sort(byTime);
        const dueOn = due.filter((task) => task.deadline === date).sort(byTime);
        const around = [...(repeats.get(date) ?? [])].sort(byTime);
        if (onDay.length === 0 && dueOn.length === 0 && around.length === 0) continue;
        lines.push(`${format.longDay(date)}:`);
        for (const task of onDay) {
          lines.push(format.taskLine(task, names) + (reminded.has(task.id) ? " · reminder" : ""));
        }
        for (const task of dueOn) lines.push(`${format.taskLine(task, names)} · due`);
        for (const task of around) lines.push(`${format.taskLine(task, names)} · repeats`);
      }
      return lines.length === 0 ? `Nothing on the calendar from ${span}.` : [`From ${span}:`, ...lines].join("\n");
    },
  },
  {
    name: "save_review_summary",
    title: "Save a review summary",
    description:
      "Saves the summary of a weekly or monthly review (a few sentences: wins, lessons, focus), for the week or " +
      "month that contains the given day. Saving again for the same period replaces the summary. A scheduled " +
      "routine can call this to leave the owner a weekly summary.",
    input: {
      kind: z.enum(["weekly", "monthly"]).describe("weekly or monthly."),
      period: day.optional().describe("Any day in the week or month; today by default."),
      summary: z.string().describe("The summary, plain text or light Markdown."),
      mood: z.number().int().optional().describe("How the period felt, 1 (low) to 5 (great), if the owner said."),
      energy: z.number().int().optional().describe("The owner's energy, 1 (low) to 5 (high), if they said."),
      reflections: z.array(z.object({
        prompt: z.string().describe("The prompt's id, as the review asked it."),
        answer: z.string().describe("What the owner wrote back."),
      })).optional().describe("The prompts the review asked and the answers written, in the order asked."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const inPeriod = (await dayFrom(planner, args.period)) ?? (await planner.now()).today;
      const review = await planner.saveReview(args.kind, inPeriod, {
        summary: args.summary,
        mood: args.mood,
        energy: args.energy,
        reflections: args.reflections,
      });
      return `Saved the ${review.kind} review summary for the period starting ${format.longDay(review.periodStart)}.`;
    },
  },
  {
    name: "get_review_summaries",
    title: "Past review summaries",
    description: "The latest weekly and monthly review summaries, newest first, to compare with earlier periods.",
    input: {
      kind: z.enum(["weekly", "monthly"]).optional().describe("Only this kind."),
      limit: z.number().int().min(1).max(20).optional().describe("How many; 5 by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const reviews = await planner.reviews(args.kind ?? null, args.limit ?? 5);
      if (reviews.length === 0) return "No review summaries yet.";
      return reviews.map((review) =>
        [
          `${review.kind} review, from ${format.longDay(review.periodStart)}` +
          (review.mood !== null ? `, mood ${review.mood}/5` : "") +
          (review.energy !== null ? `, energy ${review.energy}/5` : "") + ":",
          review.summary,
          ...review.reflections.map((one) => `- ${one.prompt}: ${one.answer}`),
        ].join("\n")
      ).join("\n\n");
    },
  },
];
