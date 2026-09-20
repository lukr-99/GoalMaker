import { z } from "../deps.ts";
import { type GoalFields, type Planner, PlannerError, type TaskFields } from "../planner/planner.ts";
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
      };
      const goal = await planner.addGoal(fields);
      const progress = await progressOf(planner);
      return ["Goal set.", format.goalLine(goal, progress(goal), periodText(goal))].join("\n");
    },
  },
  {
    name: "update_goal",
    title: "Change a goal",
    description: "Changes a goal's title, emoji, target or unit. What is left out stays as it was.",
    input: {
      id: goalId,
      title: z.string().optional(),
      emoji: z.string().nullable().optional(),
      target: z.number().optional().describe("The new target of a goal that counts a number."),
      unit: z.string().nullable().optional(),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const goal = await planner.updateGoal(args.id, {
        title: args.title,
        emoji: args.emoji,
        target: args.target,
        unit: args.unit,
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
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const inPeriod = (await dayFrom(planner, args.period)) ?? (await planner.now()).today;
      const review = await planner.saveReview(args.kind, inPeriod, {
        summary: args.summary,
        mood: args.mood,
        energy: args.energy,
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
        ].join("\n")
      ).join("\n\n");
    },
  },
];
