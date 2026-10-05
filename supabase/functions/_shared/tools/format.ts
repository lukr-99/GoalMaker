import type { Area, Habit, Reminder, Step, Tag } from "../planner/planner.ts";
import type { LifeGoal } from "../planner/lifeGoalList.ts";
import type { Want } from "../planner/wantList.ts";
import { type Day, daysBetween, weekday } from "../rules/day.ts";
import type { GoalItem, GoalProgress, GoalStanding } from "../rules/goals.ts";
import { type HabitStanding, isPeriodic, limit } from "../rules/habits.ts";
import type { TimeLeft } from "../rules/lifeGoals.ts";
import type { PlanningLists } from "../rules/listRules.ts";
import { type Column, type ProjectItem, type ProjectMilestone, shownColumn } from "../rules/projects.ts";
import type { TaskItem } from "../rules/task.ts";
import { NEED, needLate, type WantState } from "../rules/wants.ts";

const WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

/** Everything a task line can show besides the task: the names of its area, its tags and its project. */
export interface Names {
  areas: Map<string, Area>;
  tags: Map<string, Tag>;
  links: Map<string, string[]>;
  projects: Map<string, ProjectItem>;
}

export function names(
  areas: Area[],
  tags: Tag[],
  links: Map<string, string[]>,
  projects: ProjectItem[] = [],
): Names {
  return {
    areas: new Map(areas.map((area) => [area.id, area])),
    tags: new Map(tags.map((tag) => [tag.id, tag])),
    links,
    projects: new Map(projects.map((project) => [project.id, project])),
  };
}

/** `Saturday 19 September 2026`. */
export function longDay(day: Day): string {
  const [year, month, date] = day.split("-").map(Number);
  return `${WEEKDAYS[weekday(day) - 1]} ${date} ${MONTHS[month - 1]} ${year}`;
}

/**
 * One task on one line, the way Claude can quote it and act on it: a box, the title, what the apps
 * show next to it, and the id for follow-up calls.
 */
export function taskLine(
  task: TaskItem,
  names: Names,
  options: { showDay?: boolean; showProject?: boolean; milestone?: string; showMaker?: boolean } = {},
): string {
  const box = task.state === "done" ? "[x]" : task.state === "dropped" ? "[-]" : "[ ]";
  const parts = [`${box} ${task.title}`];
  if (task.topPriority) parts.push("top priority");
  if (options.showDay && task.plannedDate) parts.push(task.plannedDate);
  if (task.plannedTime) parts.push(task.plannedTime);
  const area = task.areaId ? names.areas.get(task.areaId) : undefined;
  if (area) parts.push(`@${area.name}`);
  const tags = (names.links.get(task.id) ?? []).map((id) => names.tags.get(id)).filter((tag) => tag !== undefined);
  if (tags.length > 0) parts.push(tags.map((tag) => `#${tag!.name}`).join(" "));
  const project = task.projectId ? names.projects.get(task.projectId) : undefined;
  if (project && options.showProject !== false) parts.push(`+${project.name}`);
  if (task.itemType && task.itemType !== "task") parts.push(task.itemType);
  if (task.priority && task.priority !== "normal") parts.push(task.priority);
  if (options.milestone) parts.push(options.milestone);
  if (task.recurrence) parts.push(`repeats ${task.recurrence}`);
  if (task.deadline) parts.push(`due ${task.deadline}`);
  if (options.showMaker && task.madeBy === "claude") parts.push("by Claude");
  return `- ${parts.join(" · ")} (id ${task.id})`;
}

/** What the Goals screen adds to a goal's line: its pace and the goal it feeds. */
export interface GoalExtras {
  standing: GoalStanding;
  /** The title of the goal it feeds, or null when it stands on its own. */
  feeds: string | null;
}

/**
 * A goal with where it stands, the way the goals screen reads out loud. With `extras`, an open goal
 * also says its pace (On track, Behind by 6 km, Needs you, Hit) and the goal it feeds.
 */
