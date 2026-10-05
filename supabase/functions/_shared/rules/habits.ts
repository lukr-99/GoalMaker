import { addDays, type Day, mondayOf, weekday } from "./day.ts";
import { type GoalItem, periodEnd as goalPeriodEnd } from "./goals.ts";
import { nameBasedUuid } from "./nameBasedUuid.ts";

/**
 * Habit cadences, periods, streaks, the heatmap and today's ring (docs/habits.md,
 * contracts/vectors/habits.json). The check-ins and pauses handed in are the habit's own.
 */
export type HabitCadence = "daily" | "weekdays" | "per_week" | "per_month";
export type HabitMeasure = "check" | "count" | "amount";
/**
 * at_least: the target is something to reach. at_most: it is a limit for the day, or for the week or month
 * of a weekly or monthly habit, and going over breaks the period.
 */
export type HabitDirection = "at_least" | "at_most";
export type HabitPeriodState = "none" | "met" | "paused" | "skipped" | "open" | "missed";

/** A heatmap day: nothing (null), paused, skipped, or the day's value against its target from 0 to 1. */
export type HabitHeat = null | "paused" | "skipped" | "over" | number;

export interface HabitItem {
  id: string;
  cadence: HabitCadence;
  /** Monday 1, Tuesday 2 ... Sunday 64. */
  weekdays: number | null;
  times: number | null;
  measure: HabitMeasure;
  target: number | null;
  direction: HabitDirection;
  unit: string | null;
  goalId: string | null;
  startsOn: Day;
  deleted: boolean;
  /** Put away: off Today and the list. Left out means not archived. */
  archived?: boolean;
  /** false keeps the habit off Today and its widgets; left out means it shows (migration 0020). */
  showOnToday?: boolean;
}

