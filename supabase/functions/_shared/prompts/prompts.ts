import { type GetPromptResult, z } from "../deps.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import { reviewDigest } from "../planner/reviewDigest.ts";
import { type Day, isDay, mondayOf } from "../rules/day.ts";
import { type Digest, horizonOf, periodOf } from "../rules/digest.ts";
import { periodEnd as goalPeriodEnd } from "../rules/goals.ts";
import { lists } from "../rules/listRules.ts";
import { MAX_PRIORITIES, review, tomorrow } from "../rules/planRules.ts";
import type { TaskItem } from "../rules/task.ts";
import * as format from "../tools/format.ts";

// What each trigger is about, in the words the prompt uses (contracts/content/prompts.json).
export const TRIGGER_WORDS: Record<string, string> = {
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
function lookBack(found: Digest): string[] {
  const horizon = horizonOf(found.period.kind);
  const { start } = found.period;
  const lines: string[] = [];
  lines.push(
    found.goals.length === 0 ? `Goals of this ${horizon}: none set.` : [
      `Goals of this ${horizon}:`,
      ...found.goals.map((row) =>
        format.goalLine(row.goal, row.progress, `${start} to ${goalPeriodEnd(horizon, start)}`)
      ),
    ].join("\n"),
  );
  if (found.habits.length > 0) {
    lines.push([
      "Habits:",
      ...found.habits.map((row) =>
        `- ${row.habit.emoji ? `${row.habit.emoji} ` : ""}${row.habit.name} · ${row.met} of ${row.periods} met` +
        (row.streak > 0 ? ` · streak ${row.streak}` : "") + ` (habit id ${row.habit.id})`
      ),
    ].join("\n"));
  }
  const called = found.triggers.map(({ trigger, subject }) =>
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
      const found = await reviewDigest(planner, periodOf("weekly", first));
      const last = found.period.end;
      const names = await namesOf(planner);
      return message([
        `Let's do my GoalMaker weekly review for the week of ${format.longDay(first)} to ${format.longDay(last)}.`,
        "",
        block("Done this week", found.done, names, "nothing marked done."),
        block("Still open from this week or earlier", [...found.earlier, ...found.left], names, "nothing."),
        block("Planned for next week", found.nextTasks, names, "nothing yet."),
        "",
        ...lookBack(found),
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
      const found = await reviewDigest(planner, periodOf("monthly", `${month}-01`));
      const { start: first, end: last } = found.period;
      const names = await namesOf(planner);
      const done = found.done;
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
        block("Still open and overdue", found.overdue, names, "nothing."),
        "",
        ...lookBack(found),
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
