import type { Db } from "../owner.ts";
import { addDays, type Day, isDay, localNow } from "../rules/day.ts";
import { nameBasedUuid } from "../rules/nameBasedUuid.ts";
import { moves } from "../rules/planRules.ts";
import { seriesOf, successorId, tagLinkId } from "../rules/occurrences.ts";
import { DEFAULT_START_HOUR, planningDay } from "../rules/planningDay.ts";
import { nextOccurrence, parseRecurrence } from "../rules/recurrence.ts";
import { periodStart, reviewId, type ReviewKind } from "../rules/reviews.ts";
import { compareText, type TaskItem, type TaskState } from "../rules/task.ts";
import {
  canServe,
  type GoalEntryItem,
  type GoalHorizon,
  type GoalItem,
  type GoalMode,
  type GoalStatus,
  periodEnd,
  periodStart as goalPeriodStart,
} from "../rules/goals.ts";
import {
  checkinId,
  type HabitCadence,
  type HabitCheckin,
  type HabitDirection,
  type HabitItem,
  type HabitMeasure,
} from "../rules/habits.ts";
import {
  type BoardColumn,
  columnFor,
  COLUMNS,
  finishedIn,
  folderKey,
  type ItemType,
  matchProject,
  moved,
  PRIORITIES,
  type Priority,
  type ProjectItem,
  type ProjectMilestone,
  type ProjectStatus,
  repositoryKey,
} from "../rules/projects.ts";
import { AREA_COLORS, colorForNewArea } from "./palette.ts";

export interface Area {
  id: string;
  name: string;
  color: string;
  emoji: string | null;
  archived: boolean;
}

export interface Tag {
  id: string;
  name: string;
}

export interface Step {
  id: string;
  title: string;
  done: boolean;
}

export interface Reminder {
  id: string;
  /** Local time in the owner's zone, `2026-09-18 17:00`, for a fixed reminder. */
  at: string | null;
  minutesBefore: number | null;
  important: boolean;
  state: string;
}

/** What a new task or an edit says. Undefined leaves a field alone; null clears it. */
export interface TaskFields {
  title?: string;
  notes?: string;
  day?: Day | null;
  time?: string | null;
  deadline?: Day | null;
  area?: string | null;
  tags?: string[];
  topPriority?: boolean;
  repeat?: string | null;
  /** The project the task is an item of, and what the board says about it (docs/projects.md). */
  projectId?: string | null;
  itemType?: string;
  priority?: string;
  milestoneId?: string | null;
  /**
   * Who made a new task (supabase/migrations/0015_task_made_by.sql). Left out, the server takes the
   * actor, which through the connector is Claude; an edit never changes it.
   */
  madeBy?: "owner" | "claude";
}

/** What a new goal or an edit says. Undefined leaves a field alone; null clears it. */
export interface GoalFields {
  title?: string;
  /** The goal this one sits under, by id; empty for none. */
  parent?: string | null;
  emoji?: string | null;
  horizon?: GoalHorizon;
  /** Any day in the period the goal belongs to; its first day is worked out from the horizon. */
  day?: Day;
  mode?: GoalMode;
  target?: number | null;
  unit?: string | null;
}

/** A habit as the connector shows it: the rules' habit with what it is called. */
/** An area's own fields; what is left out stays as it was. */
export interface AreaFields {
  name?: string;
  color?: string;
  emoji?: string | null;
  archived?: boolean;
}

/** A habit's own fields; what is left out stays as it was. */
export interface HabitFields {
  name?: string;
  emoji?: string | null;
  cadence?: HabitCadence;
  weekdays?: number | null;
  times?: number | null;
  measure?: HabitMeasure;
  target?: number | null;
  direction?: HabitDirection;
  unit?: string | null;
  goal?: string | null;
  startsOn?: Day;
  archived?: boolean;
}

/** The settings every planning day is worked out from. */
export interface Settings {
  displayName: string | null;
  timeZone: string;
  dayStartHour: number;
}

/** One entry of the activity log, as the apps' Activity screen reads it. */
export interface Change {
  id: string;
  entity: string;
  entityId: string;
  action: string;
  actor: string;
  at: string;
  undone: boolean;
  label: string | null;
}

/** What a new project is made of; what is left out takes the column's default. */
export interface ProjectFields {
  name?: string;
  description?: string;
  area?: string | null;
  status?: ProjectStatus;
  repository?: string | null;
  folder?: string | null;
  notes?: string;
}

export interface Habit extends HabitItem {
  name: string;
  emoji: string | null;
  archived: boolean;
}

/** A habit's rest, with the habit it belongs to. */
export interface Pause {
  id: string;
  habitId: string;
  from: Day;
  until: Day | null;
  deleted: boolean;
}

/** A day's check-in, with the habit it belongs to. */
export type Checkin = HabitCheckin & { habitId: string };

export interface Review {
  kind: ReviewKind;
  periodStart: Day;
  mood: number | null;
  energy: number | null;
  summary: string;
  reflections: Reflection[];
}

/** One prompt a review asked and what was written back (supabase/migrations/0011). */
export interface Reflection {
  prompt: string;
  answer: string;
}

export class PlannerError extends Error {}

const RITUAL_NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";
const MAX_TITLE = 500;
const MAX_NOTES = 20_000;
const MAX_AREA = 60;
const MAX_TAG = 40;
const MAX_STEP = 300;
const MAX_GOAL_TITLE = 200;
const MAX_EMOJI = 16;
const MAX_UNIT = 20;
const MAX_PROJECT_NAME = 120;
const MAX_DESCRIPTION = 2_000;
const MAX_LOCATION = 500;
const MAX_HABIT_NAME = 100;
const MAX_DISPLAY_NAME = 80;
const MAX_TIME_ZONE = 64;
// Further off than any pause reaches, so an open ended one compares like every other.
const FAR_OFF = "9999-12-31";
const TIMESTAMP = `'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'`;

/**
 * GoalMaker's planning data as one owner sees it, read and changed through that owner's row security
 * (a `Db` from `asOwner`). It does what the apps' TaskList, AreaList and TagList do, with the same
 * rules, so a change made here looks the same on every device after a sync.
 */
export class Planner {
  private profile: { timeZone: string; startHour: number } | null = null;

  constructor(private readonly db: Db, private readonly clock: () => Date = () => new Date()) {}

  /** The owner's local date-time and planning day, from the profile's time zone and day start. */
  async now(): Promise<{ local: string; today: Day; timeZone: string }> {
    if (this.profile === null) {
      const rows = await this.db`select time_zone, day_rollover_hour from public.profiles`;
      this.profile = rows.length === 0
        ? { timeZone: "UTC", startHour: DEFAULT_START_HOUR }
        : { timeZone: rows[0].time_zone, startHour: rows[0].day_rollover_hour };
    }
    const local = localNow(this.profile.timeZone, this.clock());
    return { local, today: planningDay(local, this.profile.startHour), timeZone: this.profile.timeZone };
  }

  /** The planning day a server timestamp (a completion, say) falls on for the owner. */
  async dayOf(timestamp: string): Promise<Day> {
    await this.now();
    return planningDay(localNow(this.profile!.timeZone, new Date(timestamp)), this.profile!.startHour);
  }

  /** The done tasks among `tasks` completed on a planning day from `first` to `last`, in the order they were done. */
  async completedBetween(tasks: TaskItem[], first: Day, last: Day): Promise<TaskItem[]> {
    const done: TaskItem[] = [];
    for (const task of tasks.filter((task) => task.state === "done" && task.completedAt !== null)) {
      const day = await this.dayOf(task.completedAt!);
      if (day >= first && day <= last) done.push(task);
    }
    return done.sort((a, b) => compareText(a.completedAt!, b.completedAt!));
  }

  /** `today`, `tomorrow` or an ISO date, as a day; null for anything else. */
  async day(text: string): Promise<Day | null> {
    const words = text.trim().toLowerCase();
    const { today } = await this.now();
    if (words === "today") return today;
    if (words === "tomorrow") return addDays(today, 1);
    return isDay(words) ? words : null;
  }

  async tasks(): Promise<TaskItem[]> {
    return (await this.db`${this.taskColumns()} where deleted_at is null`).map(toTask);
  }

  /** The task with this id, deleted or not; null when there is none (or it is someone else's). */
  async task(id: string): Promise<TaskItem | null> {
    if (!isUuid(id)) return null;
    const rows = await this.db`${this.taskColumns()} where id = ${id}`;
    return rows.length === 0 ? null : toTask(rows[0]);
  }

