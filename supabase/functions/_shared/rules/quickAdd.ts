import { addDays, type Day } from "./day.ts";
import { type GoalHorizon, periodEnd, periodStart } from "./goals.ts";

/**
 * What the bottom bar on Wants, Habits and Goals reads from a typed line (docs/composer.md, "Adding
 * on Wants, Habits and Goals"; contracts/vectors/quick-add.json). The apps run the same rules; the
 * connector keeps a copy so all three agree. A line is read word by word: a word is what sits
 * between spaces, compared without case and without the punctuation at its end. Anything not
 * understood stays in the title.
 */
export interface WantLine {
  title: string;
  reason: string | null;
  price: number | null;
  currency: string | null;
  waitDays: number | null;
}

export interface HabitLine {
  name: string;
  cadence: "daily" | "weekdays" | "per_week" | "per_month";
  weekdays: number | null;
  times: number | null;
  measure: "check" | "count" | "amount";
  target: number | null;
  unit: string | null;
}

export interface GoalLine {
  title: string;
  horizon: GoalHorizon;
  periodStart: Day;
  mode: "done" | "number";
  target: number | null;
  unit: string | null;
}

/** The longest wait a want can pick, as in the want form. */
export const MAX_WAIT_DAYS = 365;

interface Word {
  text: string;
  key: string;
}

// A word is free to read, used by what was read, or kept as text after it read as something out of range.
const FREE = 0;
const USED = 1;
const KEPT = 2;

const TRAILING = /[.,;:!?]+$/;
const WHOLE = /^[0-9]+$/;
const DECIMAL = /^[0-9]+([.,][0-9]{1,2})?$/;
const GROUPED = /^[0-9]{1,3}([.,][0-9]{3})+$/;
const GROUP_HEAD = /^[0-9]{1,3}$/;
const GROUP = /^[0-9]{3}$/;
const JOINED = /^([0-9][0-9.,]*)(\p{L}+)$/u;
const UNIT = /^\p{L}[\p{L}-]*$/u;

const CURRENCIES: Record<string, string> = {
  "kč": "CZK",
  kc: "CZK",
  czk: "CZK",
  "€": "EUR",
  eur: "EUR",
  euro: "EUR",
  euros: "EUR",
  "$": "USD",
  usd: "USD",
  "£": "GBP",
  gbp: "GBP",
};
const SIGNS = ["€", "$", "£"];

const WAIT_UNITS: Record<string, number> = { day: 1, days: 1, week: 7, weeks: 7, month: 30, months: 30 };
const PER = ["a", "per", "each", "every"];
const DAY_NAMES: Record<string, number> = {
  mon: 1,
  monday: 1,
  tue: 2,
  tues: 2,
  tuesday: 2,
  wed: 4,
  wednesday: 4,
  thu: 8,
  thur: 8,
  thurs: 8,
  thursday: 8,
  fri: 16,
  friday: 16,
  sat: 32,
  saturday: 32,
  sun: 64,
  sunday: 64,
};
const WORK_WEEK = 31;
const WEEKEND = 96;
const MONTHS: Record<string, number> = {
  january: 1,
  jan: 1,
  february: 2,
  feb: 2,
  march: 3,
  mar: 3,
  april: 4,
  apr: 4,
  may: 5,
  june: 6,
  jun: 6,
  july: 7,
  jul: 7,
  august: 8,
  aug: 8,
  september: 9,
  sep: 9,
  sept: 9,
  october: 10,
  oct: 10,
  november: 11,
  nov: 11,
  december: 12,
  dec: 12,
};

/** Small words that never name a unit, so "2 of my friends" is no amount. */
const NOT_UNITS = new Set([
  "a",
  "an",
  "the",
  "of",
  "and",
  "or",
  "to",
  "in",
  "on",
  "at",
  "for",
  "per",
  "each",
  "every",
  "x",
  "day",
  "days",
  "week",
  "weeks",
  "month",
  "months",
  "year",
  "years",
]);