export function goalLine(goal: GoalItem, progress: GoalProgress, period: string, extras?: GoalExtras): string {
  const parts = [`${goal.emoji ? `${goal.emoji} ` : ""}${goal.title}`, `${goal.horizon} of ${period}`];
  if (goal.mode === "number") {
    parts.push(`${round(progress.value)} of ${round(progress.target)}${goal.unit ? ` ${goal.unit}` : ""}`);
  } else if (goal.mode === "tasks") {
    parts.push(`${round(progress.value)} of ${round(progress.target)} tasks done`);
  }
  parts.push(goal.status === "open" ? `${Math.round(progress.fraction * 100)}%` : goal.status);
  if (extras !== undefined && goal.status === "open") {
    parts.push(progress.hit ? "Hit, waiting to be marked done" : paceText(goal, extras.standing));
  } else if (goal.status === "open" && progress.hit) {
    parts.push("reached, waiting to be marked done");
  }
  if (extras?.feeds) parts.push(`feeds ${extras.feeds}`);
  return `- ${parts.join(" · ")} (goal id ${goal.id})`;
}

/** A goal's pace the way its card says it: On track, Behind by 6 km, Behind by 2 tasks, Needs you or Hit. */
export function paceText(goal: Pick<GoalItem, "mode" | "unit">, standing: GoalStanding): string {
  switch (standing.pace) {
    case "hit":
      return "Hit";
    case "dropped":
      return "Dropped";
    case "on_track":
      return "On track";
    case "behind": {
      if (standing.behind === null) return "Needs you";
      const by = round(standing.behind);
      if (goal.mode === "tasks") return `Behind by ${by} ${standing.behind === 1 ? "task" : "tasks"}`;
      return `Behind by ${by}${goal.unit ? ` ${goal.unit}` : ""}`;
    }
  }
}

/** What a habit card shows for a day, worked out from the habit's rules (docs/habits.md). */
export interface HabitView {
  standing: HabitStanding;
  /** The day's value: what was checked in on it, 0 with nothing. */
  value: number;
  /** For a weekly or monthly habit, the days met so far in its period. */
  met: number;
  /** For a limit, what its day, or its week or month so far, has had; the day's value when left out. */
  used?: number;
  streak: number;
  /** The title of the goal it serves, when it serves one. */
  serves?: string | null;
}

const DAY_SHORT = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/**
 * How often a habit asks: every day, on Mon, Wed, Fri, 3 times a week, 2 times a month. A weekly or monthly
 * limit is just weekly or monthly, since its number is in where it stands.
 */
export function cadenceText(habit: Pick<Habit, "cadence" | "times" | "weekdays"> & { direction?: string }): string {
  if (habit.direction === "at_most" && habit.cadence === "per_week") return "weekly";
  if (habit.direction === "at_most" && habit.cadence === "per_month") return "monthly";
  switch (habit.cadence) {
    case "per_week":
      return `${habit.times} times a week`;
    case "per_month":
      return `${habit.times} times a month`;
    case "weekdays": {
      const days = DAY_SHORT.filter((_, index) => ((habit.weekdays ?? 0) & (1 << index)) !== 0);
      return days.length === 0 ? "on chosen weekdays" : `on ${days.join(", ")}`;
    }
    default:
      return "every day";
  }
}

/**
 * Where a habit stands on the day, the way its card says it: the standing (done, left, skipped, failed, paused,
 * limit, not due today), then the count behind it ("3 of 8 glasses today", "1 of 3 this week",
 * "1 of at most 2 today", "3 of at most 5 drinks this month").
 */
export function standingText(habit: Habit, view: HabitView): string {
  const unit = habit.unit ? ` ${habit.unit}` : "";
  switch (view.standing) {
    case "none":
      return habit.archived ? "archived" : "not due today";
    case "paused":
    case "skipped":
    case "failed":
      return view.standing;
    case "limit": {
      // A daily limit counts the day; a weekly or monthly one counts its period so far.
      const had = view.used ?? view.value;
      const most = limit(habit);
      const when = !isPeriodic(habit) ? "today" : habit.cadence === "per_week" ? "this week" : "this month";
      if (most <= 0) return had > 0 ? `limit · over the line ${when}` : `limit · none ${when}`;
      const over = had > most ? ", over the line" : "";
      return `limit · ${round(had)} of at most ${round(most)}${unit} ${when}${over}`;
    }
    default: {
      if (habit.cadence === "per_week" || habit.cadence === "per_month") {
        const period = habit.cadence === "per_week" ? "week" : "month";
        return `${view.standing} · ${view.met} of ${habit.times ?? 1} this ${period}`;
      }
      if (habit.measure === "check") return view.standing;
      return `${view.standing} · ${round(view.value)} of ${round(habit.target ?? 0)}${unit} today`;
    }
  }
}

