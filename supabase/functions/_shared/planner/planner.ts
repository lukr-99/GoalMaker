import type { Db } from "../owner.ts";
import { addDays, type Day, isDay, localNow } from "../rules/day.ts";
import { nameBasedUuid } from "../rules/nameBasedUuid.ts";
import { seriesOf, successorId, tagLinkId } from "../rules/occurrences.ts";
import { DEFAULT_START_HOUR, planningDay } from "../rules/planningDay.ts";
import { nextOccurrence, parseRecurrence } from "../rules/recurrence.ts";
import { periodStart, reviewId, type ReviewKind } from "../rules/reviews.ts";
import { compareText, type TaskItem, type TaskState } from "../rules/task.ts";
import { colorForNewArea } from "./palette.ts";

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
}

export interface Review {
  kind: ReviewKind;
  periodStart: Day;
  mood: number | null;
  energy: number | null;
  summary: string;
}

export class PlannerError extends Error {}

const RITUAL_NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";
const MAX_TITLE = 500;
const MAX_NOTES = 20_000;
const MAX_AREA = 60;
const MAX_TAG = 40;
const MAX_STEP = 300;
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
    const id = crypto.randomUUID();
    await this.db`
      insert into public.tasks
        (id, title, notes, top_priority, status, position, planned_date, planned_time, deadline, area_id, recurrence, series_id)
      values
        (${id}, ${title}, ${cleanNotes(fields.notes ?? "")}, ${fields.topPriority ?? false}, 'open', 0, ${day}, ${time},
         ${fields.deadline ?? null}, ${areaId}, ${repeat}, ${repeat === null ? null : id})`;
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
    await this.db`
      update public.tasks set
        title = ${fields.title === undefined ? task.title : cleanTitle(fields.title)},
        notes = ${fields.notes === undefined ? task.notes : cleanNotes(fields.notes)},
        planned_date = ${day},
        planned_time = ${time},
        deadline = ${fields.deadline === undefined ? task.deadline : fields.deadline},
        area_id = ${areaId},
        top_priority = ${fields.topPriority ?? task.topPriority},
        recurrence = ${repeat},
        series_id = ${repeat === null ? task.seriesId : task.seriesId ?? task.id}
      where id = ${id}`;
    if (fields.tags !== undefined) await this.setTags(id, fields.tags);
    return (await this.task(id))!;
  }

  /** Done or dropped; a repeating task moves on to its next occurrence (docs/repeating.md). */
  async finish(id: string, status: "done" | "dropped"): Promise<{ task: TaskItem; next: TaskItem | null }> {
    const task = await this.live(id);
    await this.db`
      update public.tasks set status = ${status}, completed_at = ${status === "done" ? this.db`now()` : null}
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
        planned_time = ${planned === null ? null : task.plannedTime}
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
    review: { summary: string; mood?: number; energy?: number },
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
    const start = periodStart(kind, day);
    const owner = (await this.db`select auth.uid()::text as id`)[0].id as string;
    const id = await reviewId(owner, kind, start);
    await this.db`
      insert into public.reviews (id, kind, period_start, summary, mood, energy)
      values (${id}, ${kind}, ${start}, ${summary}, ${review.mood ?? null}, ${review.energy ?? null})
      on conflict (id) do update set
        summary = excluded.summary,
        mood = coalesce(excluded.mood, public.reviews.mood),
        energy = coalesce(excluded.energy, public.reviews.energy),
        deleted_at = null`;
    return (await this.reviews(kind, 1, start))[0];
  }

  /** The latest reviews, newest period first; of one kind, and from one period start, when given. */
  async reviews(kind: ReviewKind | null, limit: number, start: Day | null = null): Promise<Review[]> {
    const rows = await this.db`
      select kind, period_start::text, mood, energy, summary from public.reviews
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

  private taskColumns() {
    return this.db`
      select id::text, title, notes, status, top_priority, planned_date::text,
             to_char(planned_time, 'HH24:MI') as planned_time, deadline::text, area_id::text, recurrence,
             series_id::text, to_char(created_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as created_at,
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

  // The next occurrence copies this one's plan onto the rule's next day, with its tags; nothing when
  // the task doesn't repeat, the rule can't be followed, or the next occurrence is already there.
  private async moveOn(current: TaskItem): Promise<TaskItem | null> {
    const rule = parseRecurrence(current.recurrence);
    if (rule === null) return null;
    const day = nextOccurrence(rule, current.plannedDate, (await this.now()).today);
    if (day === null) return null;
    const nextId = await successorId(current.id);
    const existing = await this.task(nextId);
    if (existing !== null && !existing.deleted) return null;
    await this.db`
      insert into public.tasks
        (id, title, notes, top_priority, status, position, planned_date, planned_time, area_id, recurrence, series_id)
      values
        (${nextId}, ${current.title}, ${current.notes}, ${current.topPriority}, 'open', 0, ${day}, ${current.plannedTime},
         ${current.areaId}, ${current.recurrence}, ${seriesOf(current)})
      on conflict (id) do update set
        title = excluded.title, notes = excluded.notes, top_priority = excluded.top_priority, status = 'open',
        completed_at = null, planned_date = excluded.planned_date, planned_time = excluded.planned_time,
        area_id = excluded.area_id, recurrence = excluded.recurrence, series_id = excluded.series_id, deleted_at = null`;
    for (const tagId of (await this.tagLinks()).get(current.id) ?? []) {
      await this.link(nextId, tagId, await tagLinkId(nextId, tagId));
    }
    return await this.task(nextId);
  }
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