/** Units a habit measures as an amount (asked for its value); any other unit is a count (+1 a tap). */
const AMOUNT_UNITS = new Set([
  "min",
  "mins",
  "minute",
  "minutes",
  "h",
  "hr",
  "hrs",
  "hour",
  "hours",
  "s",
  "sec",
  "secs",
  "second",
  "seconds",
  "km",
  "kms",
  "mi",
  "mile",
  "miles",
  "m",
  "meter",
  "meters",
  "metre",
  "metres",
  "step",
  "steps",
  "page",
  "pages",
  "ml",
  "l",
  "liter",
  "liters",
  "litre",
  "litres",
  "kcal",
  "cal",
  "kg",
  "g",
]);

function words(line: string): Word[] {
  return line.split(/\s+/).filter((text) => text.length > 0).map((text) => ({
    text,
    key: text.toLowerCase().replace(TRAILING, ""),
  }));
}

/** A number as written: one or two decimals after a dot or comma, or groups of three after one. */
function parseNumber(text: string): { value: number; decimal: boolean } | null {
  if (DECIMAL.test(text)) {
    return { value: Number(text.replace(",", ".")), decimal: /[.,]/.test(text) };
  }
  if (GROUPED.test(text)) return { value: Number(text.replace(/[.,]/g, "")), decimal: false };
  return null;
}

class Reader {
  readonly state: number[];

  constructor(readonly words: Word[]) {
    this.state = words.map(() => FREE);
  }

  key(i: number): string | null {
    return i < this.words.length && this.state[i] === FREE ? this.words[i].key : null;
  }

  mark(from: number, to: number, state: number) {
    for (let i = from; i < to; i++) this.state[i] = state;
  }

  rest(): string {
    return this.words.filter((_, i) => this.state[i] !== USED).map((w) => w.text).join(" ");
  }

  /** A number from word `i`, maybe across words ("10 000"); `end` is the word after it. */
  number(i: number): { value: number; decimal: boolean; end: number } | null {
    const head = this.key(i);
    if (head === null) return null;
    if (GROUP_HEAD.test(head)) {
      let end = i + 1;
      let digits = head;
      while (this.key(end) !== null && GROUP.test(this.key(end)!)) digits += this.key(end++);
      if (end > i + 1) return { value: Number(digits), decimal: false, end };
    }
    const one = parseNumber(head);
    return one === null ? null : { ...one, end: i + 1 };
  }

  /** A unit word at `i`: letters (a hyphen inside is fine), and not one of the small words. */
  unit(i: number): string | null {
    if (this.key(i) === null) return null;
    const text = this.words[i].text.replace(TRAILING, "");
    return UNIT.test(text) && text.length <= 20 && !NOT_UNITS.has(text.toLowerCase()) ? text : null;
  }

  /** A number and its unit from word `i`, apart ("30 min") or together ("30min"). */
  measure(i: number): { value: number; decimal: boolean; unit: string; end: number } | null {
    const number = this.number(i);
    if (number !== null) {
      const unit = this.unit(number.end);
      return unit === null ? null : { ...number, unit, end: number.end + 1 };
    }
    const key = this.key(i);
    const joined = key === null ? null : JOINED.exec(this.words[i].text.replace(TRAILING, ""));
    if (joined === null) return null;
    const value = parseNumber(joined[1]);
    const unit = joined[2];
    if (value === null || NOT_UNITS.has(unit.toLowerCase())) return null;
    return { ...value, unit, end: i + 1 };
  }

  /** "a day", "per day" or "each day" at `i`: how many words, or 0. */
  perDay(i: number): number {
    return PER.includes(this.key(i) ?? "") && this.key(i + 1) === "day" ? 2 : 0;
  }
}