  async areas(): Promise<Area[]> {
    const rows = await this.db`
      select id::text, name, color, emoji, archived_at is not null as archived
      from public.areas where deleted_at is null order by position, lower(name)`;
    return rows.map((row) => ({
      id: row.id,
      name: row.name,
      color: row.color,
      emoji: row.emoji,
      archived: row.archived,
    }));
  }

  async tags(): Promise<Tag[]> {
    const rows = await this.db`select id::text, name from public.tags where deleted_at is null order by created_at, id`;
    return rows.map((row) => ({ id: row.id, name: row.name }));
  }

  /** Each task's tag ids. */
  async tagLinks(): Promise<Map<string, string[]>> {
    const rows = await this.db`
      select task_id::text, tag_id::text from public.task_tags where deleted_at is null order by created_at, id`;
    const links = new Map<string, string[]>();
    for (const row of rows) links.set(row.task_id, [...(links.get(row.task_id) ?? []), row.tag_id]);
    return links;
  }

  async steps(taskId: string): Promise<Step[]> {
    const rows = await this.db`
      select id::text, title, done from public.task_steps
      where task_id = ${taskId} and deleted_at is null order by position, created_at, id`;
    return rows.map((row) => ({ id: row.id, title: row.title, done: row.done }));
  }

  async reminders(taskId: string): Promise<Reminder[]> {
    const { timeZone } = await this.now();
    const rows = await this.db`
      select id::text, to_char(fire_at at time zone ${timeZone}, 'YYYY-MM-DD HH24:MI') as at,
             offset_minutes, important, state
      from public.reminders where task_id = ${taskId} and deleted_at is null order by created_at, id`;
    return rows.map((row) => ({
      id: row.id,
      at: row.at,
      minutesBefore: row.offset_minutes === null ? null : -row.offset_minutes,
      important: row.important,
      state: row.state,
    }));
  }

  /** Adds a task the way the composer does: a new area or tags it names, and the tag links. */
  async addTask(fields: TaskFields): Promise<TaskItem> {
    const title = cleanTitle(fields.title ?? "");
    const repeat = cleanRepeat(fields.repeat ?? null);
    const day = fields.day ?? null;
    const time = cleanTime(fields.time ?? null, day);
    const areaId = fields.area ? (await this.findOrCreateArea(fields.area)).id : null;
    const projectId = fields.projectId ?? null;
    const itemType = cleanItemType(fields.itemType);
    const id = crypto.randomUUID();
    await this.db`
      insert into public.tasks
        (id, title, notes, top_priority, status, position, planned_date, planned_time, deadline, area_id, recurrence,
         series_id, project_id, item_type, board_column, priority, milestone_id, made_by)
      values
        (${id}, ${title}, ${cleanNotes(fields.notes ?? "")}, ${fields.topPriority ?? false}, 'open', 0, ${day}, ${time},
         ${fields.deadline ?? null}, ${areaId}, ${repeat}, ${repeat === null ? null : id},
         ${projectId}, ${itemType}, ${projectId === null ? null : columnFor(itemType)},
         ${cleanPriority(fields.priority)}, ${projectId === null ? null : fields.milestoneId ?? null},
         ${fields.madeBy ?? null})`;
    for (const tag of fields.tags ?? []) await this.link(id, (await this.findOrCreateTag(tag)).id);
    return (await this.task(id))!;
  }

  /** Changes what `fields` names and leaves the rest. */
  async updateTask(id: string, fields: TaskFields): Promise<TaskItem> {
    const task = await this.live(id);
    const day = fields.day === undefined ? task.plannedDate : fields.day;
    const time = fields.time === undefined ? (day === null ? null : task.plannedTime) : cleanTime(fields.time, day);
    const repeat = fields.repeat === undefined ? task.recurrence : cleanRepeat(fields.repeat);
    const areaId = fields.area === undefined
      ? task.areaId
      : fields.area === null
      ? null
      : (await this.findOrCreateArea(fields.area)).id;
    const projectId = fields.projectId === undefined ? task.projectId ?? null : fields.projectId;
    const itemType = fields.itemType === undefined ? cleanItemType(task.itemType) : cleanItemType(fields.itemType);
    // A task out of a project keeps neither a column nor a milestone; one that joins a project lands
    // in the column its type calls for, and one that stays put keeps where it sits (docs/projects.md).
    const same = projectId !== null && projectId === (task.projectId ?? null);
    const column = projectId === null ? null : same && task.boardColumn ? task.boardColumn : columnFor(itemType);
    const milestoneId = projectId === null
      ? null
      : fields.milestoneId !== undefined
      ? fields.milestoneId
      : same
      ? task.milestoneId ?? null
      : null;
    await this.db`
      update public.tasks set
        title = ${fields.title === undefined ? task.title : cleanTitle(fields.title)},
        notes = ${fields.notes === undefined ? task.notes : cleanNotes(fields.notes)},
        planned_date = ${day},
        moved_count = ${moves(task.plannedDate, day, task.movedCount ?? 0)},
        planned_time = ${time},
        deadline = ${fields.deadline === undefined ? task.deadline : fields.deadline},
        area_id = ${areaId},
        top_priority = ${fields.topPriority ?? task.topPriority},
        recurrence = ${repeat},
        series_id = ${repeat === null ? task.seriesId : task.seriesId ?? task.id},
        project_id = ${projectId},
        item_type = ${itemType},
        board_column = ${column},
        priority = ${fields.priority === undefined ? cleanPriority(task.priority) : cleanPriority(fields.priority)},
        milestone_id = ${milestoneId}
      where id = ${id}`;
    if (fields.tags !== undefined) await this.setTags(id, fields.tags);
    return (await this.task(id))!;
  }

  /** Done or dropped; a repeating task moves on to its next occurrence (docs/repeating.md). */
  async finish(id: string, status: "done" | "dropped"): Promise<{ task: TaskItem; next: TaskItem | null }> {
    const task = await this.live(id);
    await this.db`
      update public.tasks set status = ${status}, completed_at = ${status === "done" ? this.db`now()` : null},
        board_column = ${columnAfter(task, status)}
      where id = ${id}`;
    const next = task.state === "open" ? await this.moveOn({ ...task, state: status }) : null;
    return { task: (await this.task(id))!, next };
  }

  /** Open again, planned for `day` when given (null: the Inbox); a finished occurrence takes back its next one. */
  async reopen(id: string, day?: Day | null): Promise<TaskItem> {
    const task = await this.live(id);
    const planned = day === undefined ? task.plannedDate : day;
    await this.db`
      update public.tasks set status = 'open', completed_at = null, planned_date = ${planned},
        moved_count = ${moves(task.plannedDate, planned, task.movedCount ?? 0)},
        planned_time = ${planned === null ? null : task.plannedTime},
        board_column = ${columnAfter(task, "open")}
      where id = ${id}`;
    if (task.state !== "open") {
      const next = await this.task(await successorId(id));
      if (next !== null && !next.deleted && next.state === "open") {
        await this.db`update public.tasks set deleted_at = now() where id = ${next.id}`;
      }
    }
    return (await this.task(id))!;
  }

  async delete(id: string): Promise<TaskItem> {
    await this.live(id);
    await this.db`update public.tasks set deleted_at = now() where id = ${id}`;
    return (await this.task(id))!;
  }

  async restore(id: string): Promise<TaskItem> {
    const task = await this.task(id);
    if (task === null) throw new PlannerError(`No task with id ${id}.`);
    await this.db`update public.tasks set deleted_at = null where id = ${id}`;
    return (await this.task(id))!;
  }

  /** A reminder at a local time in the owner's zone, or `minutesBefore` the task's planned time. */
  async addReminder(
    taskId: string,
    reminder: { at?: string; minutesBefore?: number; important?: boolean },
  ): Promise<Reminder> {
    const task = await this.live(taskId);
    const { timeZone } = await this.now();
    const id = crypto.randomUUID();
    if (reminder.minutesBefore !== undefined) {
      if (task.plannedDate === null || task.plannedTime === null) {
        throw new PlannerError("The task needs a day and a time before a reminder can count back from it.");
      }
      if (!Number.isInteger(reminder.minutesBefore) || reminder.minutesBefore < 0 || reminder.minutesBefore > 525_600) {
        throw new PlannerError("minutes_before must be a whole number from 0 to 525600.");
      }
      await this.db`
        insert into public.reminders (id, task_id, offset_minutes, important)
        values (${id}, ${taskId}, ${-reminder.minutesBefore}, ${reminder.important ?? false})`;
    } else {
      const at = cleanLocalTime(reminder.at);
      await this.db`
        insert into public.reminders (id, task_id, fire_at, important)
        values (${id}, ${taskId}, (${at}::timestamp at time zone ${timeZone}), ${reminder.important ?? false})`;
    }
    return (await this.reminders(taskId)).find((item) => item.id === id)!;
  }

