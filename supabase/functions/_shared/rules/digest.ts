import { addDays, type Day, toEpochDay } from "./day.ts";
import { type GoalEntryItem, type GoalItem, type GoalProgress, goalProgress } from "./goals.ts";
import {
  goalAmounts,
  type HabitCheckin,
  type HabitItem,
  type HabitPause,
  habitState,
  periodsBetween,
  streak,
} from "./habits.ts";
import { type PeriodFacts, type Trigger, triggers } from "./prompts.ts";
import { periodStart, type ReviewKind } from "./reviews.ts";
import { compareText, type TaskItem } from "./task.ts";
import type { WantItem } from "./wants.ts";

/**
 * One period of GoalMaker in one piece (docs/letter.md, contracts/vectors/reviews.json 'digest'):
 * what was done and what is left, the goals and habits, the board, what the period's facts call for,
 * the review so far and the last letter, what the next period holds, and the wants. The connector's
 * get_review_digest and its weekly and monthly review prompts are both built from it.
 */
export type DigestKind = Extract<ReviewKind, "weekly" | "monthly">;

export interface DigestPeriod {
  kind: DigestKind;
  start: Day;
  end: Day;
}

export interface DigestHabit extends HabitItem {
  name: string;
  emoji: string | null;
  archived: boolean;
}

export interface DigestWant extends WantItem {
  /** The planning day it was decided on, or null while undecided. */
  decidedOn: Day | null;
}

export interface DigestReview {
  kind: ReviewKind;
  periodStart: Day;
  mood: number | null;
  energy: number | null;
  summary: string;
  reflections: { prompt: string; answer: string }[];
}

export interface DigestInput {
  period: DigestPeriod;
  today: Day;
  /** The tasks that are not deleted. */
  tasks: TaskItem[];
  /** The planning day each done task was completed on, by task id. */
  completedOn: Map<string, Day>;
  goals: GoalItem[];
  entries: Map<string, GoalEntryItem[]>;
  habits: DigestHabit[];
  checkins: (HabitCheckin & { habitId: string })[];
  pauses: (HabitPause & { habitId: string })[];
  wants: DigestWant[];
  reviews: DigestReview[];
}

export interface DigestGoal {
  goal: GoalItem;
  progress: GoalProgress;
  /** Where the goal should be by now, 0 to 1: how much of the period has gone. */
  expected: number;
}

export interface DigestHabitRow {
  habit: DigestHabit;
  met: number;
  missed: number;
  skipped: number;
  /** The periods that counted: every one but those before the habit started or not due. */
  periods: number;
  streak: number;
}

export interface Digest {
  period: DigestPeriod;
  /** Done in the period, in the order they were done. */
  done: TaskItem[];
  /** Still open and planned inside the period. */
  left: TaskItem[];
  /** Still open and planned before the period started. */
  earlier: TaskItem[];
  /** Still open and planned before today. */
  overdue: TaskItem[];
  /** Open tasks of the period that keep moving, most moves first. */
  slipping: TaskItem[];
  goals: DigestGoal[];
  habits: DigestHabitRow[];
  /** Project items done in the period. */
  finishedItems: TaskItem[];
  facts: PeriodFacts;
  triggers: Trigger[];
  /** This period's review, when there is one. */
  review: DigestReview | null;
  /** The last period's review, when it has a letter. */
  lastLetter: DigestReview | null;
  next: DigestPeriod;
  nextTasks: TaskItem[];
  nextDeadlines: TaskItem[];
  nextGoals: GoalItem[];
  wantsReady: DigestWant[];
  wantsDecided: DigestWant[];
  wantsReadyNext: DigestWant[];
}

const SLIPPING_MOVES = 3;

/** The goals' horizon a review of `kind` looks at. */
export function horizonOf(kind: DigestKind): "week" | "month" {
  return kind === "weekly" ? "week" : "month";
}

/** The last day of the period of `kind` that starts on `start`. */
export function periodEnd(kind: DigestKind, start: Day): Day {
  if (kind === "weekly") return addDays(start, 6);
  const [year, month] = start.split("-").map(Number);
  return addDays(new Date(Date.UTC(year, month, 1)).toISOString().slice(0, 10), -1);
}

/** The period of `kind` that holds `day`. */
export function periodOf(kind: DigestKind, day: Day): DigestPeriod {
  const start = periodStart(kind, day);
  return { kind, start, end: periodEnd(kind, start) };
}

/**
 * The period a digest means when none is named: the one holding yesterday's planning day, so a run on
 * Sunday evening and one on Monday morning both mean the week that is just finishing.
 */
export function defaultPeriod(kind: DigestKind, today: Day): DigestPeriod {
  return periodOf(kind, addDays(today, -1));
}

