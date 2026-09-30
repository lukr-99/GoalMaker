import { addDays, type Day, mondayOf } from "./day.ts";
import { nameBasedUuid } from "./nameBasedUuid.ts";
import { planningDay } from "./planningDay.ts";

/**
 * Tally's rules (docs/tally.md, ADR 0013, contracts/vectors/tally.json), the same ones both apps
 * run: which category and project a moment of foreground time belongs to, when the clock stops for
 * idle, and how a device's intervals become the daily totals that sync.
 */
export type TallyMatch = "app" | "title" | "folder";
export type TallyPlatform = "android" | "windows";

/** One sorting rule: the owner's (with an id and maybe a project) or a shipped default. */
export interface TallyRule {
  match: TallyMatch;
  pattern: string;
  platform: TallyPlatform | "any";
  category: string;
  project?: string | null;
}

/** What was in front: an app (package or executable), and on Windows its window title. */
export interface TallySample {
  platform: TallyPlatform;
  app: string;
  title?: string | null;
}

export interface TallySort {
  category: string;
  project: string | null;
}

/** A stretch of foreground time in local time, already sorted. */
export interface TallyInterval {
  start: string;
  end: string;
  category: string;
  project: string | null;
}

export interface TallyTotal {
  day: Day;
  category: string;
  project: string | null;
  minutes: number;
}

/** Where time goes that no rule claims. */
export const OTHER = "other";

/** The clock stops after this long without input, unless the window is in the Video category. */
export const IDLE_SECONDS = 300;
export const VIDEO = "video";

/** A day holds at most this many minutes, per device and category. */
export const MAX_MINUTES = 1440;

const NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91";
const VS_CODE = " - Visual Studio Code";
const VISUAL_STUDIO = " - Microsoft Visual Studio";

/**
 * The folder an editor's window title names: Visual Studio Code's workspace (the part before
 * " - Visual Studio Code"), Android Studio's project (the part before the first " – "), and Visual
 * Studio's solution (the part before " - Microsoft Visual Studio", without "(Running)" and the like).
 * Null for anything that isn't one of these editors.
 */
export function editorFolder(app: string, title: string | null | undefined): string | null {
  const text = (title ?? "").trim();
  if (text === "") return null;
  const executable = app.trim().toLowerCase();
  let folder: string | null = null;
  if (executable === "code.exe" && text.endsWith(VS_CODE)) {
    const parts = text.slice(0, -VS_CODE.length).split(" - ");
    folder = parts[parts.length - 1];
  } else if (executable === "studio64.exe") {
    folder = text.split(" – ")[0];
  } else if (executable === "devenv.exe") {
    const end = text.indexOf(VISUAL_STUDIO);
    if (end > 0) folder = text.slice(0, end).replace(/\s*\([^)]*\)\s*$/, "");
  }
  const cleaned = folder?.replace(/^[●\s]+/, "").trim() ?? "";
  return cleaned === "" ? null : cleaned;
}

/** The last part of a path, or the whole of a bare name, ignoring case. */
function folderName(path: string | null | undefined): string | null {
  const parts = (path ?? "").trim().replace(/[\\/]+$/, "").split(/[\\/]/);
  const name = parts[parts.length - 1].trim().toLowerCase();
  return name === "" ? null : name;
}

/**
 * The project an editor's folder belongs to: the one project whose local folder has that name. None
 * when no project, or more than one, has it.
 */
export function projectFor(
  folder: string | null,
  projects: { id: string; localFolder: string | null }[],
): string | null {
  const name = folderName(folder);
  if (name === null) return null;
  const found = projects.filter((project) => folderName(project.localFolder) === name);
  return found.length === 1 ? found[0].id : null;
}

function matches(rule: TallyRule, sample: TallySample, folder: string | null): boolean {
  if (rule.platform !== "any" && rule.platform !== sample.platform) return false;
  const pattern = rule.pattern.trim().toLowerCase();
  if (pattern === "") return false;
  switch (rule.match) {
    case "app":
      return sample.app.trim().toLowerCase() === pattern;
    case "title":
      return sample.platform === "windows" && (sample.title ?? "").toLowerCase().includes(pattern);
    case "folder": {
      const name = folderName(folder);
      return name !== null && name === folderName(pattern);
    }
  }
}

/**
 * Where a sample goes: the first of the owner's rules that matches, then the first default, then
 * Other. On Windows a rule's own project wins, otherwise the editor's folder names the project; the
 * phone never links time to a project.
 */
export function sortSample(
  sample: TallySample,
  own: TallyRule[],
  defaults: TallyRule[],
  projects: { id: string; localFolder: string | null }[] = [],
): TallySort {
  const folder = sample.platform === "windows" ? editorFolder(sample.app, sample.title) : null;
  const rule = [...own, ...defaults].find((one) => matches(one, sample, folder));
  const project = sample.platform === "windows" ? rule?.project ?? projectFor(folder, projects) : null;
  return { category: rule?.category ?? OTHER, project };
}

/** Whether the clock runs: not while locked or asleep, and not after five idle minutes unless it is video. */
export function counts(secondsSinceInput: number, category: string, locked: boolean, asleep: boolean): boolean {
  if (locked || asleep) return false;
  return category === VIDEO || secondsSinceInput < IDLE_SECONDS;
}

// Seconds since 1970 of a local date-time like 2026-09-28T23:50 or 2026-09-28T23:50:30, read as if UTC.
function secondsOf(local: string): number {
  const [date, time = "00:00"] = local.split("T");
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute, second = 0] = time.split(":").map(Number);
  return Date.UTC(year, month - 1, day, hour, minute, second) / 1000;
}