  async removeReminder(id: string): Promise<void> {
    const rows = await this.db`
      update public.reminders set deleted_at = now()
      where id = ${isUuid(id) ? id : null} and deleted_at is null returning id`;
    if (rows.length === 0) throw new PlannerError(`No reminder with id ${id}.`);
  }

  async addStep(taskId: string, title: string): Promise<Step> {
    await this.live(taskId);
    const text = title.trim();
    if (text.length === 0 || text.length > MAX_STEP) {
      throw new PlannerError(`A step needs 1 to ${MAX_STEP} characters.`);
    }
    const id = crypto.randomUUID();
    const position = (await this.steps(taskId)).length;
    await this
      .db`insert into public.task_steps (id, task_id, title, position) values (${id}, ${taskId}, ${text}, ${position})`;
    return { id, title: text, done: false };
  }

  async setStepDone(id: string, done: boolean): Promise<Step> {
    const rows = await this.db`
      update public.task_steps set done = ${done}
      where id = ${isUuid(id) ? id : null} and deleted_at is null returning id::text, title, done`;
    if (rows.length === 0) throw new PlannerError(`No step with id ${id}.`);
    return { id: rows[0].id, title: rows[0].title, done: rows[0].done };
  }

  /** Records a ritual as done or skipped for a planning day, under the id every device gives it. */
  async recordRitual(ritual: "plan_tomorrow" | "weekly_review" | "monthly_review", day: Day, skipped = false) {
    const owner = (await this.db`select auth.uid()::text as id`)[0].id as string;
    const id = await nameBasedUuid(RITUAL_NAMESPACE, `${owner.toLowerCase()}/${ritual}/${day}`);
    await this.db`
      insert into public.ritual_runs (id, ritual, day, outcome)
      values (${id}, ${ritual}, ${day}, ${skipped ? "skipped" : "done"})
      on conflict (id) do update set outcome = excluded.outcome, deleted_at = null`;
  }

  /** Saves a review's summary (and mood and energy, when given) for the period `day` falls in. */
  async saveReview(
    kind: ReviewKind,
    day: Day,
    review: { summary: string; mood?: number; energy?: number; reflections?: Reflection[] },
  ): Promise<Review> {
    const summary = review.summary.trim();
    if (summary.length === 0 || summary.length > MAX_NOTES) {
      throw new PlannerError(`A summary needs 1 to ${MAX_NOTES} characters.`);
    }
    for (const [name, value] of [["mood", review.mood], ["energy", review.energy]] as const) {
      if (value !== undefined && (!Number.isInteger(value) || value < 1 || value > 5)) {
        throw new PlannerError(`${name} is a whole number from 1 to 5.`);
      }
    }
    if (review.reflections !== undefined && review.reflections.length > 20) {
      throw new PlannerError("A review holds at most 20 reflections.");
    }
    const written = review.reflections?.map((one) => {
      const prompt = one.prompt.trim();
      const answer = one.answer.trim();
      if (prompt.length === 0 || prompt.length > 60) {
        throw new PlannerError("A reflection needs the prompt it answers.");
      }
      if (answer.length > 4_000) throw new PlannerError("A reflection's answer runs to 4000 characters.");
      return { prompt, answer };
    });
    // A plain string parameter would reach Postgres as a JSON string rather than the array it holds.
    const reflections = written === undefined ? null : this.db.json(written);
    const start = periodStart(kind, day);
    const owner = (await this.db`select auth.uid()::text as id`)[0].id as string;
    const id = await reviewId(owner, kind, start);
    await this.db`
      insert into public.reviews (id, kind, period_start, summary, mood, energy, reflections)
      values (${id}, ${kind}, ${start}, ${summary}, ${review.mood ?? null}, ${review.energy ?? null},
              coalesce(${reflections}::jsonb, '[]'::jsonb))
      on conflict (id) do update set
        summary = excluded.summary,
        mood = coalesce(excluded.mood, public.reviews.mood),
        energy = coalesce(excluded.energy, public.reviews.energy),
        reflections = coalesce(${reflections}::jsonb, public.reviews.reflections),
        deleted_at = null`;
    return (await this.reviews(kind, 1, start))[0];
  }

  /** The latest reviews, newest period first; of one kind, and from one period start, when given. */
  async reviews(kind: ReviewKind | null, limit: number, start: Day | null = null): Promise<Review[]> {
    const rows = await this.db`
      select kind, period_start::text, mood, energy, summary, reflections from public.reviews
      where deleted_at is null
        and (${kind}::text is null or kind = ${kind})
        and (${start}::date is null or period_start = ${start}::date)
      order by period_start desc, kind limit ${limit}`;
    return rows.map((row) => ({
      kind: row.kind,
      periodStart: row.period_start,
      mood: row.mood,
      energy: row.energy,
      summary: row.summary,
      reflections: row.reflections ?? [],
    }));
  }

  /** The area with this name (ignoring case), brought back if archived, or a new one with the next free color. */
  async findOrCreateArea(name: string): Promise<Area> {
    const trimmed = name.trim().replace(/^@/, "").slice(0, MAX_AREA);
    if (trimmed.length === 0) throw new PlannerError("An area needs a name.");
    const areas = await this.areas();
    const found = areas.find((area) => area.name.trim().toLowerCase() === trimmed.toLowerCase());
    if (found) {
      if (found.archived) await this.db`update public.areas set archived_at = null where id = ${found.id}`;
      return { ...found, archived: false };
    }
    const color = colorForNewArea(areas.map((area) => area.color), areas.length);
    const id = crypto.randomUUID();
    await this.db`
      insert into public.areas (id, name, color, position) values (${id}, ${trimmed}, ${color}, ${areas.length})`;
    return { id, name: trimmed, color, emoji: null, archived: false };
  }