/** The January nudge in a line: what the new year still asks for. */
export function newYearLine(nudge: { year: number; review: boolean; goals: boolean }): string {
  const asks = [
    ...(nudge.review ? [`${nudge.year - 1} has no yearly review yet`] : []),
    ...(nudge.goals ? [`${nudge.year} has no year goals yet`] : []),
  ];
  return `New year: ${asks.join(" and ")}.`;
}

/** `4-day streak`, `2-week streak`: a streak counts the habit's periods. */
export function streakText(habit: Pick<Habit, "cadence">, streak: number): string {
  const period = habit.cadence === "per_week" ? "week" : habit.cadence === "per_month" ? "month" : "day";
  return `${streak}-${period} streak`;
}

/** A habit the way its card shows it: how often, where it stands, its streak and the goal it serves. */
export function habitLine(habit: Habit, view: HabitView): string {
  const parts = [`${habit.emoji ? `${habit.emoji} ` : ""}${habit.name}`, cadenceText(habit), standingText(habit, view)];
  if (view.streak > 0) parts.push(streakText(habit, view.streak));
  if (view.serves) parts.push(`serves ${view.serves}`);
  if (!habit.showOnToday) parts.push("not on Today");
  if (habit.remindAt) parts.push(`reminds at ${habit.remindAt}`);
  return `- ${parts.join(" · ")} (habit id ${habit.id})`;
}

/** `4500 CZK`. */
export function money(amount: number, currency: string): string {
  return `${round(amount)} ${currency}`;
}

/**
 * A want the way the Wants place shows it, with its reason, link, last price check and decision note
 * on the lines under it, so Claude can talk the decision through without another call. A need says so,
 * with the day it is needed by and "late" once that day passed.
 */
export function wantLine(want: Want, state: WantState, today: Day, names: Names): string {
  const parts = [want.title];
  if (want.kind === NEED) parts.push("need");
  if (want.price !== null) parts.push(money(want.price, want.currency));
  if (want.kind === NEED) {
    if (want.needBy !== null) parts.push(`needed by ${want.needBy}`);
    if (needLate(want, today)) parts.push("late");
    if (want.decision !== null) parts.push(want.decision);
  } else if (state === "cooling") {
    const left = daysBetween(today, want.coolsUntil);
    parts.push(`cooling, ready on ${want.coolsUntil} (${left} ${left === 1 ? "day" : "days"} to go)`);
  } else if (state === "ready") {
    parts.push(`ready to decide since ${want.coolsUntil}`);
  } else {
    parts.push(want.decision ?? "decided");
  }
  const area = want.areaId ? names.areas.get(want.areaId) : undefined;
  if (area) parts.push(`@${area.name}`);
  if (want.madeBy === "claude") parts.push("by Claude");
  const lines = [`- ${parts.join(" · ")} (want id ${want.id})`];
  if (want.reason.trim().length > 0) lines.push(`  Why: ${want.reason}`);
  if (want.link) lines.push(`  Link: ${want.link}`);
  if (want.checkedPrice !== null) {
    const note = want.checkedNote.trim().replace(/\s*\n\s*/g, " · ");
    lines.push(
      `  Last checked: ${money(want.checkedPrice, want.currency)} on ${want.checkedAt?.slice(0, 10)}` +
        (note.length > 0 ? ` · ${note}` : ""),
    );
  }
  if (want.decision !== null && want.decisionNote.trim().length > 0) lines.push(`  Note: ${want.decisionNote.trim()}`);
  return lines.join("\n");
}

/** A life goal's time left the way the apps word it: "10 years left", "8 months left", "Today", "Past its date". */
export function timeLeftText(left: TimeLeft): string {
  switch (left.unit) {
    case "years":
      return `${left.count} ${left.count === 1 ? "year" : "years"} left`;
    case "months":
      return `${left.count} ${left.count === 1 ? "month" : "months"} left`;
    case "days":
      return `${left.count} ${left.count === 1 ? "day" : "days"} left`;
    case "today":
      return "Today";
    case "past":
      return "Past its date";
  }
}

