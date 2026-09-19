import type { Area, Reminder, Step, Tag } from "../planner/planner.ts";
import { type Day, weekday } from "../rules/day.ts";
import type { PlanningLists } from "../rules/listRules.ts";
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

/** Everything a task line can show besides the task: its area's name and its tags' names. */
export interface Names {
  areas: Map<string, Area>;
  tags: Map<string, Tag>;
  links: Map<string, string[]>;
}

export function names(areas: Area[], tags: Tag[], links: Map<string, string[]>): Names {
  return {
    areas: new Map(areas.map((area) => [area.id, area])),
    tags: new Map(tags.map((tag) => [tag.id, tag])),
    links,
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
export function taskLine(task: TaskItem, names: Names, options: { showDay?: boolean } = {}): string {
  const box = task.state === "done" ? "[x]" : task.state === "dropped" ? "[-]" : "[ ]";
  const parts = [`${box} ${task.title}`];
  if (task.topPriority) parts.push("top priority");
  if (options.showDay && task.plannedDate) parts.push(task.plannedDate);
  if (task.plannedTime) parts.push(task.plannedTime);
  const area = task.areaId ? names.areas.get(task.areaId) : undefined;
  if (area) parts.push(`@${area.name}`);
  const tags = (names.links.get(task.id) ?? []).map((id) => names.tags.get(id)).filter((tag) => tag !== undefined);
  if (tags.length > 0) parts.push(tags.map((tag) => `#${tag!.name}`).join(" "));
  if (task.recurrence) parts.push(`repeats ${task.recurrence}`);
  if (task.deadline) parts.push(`due ${task.deadline}`);
  return `- ${parts.join(" · ")} (id ${task.id})`;
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

/** One task in full: its line, notes, steps and reminders. */
export function taskDetail(task: TaskItem, names: Names, steps: Step[], reminders: Reminder[]): string {
  const lines = [taskLine(task, names, { showDay: true })];
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