  /** The tag with this name (ignoring case), or a new one. */
  async findOrCreateTag(name: string): Promise<Tag> {
    const trimmed = name.trim().replace(/^#/, "").slice(0, MAX_TAG);
    if (trimmed.length === 0) throw new PlannerError("A tag needs a name.");
    const found = (await this.tags()).find((tag) => tag.name.trim().toLowerCase() === trimmed.toLowerCase());
    if (found) return found;
    const id = crypto.randomUUID();
    await this.db`insert into public.tags (id, name) values (${id}, ${trimmed})`;
    return { id, name: trimmed };
  }

  /** Every goal that is not deleted, newest period first, in the order the apps keep them. */
  async goals(): Promise<GoalItem[]> {
    const rows = await this.db`
      select id::text, title, emoji, horizon, period_start::text, parent_id::text, progress_mode, target, unit, status
      from public.goals where deleted_at is null
      order by period_start desc, position, created_at, id`;
    return rows.map(toGoal);
  }

  /** The amounts logged by hand on each numeric goal, by goal id. */
  async goalEntries(): Promise<Map<string, GoalEntryItem[]>> {
    const rows = await this.db`
      select goal_id::text, amount from public.goal_entries where deleted_at is null order by day, created_at, id`;
    const entries = new Map<string, GoalEntryItem[]>();
    for (const row of rows) {
      entries.set(row.goal_id, [...(entries.get(row.goal_id) ?? []), { amount: row.amount, deleted: false }]);
    }
    return entries;
  }

  /** The goal with this id; it has to be the owner's and not deleted. */
  async goal(id: string): Promise<GoalItem> {
    if (!isUuid(id)) throw new PlannerError(`No goal with id ${id}.`);
    const rows = await this.db`
      select id::text, title, emoji, horizon, period_start::text, parent_id::text, progress_mode, target, unit, status
      from public.goals where id = ${id} and deleted_at is null`;
    if (rows.length === 0) throw new PlannerError(`No goal with id ${id}.`);
    return toGoal(rows[0]);
  }

  /** Adds a goal to the period the day falls in, the way the goals screen does. */
  async addGoal(fields: GoalFields): Promise<GoalItem> {
    const title = (fields.title ?? "").trim().slice(0, MAX_GOAL_TITLE);
    if (title.length === 0) throw new PlannerError("A goal needs a title.");
    const horizon = fields.horizon ?? "week";
    const start = goalPeriodStart(horizon, fields.day ?? (await this.now()).today);
    const mode = fields.mode ?? (fields.target === undefined || fields.target === null ? "done" : "number");
    const target = mode === "number" ? fields.target ?? null : null;
    if (mode === "number" && (target === null || !(target > 0))) {
      throw new PlannerError("A goal that counts a number needs a target above zero.");
    }
    const position = (await this.goals())
      .filter((goal) => goal.horizon === horizon && goal.periodStart === start).length;
    const id = crypto.randomUUID();
    await this.db`
      insert into public.goals (id, title, emoji, horizon, period_start, parent_id, progress_mode, target, unit,
                                position)
      values (${id}, ${title}, ${clip(fields.emoji ?? null, MAX_EMOJI)}, ${horizon}, ${start},
              ${await this.parentGoal(fields.parent, { id: null, horizon, periodStart: start })}, ${mode}, ${target},
              ${mode === "number" ? clip(fields.unit ?? null, MAX_UNIT) : null}, ${position})`;
    return await this.goal(id);
  }

  /** Changes a goal's title, emoji, target or unit; what is left out stays as it was. */
  async updateGoal(id: string, fields: GoalFields): Promise<GoalItem> {
    const goal = await this.goal(id);
    const title = fields.title === undefined ? goal.title : fields.title.trim().slice(0, MAX_GOAL_TITLE);
    if (title.length === 0) throw new PlannerError("A goal needs a title.");
    const target = fields.target === undefined ? goal.target : fields.target;
    if (goal.mode === "number" && (target === null || !(target > 0))) {
      throw new PlannerError("A goal that counts a number needs a target above zero.");
    }
    const numeric = goal.mode === "number";
    await this.db`
      update public.goals set
        title = ${title},
        emoji = ${fields.emoji === undefined ? goal.emoji : clip(fields.emoji, MAX_EMOJI)},
        parent_id = ${
      fields.parent === undefined
        ? goal.parentId
        : await this.parentGoal(fields.parent, { id, horizon: goal.horizon, periodStart: goal.periodStart })
    },
        target = ${numeric ? target : null},
        unit = ${numeric ? (fields.unit === undefined ? goal.unit : clip(fields.unit, MAX_UNIT)) : null}
      where id = ${id}`;
    return await this.goal(id);
  }

  /** Marks a goal open, done or dropped, stamping when it was hit. */
  async setGoalStatus(id: string, status: GoalStatus): Promise<GoalItem> {
    await this.goal(id);
    await this.db`
      update public.goals set status = ${status},
        completed_at = case when ${status}::text = 'done' then now() else null end
      where id = ${id}`;
    return await this.goal(id);
  }

  /** Logs an amount on a numeric goal, like "+5 km"; a negative amount takes one back. */
  async logAmount(goalId: string, day: Day, amount: number): Promise<GoalItem> {
    const goal = await this.goal(goalId);
    if (goal.mode !== "number") {
      throw new PlannerError(`"${goal.title}" does not count a number, so there is nothing to log on it.`);
    }
    if (!Number.isFinite(amount) || amount === 0) {
      throw new PlannerError("An amount has to be something other than zero.");
    }
    await this.db`
      insert into public.goal_entries (id, goal_id, day, amount)
      values (${crypto.randomUUID()}, ${goalId}, ${day}, ${amount})`;
    return goal;
  }

  /** Every habit that is not deleted, in the order the apps keep them. */
  async habits(): Promise<Habit[]> {
    const rows = await this.db`
      select id::text, name, emoji, cadence, weekdays, times, measure, target, direction, unit,
             goal_id::text, starts_on::text, archived_at is not null as archived
      from public.habits where deleted_at is null order by position, created_at, id`;
    return rows.map((row) => ({
      id: row.id,
      name: row.name,
      emoji: row.emoji,
      cadence: row.cadence,
      weekdays: row.weekdays,
      times: row.times,
      measure: row.measure,
      target: row.target,
      direction: row.direction,
      unit: row.unit,
      goalId: row.goal_id,
      startsOn: row.starts_on,
      archived: row.archived,
      deleted: false,
    }));
  }

  /** Every check-in that is not deleted, with the habit it belongs to. */
  async checkins(): Promise<Checkin[]> {
    const rows = await this.db`
      select habit_id::text, day::text, value, skipped from public.habit_checkins
      where deleted_at is null order by day, habit_id`;
    return rows.map((row) => ({
      habitId: row.habit_id,
      day: row.day,
      value: row.value,
      skipped: row.skipped,
      deleted: false,
    }));
  }

  /** Every pause that is not deleted. */
  async pauses(): Promise<Pause[]> {
    const rows = await this.db`
      select id::text, habit_id::text, starts_on::text, ends_on::text from public.habit_pauses
      where deleted_at is null order by starts_on, habit_id`;
    return rows.map((row) => ({
      id: row.id,
      habitId: row.habit_id,
      from: row.starts_on,
      until: row.ends_on,
      deleted: false,
    }));
  }

  /** The habit with this id; it has to be the owner's and not deleted. */
  async habit(id: string): Promise<Habit> {
    if (!isUuid(id)) throw new PlannerError(`No habit with id ${id}.`);
    const habit = (await this.habits()).find((row) => row.id === id);
    if (habit === undefined) throw new PlannerError(`No habit with id ${id}.`);
    return habit;
  }

  /**
   * Checks a habit in on a day, like tapping its ring: a check is met, a count or an amount adds to
   * whatever the day already had. The day's one check-in is named after the habit and the day, so a
   * check-in written here and one written on a device are the same row.
   */
  async checkIn(habitId: string, day: Day, amount = 1): Promise<{ habit: Habit; value: number }> {
    const habit = await this.habit(habitId);
    if (!Number.isFinite(amount) || amount <= 0) throw new PlannerError("An amount has to be more than zero.");
    const before = (await this.checkins()).find((checkin) => checkin.habitId === habitId && checkin.day === day);
    const had = before === undefined || before.skipped ? 0 : before.value;
    const value = habit.measure === "check" ? 1 : had + amount;
    await this.writeCheckin(habitId, day, value, false);
    return { habit, value };
  }

  /** Sets a day's value outright, which is how a check-in is taken back (a value of 0). */
  async setCheckin(habitId: string, day: Day, value: number): Promise<Habit> {
    const habit = await this.habit(habitId);
    if (!Number.isFinite(value) || value < 0) throw new PlannerError("A value cannot be less than zero.");
    await this.writeCheckin(habitId, day, value, false);
    return habit;
  }

  /** Every project that isn't deleted, active first, then paused, then done, in the owner's order. */
  async projects(): Promise<ProjectItem[]> {
    const rows = await this.db`
      select id::text, name, description, area_id::text, status, repository_url, local_folder, notes, position
      from public.projects where deleted_at is null
      order by (status = 'done'), (status = 'paused'), position, lower(name)`;
    return rows.map(toProject);
  }

  /** Every milestone that isn't deleted, in the order its project lists them. */
  async milestones(): Promise<ProjectMilestone[]> {
    const rows = await this.db`
      select id::text, project_id::text, name, position from public.project_milestones
      where deleted_at is null order by position, lower(name)`;
    return rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      name: row.name,
      position: row.position,
      deleted: false,
    }));
  }

  /**
   * The project a reference points at: its id, its repository URL, a folder inside it, or its name
   * (docs/projects.md, story 76). Throws when nothing matches, naming what the owner has.
   */
  async findProject(reference: string): Promise<ProjectItem> {
    const projects = await this.projects();
    const project = matchProject(projects, reference);
    if (project !== null) return project;
    const known = projects.length === 0
      ? "There are no projects yet."
      : `The owner's projects are: ${projects.map((one) => one.name).join(", ")}.`;
    throw new PlannerError(`No project matches "${reference}" by id, repository, folder or name. ${known}`);
  }

  /** A milestone of this project by id or by name; null when the reference is empty. */
  async findMilestone(projectId: string, reference: string | null): Promise<ProjectMilestone | null> {
    if (reference === null || reference.trim() === "") return null;
    const own = (await this.milestones()).filter((milestone) => milestone.projectId === projectId);
    const text = reference.trim().toLowerCase();
    const found = own.find((milestone) => milestone.id === reference.trim()) ??
      own.find((milestone) => milestone.name.trim().toLowerCase() === text);
    if (found !== undefined) return found;
    const known = own.length === 0
      ? "That project has no milestones."
      : `Its milestones are: ${own.map((milestone) => milestone.name).join(", ")}.`;
    throw new PlannerError(`No milestone "${reference}" in that project. ${known}`);
  }

  /**
   * Adds a project with a board of its own, the way the Projects screen's new-project form does.
   * The name has to be free, and so do the repository and the folder, because those are what
   * findProject matches on: two projects sharing one would make the match a toss-up (story 76).
   * A folder sitting inside another project's folder is fine, which is what lets checkouts nest.
   */
  async addProject(fields: ProjectFields): Promise<ProjectItem> {
    const name = (fields.name ?? "").trim().slice(0, MAX_PROJECT_NAME);
    if (name.length === 0) throw new PlannerError("A project needs a name.");
    const repository = clip(fields.repository ?? null, MAX_LOCATION);
    const folder = clip(fields.folder ?? null, MAX_LOCATION);
    const repositoryMatch = repositoryKey(repository);
    const folderMatch = folderKey(folder);
    const projects = await this.projects();
    for (const project of projects) {
      if (project.name.trim().toLowerCase() === name.toLowerCase()) {
        throw new PlannerError(`There is already a project called ${project.name} (project id ${project.id}).`);
      }
      if (repositoryMatch !== null && repositoryKey(project.repositoryUrl) === repositoryMatch) {
        throw new PlannerError(`${project.name} is already that repository (project id ${project.id}).`);
      }
      if (folderMatch !== null && folderKey(project.localFolder) === folderMatch) {
        throw new PlannerError(`${project.name} is already that folder (project id ${project.id}).`);
      }
    }
    const area = clip(fields.area ?? null, MAX_AREA) === null ? null : await this.findOrCreateArea(fields.area!);
    const id = crypto.randomUUID();
    await this.db`
      insert into public.projects
        (id, name, description, area_id, status, repository_url, local_folder, notes, position)
      values (${id}, ${name}, ${clip(fields.description ?? null, MAX_DESCRIPTION) ?? ""}, ${area?.id ?? null},
              ${fields.status ?? "active"}, ${repository}, ${folder},
              ${clip(fields.notes ?? null, MAX_NOTES) ?? ""}, ${projects.length})`;
    return await this.findProject(id);
  }

  /** Adds a milestone at the end of a project's list; its name has to be free within that project. */
  async addMilestone(projectId: string, name: string): Promise<ProjectMilestone> {
    const trimmed = name.trim().slice(0, MAX_PROJECT_NAME);
    if (trimmed.length === 0) throw new PlannerError("A milestone needs a name.");
    const own = (await this.milestones()).filter((milestone) => milestone.projectId === projectId);
    if (own.some((milestone) => milestone.name.trim().toLowerCase() === trimmed.toLowerCase())) {
      throw new PlannerError(`That project already has a milestone called ${trimmed}.`);
    }
    const id = crypto.randomUUID();
    await this.db`
      insert into public.project_milestones (id, project_id, name, position)
      values (${id}, ${projectId}, ${trimmed}, ${own.length})`;
    return { id, projectId, name: trimmed, position: own.length, deleted: false };
  }

  /**
   * Moves an item to a board column, the way dragging its card does: the done column completes the
   * task (so a repeating one moves on), any other column reopens a done one, and a dropped item
   * keeps its state wherever it sits (docs/projects.md).
   */
  async moveItem(id: string, column: BoardColumn): Promise<{ task: TaskItem; next: TaskItem | null }> {
    const task = await this.live(id);
    if (!task.projectId) throw new PlannerError(`"${task.title}" is not a project item, so it has no board column.`);
    const wanted = moved(column, task.state);
    let next: TaskItem | null = null;
    if (wanted !== task.state) {
      if (wanted === "done") next = (await this.finish(id, "done")).next;
      else await this.reopen(id);
    }
    await this.db`update public.tasks set board_column = ${column} where id = ${id}`;
    return { task: (await this.task(id))!, next };
  }

  /** Skips the habit's period holding this day, or takes the skip back. */
  async skipHabit(habitId: string, day: Day, skipped = true): Promise<Habit> {
    const habit = await this.habit(habitId);
    await this.writeCheckin(habitId, day, 0, skipped);
    return habit;
  }

  /** The owner's settings: the name they go by, their time zone and the hour a planning day starts. */
  async settings(): Promise<Settings> {
    const [row] = await this.db`select display_name, time_zone, day_rollover_hour from public.profiles`;
    return { displayName: row.display_name, timeZone: row.time_zone, dayStartHour: row.day_rollover_hour };
  }

  /**
   * Changes the settings every planning day is worked out from. A new time zone or day start moves
   * what "today" means, so the profile this planner had cached goes with it.
   */
  async updateSettings(
    fields: { displayName?: string | null; timeZone?: string; dayStartHour?: number },
  ): Promise<Settings> {
    const current = await this.settings();
    const timeZone = fields.timeZone === undefined ? current.timeZone : fields.timeZone.trim();
    if (timeZone.length === 0 || timeZone.length > MAX_TIME_ZONE) {
      throw new PlannerError("A time zone is named like Europe/Prague.");
    }
    try {
      new Intl.DateTimeFormat("en-GB", { timeZone });
    } catch {
      throw new PlannerError(`"${timeZone}" is not a time zone. Name one like Europe/Prague.`);
    }
    const hour = fields.dayStartHour ?? current.dayStartHour;
    if (!Number.isInteger(hour) || hour < 0 || hour > 23) {
      throw new PlannerError("A planning day starts at a whole hour from 0 to 23.");
    }
    const displayName = fields.displayName === undefined
      ? current.displayName
      : clip(fields.displayName, MAX_DISPLAY_NAME);
    await this.db`
      update public.profiles
      set display_name = ${displayName}, time_zone = ${timeZone}, day_rollover_hour = ${hour}`;
    this.profile = null;
    return await this.settings();
  }

  /** Adds an area, or takes the one that already has this name and sets what was asked on it. */
  async addArea(fields: AreaFields): Promise<Area> {
    const area = await this.findOrCreateArea(fields.name ?? "");
    const more = fields.color !== undefined || fields.emoji !== undefined || fields.archived !== undefined;
    return more ? await this.updateArea(area.id, { ...fields, name: undefined }) : area;
  }

  /** Renames an area, recolors it, gives it an emoji, or archives it and brings it back. */
  async updateArea(id: string, fields: AreaFields): Promise<Area> {
    const areas = await this.areas();
    const area = areas.find((one) => one.id === id);
    if (area === undefined) throw new PlannerError(`No area with id ${id}.`);
    const name = fields.name === undefined ? area.name : fields.name.trim().replace(/^@/, "").slice(0, MAX_AREA);
    if (name.length === 0) throw new PlannerError("An area needs a name.");
    const taken = areas.find((one) => one.id !== id && one.name.trim().toLowerCase() === name.toLowerCase());
    if (taken !== undefined) throw new PlannerError(`There is already an area called ${taken.name}.`);
    let color = area.color;
    if (fields.color !== undefined) {
      color = fields.color.trim().toLowerCase();
      if (!AREA_COLORS.includes(color)) {
        throw new PlannerError(`"${color}" is not an area color. They are: ${AREA_COLORS.join(", ")}.`);
      }
    }
    const archived = fields.archived ?? area.archived;
    await this.db`
      update public.areas set name = ${name}, color = ${color},
        emoji = ${fields.emoji === undefined ? area.emoji : clip(fields.emoji, MAX_EMOJI)},
        archived_at = case when ${archived}::boolean then coalesce(archived_at, now()) else null end
      where id = ${id}`;
    return (await this.areas()).find((one) => one.id === id)!;
  }

  /** Deletes an area. Its tasks and projects keep everything else and simply have no area. */
  async deleteArea(id: string): Promise<Area> {
    const area = (await this.areas()).find((one) => one.id === id);
    if (area === undefined) throw new PlannerError(`No area with id ${id}.`);
    await this.db`update public.tasks set area_id = null where area_id = ${id} and deleted_at is null`;
    await this.db`update public.projects set area_id = null where area_id = ${id} and deleted_at is null`;
    await this.db`update public.areas set deleted_at = now() where id = ${id}`;
    return area;
  }

  /** Renames a tag everywhere it is used. */
  async updateTag(id: string, name: string): Promise<Tag> {
    const tags = await this.tags();
    const tag = tags.find((one) => one.id === id);
    if (tag === undefined) throw new PlannerError(`No tag with id ${id}.`);
    const text = name.trim().replace(/^#/, "").slice(0, MAX_TAG);
    if (text.length === 0) throw new PlannerError("A tag needs a name.");
    const taken = tags.find((one) => one.id !== id && one.name.trim().toLowerCase() === text.toLowerCase());
    if (taken !== undefined) throw new PlannerError(`There is already a tag called ${taken.name}.`);
    await this.db`update public.tags set name = ${text} where id = ${id}`;
    return { id, name: text };
  }

  /** Deletes a tag and takes it off every task that carried it. */
  async deleteTag(id: string): Promise<Tag> {
    const tag = (await this.tags()).find((one) => one.id === id);
    if (tag === undefined) throw new PlannerError(`No tag with id ${id}.`);
    await this.db`update public.task_tags set deleted_at = now() where tag_id = ${id} and deleted_at is null`;
    await this.db`update public.tags set deleted_at = now() where id = ${id}`;
    return tag;
  }

  /** Renames a step. */
  async renameStep(id: string, title: string): Promise<Step> {
    const text = title.trim();
    if (text.length === 0 || text.length > MAX_STEP) {
      throw new PlannerError(`A step needs 1 to ${MAX_STEP} characters.`);
    }
    const rows = await this.db`
      update public.task_steps set title = ${text}
      where id = ${isUuid(id) ? id : null} and deleted_at is null
      returning id::text, title, done`;
    if (rows.length === 0) throw new PlannerError(`No step with id ${id}.`);
    return { id: rows[0].id, title: rows[0].title, done: rows[0].done };
  }

  /** Takes a step off its task. */
  async removeStep(id: string): Promise<Step> {
    const rows = await this.db`
      update public.task_steps set deleted_at = now()
      where id = ${isUuid(id) ? id : null} and deleted_at is null
      returning id::text, title, done`;
    if (rows.length === 0) throw new PlannerError(`No step with id ${id}.`);
    return { id: rows[0].id, title: rows[0].title, done: rows[0].done };
  }

  /** The tasks that carry at least one reminder, for the calendar's mark. */
  async remindedTasks(): Promise<Set<string>> {
    const rows = await this.db`select distinct task_id::text from public.reminders where deleted_at is null`;
    return new Set<string>(rows.map((row) => row.task_id as string));
  }

  /** The latest changes to the owner's rows, newest first, the way the Activity screen reads them. */
  async changes(limit: number): Promise<Change[]> {
    const rows = await this.db`${this.changeColumns()} order by id desc limit ${limit}`;
    return rows.map(toChange);
  }

  /**
   * Puts a row back the way a change found it, the way the apps' undo does: an edit gets its old
   * values, a deletion comes back, and something added is deleted softly (docs/activity.md).
   */
  async undo(entryId: string): Promise<Change> {
    const entry = entryId.trim();
    if (!/^[0-9]{1,18}$/.test(entry)) throw new PlannerError(`${entryId} is not a change id.`);
    try {
      await this.db`select public.undo_activity(${entry}::bigint)`;
    } catch (error) {
      const said = (error as { message?: string }).message ?? "";
      if (said.includes("no such change")) throw new PlannerError(`No change with id ${entry}.`);
      if (said.includes("already undone")) throw new PlannerError("That change was already undone.");
      if (said.includes("changed since")) {
        throw new PlannerError(
          "The row has moved on since that change, so undoing it would throw the later work away.",
        );
      }
      if (said.includes("undone")) throw new PlannerError("That change cannot be undone.");
      throw error;
    }
    const [row] = await this.db`${this.changeColumns()} where id = ${entry}::bigint`;
    return toChange(row);
  }

  /** Deletes a goal. What served it, tasks, habits and the goals under it, stops serving one. */
  async deleteGoal(id: string): Promise<GoalItem> {
    const goal = await this.goal(id);
    await this.db`update public.goals set parent_id = null where parent_id = ${id} and deleted_at is null`;
    await this.db`update public.tasks set goal_id = null where goal_id = ${id} and deleted_at is null`;
    await this.db`update public.habits set goal_id = null where goal_id = ${id} and deleted_at is null`;
    await this.db`update public.goals set deleted_at = now() where id = ${id}`;
    return goal;
  }

  /** Changes a project's own fields, under the same free-name rule addProject holds to. */
  async updateProject(id: string, fields: ProjectFields): Promise<ProjectItem> {
    const projects = await this.projects();
    const project = projects.find((one) => one.id === id);
    if (project === undefined) throw new PlannerError(`No project with id ${id}.`);
    const name = fields.name === undefined ? project.name : fields.name.trim().slice(0, MAX_PROJECT_NAME);
    if (name.length === 0) throw new PlannerError("A project needs a name.");
    const repository = fields.repository === undefined ? project.repositoryUrl : clip(fields.repository, MAX_LOCATION);
    const folder = fields.folder === undefined ? project.localFolder : clip(fields.folder, MAX_LOCATION);
    const repositoryMatch = repositoryKey(repository);
    const folderMatch = folderKey(folder);
    for (const other of projects) {
      if (other.id === id) continue;
      if (other.name.trim().toLowerCase() === name.toLowerCase()) {
        throw new PlannerError(`There is already a project called ${other.name} (project id ${other.id}).`);
      }
      if (repositoryMatch !== null && repositoryKey(other.repositoryUrl) === repositoryMatch) {
        throw new PlannerError(`${other.name} is already that repository (project id ${other.id}).`);
      }
      if (folderMatch !== null && folderKey(other.localFolder) === folderMatch) {
        throw new PlannerError(`${other.name} is already that folder (project id ${other.id}).`);
      }
    }
    let areaId = project.areaId;
    if (fields.area !== undefined) {
      const wanted = clip(fields.area, MAX_AREA);
      areaId = wanted === null ? null : (await this.findOrCreateArea(wanted)).id;
    }
    const description = fields.description === undefined
      ? project.description
      : clip(fields.description, MAX_DESCRIPTION) ?? "";
    const notes = fields.notes === undefined ? project.notes : clip(fields.notes, MAX_NOTES) ?? "";
    await this.db`
      update public.projects set name = ${name}, description = ${description}, area_id = ${areaId},
        status = ${fields.status ?? project.status}, repository_url = ${repository}, local_folder = ${folder},
        notes = ${notes}
      where id = ${id}`;
    return await this.findProject(id);
  }

  /** Deletes a project. Its items stay behind as plain tasks rather than going with it. */
  async deleteProject(id: string): Promise<ProjectItem> {
    const project = (await this.projects()).find((one) => one.id === id);
    if (project === undefined) throw new PlannerError(`No project with id ${id}.`);
    await this.db`
      update public.tasks set project_id = null, board_column = null, milestone_id = null where project_id = ${id}`;
    await this.db`
      update public.project_milestones set deleted_at = now() where project_id = ${id} and deleted_at is null`;
    await this.db`update public.projects set deleted_at = now() where id = ${id}`;
    return project;
  }

  /** Renames a milestone; its name has to be free within its own project. */
  async renameMilestone(id: string, name: string): Promise<ProjectMilestone> {
    const milestones = await this.milestones();
    const milestone = milestones.find((one) => one.id === id);
    if (milestone === undefined) throw new PlannerError(`No milestone with id ${id}.`);
    const text = name.trim().slice(0, MAX_PROJECT_NAME);
    if (text.length === 0) throw new PlannerError("A milestone needs a name.");
    const taken = milestones.some((one) =>
      one.projectId === milestone.projectId && one.id !== id &&
      one.name.trim().toLowerCase() === text.toLowerCase()
    );
    if (taken) throw new PlannerError(`That project already has a milestone called ${text}.`);
    await this.db`update public.project_milestones set name = ${text} where id = ${id}`;
    return { ...milestone, name: text };
  }

  /** Removes a milestone; the items that carried it stay on the board without one. */
  async removeMilestone(id: string): Promise<ProjectMilestone> {
    const milestone = (await this.milestones()).find((one) => one.id === id);
    if (milestone === undefined) throw new PlannerError(`No milestone with id ${id}.`);
    await this.db`update public.tasks set milestone_id = null where milestone_id = ${id}`;
    await this.db`update public.project_milestones set deleted_at = now() where id = ${id}`;
    return milestone;
  }

  /** Adds a habit, the way the Habits screen's new-habit form does. */
  async addHabit(fields: HabitFields): Promise<Habit> {
    const name = (fields.name ?? "").trim().slice(0, MAX_HABIT_NAME);
    if (name.length === 0) throw new PlannerError("A habit needs a name.");
    const shape = habitShape({
      cadence: fields.cadence ?? "daily",
      weekdays: fields.weekdays ?? null,
      times: fields.times ?? null,
      measure: fields.measure ?? "check",
      target: fields.target ?? null,
      direction: fields.direction ?? "at_least",
      unit: clip(fields.unit ?? null, MAX_UNIT),
    });
    const goalId = await this.habitGoal(fields.goal);
    const startsOn = fields.startsOn ?? (await this.now()).today;
    const position = (await this.habits()).length;
    const id = crypto.randomUUID();
    await this.db`
      insert into public.habits (id, name, emoji, cadence, weekdays, times, measure, target, direction, unit,
                                 goal_id, starts_on, position)
      values (${id}, ${name}, ${clip(fields.emoji ?? null, MAX_EMOJI)}, ${shape.cadence}, ${shape.weekdays},
              ${shape.times}, ${shape.measure}, ${shape.target}, ${shape.direction}, ${shape.unit},
              ${goalId}, ${startsOn}, ${position})`;
    return await this.habit(id);
  }

  /** Changes a habit's own fields; what is left out stays as it was. */
  async updateHabit(id: string, fields: HabitFields): Promise<Habit> {
    const habit = await this.habit(id);
    const name = fields.name === undefined ? habit.name : fields.name.trim().slice(0, MAX_HABIT_NAME);
    if (name.length === 0) throw new PlannerError("A habit needs a name.");
    const shape = habitShape({
      cadence: fields.cadence ?? habit.cadence,
      weekdays: fields.weekdays === undefined ? habit.weekdays : fields.weekdays,
      times: fields.times === undefined ? habit.times : fields.times,
      measure: fields.measure ?? habit.measure,
      target: fields.target === undefined ? habit.target : fields.target,
      direction: fields.direction ?? habit.direction,
      unit: fields.unit === undefined ? habit.unit : clip(fields.unit, MAX_UNIT),
    });
    const goalId = fields.goal === undefined ? habit.goalId : await this.habitGoal(fields.goal);
    const archived = fields.archived ?? habit.archived;
    await this.db`
      update public.habits set name = ${name},
        emoji = ${fields.emoji === undefined ? habit.emoji : clip(fields.emoji, MAX_EMOJI)},
        cadence = ${shape.cadence}, weekdays = ${shape.weekdays}, times = ${shape.times},
        measure = ${shape.measure}, target = ${shape.target}, direction = ${shape.direction}, unit = ${shape.unit},
        goal_id = ${goalId}, starts_on = ${fields.startsOn ?? habit.startsOn},
        archived_at = case when ${archived}::boolean then coalesce(archived_at, now()) else null end
      where id = ${id}`;
    return await this.habit(id);
  }

  /** Deletes a habit with its check-ins and its pauses. */
  async deleteHabit(id: string): Promise<Habit> {
    const habit = await this.habit(id);
    await this.db`update public.habit_checkins set deleted_at = now() where habit_id = ${id} and deleted_at is null`;
    await this.db`update public.habit_pauses set deleted_at = now() where habit_id = ${id} and deleted_at is null`;
    await this.db`update public.habits set deleted_at = now() where id = ${id}`;
    return habit;
  }

  /**
   * Pauses a habit from a day, open ended or up to a last day. A pause neither breaks a streak nor
   * counts, and it stays after the habit resumes so old streaks still read right.
   */
  async pauseHabit(habitId: string, from: Day, until: Day | null): Promise<Pause> {
    const habit = await this.habit(habitId);
    if (until !== null && until < from) throw new PlannerError("A pause cannot end before it starts.");
    const last = until ?? FAR_OFF;
    const clash = (await this.pauses()).filter((pause) => pause.habitId === habitId).find((pause) =>
      // What the new stretch runs into, and the later open ended pause the apps refuse as well.
      (pause.from <= last && (pause.until ?? FAR_OFF) >= from) || (pause.from > from && pause.until === null)
    );
    if (clash !== undefined) {
      const reaches = clash.until === null ? "with no end yet" : `to ${clash.until}`;
      throw new PlannerError(
        `"${habit.name}" is already paused from ${clash.from} ${reaches}. Resume it before pausing it again.`,
      );
    }
    const id = crypto.randomUUID();
    await this.db`
      insert into public.habit_pauses (id, habit_id, starts_on, ends_on)
      values (${id}, ${habitId}, ${from}, ${until})`;
    return { id, habitId, from, until, deleted: false };
  }

  /**
   * Brings a habit back on a day: the pause covering it ends the day before, or goes away when it
   * started that day, so the habit counts again from that day on (windows HabitList.Resume).
   */
  async resumeHabit(habitId: string, day: Day): Promise<Habit> {
    const habit = await this.habit(habitId);
    const open = (await this.pauses()).filter((pause) => pause.habitId === habitId).find((pause) =>
      (pause.from <= day && (pause.until === null || pause.until >= day)) || (pause.from > day && pause.until === null)
    );
    if (open === undefined) throw new PlannerError(`"${habit.name}" is not paused on ${day}.`);
    if (open.from >= day) {
      await this.db`update public.habit_pauses set deleted_at = now() where id = ${open.id}`;
    } else {
      await this.db`update public.habit_pauses set ends_on = ${addDays(day, -1)} where id = ${open.id}`;
    }
    return habit;
  }

  /** The goal a habit serves, by id; null when the reference is empty. */
  private async habitGoal(reference: string | null | undefined): Promise<string | null> {
    if (reference === undefined || reference === null || reference.trim() === "") return null;
    return (await this.goal(reference.trim())).id;
  }

  /**
   * The goal a parent reference points at. It has to be able to hold the child, which is a longer
   * horizon over a period that overlaps (rules/goals.ts canServe, the rule the apps offer parents
   * by), and it can be neither the goal itself nor one of its own.
   */
  private async parentGoal(
    reference: string | null | undefined,
    child: { id: string | null; horizon: GoalHorizon; periodStart: Day },
  ): Promise<string | null> {
    if (reference === undefined || reference === null || reference.trim() === "") return null;
    const parent = await this.goal(reference.trim());
    if (!canServe(child.horizon, child.periodStart, parent.horizon, parent.periodStart)) {
      throw new PlannerError(
        `"${parent.title}" cannot hold this goal: a parent needs a longer horizon and a period that overlaps it.`,
      );
    }
    if (child.id !== null) {
      const goals = await this.goals();
      let walk: string | null = parent.id;
      while (walk !== null) {
        if (walk === child.id) throw new PlannerError("A goal cannot sit under itself.");
        walk = goals.find((one) => one.id === walk)?.parentId ?? null;
      }
    }
    return parent.id;
  }

  private changeColumns() {
    return this.db`
      select id::text, entity, entity_id::text, action, actor, undone_at is not null as undone,
             coalesce(after ->> 'title', after ->> 'name', before ->> 'title', before ->> 'name') as label,
             to_char(created_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as at
      from public.activity_log`;
  }

  private async writeCheckin(habitId: string, day: Day, value: number, skipped: boolean) {
    const id = await checkinId(habitId, day);
    await this.db`
      insert into public.habit_checkins (id, habit_id, day, value, skipped)
      values (${id}, ${habitId}, ${day}, ${value}, ${skipped})
      on conflict (id) do update set value = excluded.value, skipped = excluded.skipped, deleted_at = null`;
  }

  private taskColumns() {
    return this.db`
      select id::text, title, notes, status, top_priority, planned_date::text,
             to_char(planned_time, 'HH24:MI') as planned_time, deadline::text, area_id::text, recurrence,
             series_id::text, goal_id::text, moved_count, position,
             project_id::text, item_type, board_column, priority, milestone_id::text, made_by,
             to_char(created_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as created_at,
             to_char(completed_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as completed_at,
             deleted_at is not null as deleted
      from public.tasks`;
  }

  private async live(id: string): Promise<TaskItem> {
    const task = await this.task(id);
    if (task === null || task.deleted) throw new PlannerError(`No task with id ${id}.`);
    return task;
  }

  private async setTags(taskId: string, names: string[]) {
    const wanted = new Set<string>();
    for (const name of names) wanted.add((await this.findOrCreateTag(name)).id);
    const current = (await this.tagLinks()).get(taskId) ?? [];
    for (const tagId of current.filter((tagId) => !wanted.has(tagId))) {
      await this.db`update public.task_tags set deleted_at = now() where task_id = ${taskId} and tag_id = ${tagId}`;
    }
    for (const tagId of [...wanted].filter((tagId) => !current.includes(tagId))) await this.link(taskId, tagId);
  }

  private async link(taskId: string, tagId: string, id: string = crypto.randomUUID()) {
    await this.db`
      insert into public.task_tags (id, task_id, tag_id) values (${id}, ${taskId}, ${tagId})
      on conflict (id) do update set deleted_at = null`;
  }

  // The goal a next occurrence on day still serves: the current one's, while its period lasts (docs/repeating.md).
  private async goalFor(goalId: string | null, day: Day): Promise<string | null> {
    if (goalId === null) return null;
    const [goal] = await this.db`
      select horizon, period_start::text from public.goals where id = ${goalId} and deleted_at is null`;
    if (!goal) return null;
    const start = goal.period_start as Day;
    return day >= start && day <= periodEnd(goal.horizon as GoalHorizon, start) ? goalId : null;
  }

  // The next occurrence copies this one's plan onto the rule's next day, with its tags and who made
  // it; nothing when the task doesn't repeat, the rule can't be followed, or the next occurrence is
  // already there.
  private async moveOn(current: TaskItem): Promise<TaskItem | null> {
    const rule = parseRecurrence(current.recurrence);
    if (rule === null) return null;
    const day = nextOccurrence(rule, current.plannedDate, (await this.now()).today);
    if (day === null) return null;
    const nextId = await successorId(current.id);
    const existing = await this.task(nextId);
    if (existing !== null && !existing.deleted) return null;
    const goalId = await this.goalFor(current.goalId ?? null, day);
    await this.db`
      insert into public.tasks
        (id, title, notes, top_priority, status, position, planned_date, planned_time, area_id, recurrence, series_id, goal_id,
         made_by)
      values
        (${nextId}, ${current.title}, ${current.notes}, ${current.topPriority}, 'open', 0, ${day}, ${current.plannedTime},
         ${current.areaId}, ${current.recurrence}, ${seriesOf(current)}, ${goalId}, ${current.madeBy ?? "owner"})
      on conflict (id) do update set
        title = excluded.title, notes = excluded.notes, top_priority = excluded.top_priority, status = 'open',
        completed_at = null, planned_date = excluded.planned_date, planned_time = excluded.planned_time,
        area_id = excluded.area_id, recurrence = excluded.recurrence, series_id = excluded.series_id,
        goal_id = excluded.goal_id, deleted_at = null`;
    for (const tagId of (await this.tagLinks()).get(current.id) ?? []) {
      await this.link(nextId, tagId, await tagLinkId(nextId, tagId));
    }
    return await this.task(nextId);
  }
}