/** `no pictures`, `1 picture`, `3 pictures`. */
export function picturesText(count: number): string {
  return count === 0 ? "no pictures" : `${count} ${count === 1 ? "picture" : "pictures"}`;
}

/**
 * A life goal the way the Life goals place shows it: an open one with its time left, a closed one with
 * how it closed, then its by date, area, maker and pictures, and its why on the line under it.
 */
export function lifeGoalLine(goal: LifeGoal, left: TimeLeft | null, names: Names): string {
  const parts = [goal.title];
  if (goal.status !== "open") parts.push(goal.status);
  else if (left !== null) parts.push(timeLeftText(left));
  if (goal.by !== null) parts.push(`by date ${goal.by}`);
  const area = goal.areaId ? names.areas.get(goal.areaId) : undefined;
  if (area) parts.push(`@${area.name}`);
  if (goal.madeBy === "claude") parts.push("by Claude");
  parts.push(picturesText(goal.pictures));
  return [`- ${parts.join(" · ")} (life goal id ${goal.id})`, `  Why: ${goal.why}`].join("\n");
}

/** A number without a trailing .0, so "5 of 20 km" reads like a person wrote it. */
export function round(value: number): string {
  return Number.isInteger(value) ? value.toString() : value.toFixed(2).replace(/0+$/, "").replace(/\.$/, "");
}

function section(title: string, tasks: TaskItem[], names: Names, showDay = false): string[] {
  return tasks.length === 0 ? [] : [`${title}:`, ...tasks.map((task) => taskLine(task, names, { showDay }))];
}

/** Today's habits as the cards show them, how many are left, and how many due today are kept off Today. */
export interface TodayHabits {
  lines: string[];
  keptOff: number;
  /** The habits on Today still left; a limit is never left (contracts/vectors/habits.json, standings). */
  left: number;
  /** Whether Today shows its all done card: none left and at least one done. */
  allDone: boolean;
}

/** `1 habit left`, `3 habits left`. */
export function habitsLeft(left: number): string {
  return `${left} ${left === 1 ? "habit" : "habits"} left`;
}

/**
 * Today as the app shows it: top priorities, scheduled, more, and overdue, with the day's count and the
 * habits left, then the habits on Today with where each stands. Habits kept off Today are left out and
 * only counted.
 */
export function today(
  lists: PlanningLists,
  names: Names,
  habits: TodayHabits = { lines: [], keptOff: 0, left: 0, allDone: false },
): string {
  const sections = lists.todaySections;
  const body = [
    ...section("Top priorities", sections.priorities, names),
    ...section("Scheduled", sections.scheduled, names),
    ...section("More today", sections.more, names),
    ...section("Overdue (planned for an earlier day)", sections.overdue, names, true),
  ];
  const keptOff = habits.keptOff === 0 ? [] : [
    `${habits.keptOff} more ${habits.keptOff === 1 ? "habit is" : "habits are"} due today but kept off Today; ` +
    "get_habits lists every habit.",
  ];
  const left = habits.left === 0 ? "" : `, ${habitsLeft(habits.left)}`;
  const heading = habits.left > 0
    ? `Habits, ${habitsLeft(habits.left)}:`
    : habits.allDone
    ? "Habits, all done:"
    : "Habits:";
  return [
    `Today is ${longDay(lists.today)}: ${lists.summary.done} of ${lists.summary.total} done${left}.`,
    ...(body.length === 0 ? ["Nothing open is planned for today."] : body),
    ...(habits.lines.length === 0 ? [] : [heading, ...habits.lines]),
    ...keptOff,
  ].join("\n");
}

export function tomorrow(lists: PlanningLists, names: Names, day: Day): string {
  return lists.tomorrow.length === 0
    ? `Nothing is planned for tomorrow (${longDay(day)}) yet.`
    : [`Tomorrow, ${longDay(day)}:`, ...lists.tomorrow.map((task) => taskLine(task, names))].join("\n");
}

export function inbox(lists: PlanningLists, names: Names): string {
  return lists.inbox.length === 0 ? "The Inbox is empty: every open task has a day or an area." : [
    `Inbox, ${lists.inbox.length} to sort (no day and no area):`,
    ...lists.inbox.map((task) => taskLine(task, names)),
  ]
    .join("\n");
}