/** A want: "Kindle 3290 Kč wait 2 weeks because I read on the train". */
export function readWantLine(line: string): WantLine {
  const all = words(line);
  const cut = all.findIndex((w) => w.key === "because");
  const reason = cut < 0 ? "" : all.slice(cut + 1).map((w) => w.text).join(" ");
  const read = new Reader(cut < 0 ? all : all.slice(0, cut));
  let price: number | null = null;
  let currency: string | null = null;
  let waitDays: number | null = null;

  for (let i = 0; i < read.words.length && price === null; i++) {
    const key = read.key(i);
    if (key === null) continue;
    const sign = SIGNS.find((s) => key.startsWith(s));
    const joined = /^([0-9][0-9.,]*)(\p{L}+|[€$£])$/u.exec(key);
    if (sign !== undefined && parseNumber(key.slice(sign.length)) !== null) {
      price = parseNumber(key.slice(sign.length))!.value;
      currency = CURRENCIES[sign];
      read.mark(i, i + 1, USED);
    } else if (joined !== null && CURRENCIES[joined[2]] !== undefined && parseNumber(joined[1]) !== null) {
      price = parseNumber(joined[1])!.value;
      currency = CURRENCIES[joined[2]];
      read.mark(i, i + 1, USED);
    } else {
      const number = read.number(i);
      const code = number === null ? null : read.key(number.end);
      if (number !== null && code !== null && CURRENCIES[code] !== undefined) {
        price = number.value;
        currency = CURRENCIES[code];
        read.mark(i, number.end + 1, USED);
      }
    }
  }

  for (let i = 0; i < read.words.length && waitDays === null; i++) {
    if (read.key(i) !== "wait") continue;
    let j = i + 1;
    if (read.key(j) === "for") j++;
    const amount = read.key(j);
    const n = amount === null
      ? null
      : WHOLE.test(amount)
      ? Number(amount)
      : ["a", "an", "one"].includes(amount)
      ? 1
      : null;
    const per = WAIT_UNITS[read.key(j + 1) ?? ""];
    if (n === null || per === undefined) continue;
    const days = n * per;
    if (days >= 1 && days <= MAX_WAIT_DAYS) {
      waitDays = days;
      read.mark(i, j + 2, USED);
    } else {
      read.mark(i, j + 2, KEPT);
    }
  }

  return { title: read.rest(), reason: reason.length > 0 ? reason : null, price, currency, waitDays };
}

/** A habit: "Swim 2 times a week", "Read 20 minutes every day", "Piano every mon and thu". */
export function readHabitLine(line: string): HabitLine {
  const read = new Reader(words(line));
  const habit: HabitLine = {
    name: "",
    cadence: "daily",
    weekdays: null,
    times: null,
    measure: "check",
    target: null,
    unit: null,
  };

  for (let i = 0; i < read.words.length; i++) {
    const found = cadenceAt(read, i);
    if (found === null) continue;
    if (!found.fits) {
      read.mark(i, found.end, KEPT);
      continue;
    }
    habit.cadence = found.cadence;
    habit.weekdays = found.weekdays;
    habit.times = found.times;
    read.mark(i, found.end, USED);
    break;
  }

  for (let i = 0; i < read.words.length; i++) {
    const key = read.key(i);
    if (key === null) continue;
    const times = WHOLE.test(key) && Number(key) >= 1 && ["times", "time"].includes(read.key(i + 1) ?? "")
      ? { n: Number(key), end: i + 2 }
      : (key === "once" || key === "twice") && read.perDay(i + 1) > 0
      ? { n: key === "once" ? 1 : 2, end: i + 1 }
      : null;
    if (times !== null) {
      habit.measure = "count";
      habit.target = times.n;
      read.mark(i, times.end + read.perDay(times.end), USED);
      break;
    }
    const measure = read.measure(i);
    if (measure === null || measure.value <= 0) continue;
    habit.measure = measure.decimal || AMOUNT_UNITS.has(measure.unit.toLowerCase()) ? "amount" : "count";
    habit.target = measure.value;
    habit.unit = measure.unit;
    read.mark(i, measure.end + read.perDay(measure.end), USED);
    break;
  }

  habit.name = read.rest();
  return habit;
}

type Cadence = Pick<HabitLine, "cadence" | "weekdays" | "times"> & { end: number; fits: boolean };

