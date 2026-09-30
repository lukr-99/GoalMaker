import { addDays, type Day } from "../rules/day.ts";
import { type Digest, digest, type DigestPeriod, type DigestWant } from "../rules/digest.ts";
import { periodStart } from "../rules/reviews.ts";
import type { Planner } from "./planner.ts";
import type { Want } from "./wantList.ts";

/** A want in a digest: everything the Wants place shows, with the day it was decided on. */
export type ReviewWant = Want & DigestWant;

/**
 * One period of the owner's GoalMaker (rules/digest.ts), read through their row security: the one
 * builder get_review_digest and the weekly and monthly review prompts share, so the two never
 * disagree (docs/letter.md).
 */
export async function reviewDigest(planner: Planner, period: DigestPeriod): Promise<Digest> {
  const { today } = await planner.now();
  const tasks = await planner.tasks();
  const completedOn = new Map<string, Day>();
  for (const task of tasks) {
    if (task.state === "done" && task.completedAt !== null) {
      completedOn.set(task.id, await planner.dayOf(task.completedAt));
    }
  }
  const wants: ReviewWant[] = [];
  for (const want of await planner.wants().all()) {
    wants.push({ ...want, decidedOn: want.decidedAt === null ? null : await planner.dayOf(want.decidedAt) });
  }
  const last = periodStart(period.kind, addDays(period.start, -1));
  const reviews = [
    ...await planner.reviews(period.kind, 1, period.start),
    ...await planner.reviews(period.kind, 1, last),
  ];
  return digest({
    period,
    today,
    tasks,
    completedOn,
    goals: await planner.goals(),
    entries: await planner.goalEntries(),
    habits: await planner.habits(),
    checkins: await planner.checkins(),
    pauses: await planner.pauses(),
    wants,
    reviews,
  });
}
