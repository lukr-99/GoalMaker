import { z } from "../deps.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import { reviewDigest, type ReviewWant } from "../planner/reviewDigest.ts";
import { TRIGGER_WORDS } from "../prompts/prompts.ts";
import { defaultPeriod, type Digest, type DigestKind, horizonOf, periodOf } from "../rules/digest.ts";
import { periodEnd as goalPeriodEnd } from "../rules/goals.ts";
import type { TaskItem } from "../rules/task.ts";
import * as format from "./format.ts";
import { tallySummary } from "./tallyTools.ts";
import type { Tool } from "./tools.ts";

/**
 * The review digest as JSON (docs/letter.md): one period of GoalMaker in one answer, for a scheduled
 * routine that writes the owner's letter. Names stand in for ids wherever Claude would write them.
 * `counts` says how many tasks were done and how many of them were project work, as the apps' stats
 * count it (docs/stats.md): items of a project that is still there, so a deleted project's are other.
 */
export function digestJson(found: Digest, names: format.Names, completedOn: (task: TaskItem) => string | null) {
  const area = (task: TaskItem) => (task.areaId ? names.areas.get(task.areaId)?.name ?? null : null);
  const project = (task: TaskItem) => (task.projectId ? names.projects.get(task.projectId)?.name ?? null : null);
  const task = (one: TaskItem) => ({
    id: one.id,
    title: one.title,
    day: one.plannedDate,
    area: area(one),
    project: project(one),
  });
  const want = (one: ReviewWant) => ({
    id: one.id,
    title: one.title,
    reason: one.reason,
    price: one.price,
    currency: one.currency,
    cools_until: one.coolsUntil,
    decision: one.decision,
    decided_on: one.decidedOn,
    note: one.decisionNote.length > 0 ? one.decisionNote : null,
  });
  const byDay = new Map<string, TaskItem[]>();
  for (const one of found.done) {
    const day = completedOn(one) ?? "unknown";
    byDay.set(day, [...(byDay.get(day) ?? []), one]);
  }
  const byProject = new Map<string, TaskItem[]>();
  for (const one of found.finishedItems) {
    const name = project(one) ?? "a deleted project";
    byProject.set(name, [...(byProject.get(name) ?? []), one]);
  }
  const horizon = horizonOf(found.period.kind);
  const projectWork = found.done.filter((one) => project(one) !== null).length;
  return {
    period: found.period,
    counts: { done: found.done.length, project_work: projectWork, other_work: found.done.length - projectWork },
    done: [...byDay].map(([day, tasks]) => ({
      day,
      tasks: tasks.map((one) => ({ id: one.id, title: one.title, area: area(one), project: project(one) })),
    })),
    open: {
      left: found.left.map((one) => ({ ...task(one), moves: one.movedCount ?? 0 })),
      overdue: found.overdue.map(task),
      slipping: found.slipping.map((one) => ({ id: one.id, title: one.title, moves: one.movedCount ?? 0 })),
    },
    goals: found.goals.map(({ goal, progress, expected }) => ({
      id: goal.id,
      title: goal.title,
      status: goal.status,
      counts: goal.mode,
      value: progress.value,
      target: progress.target,
      unit: goal.unit,
      progress: Math.round(progress.fraction * 100),
      expected_by_now: Math.round(expected * 100),
      reached: progress.hit,
    })),
    habits: found.habits.map((row) => ({
      id: row.habit.id,
      name: row.habit.name,
      met: row.met,
      missed: row.missed,
      skipped: row.skipped,
      periods: row.periods,
      streak: row.streak,
    })),
    projects: [...byProject].map(([name, items]) => ({
      project: name,
      done: items.map((one) => ({ id: one.id, title: one.title })),
    })),
    triggers: found.triggers.map(({ trigger, subject }) => ({
      trigger,
      about: TRIGGER_WORDS[trigger] ?? trigger,
      subject,
    })),
    review: found.review === null ? null : {
      mood: found.review.mood,
      energy: found.review.energy,
      reflections: found.review.reflections,
      has_letter: found.review.summary.trim().length > 0,
    },
    last_letter: found.lastLetter === null
      ? null
      : { period_start: found.lastLetter.periodStart, letter: found.lastLetter.summary },
    next: {
      start: found.next.start,
      end: found.next.end,
      tasks: found.nextTasks.map(task),
      deadlines: found.nextDeadlines.map((one) => ({ ...task(one), deadline: one.deadline })),
      goals: found.nextGoals.map((goal) => ({
        id: goal.id,
        title: goal.title,
        [horizon]: `${goal.periodStart} to ${goalPeriodEnd(goal.horizon, goal.periodStart)}`,
      })),
    },
    wants: {
      became_ready: (found.wantsReady as ReviewWant[]).map(want),
      decided: (found.wantsDecided as ReviewWant[]).map(want),
      ready_next: (found.wantsReadyNext as ReviewWant[]).map(want),
    },
  };
}

async function completedDays(planner: Planner, found: Digest): Promise<Map<string, string>> {
  const days = new Map<string, string>();
  for (const one of found.done) if (one.completedAt !== null) days.set(one.id, await planner.dayOf(one.completedAt));
  return days;
}

export const digestTools: Tool[] = [
  {
    name: "get_review_digest",
    title: "Review digest",
    description:
      "One week, month or year of GoalMaker in one answer, as JSON, for writing the owner's letter about it (a " +
      "scheduled routine does this) or for a review: tasks done by day and how many were project work, what is left, overdue and slipping, the " +
      "period's goals against where they should be by now, each habit's met, missed and skipped periods and its " +
      "streak, project items done, what the period's data asks about, this period's review so far and the last " +
      "period's letter, what the next period already holds, and the wants that became ready, were decided, or " +
      "are ready next, and where the time went (Tally minutes by category, project and device, when Tally is on). " +
      "Without a period it is the week, month or year holding yesterday, so a Sunday evening run and a " +
      "Monday morning run both mean the week just finishing. Pass the period's start to save_review_summary.",
    input: {
      kind: z.enum(["weekly", "monthly", "yearly"]).optional().describe(
        "weekly, monthly or yearly; weekly by default.",
      ),
      period: z.string().optional().describe(
        'Any day in the week, month or year, like 2026-09-14 or "today". The one holding yesterday by default.',
      ),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const kind: DigestKind = args.kind ?? "weekly";
      const { today } = await planner.now();
      let period = defaultPeriod(kind, today);
      if (args.period !== undefined && args.period.trim() !== "") {
        const day = await planner.day(args.period);
        if (day === null) throw new PlannerError(`"${args.period}" isn't a day: use a date like 2026-09-14.`);
        period = periodOf(kind, day);
      }
      const found = await reviewDigest(planner, period);
      const names = format.names(await planner.areas(), [], new Map(), await planner.projects());
      const days = await completedDays(planner, found);
      // Tally's minutes for the period (docs/tally.md): categories, projects and devices, never apps.
      const tally = await tallySummary(planner, await planner.tally().between(period.start, period.end));
      return JSON.stringify({ ...digestJson(found, names, (task) => days.get(task.id) ?? null), tally }, null, 2);
    },
  },
];
