import { type GetPromptResult, z } from "../deps.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import { addDays, type Day, isDay, mondayOf, toEpochDay } from "../rules/day.ts";
import { type GoalHorizon, goalProgress, periodEnd as goalPeriodEnd } from "../rules/goals.ts";
import { goalAmounts, habitState, periodsBetween, streak } from "../rules/habits.ts";
import { lists } from "../rules/listRules.ts";
import { MAX_PRIORITIES, review, tomorrow } from "../rules/planRules.ts";
import { type PeriodFacts, triggers } from "../rules/prompts.ts";
import type { TaskItem } from "../rules/task.ts";
import * as format from "../tools/format.ts";

// What each trigger is about, in the words the prompt uses (contracts/content/prompts.json).
const TRIGGER_WORDS: Record<string, string> = {
  goal_behind: "a goal behind where the period says it should be",
  habit_missed: "a habit that missed half its periods or more",
  task_slipping: "a task that keeps moving to another day",
  habit_streak: "a streak worth noticing",
  goal_ahead: "a goal ahead of plan",
  no_goals: "a period with no goals set",
  quiet_period: "a quieter period than usual",
  busy_period: "a busier period than usual",
};

/**
 * A ritual prompt (spec, story 74): Claude walks the owner through a GoalMaker ritual with their real
 * data, which the prompt carries, and changes things through the tools.
 */
export interface Prompt {
  name: string;
  title: string;
  description: string;
  args: z.ZodRawShape;
  build(planner: Planner, args: Record<string, string | undefined>): Promise<GetPromptResult>;
}

function message(text: string): GetPromptResult {
  return { messages: [{ role: "user", content: { type: "text", text } }] };
}

function block(title: string, tasks: TaskItem[], names: format.Names, empty: string): string {
  return tasks.length === 0
    ? `${title}: ${empty}`
    : [`${title}:`, ...tasks.map((task) => format.taskLine(task, names, { showDay: true }))].join("\n");
}

async function namesOf(planner: Planner): Promise<format.Names> {
  return format.names(await planner.areas(), await planner.tags(), await planner.tagLinks());
}

/**
 * What a review of a period looks back on besides its tasks (docs/reviews.md): the goals of the
 * period with where they stand, how each habit held up, and what the period's facts call for.
 */
async function lookBack(
  planner: Planner,
  horizon: GoalHorizon,
  first: Day,
  last: Day,
  today: Day,
): Promise<string[]> {
  const until = today < last ? today : last;
  const length = toEpochDay(last) - toEpochDay(first) + 1;
  const expected = length <= 0 ? 1 : Math.min(1, Math.max(0, (toEpochDay(until) - toEpochDay(first) + 1) / length));
  const tasks = await planner.tasks();
  const entries = await planner.goalEntries();
  const habits = (await planner.habits()).filter((habit) => !habit.archived);
  const checkins = await planner.checkins();
  const pauses = await planner.pauses();

  const goals = (await planner.goals()).filter((goal) => goal.horizon === horizon && goal.periodStart === first);
  const goalRows = goals.map((goal) => ({
    goal,
    progress: goalProgress(
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
    ),
  }));

  const habitRows = habits.map((habit) => {
    const own = checkins.filter((checkin) => checkin.habitId === habit.id);
    const rests = pauses.filter((pause) => pause.habitId === habit.id);
    const states = periodsBetween(habit, first, until).map((start) => habitState(habit, start, today, own, rests));
    return {
      habit,
      met: states.filter((state) => state === "met").length,
      periods: states.filter((state) => state !== "none").length,
      streak: streak(habit, until, own, rests),
    };
  });

  const before = await planner.completedBetween(tasks, addDays(first, -length), addDays(first, -1));
  const done = await planner.completedBetween(tasks, first, last);
  const open = tasks.filter((task) =>
    task.state === "open" && task.plannedDate !== null && task.plannedDate >= first && task.plannedDate <= last
  );
  const facts: PeriodFacts = {
    doneTasks: done.length,
    averageDone: before.length,
    goals: goalRows.map((row) => ({ title: row.goal.title, fraction: row.progress.fraction, expected })),
    habits: habitRows.map((row) => ({
      name: row.habit.name,
      missed: row.periods - row.met,
      periods: row.periods,
      streak: row.streak,
    })),
    tasks: open.map((task) => ({ title: task.title, moves: task.movedCount ?? 0 })),
  };

  const lines: string[] = [];
  lines.push(
    goalRows.length === 0 ? `Goals of this ${horizon}: none set.` : [
      `Goals of this ${horizon}:`,
      ...goalRows.map((row) => format.goalLine(row.goal, row.progress, `${first} to ${goalPeriodEnd(horizon, first)}`)),
    ].join("\n"),
  );
  if (habitRows.length > 0) {
    lines.push([
      "Habits:",
      ...habitRows.map((row) =>
        `- ${row.habit.emoji ? `${row.habit.emoji} ` : ""}${row.habit.name} · ${row.met} of ${row.periods} met` +
        (row.streak > 0 ? ` · streak ${row.streak}` : "") + ` (habit id ${row.habit.id})`
      ),
    ].join("\n"));
  }
  const called = triggers(facts).map(({ trigger, subject }) =>
    `${TRIGGER_WORDS[trigger] ?? trigger}${subject ? ` (${subject})` : ""}`
  );
  if (called.length > 0) lines.push(`What this period's data asks about: ${called.join("; ")}.`);
  return lines;
}

