import type { Db } from "../owner.ts";
import type { Day } from "../rules/day.ts";
import type { TallyRow } from "../rules/tally.ts";

/**
 * The owner's Tally minutes (docs/tally.md), read through their row security: only the daily totals
 * every device synced, never an app or a window, which never leave the device (ADR 0013).
 */
export class TallyDays {
  constructor(private readonly db: Db) {}

  /** Every device's minutes from `from` to `to`, both included. */
  async between(from: Day, to: Day): Promise<TallyRow[]> {
    const rows = await this.db`
      select day::text, device_kind, category, project_id::text, minutes from public.tally_days
      where deleted_at is null and day between ${from}::date and ${to}::date
      order by day, category, project_id nulls first`;
    return rows.map((row) => ({
      day: row.day,
      deviceKind: row.device_kind,
      category: row.category,
      projectId: row.project_id,
      minutes: row.minutes,
    }));
  }

  /** The names of the owner's own categories, by id; the defaults are named by their key. */
  async categoryNames(): Promise<Map<string, string>> {
    const rows = await this.db`select id::text, name from public.tally_categories where deleted_at is null`;
    return new Map(rows.map((row) => [row.id as string, row.name as string]));
  }
}
