import { z } from "../deps.ts";
import { type Planner, PlannerError } from "../planner/planner.ts";
import type { Want, WantFields } from "../planner/wantList.ts";
import { MAX_DAYS, type WantState, wantState } from "../rules/wants.ts";
import * as format from "./format.ts";
import type { Tool } from "./tools.ts";

const wantId = z.string().describe("The want's id, from get_wants.");
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
 * The wants tools (spec, stories 104 to 108; docs/wants.md). Prices are looked up by Claude with its
 * own web search and only recorded here: GoalMaker never fetches from a shop.
 */
export const wantTools: Tool[] = [
  {
    name: "get_wants",
    title: "Wants",
    description:
      "The owner's wants: things they would like to buy, each waiting out a cooldown before it is decided. " +
      "Each one comes with why it is wanted, its price, where it stands (cooling, ready to decide, or bought or " +
      "dropped), the last price check and the decision note. Ready and cooling ones by default.",
    input: {
      state: z.enum(["ready", "cooling", "decided", "open", "all"]).optional().describe(
        "Which wants: ready, cooling, decided, open (ready and cooling) or all. open by default.",
      ),
    },
    readOnly: true,
    destructive: false,
    run: async (planner, args) => {
      const today = (await planner.now()).today;
      const wanted: string = args.state ?? "open";
      const all = await planner.wants().all();
      if (all.length === 0) return "There are no wants yet. add_want writes one down with its reason.";
      const names = format.names(await planner.areas(), [], new Map());
      const groups: [WantState, string][] = [
        ["ready", "Ready to decide"],
        ["cooling", "Cooling"],
        ["decided", "Decided"],
      ];
      const lines: string[] = [];
      for (const [state, heading] of groups) {
        if (!(wanted === state || wanted === "all" || (wanted === "open" && state !== "decided"))) continue;
        const rows = all.filter((want) => wantState(want, today) === state);
        if (state === "decided") rows.sort((a, b) => (b.decidedAt ?? "").localeCompare(a.decidedAt ?? ""));
        if (rows.length === 0) continue;
        lines.push(`${heading}:`, ...rows.map((want) => format.wantLine(want, state, today, names)));
      }
      return lines.length === 0 ? `No ${wanted} wants.` : lines.join("\n");
    },
  },
  {
    name: "add_want",
    title: "Add a want",
    description:
      "Writes down something the owner would like to buy, with why they want it. It waits out a cooldown before " +
      "it is decided: the owner's thresholds give it days from its price (by default 7 under 1,000, 30 under " +
      "10,000, 90 from there, and 30 with no price), unless the owner picked a number of days. The cooldown is " +
      "fixed once it is added.",
    input: {
      title: z.string().describe("What it is."),
      reason: z.string().describe("Why the owner wants it, in their words. Required: it is the point of a want."),
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
      const want = await planner.wants().add({
        title: args.title,
        reason: args.reason,
        price: args.price,
        currency: args.currency,
        link: args.link,
        areaId: await areaOf(planner, args.area),
        days: args.cooldown_days,
      });
      return [`Added, cooling for ${want.cooldownDays} days:`, await wantText(planner, want)].join("\n");
    },
  },
  {
    name: "update_want",
    title: "Edit a want",
    description:
      "Changes a want's title, reason, price, currency, link or area. What is left out stays as it was, and so " +
      "does its cooldown. An empty link or area clears it. A price found by a check goes through " +
      "record_price_check instead.",
    input: {
      id: wantId,
      title: z.string().optional(),
      reason: z.string().optional(),
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
      };
      const want = await planner.wants().update(args.id, fields);
      return ["Updated:", await wantText(planner, want)].join("\n");
    },
  },
  {
    name: "decide_want",
    title: "Decide a want",
    description:
      "Records the owner's decision on a want: bought or dropped, with an optional note on why, or reopen to take " +
      "a decision back. Only record what the owner decided in this conversation: ask them about each want first " +
      "and never decide one for them. A want can be decided before it is ready if the owner wants to.",
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