function localOf(seconds: number): string {
  return new Date(seconds * 1000).toISOString().slice(0, 19);
}

/**
 * A device's intervals as planning-day totals: each interval is cut at the hour the day starts,
 * time two intervals share is counted once (for the one that started first), and each day, category
 * and project gets its seconds rounded to the nearest minute, at most a whole day. Rows with no
 * minutes are left out; the rest are ordered by day, category and project.
 */
export function dayTotals(intervals: TallyInterval[], startHour: number): TallyTotal[] {
  const sorted = [...intervals].sort((a, b) => secondsOf(a.start) - secondsOf(b.start));
  const seconds = new Map<string, number>();
  let covered = Number.NEGATIVE_INFINITY;
  for (const interval of sorted) {
    let from = Math.max(secondsOf(interval.start), covered);
    const to = secondsOf(interval.end);
    if (to <= from) continue;
    covered = to;
    while (from < to) {
      const day = planningDay(localOf(from), startHour);
      const nextStart = secondsOf(`${addDays(day, 1)}T${String(startHour).padStart(2, "0")}:00`);
      const until = Math.min(to, nextStart);
      const key = JSON.stringify([day, interval.category, interval.project]);
      seconds.set(key, (seconds.get(key) ?? 0) + (until - from));
      from = until;
    }
  }
  const totals: TallyTotal[] = [];
  for (const [key, value] of seconds) {
    const [day, category, project] = JSON.parse(key);
    const minutes = Math.min(MAX_MINUTES, Math.round(value / 60));
    if (minutes > 0) totals.push({ day, category, project, minutes });
  }
  return totals.sort((a, b) =>
    a.day !== b.day
      ? (a.day < b.day ? -1 : 1)
      : a.category !== b.category
      ? (a.category < b.category ? -1 : 1)
      : (a.project ?? "") < (b.project ?? "")
      ? -1
      : (a.project ?? "") > (b.project ?? "")
      ? 1
      : 0
  );
}

/** A synced Tally row as the connector reads it: minutes, never an app or a window. */
export interface TallyRow {
  day: Day;
  deviceKind: "phone" | "pc";
  category: string;
  projectId: string | null;
  minutes: number;
}

export type TallyGrouping = "category" | "project" | "device";

/** One line of a grouping: the key (a category, a project id or null, a device kind) and its minutes. */
export interface TallyGroup {
  key: string | null;
  minutes: number;
}

/** What the Tally place and the stats show: one kind of device, one category, or everything (null). */
export interface TallyFilter {
  kind: "phone" | "pc" | null;
  category: string | null;
}

/** One week of the stats block: its Monday, its minutes, and each category's, most first. */
export interface TallyWeek {
  start: Day;
  minutes: number;
  categories: { category: string; minutes: number }[];
}

/**
 * The `count` weeks ending with the one holding `today`, oldest first, each with the minutes the
 * filter keeps (contracts/vectors/tally.json 'weeks'). A week with nothing is still there, empty.
 */
export function tallyWeeks(rows: TallyRow[], today: Day, count: number, filter: TallyFilter): TallyWeek[] {
  const last = mondayOf(today);
  const weeks: TallyWeek[] = [];
  for (let back = count - 1; back >= 0; back--) {
    const start = addDays(last, -7 * back);
    const end = addDays(start, 6);
    const kept = rows.filter((row) =>
      row.day >= start && row.day <= end &&
      (filter.kind === null || row.deviceKind === filter.kind) &&
      (filter.category === null || row.category === filter.category)
    );
    weeks.push({
      start,
      minutes: kept.reduce((sum, row) => sum + row.minutes, 0),
      categories: groupTally(kept, "category").map((group) => ({ category: group.key!, minutes: group.minutes })),
    });
  }
  return weeks;
}

/** A default category's name, from its key: coding is Coding (contracts/content/tally-rules.json). */
export function defaultCategoryName(key: string): string {
  return key.length === 0 ? key : key[0].toUpperCase() + key.slice(1);
}

/**
 * The rows' minutes grouped by category, project or device kind, most first, then by key with none
 * last. Every device's minutes add up, so a phone and a PC in the same category count together.
 */
export function groupTally(rows: TallyRow[], by: TallyGrouping): TallyGroup[] {
  const sums = new Map<string | null, number>();
  for (const row of rows) {
    const key = by === "category" ? row.category : by === "project" ? row.projectId : row.deviceKind;
    sums.set(key, (sums.get(key) ?? 0) + row.minutes);
  }
  return [...sums]
    .map(([key, minutes]) => ({ key, minutes }))
    .filter((group) => group.minutes > 0)
    .sort((a, b) =>
      b.minutes - a.minutes ||
      (a.key === null ? 1 : b.key === null ? -1 : a.key < b.key ? -1 : a.key > b.key ? 1 : 0)
    );
}

/** "3 h 5 min", "45 min" or "2 h". */
export function durationText(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} min`;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/** The id every device gives its row for a day, category and project, so rewriting a day replaces it. */
export function tallyDayId(
  owner: string,
  day: Day,
  device: string,
  category: string,
  project: string | null,
): Promise<string> {
  return nameBasedUuid(
    NAMESPACE,
    `tally/${owner.toLowerCase()}/${day}/${device.toLowerCase()}/${category}/${project ?? "-"}`,
  );
}
