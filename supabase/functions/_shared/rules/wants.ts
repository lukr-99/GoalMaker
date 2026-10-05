import { addDays, type Day, daysBetween } from "./day.ts";
import { nameBasedUuid } from "./nameBasedUuid.ts";

/**
 * Wants and their cooldowns (docs/wants.md, contracts/vectors/wants.json), the same rules the apps
 * run: the cooldown a new want gets from its price, the day it cools, where it stands on a planning
 * day, which wants a day's notification names, and the stats.
 */
export type Decision = "bought" | "dropped";
export type WantState = "cooling" | "ready" | "decided";
/** What a row is (supabase/migrations/0025_wants_needs.sql): a want to wait out, or a need to buy. */
export type WantKind = "want" | "need";

export const WANT: WantKind = "want";
export const NEED: WantKind = "need";

/** The most days a picked cooldown can be. */
export const MAX_DAYS = 365;

const NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";

export interface WantCooldowns {
  smallUnder: number;
  smallDays: number;
  mediumUnder: number;
  mediumDays: number;
  largeDays: number;
  unpricedDays: number;
  currency: string;
}

/** What a new owner starts with, pinned by the 'defaults' of the vectors. */
export const DEFAULT_COOLDOWNS: WantCooldowns = {
  smallUnder: 1000,
  smallDays: 7,
  mediumUnder: 10000,
  mediumDays: 30,
  largeDays: 90,
  unpricedDays: 30,
  currency: "CZK",
};

export interface WantItem {
  id: string;
  title: string;
  price: number | null;
  currency: string;
  addedOn: Day;
  coolsUntil: Day;
  decision: Decision | null;
  deleted: boolean;
  /** A want, or a need: something to buy, with no cooldown and maybe a day it is needed by. */
  kind: WantKind;
  needBy: Day | null;
}

/** The id of an owner's one row of thresholds, the same on every device. */
export function cooldownsId(owner: string): Promise<string> {
  return nameBasedUuid(NAMESPACE, `want-cooldowns/${owner.toLowerCase()}`);
}

/** The days a new want waits: none for a need, `picked` when the owner chose, otherwise by its price. */
export function cooldownDays(
  price: number | null,
  currency: string,
  cooldowns: WantCooldowns = DEFAULT_COOLDOWNS,
  picked: number | null = null,
  kind: WantKind = WANT,
): number {
  if (kind === NEED) return 0;
  if (picked !== null) return Math.min(Math.max(picked, 0), MAX_DAYS);
  if (price === null || currency !== cooldowns.currency) return cooldowns.unpricedDays;
  if (price < cooldowns.smallUnder) return cooldowns.smallDays;
  if (price < cooldowns.mediumUnder) return cooldowns.mediumDays;
  return cooldowns.largeDays;
}

export function coolsUntil(addedOn: Day, days: number): Day {
  return addDays(addedOn, days);
}

/** Where a want stands on the planning day `today`; null for a deleted want. */
export function wantState(want: WantItem, today: Day): WantState | null {
  if (want.deleted) return null;
  if (want.decision !== null) return "decided";
  return today >= want.coolsUntil ? "ready" : "cooling";
}

/** How far the cooldown has run on `today`, 0 to 1, for the ring. */
export function progress(want: WantItem, today: Day): number {
  if (want.decision !== null) return 1;
  const total = daysBetween(want.addedOn, want.coolsUntil);
  if (total <= 0) return 1;
  return Math.min(Math.max(daysBetween(want.addedOn, today) / total, 0), 1);
}

/**
 * The wants a notification on `today` names: undecided ones that became ready after `lastNotified`
 * and by today, or only today's when there was none before; oldest first, then by title.
 */
export function readyWants(wants: WantItem[], lastNotified: Day | null, today: Day): WantItem[] {
  return wants
    .filter((want) =>
      !want.deleted && want.kind !== NEED && want.decision === null && want.coolsUntil <= today &&
      (lastNotified === null ? want.coolsUntil === today : want.coolsUntil > lastNotified)
    )
    .sort((a, b) => {
      if (a.coolsUntil !== b.coolsUntil) return a.coolsUntil < b.coolsUntil ? -1 : 1;
      const left = a.title.toLowerCase();
      const right = b.title.toLowerCase();
      return left < right ? -1 : left > right ? 1 : 0;
    });
}

/** Bought and dropped, and the dropped prices in `currency` added up; deleted wants and needs never count. */
export function wantStats(wants: WantItem[], currency: string): { bought: number; dropped: number; notSpent: number } {
  const kept = wants.filter((want) => !want.deleted && want.kind !== NEED);
  const dropped = kept.filter((want) => want.decision === "dropped");
  return {
    bought: kept.filter((want) => want.decision === "bought").length,
    dropped: dropped.length,
    notSpent: dropped.filter((want) => want.currency === currency).reduce((sum, want) => sum + (want.price ?? 0), 0),
  };
}

/** The open needs: by the day they are needed by (none last), then when they were added, then title. */
export function openNeeds<T extends WantItem>(wants: T[]): T[] {
  return wants
    .filter((want) => !want.deleted && want.kind === NEED && want.decision === null)
    .sort((a, b) => {
      if (a.needBy !== b.needBy) {
        if (a.needBy === null) return 1;
        if (b.needBy === null) return -1;
        return a.needBy < b.needBy ? -1 : 1;
      }
      if (a.addedOn !== b.addedOn) return a.addedOn < b.addedOn ? -1 : 1;
      const left = a.title.toLowerCase();
      const right = b.title.toLowerCase();
      return left < right ? -1 : left > right ? 1 : 0;
    });
}

/** Whether an open need's day passed before the planning day `today`. */
export function needLate(want: WantItem, today: Day): boolean {
  return want.kind === NEED && want.decision === null && want.needBy !== null && want.needBy < today;
}
