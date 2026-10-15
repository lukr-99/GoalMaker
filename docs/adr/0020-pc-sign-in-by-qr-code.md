# ADR 0020: The PC signs in by a QR code the phone approves

Typing an emailed code on the PC every week is the slowest thing in GoalMaker, and the phone is
nearly always signed in already. The sign-in screen on Windows now also shows a QR code. The phone
scans it from Settings, asks "Sign in on DESKTOP-1?", and the owner taps Sign in. The PC then signs
in as the owner without an email. The emailed code stays as it was, as the fallback.

**The shape.** A pairing is a row in `public.pc_sign_ins` that lives two minutes and is used once.
It has two random secrets, and only their SHA-256 hashes are kept:

- the **scan secret** is in the QR code (`goalmaker://sign-in/pc/<43 characters>`). The phone sends
  it, with its own session, to approve or decline. That goes through PostgREST, so the session is
  checked the usual way, and the row records who approved it.
- the **pick-up secret** never leaves the PC. The PC asks the `pc-sign-in` Edge Function with it
  every two seconds. Once the row is approved, the function marks it picked up (in the same
  statement, so only one call ever gets it) and asks Supabase Auth's admin API for a one-time email
  code for the approving owner, without sending any email. The PC signs in with that code through
  the same call the emailed code uses, so the session, the week (`SignInWatch`) and everything after
  are the ones the app already has.

**What this keeps safe.**

- No session, refresh token or long-lived secret is ever in the QR code. Someone who photographs it
  can only ask the owner's phone to approve, which still needs the owner's tap.
- Knowing the scan secret is not enough to sign in: the code goes only to the holder of the pick-up
  secret, and only once. The one-time code itself is the one Supabase Auth already issues for email
  sign-in, and it is used within a second of being made.
- The table has row security on and no policies, and `anon` and `authenticated` have no rights on
  it. The phone only reaches it through two functions that check `auth.uid()`; the pick-up and the
  start run as the service role inside the Edge Function.
- A request is refused when it is older than two minutes, already approved, declined or picked up.
  The function refuses new requests while too many are open, so nobody can fill the table.
- The device name the PC sends is only a label: anyone can make a QR code that says any name. The
  phone shows it and also says to approve only a code on the screen in front of you. The scanner is
  only inside the app, so a QR code seen somewhere else cannot open the approve screen.

**The scanner.** The phone uses Google's code scanner (`play-services-code-scanner`). Play
services draws the camera screen, so GoalMaker needs no camera permission, CameraX or a bundled
model. A phone without Play services can't scan, and the PC then uses the emailed code.

**What it costs.** One more Edge Function to deploy and one more table. The function holds the
service role key, like every function does. A QR sign-in replaces any emailed code that was waiting
for the same owner, because Supabase Auth keeps one at a time.
