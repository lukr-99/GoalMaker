// The quick chat's tool loop (docs/assistant.md): the model answers or asks for tools, the tools run
// as the owner, their results go back, until the model answers in words or the rounds run out.
import type { ChatProvider, Message, ToolCall, ToolResult, ToolSpec } from "./chatProvider.ts";

/** Tool rounds a request may take before the chat stops and says so. */
export const MAX_ROUNDS = 8;
/** Tool calls one round may make; more are refused, so one reply can't run away with the owner's data. */
export const MAX_CALLS_PER_ROUND = 10;

export const STOPPED =
  "That needed more steps than I can take in one go, so I stopped. What I changed so far stays; the " +
  "Activity screen lists it. Ask again with a smaller request to carry on.";

export interface LoopResult {
  text: string;
  /** Rounds of tool calls that ran. */
  rounds: number;
}

/**
 * Runs the conversation to a text answer. `run` carries out one offered tool call; a call for a tool
 * that isn't offered (one that deletes, or a made-up name) never reaches it.
 */
export async function runLoop(
  provider: ChatProvider,
  system: string,
  thread: Message[],
  tools: ToolSpec[],
  run: (call: ToolCall) => Promise<ToolResult>,
): Promise<LoopResult> {
  const offered = new Set(tools.map((tool) => tool.name));
  const messages = [...thread];
  for (let round = 0;; round++) {
    const allowCalls = round < MAX_ROUNDS;
    const reply = await provider.reply({ system, messages, tools, allowCalls });
    if (reply.kind === "text") return { text: reply.text.trim(), rounds: round };
    if (!allowCalls) return { text: STOPPED, rounds: round };

    messages.push({ role: "model", text: "", calls: reply.calls, raw: reply.raw });
    const results: ToolResult[] = [];
    for (const [index, call] of reply.calls.entries()) {
      if (index >= MAX_CALLS_PER_ROUND) {
        results.push(refused(call, "Too many tool calls in one go; call fewer at a time."));
      } else if (!offered.has(call.name)) {
        results.push(refused(
          call,
          `There is no tool called ${call.name} here. The chat can't delete anything; the owner can do ` +
            "that in the app.",
        ));
      } else {
        results.push(await run(call));
      }
    }
    messages.push({ role: "tool", results });
  }
}

function refused(call: ToolCall, text: string): ToolResult {
  return { id: call.id, name: call.name, text, isError: true };
}
