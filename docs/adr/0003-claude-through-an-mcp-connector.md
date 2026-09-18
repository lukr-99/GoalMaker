# ADR 0003: Claude through an MCP connector, secret link in v1

The owner has no Anthropic API account and doesn't want API costs, but wants Claude (including the
Claude phone app and Claude Code) to read and change GoalMaker. We expose a remote MCP server as a
Supabase Edge Function that Claude apps use on the owner's Claude plan; there is no in-app LLM in
v1. Proper OAuth needs a hosted sign-in page, which Edge Functions can't serve on the default domain,
so v1 authorizes the connector with a secret link: a random secret stored only as a hash, rotatable
and revocable from both apps, rate-limited, acting as the owner under row security, with no hard
delete and every change in the activity log. OAuth 2.1 through Supabase Auth plus a static page on
Cloudflare Pages replaces it after v1. Anyone who sees the link has full access until it's rotated;
that risk is accepted for a single-user v1.
