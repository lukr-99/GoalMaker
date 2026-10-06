import { addDays, type Day } from "./day.ts";
import { compareText, type TaskItem, type TaskState } from "./task.ts";

/**
 * The board of a project (docs/projects.md, contracts/vectors/projects.json): where a new item lands,
 * how a column and the task's own state move together, and the order items sit in.
 */
export type BoardColumn = "backlog" | "todo" | "doing" | "done";
/** A column a board shows: the four an item is stored in, then dropped. */
export type ShownColumn = BoardColumn | "dropped";
export type ItemType = "task" | "idea" | "bug";
export type Priority = "low" | "normal" | "high" | "urgent";
export type ProjectStatus = "active" | "paused" | "done";

/** The four columns an item is stored in, left to right. */
export const COLUMNS: BoardColumn[] = ["backlog", "todo", "doing", "done"];

/** The board's last column: every dropped item, whatever column it is stored in (docs/projects.md). */
export const DROPPED = "dropped";

/** The columns a board shows: the four, then dropped. */
export const BOARD_COLUMNS: ShownColumn[] = [...COLUMNS, DROPPED];

/** The priorities, most important first. */
export const PRIORITIES: Priority[] = ["urgent", "high", "normal", "low"];

/** Who can make an item (supabase/migrations/0015_task_made_by.sql). */
export type Maker = "owner" | "claude";
export const MAKERS: Maker[] = ["owner", "claude"];

/** What the board's who-made-it switch can show: everything, or one maker's items. */
export type MakerFilter = "all" | Maker;
export const MAKER_FILTERS: MakerFilter[] = ["all", "owner", "claude"];

const RANK: Record<string, number> = { urgent: 3, high: 2, normal: 1, low: 0 };

export interface ProjectItem {
  id: string;
  name: string;
  description: string;
  areaId: string | null;
  status: ProjectStatus;
  repositoryUrl: string | null;
  localFolder: string | null;
  notes: string;
  position: number;
  deleted: boolean;
  /** Days a done item stays on the board after it was finished; null keeps it until archived by hand. */
  archiveAfterDays?: number | null;
  /** The key its items' ids start with, like GM (docs/projects.md, "Item ids"); null for none. */
  itemKey?: string | null;
}

export interface ProjectMilestone {
  id: string;
  projectId: string;
  name: string;
  position: number;
  deleted: boolean;
}

/** One column with the items in it, in the order the board shows them. */
export interface Column {
  column: ShownColumn;
  items: TaskItem[];
}

/**
 * Whether the switch, set to `filter`, shows an item made by `madeBy`. An item that doesn't say is
 * the owner's, and a filter nobody knows shows everything.
 */
export function shows(filter: string, madeBy: string | null | undefined): boolean {
  return filter === "owner" || filter === "claude" ? (madeBy ?? "owner") === filter : true;
}

/**
 * Whether an item is on its board (contracts/vectors/projects.json 'archive'): anything not done is;
 * a done item is until it is archived by hand or `archiveAfterDays` days after the planning day it was
 * finished, and null days keep it until it is archived by hand.
 */
export function onBoard(
  state: TaskState,
  completedOn: Day | null,
  archiveAfterDays: number | null,
  archivedByHand: boolean,
  today: Day,
): boolean {
  if (state !== "done") return true;
  if (archivedByHand) return false;
  if (archiveAfterDays === null || completedOn === null) return true;
  return today < addDays(completedOn, archiveAfterDays);
}

/** The column a new item of this type lands in: an idea in the backlog, anything else in to do. */
export function columnFor(itemType: string): BoardColumn {
  return itemType === "idea" ? "backlog" : "todo";
}

/** How important a priority is; an unknown one counts as normal. */
export function rank(priority: string | undefined): number {
  return RANK[priority ?? "normal"] ?? 1;
}

/**
 * What moving an item to a board column does to a task in this state: dropped drops it, done
 * finishes it, and any other column reopens a done or dropped one.
 */
export function moved(column: string, state: TaskState): TaskState {
  if (column === DROPPED) return "dropped";
  if (column === "done") return "done";
  return state === "done" || state === "dropped" ? "open" : state;
}

/** The board column an item shows in: dropped for a dropped item, else the column it is stored in. */
export function shownColumn(item: TaskItem): string | null {
  if (!item.boardColumn) return null;
  return item.state === "dropped" ? DROPPED : item.boardColumn;
}

/** What finishing or reopening a task does to the column it sits in. */
export function finishedIn(state: TaskState, column: string): string {
  if (state === "done") return "done";
  return state === "open" && column === "done" ? "todo" : column;
}

/** One column's items in the order the board shows them. */
export function order(items: TaskItem[]): TaskItem[] {
  return [...items].sort((left, right) =>
    rank(right.priority) - rank(left.priority) ||
    (left.position ?? 0) - (right.position ?? 0) ||
    compareText(left.createdAt, right.createdAt) ||
    compareText(left.id, right.id)
  );
}

/**
 * The board columns of a project with their items in order; a column with nothing in it stays. A
 * dropped item is in the dropped column, never in the column it is stored in.
 */
export function board(items: TaskItem[]): Column[] {
  return BOARD_COLUMNS.map((column) => {
    const dropped = column === DROPPED;
    return {
      column,
      items: order(
        items.filter((item) =>
          !item.deleted && (item.state === "dropped") === dropped && (dropped || item.boardColumn === column)
        ),
      ),
    };
  });
}

