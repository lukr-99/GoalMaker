// The Claude connector (docs/connector.md, ADR 0003): a remote MCP server at
// .../functions/v1/connector/<secret>. The secret resolves to its owner through connector_resolve;
// every tool then runs in one transaction as that owner, under row security, logged as Claude.
import { McpServer, postgres, WebStandardStreamableHTTPServerTransport } from "../_shared/deps.ts";
import { asOwner, resolveLink, secretFrom } from "../_shared/owner.ts";
import { Planner, PlannerError } from "../_shared/planner/planner.ts";
import { prompts } from "../_shared/prompts/prompts.ts";
import { tools } from "../_shared/tools/tools.ts";

const VERSION = "0.5.0";
const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 3, idle_timeout: 20 });

function server(ownerId: string): McpServer {
  const mcp = new McpServer(
    { name: "goalmaker", title: "GoalMaker", version: VERSION },
    {
      instructions: "GoalMaker is the owner's planner: tasks for Today, Tomorrow and the Inbox, areas, tags, " +
        "reminders and the evening Plan tomorrow ritual. Days are the owner's planning days. Every change is " +
        "shown to the owner as made by Claude and can be undone from the apps. Ask before deleting anything.",
    },
  );
  for (const tool of tools) {
    mcp.registerTool(
      tool.name,
      {
        title: tool.title,
        description: tool.description,
        inputSchema: tool.input,
        annotations: { readOnlyHint: tool.readOnly, destructiveHint: tool.destructive, openWorldHint: false },
      },
      async (args: Record<string, unknown>) => {
        try {
          const text = await asOwner(sql, ownerId, "claude", (db) => tool.run(new Planner(db), args));
          return { content: [{ type: "text" as const, text }] };
        } catch (error) {
          if (error instanceof PlannerError) {
            return { content: [{ type: "text" as const, text: error.message }], isError: true };
          }
          console.error(`tool ${tool.name} failed`, error);
          return { content: [{ type: "text" as const, text: "GoalMaker couldn't do that just now." }], isError: true };
        }
      },
    );
  }
  for (const prompt of prompts) {
    mcp.registerPrompt(
      prompt.name,
      { title: prompt.title, description: prompt.description, argsSchema: prompt.args },
      (args: Record<string, string | undefined>) =>
        asOwner(sql, ownerId, "claude", (db) => prompt.build(new Planner(db), args)),
    );
  }
  return mcp;
}

// MCP lets a client leave out a prompt's arguments when it has none to give, but the SDK checks the
// missing object against the prompt's argument schema and refuses it, so an empty one is filled in.
async function withPromptArguments(request: Request): Promise<Request> {
  if (request.method !== "POST") return request;
  const text = await request.text();
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return new Request(request.url, { method: "POST", headers: request.headers, body: text });
  }
  const fill = (message: unknown) => {
    const call = message as { method?: string; params?: { arguments?: unknown } };
    if (call?.method === "prompts/get" && call.params && call.params.arguments === undefined) {
      call.params.arguments = {};
    }
    return message;
  };
  const filled = Array.isArray(body) ? body.map(fill) : fill(body);
  return new Request(request.url, { method: "POST", headers: request.headers, body: JSON.stringify(filled) });
}

Deno.serve(async (request) => {
  const secret = secretFrom(new URL(request.url));
  if (secret === null) return new Response("Not found", { status: 404 });

  const link = await resolveLink(sql, secret);
  if (link.kind === "unknown") return new Response("Not found", { status: 404 });
  if (link.kind === "limited") {
    return new Response("Too many requests", { status: 429, headers: { "Retry-After": "60" } });
  }

  // Stateless: a fresh server and transport per request, answering in JSON instead of a stream.
  const transport = new WebStandardStreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
    enableJsonResponse: true,
  });
  await server(link.ownerId).connect(transport);
  return await transport.handleRequest(await withPromptArguments(request));
});