/** What a column is called on a board. */
export const COLUMN_NAMES: Record<string, string> = {
  backlog: "Backlog",
  todo: "To do",
  doing: "Doing",
  done: "Done",
  dropped: "Dropped",
};

/** A project with its status, area, repository and folder, and how much of it is still open. */
export function projectLine(project: ProjectItem, names: Names, items: TaskItem[]): string {
  const own = items.filter((item) => item.projectId === project.id && !item.deleted);
  const open = own.filter((item) => item.state === "open").length;
  const parts = [project.name, project.status];
  const area = project.areaId ? names.areas.get(project.areaId) : undefined;
  if (area) parts.push(`@${area.name}`);
  parts.push(own.length === 0 ? "no items yet" : `${open} open of ${own.length}`);
  if (project.repositoryUrl) parts.push(`repo ${project.repositoryUrl}`);
  if (project.localFolder) parts.push(`folder ${project.localFolder}`);
  return `- ${parts.join(" · ")} (project id ${project.id})`;
}

/** One project's board: its columns (the four, then Dropped) with their items in order, its milestones and its notes. */
export function board(
  project: ProjectItem,
  columns: Column[],
  names: Names,
  milestones: ProjectMilestone[],
  madeBy: string = "all",
): string {
  const head = [project.name, project.status];
  const area = project.areaId ? names.areas.get(project.areaId) : undefined;
  if (area) head.push(`@${area.name}`);
  if (project.repositoryUrl) head.push(`repo ${project.repositoryUrl}`);
  if (project.localFolder) head.push(`folder ${project.localFolder}`);
  const lines = [`${head.join(" · ")} (project id ${project.id})`];
  if (project.description.trim().length > 0) lines.push(project.description.trim());
  if (madeBy === "owner") lines.push("Only the items the owner made.");
  if (madeBy === "claude") lines.push("Only the items Claude made.");
  if (milestones.length > 0) {
    lines.push(
      "Milestones:",
      ...milestones.map((milestone) => `- ${milestone.name} (milestone id ${milestone.id})`),
    );
  }
  for (const { column, items } of columns) {
    const name = COLUMN_NAMES[column] ?? column;
    lines.push(
      items.length === 0
        ? `${name}: nothing.`
        : [`${name} (${items.length}):`, ...items.map((item) => itemLine(item, names, milestones))].join("\n"),
    );
  }
  if (project.notes.trim().length > 0) lines.push("Notes:", project.notes.trim());
  return lines.join("\n");
}

/** A board item: its task line with the milestone it belongs to, and no project name to repeat. */
export function itemLine(item: TaskItem, names: Names, milestones: ProjectMilestone[]): string {
  const milestone = milestones.find((one) => one.id === item.milestoneId);
  return taskLine(item, names, { showDay: true, showProject: false, milestone: milestone?.name, showMaker: true });
}

/** One task in full: its line, notes, steps and reminders. */
export function taskDetail(
  task: TaskItem,
  names: Names,
  steps: Step[],
  reminders: Reminder[],
  milestones: ProjectMilestone[] = [],
): string {
  const lines = [taskLine(task, names, { showDay: true, showMaker: true })];
  const column = shownColumn(task);
  if (column) {
    const milestone = milestones.find((one) => one.id === task.milestoneId);
    lines.push(
      `On the board in ${COLUMN_NAMES[column] ?? column}` +
        (milestone === undefined ? "." : `, milestone ${milestone.name}.`),
    );
  }
  if (task.deleted) lines.push("This task is deleted; restore_task brings it back.");
  if (task.state === "done" && task.completedAt) lines.push(`Completed ${task.completedAt}.`);
  if (task.notes.trim().length > 0) lines.push("Notes:", task.notes);
  if (steps.length > 0) {
    lines.push("Steps:", ...steps.map((step) => `- ${step.done ? "[x]" : "[ ]"} ${step.title} (step id ${step.id})`));
  }
  if (reminders.length > 0) {
    lines.push(
      "Reminders:",
      ...reminders.map((reminder) =>
        `- ${reminder.at ? `at ${reminder.at}` : `${reminder.minutesBefore} min before`}${
          reminder.important ? ", important" : ""
        }, ${reminder.state} (reminder id ${reminder.id})`
      ),
    );
  }
  return lines.join("\n");
}
