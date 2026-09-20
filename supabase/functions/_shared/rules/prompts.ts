/**
 * Which prompts a review asks (docs/reviews.md, contracts/vectors/reviews.json): the rotation through
 * the library's categories, the prompts the period's facts call for, and the text of a prompt once the
 * period and the subject are filled in.
 */
export type ReviewKindName = "weekly" | "monthly" | "yearly";

export interface ReviewPrompt {
  id: string;
  category: string;
  reviews: ReviewKindName[];
  text: string;
  trigger?: string;
}

export interface PromptLibrary {
  version: number;
  categories: { id: string }[];
  prompts: ReviewPrompt[];
}

export interface PeriodFacts {
  doneTasks: number;
  averageDone: number;
  goals: { title: string; fraction: number; expected: number }[];
  habits: { name: string; missed: number; periods: number; streak: number }[];
  tasks: { title: string; moves: number }[];
}

export interface ReviewQuestion {
  promptId: string;
  text: string;
  subject: string | null;
}

// A goal this far from where it should be, a task moved this often and a streak this long are worth asking about.
const GOAL_GAP = 0.2;
const SLIPPING_MOVES = 3;
const LONG_STREAK = 7;

/** The prompts of the library that suit `kind` and wait for no trigger, in the file's order. */
export function libraryFor(library: PromptLibrary, kind: string): ReviewPrompt[] {
  return library.prompts.filter((prompt) =>
    prompt.trigger === undefined && prompt.reviews.includes(kind as ReviewKindName)
  );
}

/**
 * The prompt of `category` to ask next: the one shown longest ago, which is any it hasn't shown yet, in
 * the file's order. Null when the category has nothing for this kind of review.
 */
export function nextPrompt(
  library: PromptLibrary,
  kind: string,
  category: string,
  shown: string[],
): ReviewPrompt | null {
  const last = lastSeen(shown);
  const suited = libraryFor(library, kind).filter((prompt) => prompt.category === category);
  let best: ReviewPrompt | null = null;
  let bestIndex = Number.MAX_SAFE_INTEGER;
  for (const prompt of suited) {
    const index = last.get(prompt.id) ?? -1;
    if (index < bestIndex) {
      best = prompt;
      bestIndex = index;
    }
  }
  return best;
}

/**
 * The `count` prompts a review asks, walking the categories from the one after the last id in `shown`
 * and taking one from each (a category with nothing left to say is passed over).
 */
export function rotation(library: PromptLibrary, kind: string, shown: string[], count: number): ReviewPrompt[] {
  const suited = libraryFor(library, kind);
  const categories = library.categories
    .map((category) => category.id)
    .filter((category) => suited.some((prompt) => prompt.category === category));
  if (categories.length === 0 || count <= 0) return [];
  const lastId = shown.length > 0 ? shown[shown.length - 1] : null;
  const lastCategory = lastId === null
    ? null
    : library.prompts.find((prompt) => prompt.id === lastId)?.category ?? null;
  const from = lastCategory === null ? 0 : categories.indexOf(lastCategory) + 1;
  const chosen: ReviewPrompt[] = [];
  const seen = [...shown];
  for (let step = 0; step < categories.length && chosen.length < count; step++) {
    const category = categories[(from + step) % categories.length];
    const prompt = nextPrompt(library, kind, category, seen);
    if (prompt === null) continue;
    chosen.push(prompt);
    seen.push(prompt.id);
  }
  return chosen;
}

/** One thing a period's facts call for: the trigger and what it is about, if anything. */
export interface Trigger {
  trigger: string;
  subject: string | null;
}

/**
 * What `facts` call for, worst first within each trigger: a goal behind plan, a habit mostly missed, a
 * task that keeps moving, a long streak, a goal ahead of plan, a period without goals, and a quiet or
 * busy period. The prompt library turns these into questions; the connector's review prompts read them
 * as they are (contracts/vectors/reviews.json, 'reactive').
 */
