import type { Db } from "../owner.ts";
import type { Day } from "../rules/day.ts";
import {
  cooldownDays,
  coolsUntil,
  type Decision,
  DEFAULT_COOLDOWNS,
  MAX_DAYS,
  type WantCooldowns,
  type WantItem,
} from "../rules/wants.ts";
import { isUuid, PlannerError } from "./planner.ts";

/** A want as the connector shows it: the rules' want with everything the Wants place shows. */
export interface Want extends WantItem {
  reason: string;
  link: string | null;
  areaId: string | null;
  cooldownDays: number;
  decidedAt: string | null;
  decisionNote: string;
  checkedPrice: number | null;
  checkedAt: string | null;
  checkedNote: string;
  madeBy: "owner" | "claude";
}

/** What a new want or an edit says. Undefined leaves a field alone; null clears it. */
export interface WantFields {
  title?: string;
  reason?: string;
  link?: string | null;
  price?: number | null;
  currency?: string;
  areaId?: string | null;
  /** The days a new want waits when the owner picked them; its price decides otherwise. */
  days?: number | null;
}

const MAX_TITLE = 200;
const MAX_REASON = 2_000;
const MAX_LINK = 2_000;
const MAX_NOTE = 2_000;
const MAX_CHECKED_NOTE = 4_000;
const MAX_PRICE = 100_000_000;
const CURRENCY = /^[A-Z]{3}$/;
const TIMESTAMP = `'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'`;

/**
 * The owner's wants (docs/wants.md), read and changed through the owner's row security, the way the
 * apps' WantList does: a new want takes its cooldown from the owner's thresholds on the planning day
 * it is added, and keeps it.
 */
export class WantList {
  constructor(private readonly db: Db, private readonly today: () => Promise<Day>) {}

  /** Every want that is not deleted, soonest to cool first, then by title, like the apps. */
  async all(): Promise<Want[]> {
    const rows = await this.db`${this.columns()} where deleted_at is null`;
    return rows.map(toWant).sort((a, b) =>
      a.coolsUntil !== b.coolsUntil
        ? (a.coolsUntil < b.coolsUntil ? -1 : 1)
        : a.title.toLowerCase().localeCompare(b.title.toLowerCase())
    );
  }

  /** The want with this id; it has to be the owner's and not deleted. */
  async want(id: string): Promise<Want> {
    if (!isUuid(id)) throw new PlannerError(`No want with id ${id}.`);
    const rows = await this.db`${this.columns()} where id = ${id} and deleted_at is null`;
    if (rows.length === 0) throw new PlannerError(`No want with id ${id}.`);
    return toWant(rows[0]);
  }

  /** The owner's thresholds, or the defaults while they have none. */
  async cooldowns(): Promise<WantCooldowns> {
    const rows = await this.db`
      select small_under, small_days, medium_under, medium_days, large_days, unpriced_days, currency
      from public.want_cooldowns where deleted_at is null`;
    if (rows.length === 0) return DEFAULT_COOLDOWNS;
    const row = rows[0];
    return {
      smallUnder: row.small_under,
      smallDays: row.small_days,
      mediumUnder: row.medium_under,
      mediumDays: row.medium_days,
      largeDays: row.large_days,
      unpricedDays: row.unpriced_days,
      currency: row.currency,
    };
  }

  /** Adds a want with the cooldown its price or the owner's pick gives, from today. */
  async add(fields: WantFields): Promise<Want> {
    const cooldowns = await this.cooldowns();
    const title = required(fields.title, MAX_TITLE, "A want needs a title.");
    const reason = required(fields.reason, MAX_REASON, "A want needs a reason: why it is wanted is the point.");
    const price = cleanPrice(fields.price ?? null);
    const currency = cleanCurrency(fields.currency, cooldowns.currency);
    const picked = fields.days ?? null;
    if (picked !== null && (!Number.isInteger(picked) || picked < 0 || picked > MAX_DAYS)) {
      throw new PlannerError(`A cooldown is a whole number of days from 0 to ${MAX_DAYS}.`);
    }
    const days = cooldownDays(price, currency, cooldowns, picked);
    const addedOn = await this.today();
    const id = crypto.randomUUID();
    await this.db`
      insert into public.wants (id, title, reason, link, price, currency, area_id, cooldown_days, added_on, cools_until)
      values (${id}, ${title}, ${reason}, ${cleanLink(fields.link ?? null)}, ${price}, ${currency},
              ${fields.areaId ?? null}, ${days}, ${addedOn}, ${coolsUntil(addedOn, days)})`;
    return await this.want(id);
  }