function cadenceAt(read: Reader, i: number): Cadence | null {
  const key = read.key(i);
  if (key === null) return null;
  const k = (offset: number) => read.key(i + offset) ?? "";
  const per = (n: number, period: string, end: number): Cadence => {
    const week = period === "week";
    return {
      cadence: week ? "per_week" : "per_month",
      weekdays: null,
      times: n,
      end,
      fits: n >= 1 && n <= (week ? 7 : 31),
    };
  };
  const period = (word: string) => word === "week" || word === "month";

  const nx = /^([0-9]+)x$/.exec(key);
  if (WHOLE.test(key) && ["x", "times", "time"].includes(k(1)) && PER.includes(k(2)) && period(k(3))) {
    return per(Number(key), k(3), i + 4);
  }
  if (nx !== null && PER.includes(k(1)) && period(k(2))) return per(Number(nx[1]), k(2), i + 3);
  if ((key === "once" || key === "twice") && PER.includes(k(1)) && period(k(2))) {
    return per(key === "once" ? 1 : 2, k(2), i + 3);
  }
  if (key === "weekly" || key === "monthly") return per(1, key === "weekly" ? "week" : "month", i + 1);

  const daily: Cadence = { cadence: "daily", weekdays: null, times: null, end: i + 1, fits: true };
  if (key === "daily") return daily;
  if ((key === "every" || key === "each") && k(1) === "day") return { ...daily, end: i + 2 };

  const days = (mask: number, end: number): Cadence => ({
    cadence: "weekdays",
    weekdays: mask,
    times: null,
    end,
    fits: true,
  });
  if (key === "weekdays") return days(WORK_WEEK, i + 1);
  if (key === "weekends") return days(WEEKEND, i + 1);
  if (key === "every" || key === "on") {
    if (k(1) === "weekday" || k(1) === "weekdays") return days(WORK_WEEK, i + 2);
    if (k(1) === "weekend" || k(1) === "weekends") return days(WEEKEND, i + 2);
    if (DAY_NAMES[k(1)] === undefined) return null;
    let mask = DAY_NAMES[k(1)];
    let j = i + 1;
    while (true) {
      if (DAY_NAMES[read.key(j + 1) ?? ""] !== undefined) {
        j += 1;
      } else if (["and", "&", ""].includes(read.key(j + 1) ?? "-") && DAY_NAMES[read.key(j + 2) ?? ""] !== undefined) {
        j += 2;
      } else {
        break;
      }
      mask |= DAY_NAMES[read.key(j)!];
    }
    return days(mask, j + 1);
  }
  return null;
}

/** A goal: "Run 30 km this week", "Read 3 books in November". */
export function readGoalLine(line: string, today: Day): GoalLine {
  const read = new Reader(words(line));
  let horizon: GoalHorizon = "week";
  let start = periodStart("week", today);
  const next = (h: GoalHorizon) => periodStart(h, addDays(periodEnd(h, periodStart(h, today)), 1));
  const year = Number(today.slice(0, 4));
  const month = Number(today.slice(5, 7));

  for (let i = 0; i < read.words.length; i++) {
    const key = read.key(i);
    if (key === null) continue;
    const second = read.key(i + 1) ?? "";
    const found: [GoalHorizon, Day, number] | "kept" | null = (key === "this" || key === "next") &&
        (second === "year" || second === "month" || second === "week")
      ? [second, key === "this" ? periodStart(second, today) : next(second), 2]
      : key === "today"
      ? ["day", today, 1]
      : key === "tomorrow"
      ? ["day", addDays(today, 1), 1]
      : key === "in" && MONTHS[second] !== undefined
      ? ["month", monthStart(MONTHS[second] < month ? year + 1 : year, MONTHS[second]), 2]
      : key === "in" && /^[0-9]{4}$/.test(second)
      ? Number(second) >= year ? ["year", `${second}-01-01`, 2] : "kept"
      : null;
    if (found === null) continue;
    if (found === "kept") {
      read.mark(i, i + 2, KEPT);
      continue;
    }
    [horizon, start] = found;
    read.mark(i, i + found[2], USED);
    break;
  }

  let target: number | null = null;
  let unit: string | null = null;
  for (let i = 0; i < read.words.length && target === null; i++) {
    const measure = read.measure(i);
    if (measure === null || measure.value <= 0) continue;
    target = measure.value;
    unit = measure.unit;
  }

  return { title: read.rest(), horizon, periodStart: start, mode: target === null ? "done" : "number", target, unit };
}

function monthStart(year: number, month: number): Day {
  return `${year}-${String(month).padStart(2, "0")}-01`;
}
