// The quick chat's model: Gemini through its REST API and function calling (docs/assistant.md). The
// key is the GEMINI_API_KEY function secret; it never leaves the server.
import {
  type ChatProvider,
  type ChatRequest,
  type Message,
  ProviderError,
  type Reply,
  type ToolCall,
} from "./chatProvider.ts";

/** The free-tier Flash model. `-latest` follows Google's current Flash, so a retired version can't break the chat. */
export const GEMINI_MODEL = "gemini-flash-latest";
const ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models";
/** How long one model call may take before the request fails. */
const TIMEOUT_MS = 40_000;

// deno-lint-ignore no-explicit-any
type Json = any;

export class GeminiProvider implements ChatProvider {
  constructor(
    private readonly apiKey: string,
    private readonly fetcher: typeof fetch = fetch,
    private readonly model: string = GEMINI_MODEL,
  ) {}

  async reply(request: ChatRequest): Promise<Reply> {
    let response: Response;
    try {
      response = await this.fetcher(`${ENDPOINT}/${this.model}:generateContent`, {
        method: "POST",
        headers: { "content-type": "application/json", "x-goog-api-key": this.apiKey },
        body: JSON.stringify(geminiBody(request)),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
    } catch (error) {
      throw new ProviderError("failed", `Gemini could not be reached: ${error}`);
    }
    const body: Json = await response.json().catch(() => null);
    if (!response.ok) {
      const status = body?.error?.status;
      if (response.status === 429 || status === "RESOURCE_EXHAUSTED") {
        throw new ProviderError("limit", "Gemini's free tier is used up for now.");
      }
      throw new ProviderError("failed", `Gemini answered ${response.status} ${status ?? ""}: ${body?.error?.message}`);
    }
    return replyOf(body);
  }
}

/** The generateContent body for a request. */
export function geminiBody(request: ChatRequest): Json {
  const body: Json = {
    systemInstruction: { parts: [{ text: request.system }] },
    contents: request.messages.map(contentOf),
  };
  if (request.tools.length > 0) {
    body.tools = [{ functionDeclarations: request.tools }];
    body.toolConfig = { functionCallingConfig: { mode: request.allowCalls ? "AUTO" : "NONE" } };
  }
  return body;
}

function contentOf(message: Message): Json {
  switch (message.role) {
    case "user":
      return { role: "user", parts: [{ text: message.text }] };
    case "model":
      // A model turn from this request goes back exactly as Gemini sent it, thought signatures and all.
      if (message.raw !== undefined) return { role: "model", parts: message.raw };
      return {
        role: "model",
        parts: message.calls?.length
          ? message.calls.map((call) => ({ functionCall: { name: call.name, args: call.args } }))
          : [{ text: message.text }],
      };
    case "tool":
      return {
        role: "user",
        parts: message.results.map((result) => ({
          functionResponse: {
            ...(result.id === undefined ? {} : { id: result.id }),
            name: result.name,
            response: result.isError ? { error: result.text } : { result: result.text },
          },
        })),
      };
  }
}

/** Gemini's answer as a reply: the tools it called, or its text without the thinking parts. */
export function replyOf(body: Json): Reply {
  const candidate = body?.candidates?.[0];
  const parts: Json[] = candidate?.content?.parts ?? [];
  const calls: ToolCall[] = parts
    .filter((part) => part.functionCall?.name)
    .map((part) => ({
      ...(part.functionCall.id ? { id: part.functionCall.id } : {}),
      name: part.functionCall.name,
      args: part.functionCall.args ?? {},
    }));
  if (calls.length > 0) return { kind: "calls", calls, raw: parts };
  const text = parts
    .filter((part) => typeof part.text === "string" && part.thought !== true)
    .map((part) => part.text)
    .join("")
    .trim();
  if (text.length > 0) return { kind: "text", text };
  const why = body?.promptFeedback?.blockReason ?? candidate?.finishReason ?? "no candidate";
  throw new ProviderError("failed", `Gemini gave no answer (${why}).`);
}