// Text that has to fit a column, or null when there is nothing left of it.
function clip(text: string | null, length: number): string | null {
  const trimmed = text?.trim().slice(0, length) ?? "";
  return trimmed.length === 0 ? null : trimmed;
}

// deno-lint-ignore no-explicit-any
function toGoal(row: any): GoalItem {
  return {
    id: row.id,
    title: row.title,
    horizon: row.horizon,
    periodStart: row.period_start,
    mode: row.progress_mode,
    status: row.status,
    emoji: row.emoji,
    parentId: row.parent_id,
    target: row.target,
    unit: row.unit,
    deleted: false,
  };
}

// deno-lint-ignore no-explicit-any
function toChange(row: any): Change {
  return {
    id: row.id,
    entity: row.entity,
    entityId: row.entity_id,
    action: row.action,
    actor: row.actor,
    at: row.at,
    undone: row.undone,
    label: row.label,
  };
}

/**
 * The habit fields the database will take, or a PlannerError naming what does not fit: a cadence
 * carries either its weekdays or its times and never both, only a counted habit has a target, and
 * only a habit measured day by day can be a limit (supabase/migrations/0010 and 0014).
 */
function habitShape(wanted: {
  cadence: HabitCadence;
  weekdays: number | null;
  times: number | null;
  measure: HabitMeasure;
  target: number | null;
  direction: HabitDirection;
  unit: string | null;
}) {
  const { cadence, measure, direction } = wanted;
  const weekdays = cadence === "weekdays" ? wanted.weekdays : null;
  const times = cadence === "per_week" || cadence === "per_month" ? wanted.times : null;
  if (cadence === "weekdays" && (weekdays === null || !Number.isInteger(weekdays) || weekdays < 1 || weekdays > 127)) {
    throw new PlannerError(
      "A weekdays habit needs its days added up: Monday 1, Tuesday 2, Wednesday 4, Thursday 8, Friday 16, " +
        "Saturday 32, Sunday 64.",
    );
  }
  if (
    (cadence === "per_week" || cadence === "per_month") && (times === null || !Number.isInteger(times) || times < 1)
  ) {
    throw new PlannerError("A habit counted per week or per month needs how many days a period takes.");
  }
  if (cadence === "per_week" && times !== null && times > 7) throw new PlannerError("A week holds 7 days.");
  if (cadence === "per_month" && times !== null && times > 31) {
    throw new PlannerError("A month holds at most 31 days.");
  }
  const target = measure === "check" ? null : wanted.target;
  if (measure !== "check" && (target === null || !(target > 0))) {
    throw new PlannerError("A habit that counts needs a target above zero.");
  }
  if (direction === "at_most" && cadence !== "daily" && cadence !== "weekdays") {
    throw new PlannerError("Only a daily or weekdays habit can be a limit, because a limit is kept day by day.");
  }
  return { cadence, weekdays, times, measure, target, direction, unit: measure === "check" ? null : wanted.unit };
}

