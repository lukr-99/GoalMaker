import { z } from "../deps.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import type { Want, WantFields } from "../planner/wantList.ts";
import { readWantLine } from "../rules/quickAdd.ts";
import { MAX_DAYS, NEED, openNeeds, type WantState, wantState } from "../rules/wants.ts";
import * as format from "./format.ts";
import type { Tool } from "./tools.ts";

const wantId = z.string().describe("The want's or need's id, from get_wants.");
const currency = z.string().describe("A three-letter currency code like CZK or EUR. The owner's currency by default.");

/** A want's line as it stands today, with the names its area needs. */
async function wantText(planner: Planner, want: Want): Promise<string> {
  const today = (await planner.now()).today;
  const names = format.names(await planner.areas(), [], new Map());
  return format.wantLine(want, wantState(want, today)!, today, names);
}

/** The area a want is filed under, by name; an empty name clears it, and a new one is created. */
async function areaOf(planner: Planner, name: string | undefined): Promise<string | null | undefined> {
  if (name === undefined) return undefined;
  if (name.trim() === "") return null;
  return (await planner.findOrCreateArea(name)).id;
}

/**
 * The wants tools (spec, stories 104 to 108; docs/wants.md), for needs too: a need is something to buy
 * rather than wait out, with no cooldown and maybe a day it is needed by. Prices are looked up by Claude
 * with its own web search and only recorded here: GoalMaker never fetches from a shop.
 */
