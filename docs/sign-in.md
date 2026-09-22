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

## The PC's week

Windows keeps its session for a week and then asks for the code once more (`SignInPolicy`,
`SignInWatch`). The week starts when a code is accepted, not when a stored session is restored, so
it is a week of the owner's time and not of the app's. The session itself is kept and refreshed the
whole week, so the PC works offline throughout; the week only decides when the code is asked for
again. The check runs once at start-up, right after the stored session comes back, and Settings says
which day it falls on so the sign-out is no surprise. It leaves the replica where it is, the way a
refresh the server refuses does, so anything still in the outbox is waiting when the owner signs in
again; only a sign-out asked for in Settings clears the PC. A PC that signed in before this rule
existed starts its week on the next start rather than being thrown out by the update, and a clock
that ran ahead and came back never ends the week early.

The phone has no week of its own. It gets the lock below instead, which is the other half of the
same idea: the PC asks who is at it once a week, the phone asks every time it comes back.

## The phone's lock

A phone is the owner's own, and it is also the one that gets left on a table, so it locks rather
than signing in again (`AppLockPolicy`, `AppLock`). The lock is off until the owner turns it on in
Settings, under Account, next to the address and the sign out button.

It is not a sign-in. The session is kept and refreshed the whole time and the lock never signs
anybody out, so the phone works offline as it always did and the emailed code is only ever asked for
when the owner asks for it. This is the one place the two apps differ on purpose, so the seven-day
rule stays where it is, in `SignInPolicy` on the Windows side, with no Kotlin copy and no vector in
`contracts/` for a rule only one app has.

**When it asks.** The lock goes up as the app leaves the screen rather than when it comes back, so
the day is not left on show behind it. Coming back within half a minute takes it down again without
asking, which is what makes a file picker, a share sheet or the screen going dark and back bearable.
Longer than that and it asks. A cold start has no moment written down, so a lock that is on starts
locked. A clock that went backwards while the app was away says nothing about how long it was gone,
so it asks: one fingerprint is the cheap way to be wrong. The lock sits over the app rather than in
place of it, so unlocking gives the owner back the screen they were on.

**What it asks for.** A weak biometric or the screen lock, together. That pairing is the one
androidx.biometric supports on every Android the app runs on, and it means a phone with no
fingerprint enrolled can still be unlocked with its PIN, which is the first fallback. Android 8 is
left out on purpose: below API 28 the library draws its own fingerprint dialog with AppCompat, and
the app's window theme is not an AppCompat one to draw it in. A phone that cannot ask cannot turn
the lock on, and the switch says which of the two it is rather than turning on and failing later.

**Never a dead end.** A phone that has lost its fingerprint, face and screen lock since the lock was
turned on, or that cannot ask at all, says so on the lock screen and still offers the emailed code.
That signs out and starts the ordinary sign-in. Like the PC's weekly sign-out it leaves this phone's
copy of the data where it is, so nothing waiting in the outbox is lost by someone tapping it; only a
sign-out asked for in Settings clears the phone. A code that is accepted takes the lock down,
because that is the owner proving who they are. A stored session coming back is not, which is what
keeps a cold start locked.

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

**The mailbox.** The local stack catches every message it sends in a mailbox of its own, so the app
reads the code out of there and fills it in, which signs in, because a full code verifies itself. The
code in a message is the first run of exactly six digits. It happens when the code is sent and can be
asked for again from the sign-in screen. A mailbox that is not there, is slow, or holds no code is
simply no code, and the owner types it.

**The dev account.** `dev@goalmaker.test` is that in one press: the screen fills the address in, asks
for the code and reads it back. It is an account of its own with its own data, so trying things out
never touches the one the owner signs in as, which is the point.

The Supabase CLI can pin a fixed code for an address, but only for SMS (`[auth.sms.test_otp]`), so
there is no way to skip the message itself. Going through the mailbox costs about a second and needs
nothing set up.
