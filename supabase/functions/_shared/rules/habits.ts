import { addDays, type Day, mondayOf, weekday } from "./day.ts";
import { type GoalItem, periodEnd as goalPeriodEnd } from "./goals.ts";
import { nameBasedUuid } from "./nameBasedUuid.ts";

/**
 * Habit cadences, periods, streaks, the heatmap and today's ring (docs/habits.md,
 * contracts/vectors/habits.json). The check-ins and pauses handed in are the habit's own.
 */
export type HabitCadence = "daily" | "weekdays" | "per_week" | "per_month";
export type HabitMeasure = "check" | "count" | "amount";
export type HabitPeriodState = "none" | "met" | "paused" | "skipped" | "open" | "missed";

/** A heatmap day: nothing (null), paused, skipped, or the day's value against its target from 0 to 1. */
export type HabitHeat = null | "paused" | "skipped" | number;

export interface HabitItem {
  id: string;
  cadence: HabitCadence;
  /** Monday 1, Tuesday 2 ... Sunday 64. */
  weekdays: number | null;
  times: number | null;
  measure: HabitMeasure;
  target: number | null;
  unit: string | null;
  goalId: string | null;
  startsOn: Day;
  deleted: boolean;
}

export interface HabitCheckin {
  habitId: string;
  day: Day;
  value: number;
  skipped: boolean;
  deleted: boolean;
}

export interface HabitPause {
  from: Day;
  until: Day | null;
  deleted: boolean;
}

const NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";

// A daily habit's streak can't reach back further than this many periods.
const MAX_PERIODS = 3700;

/** The weekday bit of `day`: Monday 1, Tuesday 2 ... Sunday 64. */
export function weekdayBit(day: Day): number {
  return 1 << (weekday(day) - 1);
}

/** Whether `habit` is due on `day`; weekly and monthly habits are due any day. */
export function isDue(habit: HabitItem, day: Day): boolean {
  return habit.cadence !== "weekdays" || ((habit.weekdays ?? 0) & weekdayBit(day)) !== 0;
}

/** The first day of the habit's period holding `day`: the day, its week's Monday, or its month's first. */
export function habitPeriodStart(habit: HabitItem, day: Day): Day {
  switch (habit.cadence) {
    case "per_week":
      return mondayOf(day);
    case "per_month":
      return `${day.slice(0, 7)}-01`;
    default:
      return day;
  }
}

/** The last day of the habit's period starting on `start`. */
export function habitPeriodEnd(habit: HabitItem, start: Day): Day {
  switch (habit.cadence) {
    case "per_week":
      return addDays(start, 6);
    case "per_month":
      return goalPeriodEnd("month", start);
    default:
      return start;
  }
}

/** Whether a check-in meets its day: checked, or the day's value reaching the target. Skipped never does. */
export function dayMet(habit: HabitItem, checkin: HabitCheckin | undefined): boolean {
  if (!checkin || checkin.deleted || checkin.skipped) return false;
  return habit.measure === "check" ? checkin.value >= 1 : checkin.value >= (habit.target ?? Infinity);
}

/** How many days a period needs: one for a day, N for a week or month. */
export function required(habit: HabitItem): number {
  return habit.cadence === "per_week" || habit.cadence === "per_month" ? habit.times ?? 1 : 1;
}

/** The state of the habit's period starting on `start`, seen from `today`. */
export function habitState(
  habit: HabitItem,
  start: Day,
  today: Day,
  checkins: HabitCheckin[],
  pauses: HabitPause[],
): HabitPeriodState {
  const end = habitPeriodEnd(habit, start);
  if (end < habit.startsOn) return "none";
  if ((habit.cadence === "daily" || habit.cadence === "weekdays") && !isDue(habit, start)) return "none";
  const inPeriod = checkins.filter((checkin) => !checkin.deleted && checkin.day >= start && checkin.day <= end);
  if (inPeriod.filter((checkin) => dayMet(habit, checkin)).length >= required(habit)) return "met";
  if (pauses.some((pause) => covers(pause, start, end))) return "paused";
  if (inPeriod.some((checkin) => checkin.skipped)) return "skipped";
  return end >= today ? "open" : "missed";
}