/**
 * A repository URL in the form every way of writing it shares: `github.com/owner/app` for
 * `https://github.com/Owner/app.git`, `git@github.com:Owner/app` and `https://github.com/owner/app/`.
 * Null when there is nothing to compare.
 */
export function repositoryKey(url: string | null | undefined): string | null {
  const text = (url ?? "").trim();
  if (text === "") return null;
  const scp = /^[A-Za-z0-9._-]+@([A-Za-z0-9._-]+):(.+)$/.exec(text);
  const rest = scp !== null
    ? `${scp[1]}/${scp[2].replace(/^\/+/, "")}`
    : text.replace(/^[A-Za-z][A-Za-z0-9+.-]*:\/\//, "").replace(/^[^/@]+@/, "");
  return trimTail(rest.toLowerCase()) || null;
}

/** A folder in the form both slashes share: `f:/goalmaker` for `F:\GoalMaker\`. Null when empty. */
export function folderKey(path: string | null | undefined): string | null {
  const key = (path ?? "").trim().replace(/[\\/]+/g, "/").replace(/\/+$/, "").toLowerCase();
  return key === "" ? null : key;
}

/**
 * The project a reference points at: its id, its repository URL, a folder inside it, or its name
 * (docs/projects.md). This is how Claude Code names the project of the folder it works in, without
 * the owner having to say which one it is (spec, story 76).
 */
export function matchProject(projects: ProjectItem[], reference: string): ProjectItem | null {
  const text = reference.trim();
  if (text === "") return null;
  const live = projects.filter((project) => !project.deleted);

  const byId = live.find((project) => project.id === text);
  if (byId !== undefined) return byId;

  const repository = repositoryKey(text);
  const byRepository = repository === null
    ? undefined
    : live.find((project) => repositoryKey(project.repositoryUrl) === repository);
  if (byRepository !== undefined) return byRepository;

  // The deepest folder that holds the reference wins, so a project inside another project's folder
  // still gets its own items.
  const folder = folderKey(text);
  let inFolder: ProjectItem | null = null;
  let depth = -1;
  for (const project of live) {
    const own = folderKey(project.localFolder);
    if (folder === null || own === null) continue;
    if ((folder === own || folder.startsWith(`${own}/`)) && own.length > depth) {
      inFolder = project;
      depth = own.length;
    }
  }
  if (inFolder !== null) return inFolder;

  const name = text.toLowerCase();
  return live.find((project) => project.name.trim().toLowerCase() === name) ?? null;
}

/** An item id as typed: a project's key and an item's number, or only the number (#12). */
export interface ItemId {
  key: string | null;
  number: number;
}

const KEY = /^[A-Z][A-Z0-9]{1,5}$/;
const MAX_SUGGESTED = 4;

/** Whether a typed key is kept: 2 to 6 capital letters or digits starting with a letter, after upper-casing. */
export function isItemKey(key: string): boolean {
  return KEY.test(key.trim().toUpperCase());
}

/**
 * A key made from a project's name (contracts/vectors/projects.json 'itemKeys'): the capitals of one
 * word, the first letters of several, or the first three letters of one plain word, at most four, with
 * 2, 3 ... on the end when `taken` (any case) has it. Null when the name gives fewer than two characters.
 */
export function suggestItemKey(name: string, taken: Iterable<string>): string | null {
  const words = name.normalize("NFD").replace(/\p{M}/gu, "").split(/[^A-Za-z0-9]+/)
    .filter((word) => word.length > 0 && !/^[0-9]/.test(word));
  let base: string;
  if (words.length === 0) return null;
  if (words.length === 1) {
    const capitals = words[0].replace(/[^A-Z]/g, "");
    base = capitals.length >= 2 ? capitals : words[0].slice(0, 3);
  } else {
    base = words.map((word) => word[0]).join("");
  }
  base = base.toUpperCase().slice(0, MAX_SUGGESTED);
  if (base.length < 2) return null;
  const used = new Set([...taken].map((key) => key.trim().toUpperCase()));
  if (!used.has(base)) return base;
  for (let next = 2;; next++) {
    if (!used.has(`${base}${next}`)) return `${base}${next}`;
  }
}

/** An item's id as it reads: KEY-number, or #number without a key. */
export function formatItemId(key: string | null | undefined, number: number): string {
  return key ? `${key}-${number}` : `#${number}`;
}

/** An item id read back, in any case and with spaces around it; null for anything else. */
export function parseItemId(text: string): ItemId | null {
  const typed = text.trim().toUpperCase();
  const keyed = /^([A-Z][A-Z0-9]{1,5})-([0-9]{1,9})$/.exec(typed);
  const bare = keyed === null ? /^#([0-9]{1,9})$/.exec(typed) : null;
  const number = Number(keyed?.[2] ?? bare?.[1] ?? 0);
  if (number < 1) return null;
  return { key: keyed?.[1] ?? null, number };
}

// Trailing slashes and a trailing .git, however they are stacked up.
function trimTail(text: string): string {
  let out = text;
  for (;;) {
    const next = out.replace(/\/+$/, "").replace(/\.git$/, "");
    if (next === out) return out;
    out = next;
  }
}