  /** Changes what the want is; its cooldown stays as it was set. */
  async update(id: string, fields: WantFields): Promise<Want> {
    const want = await this.want(id);
    const title = fields.title === undefined ? want.title : required(fields.title, MAX_TITLE, "A want needs a title.");
    const reason = fields.reason === undefined
      ? want.reason
      : required(fields.reason, MAX_REASON, "A want needs a reason: why it is wanted is the point.");
    await this.db`
      update public.wants set title = ${title}, reason = ${reason},
        link = ${fields.link === undefined ? want.link : cleanLink(fields.link)},
        price = ${fields.price === undefined ? want.price : cleanPrice(fields.price)},
        currency = ${fields.currency === undefined ? want.currency : cleanCurrency(fields.currency, want.currency)},
        area_id = ${fields.areaId === undefined ? want.areaId : fields.areaId}
      where id = ${id}`;
    return await this.want(id);
  }

  /** Marks a want bought or dropped, with an optional note. */
  async decide(id: string, decision: Decision, note: string | undefined): Promise<Want> {
    await this.want(id);
    await this.db`
      update public.wants set decision = ${decision}, decided_at = now(),
        decision_note = ${(note ?? "").trim().slice(0, MAX_NOTE)}
      where id = ${id}`;
    return await this.want(id);
  }

  /** Takes a decision back; the want is cooling or ready again, as its day says. */
  async reopen(id: string): Promise<Want> {
    const want = await this.want(id);
    if (want.decision === null) {
      throw new PlannerError(`"${want.title}" is not decided, so there is nothing to reopen.`);
    }
    await this.db`update public.wants set decision = null, decided_at = null, decision_note = '' where id = ${id}`;
    return await this.want(id);
  }

  /** Keeps the last price Claude found, in the want's currency, with where it was found. */
  async recordPriceCheck(id: string, price: number, note: string): Promise<Want> {
    await this.want(id);
    const checked = cleanPrice(price);
    if (checked === null) throw new PlannerError("A price check needs the price that was found.");
    await this.db`
      update public.wants set checked_price = ${checked}, checked_at = now(),
        checked_note = ${note.trim().slice(0, MAX_CHECKED_NOTE)}
      where id = ${id}`;
    return await this.want(id);
  }

  private columns() {
    return this.db`
      select id::text, title, reason, link, price, currency, area_id::text, cooldown_days, added_on::text,
             cools_until::text, decision, decision_note, checked_price, checked_note, made_by,
             to_char(decided_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as decided_at,
             to_char(checked_at at time zone 'UTC', ${this.db.unsafe(TIMESTAMP)}) as checked_at
      from public.wants`;
  }
}

function required(text: string | undefined, length: number, missing: string): string {
  const trimmed = (text ?? "").trim().slice(0, length);
  if (trimmed.length === 0) throw new PlannerError(missing);
  return trimmed;
}

function cleanLink(text: string | null): string | null {
  const trimmed = text?.trim().slice(0, MAX_LINK) ?? "";
  return trimmed.length === 0 ? null : trimmed;
}

function cleanPrice(price: number | null): number | null {
  if (price === null) return null;
  if (!Number.isFinite(price) || price < 0 || price > MAX_PRICE) {
    throw new PlannerError("A price is a number from 0 to 100,000,000.");
  }
  return price;
}

function cleanCurrency(text: string | undefined, fallback: string): string {
  if (text === undefined || text.trim() === "") return fallback;
  const currency = text.trim().toUpperCase();
  if (!CURRENCY.test(currency)) throw new PlannerError(`"${text}" isn't a currency: use a code like CZK or EUR.`);
  return currency;
}

// deno-lint-ignore no-explicit-any
function toWant(row: any): Want {
  return {
    id: row.id,
    title: row.title,
    reason: row.reason,
    link: row.link,
    price: row.price,
    currency: row.currency,
    areaId: row.area_id,
    cooldownDays: row.cooldown_days,
    addedOn: row.added_on,
    coolsUntil: row.cools_until,
    decision: row.decision,
    decidedAt: row.decided_at,
    decisionNote: row.decision_note,
    checkedPrice: row.checked_price,
    checkedAt: row.checked_at,
    checkedNote: row.checked_note,
    madeBy: row.made_by,
    deleted: false,
  };
}
