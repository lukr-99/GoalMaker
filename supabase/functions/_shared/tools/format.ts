import type { Area, Habit, Reminder, Step, Tag } from "../planner/planner.ts";
import { type Day, weekday } from "../rules/day.ts";
import type { GoalItem, GoalProgress } from "../rules/goals.ts";
import type { HabitPeriodState } from "../rules/habits.ts";
import type { PlanningLists } from "../rules/listRules.ts";
import type { Column, ProjectItem, ProjectMilestone } from "../rules/projects.ts";
import type { TaskItem } from "../rules/task.ts";

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
  options: { showDay?: boolean; showProject?: boolean; milestone?: string } = {},
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
  return `- ${parts.join(" · ")} (id ${task.id})`;
}

/** A goal with where it stands, the way the goals screen reads out loud. */
export function goalLine(goal: GoalItem, progress: GoalProgress, period: string): string {
  const parts = [`${goal.emoji ? `${goal.emoji} ` : ""}${goal.title}`, `${goal.horizon} of ${period}`];
  if (goal.mode === "number") {
    parts.push(`${round(progress.value)} of ${round(progress.target)}${goal.unit ? ` ${goal.unit}` : ""}`);
  } else if (goal.mode === "tasks") {
    parts.push(`${round(progress.value)} of ${round(progress.target)} tasks done`);
  }
  parts.push(goal.status === "open" ? `${Math.round(progress.fraction * 100)}%` : goal.status);
  if (goal.status === "open" && progress.hit) parts.push("reached, waiting to be marked done");
  return `- ${parts.join(" · ")} (goal id ${goal.id})`;
}

/** A habit with what today asks of it and the run it is on. */
export function habitLine(
  habit: Habit,
  state: HabitPeriodState,
  done: number,
  streak: number,
): string {
  const asks = habit.cadence === "per_week"
    ? `${habit.times} times a week`
    : habit.cadence === "per_month"
    ? `${habit.times} times a month`
    : habit.cadence === "weekdays"
    ? "on chosen weekdays"
    : "every day";
  const parts = [`${habit.emoji ? `${habit.emoji} ` : ""}${habit.name}`, asks];
  const limit = habit.direction === "at_most";
  if (habit.measure !== "check") {
    const of = limit ? "of at most" : "of";
    parts.push(`${round(done)} ${of} ${round(habit.target ?? 0)}${habit.unit ? ` ${habit.unit}` : ""} this period`);
  } else if (limit) {
    parts.push("not once");
  }
  parts.push(state === "met" ? "done" : state === "none" ? "not due" : state);
  if (streak > 0) parts.push(`streak ${streak}`);
  return `- ${parts.join(" · ")} (habit id ${habit.id})`;
}

/** A number without a trailing .0, so "5 of 20 km" reads like a person wrote it. */
export function round(value: number): string {
  return Number.isInteger(value) ? value.toString() : value.toFixed(2).replace(/0+$/, "").replace(/\.$/, "");
}

function section(title: string, tasks: TaskItem[], names: Names, showDay = false): string[] {
  return tasks.length === 0 ? [] : [`${title}:`, ...tasks.map((task) => taskLine(task, names, { showDay }))];
}

/** Today as the app shows it: top priorities, scheduled, more, and overdue, with the day's count. */
export function today(lists: PlanningLists, names: Names): string {
  const sections = lists.todaySections;
  const body = [
    ...section("Top priorities", sections.priorities, names),
    ...section("Scheduled", sections.scheduled, names),
    ...section("More today", sections.more, names),
    ...section("Overdue (planned for an earlier day)", sections.overdue, names, true),
  ];
  return [
    `Today is ${longDay(lists.today)}: ${lists.summary.done} of ${lists.summary.total} done.`,
    ...(body.length === 0 ? ["Nothing open is planned for today."] : body),
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

/** One project's board: its four columns with their items in order, its milestones and its notes. */
export function board(
  project: ProjectItem,
  columns: Column[],
  names: Names,
  milestones: ProjectMilestone[],
): string {
  const head = [project.name, project.status];
  const area = project.areaId ? names.areas.get(project.areaId) : undefined;
  if (area) head.push(`@${area.name}`);
  if (project.repositoryUrl) head.push(`repo ${project.repositoryUrl}`);
  if (project.localFolder) head.push(`folder ${project.localFolder}`);
  const lines = [`${head.join(" · ")} (project id ${project.id})`];
  if (project.description.trim().length > 0) lines.push(project.description.trim());
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
  return taskLine(item, names, { showDay: true, showProject: false, milestone: milestone?.name });
}

/** One task in full: its line, notes, steps and reminders. */
export function taskDetail(
  task: TaskItem,
  names: Names,
  steps: Step[],
  reminders: Reminder[],
  milestones: ProjectMilestone[] = [],
): string {
  const lines = [taskLine(task, names, { showDay: true })];
  if (task.boardColumn) {
    const milestone = milestones.find((one) => one.id === task.milestoneId);
    lines.push(
      `On the board in ${COLUMN_NAMES[task.boardColumn] ?? task.boardColumn}` +
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
