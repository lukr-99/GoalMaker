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

## Dev builds fill the code in

A dev build talking to the local stack reads the code out of the stack's mailbox and fills it in
itself, so resetting the stack does not cost six digits of typing every time. The rules are in
[`contracts/vectors/dev-mailbox.json`](../contracts/vectors/dev-mailbox.json) and hold both apps to
the same two things:

- the mailbox is only ever the local stack's, which is the plain-http backend on port 55321, read on
  port 55324. Any other backend, the cloud project above all, has no mailbox and the app asks for
  the code as usual;
- the code in a message is the first run of exactly six digits.

It runs when the code is sent and can be asked for again from the sign-in screen. A mailbox that is
not there, is slow, or holds no code is simply no code, and the owner types it. Release builds carry
none of this: the way in is the email, wherever the owner reads it.
