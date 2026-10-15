# ADR 0020: The PC signs in by a QR code the phone approves

Typing an emailed code on the PC every week is the slowest thing in GoalMaker, and the phone is
nearly always signed in already. The sign-in screen on Windows now also shows a QR code and a
two-digit number. The phone scans the code from Settings, asks "Sign in on DESKTOP-1?", and the
owner taps the number the PC shows. The PC then signs in as the owner without an email. The emailed
code stays as it was, as the fallback.

**The shape.** A pairing is a row in `public.pc_sign_ins` that lives two minutes and is used once.
It has two random secrets, and only their SHA-256 hashes are kept:

- the **scan secret** is in the QR code (`goalmaker://sign-in/pc/<43 characters>`). The phone sends
  it, with its own session, to approve or decline. That goes through PostgREST, so the session is
  checked the usual way, and the row records who approved it.
- the **pick-up secret** never leaves the PC. The PC asks the `pc-sign-in` Edge Function with it
  every two seconds. Once the row is approved, the function claims it, asks Supabase Auth's admin
  API for a one-time email code for the approving owner without sending any email, and only then
  marks it picked up. When the code can't be made, the claim is let go and the next ask tries
  again, so a server error never loses an approval. The PC signs in with that code through the same
  call the emailed code uses, so the session, the week (`SignInWatch`) and everything after are the
  ones the app already has.

**The match number.** Anyone can start a request and send its QR code to the owner with a story
("scan this to get the file"). If one tap on the phone were enough, that would give them a code for
the owner's account. So the server picks a two-digit number at the start, the PC shows it large
beside the QR code, and the phone offers it among three numbers. Approving needs the number picked;
the server checks it, and a wrong pick burns the request. Someone who only sent a QR code can't
show the owner the right number on the owner's own screen, so a tap on a phishing code most likely
picks wrong and ends it. This does not stop an owner who is talked into typing what someone tells
them, but it turns one careless tap into a deliberate step.

**What this keeps safe.**

- No session, refresh token or long-lived secret is ever in the QR code. Someone who photographs it
  can only ask the owner's phone to approve, which still needs the owner's tap on the right number.
- Knowing the scan secret is not enough to sign in: the code goes only to the holder of the pick-up
  secret, and only once. The one-time code itself is the one Supabase Auth already issues for email
  sign-in, and it is used within a second of being made.
- The tables have row security on and no policies, and `anon` and `authenticated` have no rights on
  them. The phone only reaches them through two functions that check `auth.uid()`; the start and
  the pick-up run as the service role inside the Edge Function.
- A request is refused when it is older than two minutes, already answered, declined or picked up.
- **Limits per caller.** The function keys every call on a SHA-256 hash of the caller, never the
  address itself. A caller may start six requests a minute and hold three open ones, and ask 120
  times a minute, so one script can't fill the table for everyone; a cap of 50 open requests in all
  stays behind that. pg_cron clears requests older than a day and the counts every hour.
- **Who the caller is.** An IPv4 address counts whole. An IPv6 address counts by its /64, because a
  home or a server gets a whole /64 and can take a new address from it for every call; the address
  is read in any written form (`::`, capitals, brackets, a zone) and an IPv4 address written as IPv6
  (`::ffff:a.b.c.d`) counts as that IPv4 address.
- **Only a trusted address.** On the hosted project the function trusts only `cf-connecting-ip`,
  which Cloudflare in front of Supabase sets itself; `x-forwarded-for` is whatever the client wrote,
  so it is never used there. A call without a trusted address is refused (`403 no_caller`) and
  logged, rather than shared in one bucket that one script could fill. Only on the local stack
  (`onLocalStack`: the function reaches the gateway over plain http) may the gateway's
  `x-forwarded-for` stand in, so local builds and CI work. That the hosted project passes
  `cf-connecting-ip` through to the function is not documented by Supabase, so the first QR sign-in
  after deploying is the check: a refusal shows as `no_caller` in the function's logs.
- The device name the PC sends is only a label: anyone can make a QR code that says any name. The
  phone shows it and says to approve only a code on the screen in front of you. The scanner is only
  inside the app, so a QR code seen somewhere else cannot open the approve screen.

**The scanner.** The phone uses Google's code scanner (`play-services-code-scanner`). Play
services draws the camera screen, so GoalMaker needs no camera permission, CameraX or a bundled
model. A phone without Play services can't scan, and the PC then uses the emailed code.

**What it costs.** One more Edge Function to deploy and two more tables. The function holds the
service role key, like every function does. A QR sign-in replaces any emailed code that was waiting
for the same owner, because Supabase Auth keeps one at a time. The owner picks a number on every
QR sign-in, which is one tap more than a plain Sign in button.
