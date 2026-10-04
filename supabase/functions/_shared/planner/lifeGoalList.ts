import type { Db } from "../owner.ts";
import { type Day, isDay } from "../rules/day.ts";
import { type LifeGoalOrderItem, type LifeGoalStatus, orderLifeGoals } from "../rules/lifeGoals.ts";
import { isUuid, PlannerError } from "./planner.ts";

/** A life goal as the connector shows it: the rules' item with what the Life goals place shows. */
export interface LifeGoal extends LifeGoalOrderItem {
  title: string;
  why: string;
  by: Day | null;
  areaId: string | null;
  madeBy: "owner" | "claude";
  /** How many pictures it has; the pictures themselves stay in the apps. */
  pictures: number;
}

/** What a new life goal or an edit says. Undefined leaves a field alone; null clears it. */
export interface LifeGoalFields {
  title?: string;
  why?: string;
  by?: Day | null;
  areaId?: string | null;
  status?: LifeGoalStatus;
}

const MAX_TITLE = 200;
const MAX_WHY = 2_000;
const STATUSES: readonly LifeGoalStatus[] = ["open", "achieved", "dropped"];
const TIMESTAMP = `'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'`;
const NO_WHY = "A life goal needs a why: why it matters is the point. Ask the owner.";

/**
 * The owner's life goals (docs/life-goals.md), read and changed through the owner's row security, the
 * way the apps' LifeGoalList does. Who made one comes from the actor header (stamp_task_maker), and
 * every change goes to the activity log, so undo works as it does for any other row.
 */
export class LifeGoalList {
  constructor(private readonly db: Db) {}

  /** Every life goal that is not deleted: open ones in the owner's order, then the closed ones. */
  async all(): Promise<LifeGoal[]> {
    const rows = await this.db`${this.columns()} where g.deleted_at is null`;
    return orderLifeGoals(rows.map(toLifeGoal));
  }

  /** The life goal with this id; it has to be the owner's and not deleted. */
  async lifeGoal(id: string): Promise<LifeGoal> {
    if (!isUuid(id)) throw new PlannerError(`No life goal with id ${id}.`);
    const rows = await this.db`${this.columns()} where g.id = ${id} and g.deleted_at is null`;
    if (rows.length === 0) throw new PlannerError(`No life goal with id ${id}.`);
    return toLifeGoal(rows[0]);
  }

  /** Adds an open life goal after the open ones. */
  async add(fields: LifeGoalFields): Promise<LifeGoal> {
    const title = required(fields.title, MAX_TITLE, "A life goal needs a title.");
    const why = required(fields.why, MAX_WHY, NO_WHY);
    const by = cleanBy(fields.by ?? null);
    const [last] = await this.db`
      select max(position) as position from public.life_goals where status = 'open' and deleted_at is null`;
    const position = last?.position === null || last?.position === undefined ? 0 : Number(last.position) + 1;
    const id = crypto.randomUUID();
    await this.db`
      insert into public.life_goals (id, title, why, by_date, area_id, status, position)
      values (${id}, ${title}, ${why}, ${by}, ${fields.areaId ?? null}, 'open', ${position})`;
    return await this.lifeGoal(id);
  }

  /**
   * Changes what the life goal says and where it stands, in one change so one undo takes it all back.
   * Closing it stamps when; reopening clears that.
   */
  async update(id: string, fields: LifeGoalFields): Promise<LifeGoal> {
    const goal = await this.lifeGoal(id);
    const title = fields.title === undefined
      ? goal.title
      : required(fields.title, MAX_TITLE, "A life goal needs a title.");
    const why = fields.why === undefined ? goal.why : required(fields.why, MAX_WHY, NO_WHY);
    const by = fields.by === undefined ? goal.by : cleanBy(fields.by);
    const status = fields.status ?? goal.status;
    if (!STATUSES.includes(status)) throw new PlannerError("A life goal is open, achieved or dropped.");
    await this.db`
      update public.life_goals set title = ${title}, why = ${why}, by_date = ${by},
        area_id = ${fields.areaId === undefined ? goal.areaId : fields.areaId},
        status = ${status},
        closed_at = case
          when ${status}::text = 'open' then null
          when status = ${status}::text then closed_at
          else now()
        end
      where id = ${id}`;
    return await this.lifeGoal(id);
  }

  private columns() {
    return this.db`
      select g.id::text, g.title, g.why, g.by_date::text, g.area_id::text, g.status, g.position, g.made_by,
             to_char(g.created_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as created_at,
             to_char(g.closed_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as closed_at,
             (select count(*)::int from public.life_goal_pictures p
              where p.life_goal_id = g.id and p.deleted_at is null) as pictures
      from public.life_goals g`;
  }
}

/** The same day `years` later, as the apps' "In 10 years" picks it; 29 February becomes the 28th. */
export function yearsLater(day: Day, years: number): Day {
  const [year, month, date] = day.split("-").map(Number);
  const target = year + years;
  const last = new Date(Date.UTC(target, month, 0)).getUTCDate();
  return `${String(target).padStart(4, "0")}-${String(month).padStart(2, "0")}-${
    String(Math.min(date, last)).padStart(2, "0")
  }`;
}

function required(text: string | undefined, length: number, missing: string): string {
  const trimmed = (text ?? "").trim().slice(0, length);
  if (trimmed.length === 0) throw new PlannerError(missing);
  return trimmed;
}

function cleanBy(by: Day | null): Day | null {
  if (by === null) return null;
  const day = by.trim();
  if (!isDay(day)) throw new PlannerError(`"${by}" isn't a day: give the by date as YYYY-MM-DD.`);
  return day;
}

// deno-lint-ignore no-explicit-any
function toLifeGoal(row: any): LifeGoal {
  return {
    id: row.id,
    title: row.title,
    why: row.why,
    by: row.by_date,
    areaId: row.area_id,
    status: row.status,
    position: Number(row.position),
    madeBy: row.made_by,
    createdAt: row.created_at,
    closedAt: row.closed_at,
    pictures: row.pictures,
  };
}