// deno-lint-ignore no-explicit-any
function toProject(row: any): ProjectItem {
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    areaId: row.area_id,
    status: row.status,
    repositoryUrl: row.repository_url,
    localFolder: row.local_folder,
    notes: row.notes,
    position: row.position,
    deleted: false,
  };
}

// deno-lint-ignore no-explicit-any
function toTask(row: any): TaskItem {
  return {
    id: row.id,
    title: row.title,
    state: row.status as TaskState,
    topPriority: row.top_priority,
    createdAt: row.created_at,
    plannedDate: row.planned_date,
    plannedTime: row.planned_time,
    areaId: row.area_id,
    recurrence: row.recurrence,
    deleted: row.deleted,
    seriesId: row.series_id,
    notes: row.notes,
    deadline: row.deadline,
    completedAt: row.completed_at,
    goalId: row.goal_id,
    movedCount: row.moved_count ?? 0,
    projectId: row.project_id,
    itemType: row.item_type ?? "task",
    boardColumn: row.board_column,
    priority: row.priority ?? "normal",
    milestoneId: row.milestone_id,
    position: row.position ?? 0,
    madeBy: row.made_by ?? "owner",
  };
}

export function isUuid(text: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(text);
}

function cleanTitle(text: string): string {
  const title = text.trim();
  if (title.length === 0) throw new PlannerError("A task needs a title.");
  return title.slice(0, MAX_TITLE);
}

