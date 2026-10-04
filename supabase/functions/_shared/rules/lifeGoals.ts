import { type Day, daysBetween } from "./day.ts";

/**
 * Life goals (docs/life-goals.md, contracts/vectors/life-goals.json), the parts the connector shows:
 * how far a by date is and the order. The why reminder's moment runs only in the apps.
 */
export type LifeGoalStatus = "open" | "achieved" | "dropped";
export type TimeLeftUnit = "years" | "months" | "days" | "today" | "past";

export interface TimeLeft {
  unit: TimeLeftUnit;
  count: number;
}

export interface LifeGoalOrderItem {
  id: string;
  status: LifeGoalStatus;
  position: number;
  createdAt: string;
  closedAt: string | null;
}

/** How far `by` is from the planning day `today`; null without a by date. */
export function timeLeft(by: Day | null, today: Day): TimeLeft | null {
  if (by === null) return null;
  if (by < today) return { unit: "past", count: 0 };
  if (by === today) return { unit: "today", count: 0 };
  const [byYear, byMonth, byDate] = by.split("-").map(Number);
  const [year, month, date] = today.split("-").map(Number);
  let months = byYear * 12 + byMonth - (year * 12 + month);
  if (byDate < date) months -= 1;
  if (months >= 12) return { unit: "years", count: Math.floor(months / 12) };
  if (months >= 1) return { unit: "months", count: months };
  return { unit: "days", count: daysBetween(today, by) };
}

/** Open ones in the owner's order, then achieved and dropped ones, the most recently closed first. */
export function orderLifeGoals<T extends LifeGoalOrderItem>(goals: readonly T[]): T[] {
  const open = goals.filter((goal) => goal.status === "open").sort((a, b) =>
    a.position - b.position || compareInstants(a.createdAt, b.createdAt) || compareText(a.id, b.id)
  );
  const closed = goals.filter((goal) => goal.status !== "open").sort((a, b) =>
    compareInstants(b.closedAt ?? "", a.closedAt ?? "") || compareText(a.id, b.id)
  );
  return [...open, ...closed];
}

function compareInstants(a: string, b: string): number {
  return (a === "" ? 0 : Date.parse(a)) - (b === "" ? 0 : Date.parse(b));
}

function compareText(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}
