// Which provider answers a chat request (docs/assistant.md): Gemini with the GEMINI_API_KEY secret, or,
// on the local stack only, the FakeProvider with the turns the endpoint test sends.
import type { ChatProvider } from "./chatProvider.ts";
import { FakeProvider } from "./fakeProvider.ts";
import { GeminiProvider } from "./geminiProvider.ts";

/** The header that carries the endpoint test's scripted turns. */
export const FAKE_HEADER = "x-goalmaker-fake-turns";

export type Choice =
  | { kind: "provider"; provider: ChatProvider }
  | { kind: "unavailable" }
  | { kind: "bad_script" };

/**
 * True when the function runs on the local stack, where it reaches the gateway over plain HTTP as
 * http://kong:8000. A hosted project's SUPABASE_URL is always https, so the fake can't be picked there.
 */
export function onLocalStack(supabaseUrl: string | undefined): boolean {
  try {
    const url = new URL(supabaseUrl ?? "");
    return url.protocol === "http:" &&
      ["kong", "127.0.0.1", "localhost", "host.docker.internal"].includes(url.hostname);
  } catch {
    return false;
  }
}

export function chooseProvider(script: string | null, env: (name: string) => string | undefined): Choice {
  if (script !== null && onLocalStack(env("SUPABASE_URL"))) {
    const fake = FakeProvider.parse(script);
    return fake === null ? { kind: "bad_script" } : { kind: "provider", provider: fake };
  }
  const key = env("GEMINI_API_KEY");
  return key ? { kind: "provider", provider: new GeminiProvider(key) } : { kind: "unavailable" };
}