export interface HabitCheckin {
  habitId: string;
  day: Day;
  value: number;
  skipped: boolean;
  /** The owner said the day's period failed: missed at once, today included. Left out means not failed. */
  failed?: boolean;
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

/**
 * Whether the habit asks something of `today`: not archived, started, due that day and not paused.
 * A habit kept off Today is still due here, so the Habits page, the Places hub and the counts keep it.
 */
export function dueToday(habit: HabitItem, today: Day, pauses: HabitPause[]): boolean {
  return habit.archived !== true && habit.startsOn <= today && isDue(habit, today) &&
    !pauses.some((pause) => covers(pause, today, today));
}

/** Whether Today's ring row and the widgets show the habit: due today and not kept off Today. */
export function onToday(habit: HabitItem, today: Day, pauses: HabitPause[]): boolean {
  return habit.showOnToday !== false && dueToday(habit, today, pauses);
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

/** Whether the habit's number is a limit rather than something to reach (docs/habits.md). */
export function isLimit(habit: HabitItem): boolean {
  return habit.direction === "at_most";
}

/** Whether the habit counts by the week or the month rather than by the day. */
export function isPeriodic(habit: Pick<HabitItem, "cadence">): boolean {
  return habit.cadence === "per_week" || habit.cadence === "per_month";
}

/**
 * A limit habit's number. For a day: the target, or none at all for a check ("not once"). For a week or a
 * month: how many days a check habit may have (`times`), or the most a count or an amount may add up to
 * (the target). It may be 0.
 */
export function limit(habit: Pick<HabitItem, "cadence" | "measure" | "times" | "target">): number {
  if (isPeriodic(habit) && habit.measure === "check") return habit.times ?? 0;
  return habit.measure === "check" ? 0 : habit.target ?? 0;
}

/** Whether a value goes over a limit habit's number. A habit to build is never over. */
export function isOver(habit: HabitItem, value: number): boolean {
  return isLimit(habit) && value > limit(habit);
}

/**
 * What a limit habit has had in its period up to and including `day`: the day's value, or the week's or
 * month's so far. Skipped and failed check-ins hold nothing.
 */
export function used(habit: HabitItem, day: Day, checkins: HabitCheckin[]): number {
  const start = habitPeriodStart(habit, day);
  return checkins
    .filter((checkin) =>
      !checkin.deleted && !checkin.skipped && checkin.failed !== true && checkin.day >= start && checkin.day <= day
    )
    .reduce((sum, checkin) => sum + checkin.value, 0);
}

/** Whether the period went over the limit by `day`: what turns the ring and the day red. */
export function wentOver(habit: HabitItem, day: Day, checkins: HabitCheckin[]): boolean {
  return isLimit(habit) && used(habit, day, checkins) > limit(habit);
}

/**
 * Whether a check-in meets its day: checked, or the day's value reaching the target. Under a limit, a day
 * nobody logged is met, because nothing was had. Skipped and failed never meet a day.
 */
export function dayMet(habit: HabitItem, checkin: HabitCheckin | undefined): boolean {
  if (checkin && !checkin.deleted && checkin.failed === true) return false;
  if (isLimit(habit)) return !checkin || checkin.deleted || (!checkin.skipped && !isOver(habit, checkin.value));
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
  if (isLimit(habit)) {
    // A limit is kept by default, so a pause or a skip comes before the day is judged, and a day over
    // the number is missed the moment it happens, today included.
    if (pauses.some((pause) => covers(pause, start, end))) return "paused";
    if (inPeriod.some((checkin) => checkin.skipped)) return "skipped";
    if (inPeriod.some((checkin) => checkin.failed === true)) return "missed";
    if (isOver(habit, inPeriod.reduce((sum, checkin) => sum + checkin.value, 0))) return "missed";
    return end >= today ? "open" : "met";
  }
  if (inPeriod.filter((checkin) => dayMet(habit, checkin)).length >= required(habit)) return "met";
  if (pauses.some((pause) => covers(pause, start, end))) return "paused";
  if (inPeriod.some((checkin) => checkin.skipped)) return "skipped";
  if (inPeriod.some((checkin) => checkin.failed === true)) return "missed";
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

/** A day of the heatmap: null, paused, skipped, over a limit, or the day's value against its target. */
export function heat(habit: HabitItem, day: Day, checkins: HabitCheckin[], pauses: HabitPause[]): HabitHeat {
  if (day < habit.startsOn || !isDue(habit, day)) return null;
  if (pauses.some((pause) => covers(pause, day, day))) return "paused";
  const checkin = checkins.find((checkin) => !checkin.deleted && checkin.day === day);
  if (checkin?.skipped) return "skipped";
  if (checkin?.failed === true) return isLimit(habit) ? "over" : 0;
  // A limit's heatmap reads the other way round: a clean day is full, the shade fades as the day's or the
  // period's allowance is used, and going over is its own mark.
  if (isLimit(habit)) {
    const had = used(habit, day, checkins);
    return isOver(habit, had) ? "over" : 1 - limitShare(habit, had);
  }
  return share(habit, checkin?.value ?? 0);
}

/** Today's ring: the day against the target, or the days met so far against N; null when today isn't due. */
export function ring(habit: HabitItem, today: Day, checkins: HabitCheckin[]): number | null {
  // A limit's ring fills with what was had in the day, or in the week or month so far.
  if (isLimit(habit)) {
    if (!isPeriodic(habit) && !isDue(habit, today)) return null;
    if (checkins.some((checkin) => !checkin.deleted && checkin.failed === true && checkin.day === today)) return 0;
    return limitShare(habit, used(habit, today, checkins));
  }
  if (habit.cadence === "per_week" || habit.cadence === "per_month") {
    const start = habitPeriodStart(habit, today);
    const end = habitPeriodEnd(habit, start);
    const met = checkins.filter((checkin) => checkin.day >= start && checkin.day <= end && dayMet(habit, checkin));
    return Math.min(1, met.length / required(habit));
  }
  if (!isDue(habit, today)) return null;
  const checkin = checkins.find((checkin) => !checkin.deleted && !checkin.skipped && checkin.day === today);
  if (checkin?.failed === true) return 0;
  return share(habit, checkin?.value ?? 0);
}

/** The Habits page's group: limits, weekly (and monthly) ones, or the ones on days. */
export type HabitGroup = "days" | "weekly" | "limits";

/** Where a habit stands today: a limit is never done or left, so it never reads as not done. */
export type HabitStanding = "none" | "paused" | "skipped" | "failed" | "limit" | "done" | "left";

/** One day of the week's dots on a habit card. */
export type HabitDot = "none" | "paused" | "skipped" | "over" | "open" | "met" | "missed";

export function habitGroup(habit: HabitItem): HabitGroup {
  if (isLimit(habit)) return "limits";
  return habit.cadence === "per_week" || habit.cadence === "per_month" ? "weekly" : "days";
}

/**
 * Where a habit stands on `today`: none, paused, skipped, failed, a limit, done (the ring is full, or a weekly or
 * monthly habit's check-in today meets its day) or left.
 */
export function standing(habit: HabitItem, today: Day, checkins: HabitCheckin[], pauses: HabitPause[]): HabitStanding {
  if (habit.archived === true || today < habit.startsOn || !isDue(habit, today)) return "none";
  if (pauses.some((pause) => covers(pause, today, today))) return "paused";
  const start = habitPeriodStart(habit, today);
  const end = habitPeriodEnd(habit, start);
  if (checkins.some((checkin) => !checkin.deleted && checkin.skipped && checkin.day >= start && checkin.day <= end)) {
    return "skipped";
  }
  if (
    checkins.some((checkin) =>
      !checkin.deleted && checkin.failed === true && checkin.day >= start && checkin.day <= end
    )
  ) {
    return "failed";
  }
  if (isLimit(habit)) return "limit";
  if ((ring(habit, today, checkins) ?? 0) >= 1) return "done";
  const todays = checkins.find((checkin) => !checkin.deleted && checkin.day === today);
  const weekly = habit.cadence === "per_week" || habit.cadence === "per_month";
  return weekly && todays !== undefined && dayMet(habit, todays) ? "done" : "left";
}

/** One day of the week's dots: none, paused, skipped, over, open (today), met or missed. */
export function dot(habit: HabitItem, day: Day, today: Day, checkins: HabitCheckin[], pauses: HabitPause[]): HabitDot {
  if (day < habit.startsOn || !isDue(habit, day)) return "none";
  if (pauses.some((pause) => covers(pause, day, day))) return "paused";
  const checkin = checkins.find((checkin) => !checkin.deleted && checkin.day === day);
  if (checkin?.skipped) return "skipped";
  if (checkin?.failed === true) return isLimit(habit) ? "over" : "missed";
  if (isLimit(habit)) {
    if (checkin !== undefined && wentOver(habit, day, checkins)) return "over";
    return day >= today ? "open" : "met";
  }
  if (checkin !== undefined && dayMet(habit, checkin)) return "met";
  if (day >= today) return "open";
  // A weekly or monthly habit isn't due on any one day, so a day without one misses nothing.
  return habit.cadence === "per_week" || habit.cadence === "per_month" ? "none" : "missed";
}

/** Whether Today shows its "all done" card: none of its habits is left, and at least one is done. */
export function allDone(standings: HabitStanding[]): boolean {
  return !standings.includes("left") && standings.includes("done");
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
      !checkin.deleted && !checkin.skipped && checkin.failed !== true && serving.has(checkin.habitId) &&
      checkin.day >= goal.periodStart &&
      checkin.day <= end
    )
    .map((checkin) => checkin.value);
}

function covers(pause: HabitPause, start: Day, end: Day): boolean {
  return !pause.deleted && pause.from <= end && (pause.until === null || pause.until >= start);
}

// How much of a limit `had` uses, 0 to 1; with a limit of 0, anything at all uses it up.
function limitShare(habit: HabitItem, had: number): number {
  const most = limit(habit);
  if (most <= 0) return had > 0 ? 1 : 0;
  return Math.min(1, Math.max(0, had / most));
}

function share(habit: HabitItem, value: number): number {
  if (habit.measure === "check") return value >= 1 ? 1 : 0;
  return Math.min(1, Math.max(0, value / (habit.target ?? 1)));
}

function unitKey(unit: string): string {
  return unit.replace(/\s/g, "").toLowerCase();
}
