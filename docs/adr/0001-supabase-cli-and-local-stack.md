# ADR 0001: Run Supabase through the CLI with a local Docker stack

JsiNaTahu managed Supabase only through the web dashboard, pasting SQL into the editor. GoalMaker
has several Edge Functions (the connector, later the quick chat) and CodePrint requires full-chain
and isolated migration tests, which a dashboard-only workflow can't run. We use the Supabase CLI
through `npx` (nothing installed globally): `supabase start` runs a local Docker stack for
development and CI, migrations and pgTAP tests run against it, and deploys to the one free cloud
project go through `db push` and `functions deploy`. Migration files keep CodePrint's immutable
`0001_description.sql` naming, which the CLI accepts as numeric versions. The cost is Docker on the
development machine and a slower CI job; the gain is tested migrations and repeatable deploys.