export function digest(input: DigestInput): Digest {
  const { period, today } = input;
  const { start, end } = period;
  const horizon = horizonOf(period.kind);
  const until = today < end ? today : end;
  const length = toEpochDay(end) - toEpochDay(start) + 1;
  const expected = Math.min(1, Math.max(0, (toEpochDay(until) - toEpochDay(start) + 1) / length));
  const tasks = input.tasks.filter((task) => !task.deleted);
  const habits = input.habits.filter((habit) => !habit.archived && !habit.deleted);

  const completedBetween = (first: Day, last: Day) =>
    tasks
      .filter((task) => {
        const on = task.state === "done" ? input.completedOn.get(task.id) : undefined;
        return on !== undefined && on >= first && on <= last;
      })
      .sort((a, b) => compareText(a.completedAt ?? "", b.completedAt ?? ""));
  const done = completedBetween(start, end);
  const before = completedBetween(addDays(start, -length), addDays(start, -1));

  const open = tasks.filter((task) => task.state === "open" && task.plannedDate !== null);
  const byDay = (a: TaskItem, b: TaskItem) => compareText(a.plannedDate!, b.plannedDate!);
  const left = open.filter((task) => task.plannedDate! >= start && task.plannedDate! <= end).sort(byDay);
  const earlier = open.filter((task) => task.plannedDate! < start).sort(byDay);
  const overdue = open.filter((task) => task.plannedDate! < today);
  const slipping = left
    .filter((task) => (task.movedCount ?? 0) >= SLIPPING_MOVES)
    .sort((a, b) => (b.movedCount ?? 0) - (a.movedCount ?? 0) || compareText(a.title, b.title));

  const goals = input.goals
    .filter((goal) => !goal.deleted && goal.horizon === horizon && goal.periodStart === start)
    .map((goal) => ({ goal, progress: progressOf(goal, tasks, input.entries, habits, input.checkins), expected }));

  const habitRows = habits.map((habit) => {
    const own = input.checkins.filter((checkin) => checkin.habitId === habit.id);
    const rests = input.pauses.filter((pause) => pause.habitId === habit.id);
    const states = periodsBetween(habit, start, until).map((first) => habitState(habit, first, today, own, rests));
    return {
      habit,
      met: states.filter((state) => state === "met").length,
      missed: states.filter((state) => state === "missed").length,
      skipped: states.filter((state) => state === "skipped").length,
      periods: states.filter((state) => state !== "none").length,
      streak: streak(habit, until, own, rests),
    };
  });

  const facts: PeriodFacts = {
    doneTasks: done.length,
    averageDone: before.length,
    goals: goals.map((row) => ({ title: row.goal.title, fraction: row.progress.fraction, expected })),
    habits: habitRows.map((row) => ({
      name: row.habit.name,
      missed: row.periods - row.met,
      periods: row.periods,
      streak: row.streak,
    })),
    tasks: left.map((task) => ({ title: task.title, moves: task.movedCount ?? 0 })),
  };

  const next = periodOf(period.kind, addDays(end, 1));
  const inNext = (day: Day | null) => day !== null && day >= next.start && day <= next.end;
  const reviewOf = (first: Day) =>
    input.reviews.find((review) => review.kind === period.kind && review.periodStart === first) ?? null;
  const last = reviewOf(periodStart(period.kind, addDays(start, -1)));
  const wants = input.wants.filter((want) => !want.deleted);
  const inPeriod = (day: Day | null) => day !== null && day >= start && day <= end;

  return {
    period,
    done,
    left,
    earlier,
    overdue,
    slipping,
    goals,
    habits: habitRows,
    finishedItems: done.filter((task) => (task.projectId ?? null) !== null),
    facts,
    triggers: triggers(facts),
    review: reviewOf(start),
    lastLetter: last !== null && last.summary.trim().length > 0 ? last : null,
    next,
    nextTasks: open.filter((task) => inNext(task.plannedDate)),
    nextDeadlines: tasks
      .filter((task) => task.state === "open" && inNext(task.deadline))
      .sort((a, b) => compareText(a.deadline!, b.deadline!)),
    nextGoals: input.goals.filter((goal) =>
      !goal.deleted && goal.horizon === horizon && goal.periodStart === next.start
    ),
    wantsReady: wants.filter((want) => inPeriod(want.coolsUntil)).sort(byCooling),
    wantsDecided: wants.filter((want) => want.decision !== null && inPeriod(want.decidedOn)).sort(byCooling),
    wantsReadyNext: wants.filter((want) => want.decision === null && inNext(want.coolsUntil)).sort(byCooling),
  };
}

function progressOf(
  goal: GoalItem,
  tasks: TaskItem[],
  entries: Map<string, GoalEntryItem[]>,
  habits: DigestHabit[],
  checkins: HabitCheckin[],
): GoalProgress {
  return goalProgress(
    goal.mode,
    goal.status,
    goal.target,
    tasks.filter((task) => task.goalId === goal.id),
    [
      ...(entries.get(goal.id) ?? []),
      ...(goal.mode === "number"
        ? goalAmounts(goal, habits, checkins).map((amount) => ({ amount, deleted: false }))
        : []),
    ],
  );
}

function byCooling(a: WantItem, b: WantItem): number {
  return compareText(a.coolsUntil, b.coolsUntil) || compareText(a.title.toLowerCase(), b.title.toLowerCase());
}
