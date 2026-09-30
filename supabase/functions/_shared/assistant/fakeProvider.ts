// A provider that plays scripted turns, for tests (docs/assistant.md, "Tests"). No model is called.
import type { ChatProvider, ChatRequest, Reply, ToolCall } from "./chatProvider.ts";

/**
 * One scripted model turn: tools to call, or text. In text, `{{results}}` becomes the results of the
 * last round of tool calls and `{{tools}}` the names of the tools offered, so a test can see both.
 */
export type FakeTurn = { calls: ToolCall[] } | { text: string };

export class FakeProvider implements ChatProvider {
  private next = 0;
  /** Every request the provider was given, in order. */
  readonly requests: ChatRequest[] = [];

  constructor(private readonly turns: FakeTurn[]) {}

  /** The turns from JSON (the endpoint test's header), or null when they aren't a list of turns. */
  static parse(json: string): FakeProvider | null {
    let turns: unknown;
    try {
      turns = JSON.parse(json);
    } catch {
      return null;
    }
    if (!Array.isArray(turns)) return null;
    const valid = turns.every((turn) =>
      typeof turn === "object" && turn !== null &&
      (typeof turn.text === "string" || (Array.isArray(turn.calls) &&
        turn.calls.every((call: unknown) => typeof (call as ToolCall)?.name === "string")))
    );
    return valid ? new FakeProvider(turns as FakeTurn[]) : null;
  }

  reply(request: ChatRequest): Promise<Reply> {
    this.requests.push(request);
    const turn = this.turns[this.next++] ?? { text: "The script has ended." };
    if ("calls" in turn) {
      return Promise.resolve({
        kind: "calls",
        calls: turn.calls.map((call, index) => ({
          id: `fake-${this.next}-${index}`,
          name: call.name,
          args: call.args ?? {},
        })),
      });
    }
    const last = [...request.messages].reverse().find((message) => message.role === "tool");
    const results = last?.role === "tool" ? last.results.map((result) => result.text).join("\n") : "";
    const text = turn.text
      .replaceAll("{{results}}", results)
      .replaceAll("{{tools}}", request.tools.map((tool) => tool.name).join(","));
    return Promise.resolve({ kind: "text", text });
  }
}