// The column a project item sits in once the task reaches this state; null for a task outside a project.
function columnAfter(task: TaskItem, state: TaskState): string | null {
  return task.boardColumn ? finishedIn(state, task.boardColumn) : null;
}

function cleanItemType(text: string | undefined): ItemType {
  const type = (text ?? "task").trim().toLowerCase();
  if (type !== "task" && type !== "idea" && type !== "bug") {
    throw new PlannerError("An item is a task, an idea or a bug.");
  }
  return type;
}

function cleanPriority(text: string | undefined): Priority {
  const priority = (text ?? "normal").trim().toLowerCase() as Priority;
  if (!PRIORITIES.includes(priority)) throw new PlannerError("A priority is low, normal, high or urgent.");
  return priority;
}

export function cleanColumn(text: string): BoardColumn {
  const column = text.trim().toLowerCase() as BoardColumn;
  if (!COLUMNS.includes(column)) throw new PlannerError("A board column is backlog, todo, doing or done.");
  return column;
}

function cleanNotes(text: string): string {
  if (text.length > MAX_NOTES) throw new PlannerError(`Notes can be at most ${MAX_NOTES} characters.`);
  return text;
}

function cleanTime(text: string | null, day: Day | null): string | null {
  if (text === null || text.trim() === "") return null;
  const match = /^(\d{1,2}):(\d{2})$/.exec(text.trim());
  if (!match || Number(match[1]) > 23 || Number(match[2]) > 59) throw new PlannerError("A time looks like 17:30.");
  if (day === null) throw new PlannerError("A time needs a day.");
  return `${match[1].padStart(2, "0")}:${match[2]}`;
}

function cleanRepeat(text: string | null): string | null {
  if (text === null || text.trim() === "") return null;
  if (parseRecurrence(text) === null) {
    throw new PlannerError(
      "A repeat rule is FREQ=DAILY, WEEKLY or MONTHLY with optional INTERVAL, BYDAY (weekly) or BYMONTHDAY (monthly).",
    );
  }
  return text.trim().toUpperCase();
}

function cleanLocalTime(text: string | undefined): string {
  const match = text === undefined ? null : /^(\d{4}-\d{2}-\d{2})[T ](\d{1,2}):(\d{2})$/.exec(text.trim());
  if (!match || !isDay(match[1]) || Number(match[2]) > 23 || Number(match[3]) > 59) {
    throw new PlannerError("A reminder time looks like 2026-09-18 17:30 (the owner's local time).");
  }
  return `${match[1]} ${match[2].padStart(2, "0")}:${match[3]}`;
}
