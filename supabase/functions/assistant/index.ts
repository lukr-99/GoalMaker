// The quick chat (docs/assistant.md, M7): POST .../functions/v1/assistant with the owner's own session
// and the thread so far; the answer is the model's text after it used GoalMaker's tools as that owner.
// The gateway checks the session's JWT (verify_jwt in config.toml); every tool then runs in its own
// transaction as the owner, under row security, logged as the owner's change through the chat.
import { postgres, z } from "../_shared/deps.ts";
import { asOwner } from "../_shared/owner.ts";
import { Planner, PlannerError } from "../_shared/planner/planner.ts";
import type { Message, ToolCall, ToolResult } from "../_shared/assistant/chatProvider.ts";
import { ProviderError } from "../_shared/assistant/chatProvider.ts";
import { chatDeclarations, chatTools, HIDDEN_INPUTS } from "../_shared/assistant/chatTools.ts";
import { chooseProvider, FAKE_HEADER } from "../_shared/assistant/providerChoice.ts";
import { promptFor } from "../_shared/assistant/prompt.ts";
import { runLoop } from "../_shared/assistant/toolLoop.ts";

/** Requests an owner may make in a minute, and in a UTC day. */
const PER_MINUTE = 30;
const PER_DAY = 200;
const MAX_MESSAGES = 60;
const MAX_TEXT = 4000;

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 3, idle_timeout: 20 });
const tools = new Map(chatTools.map((tool) => [tool.name, tool]));

type Code = "unavailable" | "rate_limited" | "provider_limit" | "bad_request" | "failed" | "unauthorized";

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });
}

function failure(status: number, error: Code, message: string, headers: Record<string, string> = {}): Response {
  return json(status, { error, message }, headers);
}

/** The owner a session belongs to. The gateway already checked the token's signature and expiry. */
function ownerOf(request: Request): string | null {
  const token = /^Bearer (.+)$/i.exec(request.headers.get("authorization") ?? "")?.[1];
  const payload = token?.split(".")[1];
  if (!payload) return null;
  try {
    const claims = JSON.parse(atob(payload.replaceAll("-", "+").replaceAll("_", "/")));
    const fresh = typeof claims.exp !== "number" || claims.exp * 1000 > Date.now();
    const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    return claims.role === "authenticated" && typeof claims.sub === "string" && uuid.test(claims.sub) && fresh
      ? claims.sub
      : null;
  } catch {
    return null;
  }
}

/** The thread from the body, or null when it isn't the shape "The call" names. */
function threadOf(body: unknown): Message[] | null {
  const messages = (body as { messages?: unknown })?.messages;
  if (!Array.isArray(messages) || messages.length === 0 || messages.length > MAX_MESSAGES) return null;
  const thread: Message[] = [];
  for (const message of messages) {
    const { role, text } = (message ?? {}) as { role?: unknown; text?: unknown };
    if ((role !== "user" && role !== "model") || typeof text !== "string" || text.length > MAX_TEXT) return null;
    if (role === "user" && text.trim().length === 0) return null;
    thread.push({ role, text } as Message);
  }
  return thread[thread.length - 1].role === "user" ? thread : null;
}

/** Runs one offered tool as the owner, through the chat, after checking its arguments against its shape. */
async function runTool(ownerId: string, call: ToolCall): Promise<ToolResult> {
  const tool = tools.get(call.name)!;
  const given = Object.fromEntries(Object.entries(call.args ?? {}).filter(([name]) => !HIDDEN_INPUTS.has(name)));
  const parsed = z.object(tool.input).safeParse(given);
  if (!parsed.success) {
    const said = parsed.error.issues.map((issue) => `${issue.path.join(".") || "input"}: ${issue.message}`).join("; ");
    return { id: call.id, name: call.name, text: `Those arguments don't fit ${call.name}: ${said}`, isError: true };
  }
  try {
    const text = await asOwner(sql, ownerId, "owner", (db) => tool.run(new Planner(db), parsed.data), "chat");
    return { id: call.id, name: call.name, text, isError: false };
  } catch (error) {
    if (error instanceof PlannerError) return { id: call.id, name: call.name, text: error.message, isError: true };
    console.error(`tool ${call.name} failed`, error);
    return { id: call.id, name: call.name, text: "GoalMaker couldn't do that just now.", isError: true };
  }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-methods": "POST, OPTIONS",
        "access-control-allow-headers": "authorization, apikey, content-type, x-client-info",
      },
    });
  }
  if (request.method !== "POST") return failure(405, "bad_request", "Send the conversation with POST.");

  const ownerId = ownerOf(request);
  if (ownerId === null) return failure(401, "unauthorized", "Sign in to GoalMaker to use the chat.");

  const thread = threadOf(await request.json().catch(() => null));
  if (thread === null) {
    return failure(
      400,
      "bad_request",
      `Send {"messages": [{"role": "user" | "model", "text": "..."}]}, at most ${MAX_MESSAGES} messages of ` +
        `${MAX_TEXT} characters, the last one the owner's.`,
    );
  }

  // The endpoint test's scripted turns are honoured on the local stack only (providerChoice.ts).
  const choice = chooseProvider(request.headers.get(FAKE_HEADER), (name) => Deno.env.get(name));
  if (choice.kind === "bad_script") return failure(400, "bad_request", `${FAKE_HEADER} isn't a list of turns.`);
  if (choice.kind === "unavailable") {
    return failure(503, "unavailable", "The chat isn't set up on this server yet: it has no model key.");
  }
  const provider = choice.provider;

  try {
    const [row] = await sql`select public.assistant_count(${ownerId}, ${PER_MINUTE}, ${PER_DAY}) as verdict`;
    if (row.verdict === "minute") {
      return failure(429, "rate_limited", `That's more than ${PER_MINUTE} messages a minute. Wait a moment.`, {
        "Retry-After": "60",
      });
    }
    if (row.verdict === "day") {
      return failure(
        429,
        "rate_limited",
        `That's the chat's ${PER_DAY} messages for today. It opens again after midnight UTC.`,
      );
    }

    const system = await asOwner(sql, ownerId, "owner", (db) => promptFor(new Planner(db)), "chat");
    const answer = await runLoop(provider, system, thread, chatDeclarations, (call) => runTool(ownerId, call));
    return json(200, { text: answer.text });
  } catch (error) {
    if (error instanceof ProviderError && error.kind === "limit") {
      return failure(429, "provider_limit", "The chat's free model allowance is used up for now. Try again later.");
    }
    console.error("assistant failed", error);
    return failure(502, "failed", "The chat couldn't answer just now. Try again in a moment.");
  }
});
