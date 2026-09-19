import { z } from "../deps.ts";
import { type Planner, PlannerError, type TaskFields } from "../planner/planner.ts";
import { fold, searchArchive } from "../rules/archiveRules.ts";
import { addDays, type Day } from "../rules/day.ts";
import { lists } from "../rules/listRules.ts";
import { byCreation } from "../rules/task.ts";
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
  return format.names(await planner.areas(), await planner.tags(), await planner.tagLinks());
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
  };
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
];