export const wantTools: Tool[] = [
  {
    name: "get_wants",
    title: "Wants",
    description:
      "The owner's wants and needs. A want is something they would like to buy, waiting out a cooldown before " +
      "it is decided; a need is something to buy, with no cooldown and maybe a day it is needed by. Each one " +
      "comes with why, its price, where it stands (cooling, ready to decide, or bought or dropped; a need is late " +
      "once its day passed), the last price check and the decision note. Open needs come first, by the day they " +
      "are needed by, then the ready and cooling wants.",
    input: {
      state: z.enum(["ready", "cooling", "decided", "open", "all"]).optional().describe(
        "Which wants: ready, cooling, decided, open (ready and cooling) or all. open by default.",
      ),
      kind: z.enum(["want", "need", "all"]).optional().describe("Only wants, only needs, or all. all by default."),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const today = (await planner.now()).today;
      const wanted: string = args.state ?? "open";
      const kind: string = args.kind ?? "all";
      const all = (await planner.wants().all()).filter((want) => kind === "all" || want.kind === kind);
      if (all.length === 0) {
        return kind === "need"
          ? "There are no needs yet. add_want with kind need writes one down."
          : "There are no wants yet. add_want writes one down with its reason.";
      }
      const names = format.names(await planner.areas(), [], new Map());
      const shows = (state: WantState) =>
        wanted === state || wanted === "all" || (wanted === "open" && state !== "decided");
      const lines: string[] = [];
      const needs = openNeeds(all);
      if (shows("ready") && needs.length > 0) {
        lines.push("Needs:", ...needs.map((need) => format.wantLine(need, "ready", today, names)));
      }
      const groups: [WantState, string][] = [
        ["ready", "Ready to decide"],
        ["cooling", "Cooling"],
        ["decided", "Decided"],
      ];
      for (const [state, heading] of groups) {
        if (!shows(state)) continue;
        const rows = all.filter((want) =>
          wantState(want, today) === state && (state === "decided" || want.kind !== NEED)
        );
        if (state === "decided") rows.sort((a, b) => (b.decidedAt ?? "").localeCompare(a.decidedAt ?? ""));
        if (rows.length === 0) continue;
        lines.push(`${heading}:`, ...rows.map((want) => format.wantLine(want, state, today, names)));
      }
      return lines.length === 0 ? `No ${wanted} ${kind === "need" ? "needs" : "wants"}.` : lines.join("\n");
    },
  },
  {
    name: "add_want",
    title: "Add a want",
    description:
      "Writes down something the owner would like to buy, with why they want it. It waits out a cooldown before " +
      "it is decided: the owner's thresholds give it days from its price (by default 7 under 1,000, 30 under " +
      "10,000, 90 from there, and 30 with no price), unless the owner picked a number of days. The cooldown is " +
      'fixed once it is added. A short line like "Kindle 3290 Kč wait 2 weeks because I read on the train" can ' +
      "go in line instead of title, price, currency, cooldown_days and reason. With kind need it is something " +
      "the owner has to buy: no cooldown, the reason may be left out, and need_by is the day it is needed by.",
    input: {
      line: z.string().optional().describe(
        'The want as the owner said it, like "Kindle 3290 Kč wait 2 weeks because I read on the train": the ' +
          "price with its currency, the wait and the reason after because are read from it. Fields given apart win.",
      ),
      title: z.string().optional().describe("What it is. Needed without a line."),
      reason: z.string().optional().describe(
        "Why the owner wants it, in their words. Required, here or after because in the line: it is the point of " +
          "a want. Ask the owner when it is missing. A need may leave it out.",
      ),
      kind: z.enum(["want", "need"]).optional().describe("need: something to buy, no cooldown."),
      need_by: z.string().optional().describe("The day a need is needed by, YYYY-MM-DD."),
      price: z.number().optional().describe("What it costs, if known."),
      currency: currency.optional(),
      link: z.string().optional().describe("Where it is sold or described."),
      area: z.string().optional().describe("An area's name, like Home."),
      cooldown_days: z.number().int().min(0).max(MAX_DAYS).optional().describe(
        "The days it waits, only when the owner picked them; the price decides otherwise.",
      ),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const read = typeof args.line === "string" && args.line.trim() !== "" ? readWantLine(args.line) : null;
      const priced = args.price === undefined && read?.price != null;
      const want = await planner.wants().add({
        title: args.title ?? read?.title,
        reason: args.reason ?? read?.reason ?? undefined,
        price: priced ? read!.price! : args.price,
        currency: args.currency ?? (priced ? read!.currency! : undefined),
        link: args.link,
        areaId: await areaOf(planner, args.area),
        days: args.cooldown_days ?? read?.waitDays ?? undefined,
        kind: args.kind,
        needBy: args.need_by,
      });
      const added = want.kind === NEED
        ? "Added a need, ready to buy:"
        : `Added, cooling for ${want.cooldownDays} days:`;
      return [added, await wantText(planner, want)].join("\n");
    },
  },
  {
    name: "update_want",
    title: "Edit a want",
    description:
      "Changes a want's or need's title, reason, price, currency, link or area, or a need's need_by. What is " +
      "left out stays as it was, and so does its cooldown. An empty link, area or need_by clears it, and so " +
      "does an empty reason on a need. A price found by a check goes through record_price_check instead.",
    input: {
      id: wantId,
      title: z.string().optional(),
      reason: z.string().optional(),
      need_by: z.string().optional().describe("A need's day it is needed by, YYYY-MM-DD; empty for none."),
      price: z.number().nullable().optional().describe("The price the owner gives it; null for none."),
      currency: currency.optional(),
      link: z.string().optional(),
      area: z.string().optional(),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const fields: WantFields = {
        title: args.title,
        reason: args.reason,
        price: args.price,
        currency: args.currency,
        link: args.link === undefined ? undefined : args.link.trim() === "" ? null : args.link,
        areaId: await areaOf(planner, args.area),
        needBy: args.need_by,
      };
      const want = await planner.wants().update(args.id, fields);
      return ["Updated:", await wantText(planner, want)].join("\n");
    },
  },
  {
    name: "decide_want",
    title: "Decide a want",
    description:
      "Records the owner's decision on a want or need: bought or dropped, with an optional note on why, or reopen " +
      "to take a decision back. Only record what the owner decided in this conversation: ask them about each " +
      "want first and never decide one for them. A want can be decided before it is ready if the owner wants to.",
    input: {
      id: wantId,
      decision: z.enum(["bought", "dropped", "reopen"]).describe("bought, dropped, or reopen to undecide it."),
      note: z.string().optional().describe("Why, in a few words, like where it was bought or what did instead."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const wants = planner.wants();
      if (args.decision === "reopen") {
        const want = await wants.reopen(args.id);
        return ["Reopened:", await wantText(planner, want)].join("\n");
      }
      const want = await wants.decide(args.id, args.decision, args.note);
      return [`Marked ${args.decision}:`, await wantText(planner, want)].join("\n");
    },
  },
  {
    name: "record_price_check",
    title: "Record a price check",
    description:
      "Keeps what a price check found: the best current price, where it was found, and any alternatives worth a " +
      "look. Look the price up yourself with your own web search first; GoalMaker never fetches from a shop. The " +
      "price is in the want's currency, so convert it and say so in where when the shop sells in another one. A " +
      "new check replaces the last one.",
    input: {
      id: wantId,
      price: z.number().describe("The best price found, in the want's currency."),
      where: z.string().describe("Where it was found: the shop and a link."),
      alternatives: z.string().optional().describe("Alternatives found, each with its price and where."),
    },
    readOnly: false,
    destructive: false,
    run: async (planner, args) => {
      const where = (args.where as string).trim();
      if (where.length === 0) throw new PlannerError("Say where the price was found.");
      const alternatives = (args.alternatives as string | undefined)?.trim() ?? "";
      const note = alternatives.length === 0 ? where : `${where}\nAlternatives: ${alternatives}`;
      const want = await planner.wants().recordPriceCheck(args.id, args.price, note);
      return ["Price check recorded:", await wantText(planner, want)].join("\n");
    },
  },
];
