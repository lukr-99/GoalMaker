# Signing in

One person owns a GoalMaker account and signs in with a six-digit code sent to their email (ADR
0001, spec story 2). There is no password to lose and nothing else to set up: type the address, read
the code, and the app is yours on that device until you sign out.

## The two steps

1. The app asks Supabase Auth for a code for the address. A first sign-in creates the account.
2. The code goes in. Six digits verify themselves, so there is nothing to press.

A session is kept on the device and refreshed in the background. A refresh that fails because the
device is offline changes nothing: the app stays signed in and tries again later. A refresh the
server refuses (the token is gone, the project was reset) signs the device out, and the code comes
again.

## The emails

Both templates Supabase can send for this, **Magic link** and **Confirm signup**, are replaced by
[`supabase/templates/sign-in-code.html`](../supabase/templates/sign-in-code.html), which shows
`{{ .Token }}` rather than a link, because the apps ask for a code and not a click.

- The **local stack** takes those templates from `supabase/config.toml` and keeps every message in
  its own mailbox at <http://127.0.0.1:55324>. Nothing leaves the machine.
- The **cloud project** cannot have them yet: Supabase refuses template changes on a free project
  using its own email service, so it sends its default message, which carries a link instead of a
  code. Until the project has its own SMTP the code sign-in cannot work there; the step is written
  out in [docs/setup/cloud-supabase.md](setup/cloud-supabase.md).

## Dev builds get past the post

Developing against the local stack means signing in again every time the stack is reset, so a dev
build has two ways through, both only ever against that stack: the plain-http backend on port 55321,
whose mailbox is on 55324. Any other backend, the cloud project above all, has neither, and the code
is typed as it always was. Release builds carry none of it. Both rules are pinned by
[`contracts/vectors/dev-sign-in.json`](../contracts/vectors/dev-sign-in.json).

**The dev account.** `dev@goalmaker.test` signs in with a fixed code, `424242`, which no mail ever
carries: the local stack is told to take it under `[auth.email.test_otp]` in `supabase/config.toml`,
so nothing is sent at all. One press on the sign-in screen and the app is in. It is an account of its
own with its own data, kept apart from the one the owner signs in as, which is the point: it is for
trying things out. `[remotes.production]` has no such block, so the cloud project has no such door.

**The mailbox.** For any other address on the local stack, including the owner's own, the app reads
the code out of the mailbox the stack caught it in and fills it in, which signs in, because a full
code verifies itself. The code in a message is the first run of exactly six digits. It runs when the
code is sent and can be asked for again from the sign-in screen. A mailbox that is not there, is
slow, or holds no code is simply no code, and the owner types it.

Changing `config.toml` needs the stack restarted (`npx supabase stop` then `npx supabase start`)
before the dev account works.