/** The starts of the habit's periods that touch the days `from` to `to`, oldest first. */
export function periodsBetween(habit: HabitItem, from: Day, to: Day): Day[] {
  const starts: Day[] = [];
  let start = habitPeriodStart(habit, from);
  while (start <= to) {
    if (start >= from || habitPeriodEnd(habit, start) >= from) starts.push(start);
    start = addDays(habitPeriodEnd(habit, start), 1);
  }
  return starts;
}

/** Met periods back from the one holding `today`; open, paused, skipped and none pass, missed ends it. */
export function streak(habit: HabitItem, today: Day, checkins: HabitCheckin[], pauses: HabitPause[]): number {
  let count = 0;
  let start = habitPeriodStart(habit, today);
  for (let period = 0; period < MAX_PERIODS && habitPeriodEnd(habit, start) >= habit.startsOn; period++) {
    const state = habitState(habit, start, today, checkins, pauses);
    if (state === "met") count++;
    if (state === "missed") return count;
    start = habitPeriodStart(habit, addDays(start, -1));
  }
  return count;
}

/** A day of the heatmap: null, paused, skipped, or the day's value against its target. */
export function heat(habit: HabitItem, day: Day, checkins: HabitCheckin[], pauses: HabitPause[]): HabitHeat {
  if (day < habit.startsOn || !isDue(habit, day)) return null;
  if (pauses.some((pause) => covers(pause, day, day))) return "paused";
  const checkin = checkins.find((checkin) => !checkin.deleted && checkin.day === day);
  if (checkin?.skipped) return "skipped";
  return share(habit, checkin?.value ?? 0);
}

/** Today's ring: the day against the target, or the days met so far against N; null when today isn't due. */
export function ring(habit: HabitItem, today: Day, checkins: HabitCheckin[]): number | null {
  if (habit.cadence === "per_week" || habit.cadence === "per_month") {
    const start = habitPeriodStart(habit, today);
    const end = habitPeriodEnd(habit, start);
    const met = checkins.filter((checkin) => checkin.day >= start && checkin.day <= end && dayMet(habit, checkin));
    return Math.min(1, met.length / required(habit));
  }
  if (!isDue(habit, today)) return null;
  const checkin = checkins.find((checkin) => !checkin.deleted && !checkin.skipped && checkin.day === today);
  return share(habit, checkin?.value ?? 0);
}

/** The id of a habit's one check-in on `day`, the same on every device. */
export function checkinId(habitId: string, day: Day): Promise<string> {
  return nameBasedUuid(NAMESPACE, `checkin/${habitId.toLowerCase()}/${day}`);
}

/**
 * The check-in values that count toward `goal`: of habits serving it, measured by count or amount, in its
 * unit (lowercased, without spaces), not skipped, on a day in its period (story 32).
 */
export function goalAmounts(
  goal: Pick<GoalItem, "id" | "unit" | "horizon" | "periodStart">,
  habits: Pick<HabitItem, "id" | "goalId" | "measure" | "unit" | "deleted">[],
  checkins: HabitCheckin[],
): number[] {
  if (goal.unit === null) return [];
  const unit = unitKey(goal.unit);
  const end = goalPeriodEnd(goal.horizon, goal.periodStart);
  const serving = new Set(
    habits
      .filter((habit) =>
        !habit.deleted && habit.goalId === goal.id && habit.measure !== "check" && habit.unit !== null &&
        unitKey(habit.unit) === unit
      )
      .map((habit) => habit.id),
  );
  return checkins
    .filter((checkin) =>
      !checkin.deleted && !checkin.skipped && serving.has(checkin.habitId) && checkin.day >= goal.periodStart &&
      checkin.day <= end
    )
    .map((checkin) => checkin.value);
}

function covers(pause: HabitPause, start: Day, end: Day): boolean {
  return !pause.deleted && pause.from <= end && (pause.until === null || pause.until >= start);
}

function share(habit: HabitItem, value: number): number {
  if (habit.measure === "check") return value >= 1 ? 1 : 0;
  return Math.min(1, Math.max(0, value / (habit.target ?? 1)));
}

function unitKey(unit: string): string {
  return unit.replace(/\s/g, "").toLowerCase();
}