export function triggers(facts: PeriodFacts): Trigger[] {
  const found: Trigger[] = [];
  const worst = <T>(
    items: T[],
    keep: (item: T) => boolean,
    by: (item: T) => number,
    name: (item: T) => string,
  ): T | null => {
    const kept = items.filter(keep).sort((left, right) =>
      by(right) - by(left) || name(left).localeCompare(name(right))
    );
    return kept.length > 0 ? kept[0] : null;
  };

  const behind = worst(
    facts.goals,
    (goal) => goal.expected - goal.fraction >= GOAL_GAP,
    (goal) => goal.expected - goal.fraction,
    (goal) => goal.title,
  );
  if (behind) found.push({ trigger: "goal_behind", subject: behind.title });
  const missed = worst(
    facts.habits,
    (habit) => habit.periods >= 2 && habit.missed >= Math.ceil(habit.periods / 2),
    (habit) => habit.missed,
    (habit) => habit.name,
  );
  if (missed) found.push({ trigger: "habit_missed", subject: missed.name });
  const slipping = worst(
    facts.tasks,
    (task) => task.moves >= SLIPPING_MOVES,
    (task) => task.moves,
    (task) => task.title,
  );
  if (slipping) found.push({ trigger: "task_slipping", subject: slipping.title });
  const streak = worst(
    facts.habits,
    (habit) => habit.streak >= LONG_STREAK,
    (habit) => habit.streak,
    (habit) => habit.name,
  );
  if (streak) found.push({ trigger: "habit_streak", subject: streak.name });
  const ahead = worst(
    facts.goals,
    (goal) => goal.fraction - goal.expected >= GOAL_GAP,
    (goal) => goal.fraction - goal.expected,
    (goal) => goal.title,
  );
  if (ahead) found.push({ trigger: "goal_ahead", subject: ahead.title });
  if (facts.goals.length === 0) found.push({ trigger: "no_goals", subject: null });
  if (facts.averageDone > 0) {
    if (facts.doneTasks <= facts.averageDone / 2) found.push({ trigger: "quiet_period", subject: null });
    if (facts.doneTasks >= facts.averageDone * 1.5) found.push({ trigger: "busy_period", subject: null });
  }
  return found;
}

/**
 * The triggered prompts `facts` call for, in the order `triggers` gives them; a review of `kind` that
 * has no prompt for a trigger (a yearly one and the quiet or busy period) simply skips it.
 */
export function reactive(library: PromptLibrary, kind: string, facts: PeriodFacts): ReviewQuestion[] {
  const prompts = new Map<string, ReviewPrompt>();
  for (const prompt of library.prompts) {
    if (
      prompt.trigger !== undefined && prompt.reviews.includes(kind as ReviewKindName) && !prompts.has(prompt.trigger)
    ) {
      prompts.set(prompt.trigger, prompt);
    }
  }
  const questions: ReviewQuestion[] = [];
  for (const { trigger, subject } of triggers(facts)) {
    const prompt = prompts.get(trigger);
    if (prompt) questions.push({ promptId: prompt.id, text: promptText(prompt, kind, subject), subject });
  }
  return questions;
}

/** A prompt's text for a `kind` review, with the period named and the subject filled in. */
export function promptText(prompt: ReviewPrompt, kind: string, subject: string | null = null): string {
  return prompt.text.replaceAll("{period}", periodWord(kind)).replaceAll("{subject}", subject ?? "");
}

/** What a review of `kind` calls its period: week, month or year. */
export function periodWord(kind: string): string {
  switch (kind) {
    case "monthly":
      return "month";
    case "yearly":
      return "year";
    default:
      return "week";
  }
}

// Where each id was shown last, so a prompt never shown comes first.
function lastSeen(shown: string[]): Map<string, number> {
  const last = new Map<string, number>();
  shown.forEach((id, index) => last.set(id, index));
  return last;
}
