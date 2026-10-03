import { type Day, mondayOf } from "./day.ts";
import { nameBasedUuid } from "./nameBasedUuid.ts";

/** The kinds of review (supabase/migrations/0008_reviews.sql). */
export type ReviewKind = "weekly" | "monthly" | "yearly";

const NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";

/** The start of the period `day` falls in: its week's Monday, its month's first day, or January 1. */
export function periodStart(kind: ReviewKind, day: Day): Day {
  switch (kind) {
    case "weekly":
      return mondayOf(day);
    case "monthly":
      return `${day.slice(0, 7)}-01`;
    case "yearly":
      return `${day.slice(0, 4)}-01-01`;
  }
}

/** The January nudge: the year that has begun, and whether last year's review and this year's goals are still to do. */
export interface NewYearNudge {
  year: number;
  review: boolean;
  goals: boolean;
}

/**
 * The January nudge for planning day `today` (spec story 66, 'newYear' in contracts/vectors/reviews.json): null
 * outside January, once dismissed this year (`dismissedYear` is the year it was last dismissed), or when last year
 * is reviewed and this year has goals. `yearGoals` counts this year's year goals that are neither deleted nor dropped.
 */
export function newYear(
  today: Day,
  yearGoals: number,
  lastYearReviewed: boolean,
  dismissedYear: number | null,
): NewYearNudge | null {
  const year = Number(today.slice(0, 4));
  if (today.slice(5, 7) !== "01" || dismissedYear === year) return null;
  const nudge = { year, review: !lastYearReviewed, goals: yearGoals === 0 };
  return nudge.review || nudge.goals ? nudge : null;
}

/** The id every writer gives the owner's review of `kind` for the period starting on `start` (contracts/vectors/reviews.json). */
export function reviewId(owner: string, kind: ReviewKind, start: Day): Promise<string> {
  return nameBasedUuid(NAMESPACE, `review/${owner.toLowerCase()}/${kind}/${start}`);
}