export const prompts: Prompt[] = [
  {
    name: "plan_tomorrow",
    title: "Plan tomorrow",
    description: "GoalMaker's evening ritual: decide on what's left from today, then set up tomorrow.",
    args: {},
    build: async (planner) => {
      const { today } = await planner.now();
      const tasks = await planner.tasks();
      const names = await namesOf(planner);
      const planned = lists(tasks, today);
      return message([
        `Let's do my GoalMaker Plan tomorrow ritual. Today is ${format.longDay(today)}.`,
        "",
        "Step 1, today's leftovers. Nothing rolls over by itself, so for each open task below, one at a time, " +
        "ask whether it moves to tomorrow, to a later day, is done, or is dropped, and apply it with move_task, " +
        "complete_task or drop_task.",
        block("Left from today and earlier", review(tasks, today), names, "nothing, on to step 2."),
        "",
        `Step 2, tomorrow. Show me tomorrow's tasks and help me pick up to ${MAX_PRIORITIES} top priorities ` +
        "(update_task with top_priority). Add anything new with add_task for tomorrow, and offer Inbox tasks I might " +
        "want to bring in.",
        block("Tomorrow so far", tomorrow(tasks, today), names, "nothing yet."),
        block("Inbox", planned.inbox, names, "empty."),
        "",
        "When we're done, call finish_plan_tomorrow, then sum up in two lines: how many tasks tomorrow has and its " +
        "top priorities. Keep the conversation short: one question at a time.",
      ].join("\n"));
    },
  },
  {
    name: "weekly_review",
    title: "Weekly review",
    description: "Look back at a week in GoalMaker, reflect, and set up the next one.",
    args: {
      week_start: z.string().optional().describe("The Monday the week starts, like 2026-09-14; this week by default."),
    },
    build: async (planner, args) => {
      const { today } = await planner.now();
      const first = args.week_start ? mondayOf(dayArgument(args.week_start)) : mondayOf(today);
      const last = addDays(first, 6);
      const tasks = await planner.tasks();
      const names = await namesOf(planner);
      const open = tasks.filter((task) => task.state === "open");
      return message([
        `Let's do my GoalMaker weekly review for the week of ${format.longDay(first)} to ${format.longDay(last)}.`,
        "",
        block("Done this week", await planner.completedBetween(tasks, first, last), names, "nothing marked done."),
        block(
          "Still open from this week or earlier",
          open.filter((task) => task.plannedDate !== null && task.plannedDate <= last).sort((a, b) =>
            a.plannedDate! < b.plannedDate! ? -1 : 1
          ),
          names,
          "nothing.",
        ),
        block(
          "Planned for next week",
          open.filter((task) =>
            task.plannedDate !== null && task.plannedDate > last && task.plannedDate <= addDays(last, 7)
          ),
          names,
          "nothing yet.",
        ),
        "",
        ...(await lookBack(planner, "week", first, last, today)),
        "",
        "Walk me through it one step at a time: what went well, what slipped and why, what to carry into next week " +
        "(move_task for the open ones), and one or two focuses for next week. Ask about what the data above asks " +
        "about, mark the goals that are reached with set_goal_status, and set next week's goals with add_goal. " +
        "Then write a short summary of the week " +
        "(a few sentences: wins, lessons, focus) and save it with save_review_summary (kind weekly, period " +
        `${first}), with my mood and energy from 1 to 5 if I gave them. get_review_summaries shows earlier weeks.`,
      ].join("\n"));
    },
  },
  {
    name: "monthly_review",
    title: "Monthly review",
    description: "Look back at a month in GoalMaker and choose what matters next month.",
    args: {
      month: z.string().optional().describe("The month, like 2026-09; this month by default."),
    },
    build: async (planner, args) => {
      const { today } = await planner.now();
      const month = args.month ?? today.slice(0, 7);
      if (!/^\d{4}-\d{2}$/.test(month) || !isDay(`${month}-01`)) {
        throw new PlannerError(`"${month}" isn't a month: use the form 2026-09.`);
      }
      const first = `${month}-01`;
      const next = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 1)).toISOString().slice(
        0,
        10,
      );
      const last = addDays(next, -1);
      const tasks = await planner.tasks();
      const names = await namesOf(planner);
      const done = await planner.completedBetween(tasks, first, last);
      const byArea = new Map<string, number>();
      for (const task of done) {
        const area = task.areaId ? names.areas.get(task.areaId)?.name ?? "No area" : "No area";
        byArea.set(area, (byArea.get(area) ?? 0) + 1);
      }
      return message([
        `Let's do my GoalMaker monthly review for ${format.longDay(first)} to ${format.longDay(last)}.`,
        "",
        `Done this month: ${done.length} tasks` +
        (byArea.size > 0 ? ` (${[...byArea].map(([area, count]) => `${area} ${count}`).join(", ")}).` : "."),
        block("Done", done.slice(-40), names, "nothing marked done."),
        block(
          "Still open and overdue",
          tasks.filter((task) => task.state === "open" && task.plannedDate !== null && task.plannedDate < today),
          names,
          "nothing.",
        ),
        "",
        ...(await lookBack(planner, "month", first, last, today)),
        "",
        "Help me see the month: which areas got attention and which didn't, what I'm proud of, what to stop or " +
        "change, and up to three things that matter most next month. Ask about what the data above asks about, mark " +
        "the goals that are reached with set_goal_status, and set next month's goals with add_goal. One question at " +
        "a time. Then write a short " +
        `summary and save it with save_review_summary (kind monthly, period ${first}).`,
      ].join("\n"));
    },
  },
];

function dayArgument(text: string): Day {
  const day = text.trim();
  if (!isDay(day)) throw new PlannerError(`"${text}" isn't a date: use the form 2026-09-14.`);
  return day;
}
