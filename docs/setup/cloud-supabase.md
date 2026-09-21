# The cloud Supabase project

One free project holds the real data (ADR 0001). Do these steps once. The dashboard moves things
around now and then; the setting names below are what to look for.

Done: the project is `wkjnauxqwlqhsqrqnkhg` in eu-central-1 with the schema and the settings
(2026-09-20), and it sends through Resend, so the sign-in code templates are live (2026-09-21). What
is left is the secret key in step 4, turning the unused SMS provider off in the dashboard, and
creating the account in step 6.

## 1. Create the project

The CLI does this, so nothing has to be clicked:

```powershell
npx supabase login                          # opens the browser once
npx supabase orgs list                      # the org id for the next line
npx supabase projects create GoalMaker --org-id <org id> --region eu-central-1
```

Leave `--db-password` off and the CLI asks for it without putting it in your shell history. Save that
password in the password manager: nobody can recover it for you. `npx supabase projects list` then
shows the **project ref**, the part before `.supabase.co`.

## 2. Push the schema

```powershell
npx supabase link --project-ref <project ref>
npx supabase db push                        # applies supabase/migrations in order
npx supabase migration list                 # every migration on both sides
```

Every later migration goes out the same way, after it passed `python tools/supabase_migrations.py test`
locally and in CI.

## 3. The project's settings

`supabase/config.toml` ends with a `[remotes.production]` block: what the **cloud** project should
have, separate from the local stack above it. Pushing the local settings whole would weaken the real
project (a one-second email rate limit, MFA off, a site URL on localhost), so that block restates the
safe values and changes only what GoalMaker needs.

```powershell
npx supabase config diff --project-ref <project ref>   # always read this first
npx supabase config push --project-ref <project ref>
```

That sets the email OTP length to 6, the OTP expiry to 600 seconds and the site URL. Two things the
push cannot do: turn the unused Twilio SMS provider off (the dashboard can, and it sends nothing
without credentials anyway), and change the email templates, which is what "5. Sign-in emails" is
about.

## 4. Keys for the apps and the release workflow

In **Project Settings → API Keys**:

| Value | Used by | Where it goes |
| --- | --- | --- |
| Project URL | both apps' release builds, the workflow | secret `GOALMAKER_SUPABASE_URL` |
| Publishable key (`sb_publishable_...`) | both apps' release builds | secret `GOALMAKER_SUPABASE_KEY` |
| Secret key (`sb_secret_...`) | the release workflow only (uploads) | secret `GOALMAKER_SUPABASE_SECRET_KEY` |

```powershell
gh secret set GOALMAKER_SUPABASE_URL --repo lukr-99/GoalMaker
gh secret set GOALMAKER_SUPABASE_KEY --repo lukr-99/GoalMaker
gh secret set GOALMAKER_SUPABASE_SECRET_KEY --repo lukr-99/GoalMaker
```

(`gh secret set` asks for the value, so it never lands in your shell history.)

For release builds on your own PC, put the URL and publishable key in untracked files:

- `android/local.properties`: `goalmaker.supabaseUrl=...` and `goalmaker.supabaseKey=...`
- `windows/GoalMaker.local.props`:

  ```xml
  <Project>
    <PropertyGroup>
      <GoalMakerSupabaseUrl>https://PROJECT-REF.supabase.co</GoalMakerSupabaseUrl>
      <GoalMakerSupabaseKey>sb_publishable_...</GoalMakerSupabaseKey>
    </PropertyGroup>
  </Project>
  ```

Never put the secret key in either file.

## 5. Sign-in emails: the project's own sender

**Done on 2026-09-21.** GoalMaker signs in with a
6-digit code, which means both the **Magic link** and **Confirm signup** templates have to show
`{{ .Token }}` instead of a link
([supabase/templates/sign-in-code.html](../../supabase/templates/sign-in-code.html)). Supabase
refuses that on a free project using its own email service:

> Email template modification is not available for free tier projects using the default email
> provider. Please upgrade your plan or configure a custom SMTP provider.

So the project sends through its own SMTP instead. Resend's free tier (3,000 emails a month) is far
more than one owner needs, and `config.toml` already carries the block to uncomment.

### Resend, once

1. **Sign up** at <https://resend.com> with the address you sign in to GoalMaker with. That matters
   for step 2.
2. **Pick the address to send from.**
   - *No domain of your own:* use `onboarding@resend.dev`, the shared sender every account gets. It
     may only send to the address the Resend account itself is registered with, which is exactly the
     one account GoalMaker has, so it is enough. Anyone else who tried to sign in would get nothing.
   - *A domain of your own:* **Domains → Add Domain**, put the DKIM, SPF and DMARC records it shows
     into your DNS, and wait for it to go green. Then send from something like
     `goalmaker@yourdomain`, which works for any address.
3. **API Keys → Create API Key**, permission **Sending access**. Copy it once; Resend never shows it
   again.

### Tell the project about it

`supabase/config.toml` already carries it, at the bottom under `[remotes.production]`: the SMTP
block and the two sign-in code templates. Change `admin_email` only if you send from a domain of
your own. Then, with the key in the environment so it never lands in a file or your shell history:

```powershell
$env:RESEND_API_KEY = Read-Host 'Resend API key' -AsSecureString | ConvertFrom-SecureString -AsPlainText
npx supabase config diff --project-ref <project ref>   # read this first
npx supabase config push --project-ref <project ref>
```

The diff should show the SMTP settings and the two templates and nothing else. Send yourself a code
from a release build afterwards: it should carry six digits, not a link.

Two things the push cannot do, both one click in the dashboard:

- **Turn the unused Twilio SMS provider off.** `config push` can switch between SMS providers but not
  turn the active one off, so it says so and leaves it. It sends nothing without credentials;
  **Authentication → Sign In / Providers → Phone** turns it off for good.
- Change anything under the properties it lists as unmanaged, none of which GoalMaker sets.

Keep the API key in your password manager. It is not needed again unless the settings are pushed
from another machine.

**The address has to match.** Resend's shared sender will only deliver to the address the Resend
account itself is registered with. If you sign up as one address and sign in to GoalMaker as
another, the code is accepted by Resend and never arrives. A domain of your own lifts that.

## 6. Create your account, then close the door

1. Install a release build (the first release, or a local release build) and sign in with your
   email. That creates your user and your profile.
2. In **Authentication**, turn off **Allow new users to sign up**. Your account keeps working; nobody
   else can create one, so nobody else can read the release bucket either.

## 7. Check

- **Table Editor → profiles:** one row, yours.
- **Storage:** a private bucket `releases` (created by migration 0002).
- A release build signs in and shows your email in Settings.
