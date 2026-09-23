# Running your own copy

GoalMaker is built for one person: one Supabase project, one sender, one signing key, one owner. So
there is no server of ours to join. Running it means standing up your own, which this page walks
through from a clean clone. Everything here is free except a domain, which is optional.

The licence is PolyForm Noncommercial: run it, change it, share your changes, but not sell it.

## What you need

- A GitHub account, with the repository forked, so releases can be built and stored.
- A Supabase account (free tier).
- An email sending account (free tier), unless you only ever run against the local stack.
- Windows for the desktop app, Android 8 or newer for the phone app, and Docker Desktop if you want
  the local stack.

## 1. Make it yours

Four things in the repository are ours and have to become yours. Nothing else names us.

| Where | Change it to |
|---|---|
| `supabase/config.toml`: `project_id` | your project's ref |
| `supabase/config.toml`: `[remotes.production].auth.site_url` | `https://<your ref>.supabase.co` |
| `contracts/keys/release-manifest-public.b64` | your own update signing key (step 3) |
| `android/app/build.gradle.kts`: `applicationId` | only if you publish to a store |

`python tools/check_own_copy.py` says when one of these is still ours. It runs in CI too, so a fork
that forgets is told at the first push rather than at the first release nobody can install.

## 2. The cloud project and its sender

[docs/setup/cloud-supabase.md](cloud-supabase.md) has the whole of it: create the project with the
CLI, push the schema, push the settings, set the repository secrets, and give the project a sender so
the sign-in code can go out. Two things worth knowing before you start:

- **The sender matters more than it looks.** Supabase will not let a free project change its email
  templates while it uses Supabase's own email service, and GoalMaker signs in with a six-digit code
  rather than a link. Without your own SMTP, sign-in on the cloud project cannot work.
- **A shared sender only reaches you.** Resend's `onboarding@resend.dev` delivers only to the address
  the Resend account is registered with. That is enough for one owner. A domain of your own lifts it.

## 3. The two signing keys

[docs/setup/signing-and-releases.md](signing-and-releases.md) covers both, and the scripts do the
work:

```powershell
powershell -ExecutionPolicy Bypass -File android\tools\setup-signing.ps1 -GeneratePassword -BackupTo '<your offline drive>' -UploadSecrets
powershell -ExecutionPolicy Bypass -File tools\setup-update-signing.ps1 -KeyFolder '<your offline drive>' -UploadSecret
```

The first signs the Android app: lose it and installed copies can never be updated in place. The
second signs the update manifest, and its public half is committed, which is what makes both apps
refuse an update you did not sign. **Keep our public key and your apps will trust our releases and
not yours**, which is the one mistake here with teeth.

## 4. Run it

- **Locally, no cloud at all:** `npx supabase start`, then build the debug apps. A dev build finds
  the local stack, and reads its own sign-in code out of the stack's mailbox
  ([docs/sign-in.md](../sign-in.md)).
- **For real:** tag a version, let the release workflow build and publish the signed APK and
  installer, install them, and sign in with the address your sender can reach. The apps update
  from your fork's own releases: the workflow builds in the address of the repository it runs in
  (ADR 0010). They can read those releases only while your fork is public.
- **Then close the door:** in Supabase, **Authentication → Allow new users to sign up → off**. Your
  account keeps working, and nobody else can make one on your project.

## 5. Check

- `python tools/check_own_copy.py` passes.
- `gh secret list` shows eight `GOALMAKER_*` secrets.
- A task made on the phone turns up on the PC within a few seconds.
