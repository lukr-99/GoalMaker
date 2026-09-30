// The model behind the quick chat (docs/assistant.md). The tool loop only talks to this interface, so
// Gemini can be swapped for another model (a local Ollama one on the PC, say) without touching it.

/** One tool the model may call: its name, what it does, and its input as a Gemini-style schema. */
export interface ToolSpec {
  name: string;
  description: string;
  /** Left out when the tool takes no input. */
  parameters?: Schema;
}

/** The OpenAPI subset of JSON Schema that Gemini's function declarations accept. */
export interface Schema {
  type: "STRING" | "NUMBER" | "INTEGER" | "BOOLEAN" | "ARRAY" | "OBJECT";
  description?: string;
  nullable?: boolean;
  enum?: string[];
  minimum?: number;
  maximum?: number;
  items?: Schema;
  properties?: Record<string, Schema>;
  required?: string[];
}

export interface ToolCall {
  /** The provider's id for the call, when it gives one, so the result can be matched to it. */
  id?: string;
  name: string;
  args: Record<string, unknown>;
}

export interface ToolResult {
  id?: string;
  name: string;
  text: string;
  isError: boolean;
}

/**
 * The conversation as the loop keeps it. `raw` is the provider's own form of a model turn (Gemini's
 * parts with their thought signatures), handed back unchanged on the next round.
 */
export type Message =
  | { role: "user"; text: string }
  | { role: "model"; text: string; calls?: ToolCall[]; raw?: unknown }
  | { role: "tool"; results: ToolResult[] };

/** What the model answered: plain text, or tools it wants called first. */
export type Reply =
  | { kind: "text"; text: string }
  | { kind: "calls"; calls: ToolCall[]; raw?: unknown };

export interface ChatRequest {
  system: string;
  messages: Message[];
  tools: ToolSpec[];
  /** False on the last round: the model must answer in words. */
  allowCalls: boolean;
}

export interface ChatProvider {
  reply(request: ChatRequest): Promise<Reply>;
}

/** A provider failure: `limit` when the provider's own quota is used up, `failed` for anything else. */
export class ProviderError extends Error {
  constructor(readonly kind: "limit" | "failed", message: string) {
    super(message);
  }
}
