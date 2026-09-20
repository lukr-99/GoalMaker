# The cloud Supabase project

One free project holds the real data (ADR 0001). Do these steps once. The dashboard moves things
around now and then; the setting names below are what to look for.

Done on 2026-09-20: the project is `wkjnauxqwlqhsqrqnkhg` in eu-central-1, with the schema, the
settings and the repository secrets below. What is left is in "5. Sign-in emails".

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

## 5. Sign-in emails: the project needs its own SMTP

**Not done yet, and sign-in on the cloud project cannot work until it is.** GoalMaker signs in with a
6-digit code, which means both the **Magic link** and **Confirm signup** templates have to show
`{{ .Token }}` instead of a link
([supabase/templates/sign-in-code.html](../../supabase/templates/sign-in-code.html)). Supabase
refuses that on a free project using its own email service:

> Email template modification is not available for free tier projects using the default email
> provider. Please upgrade your plan or configure a custom SMTP provider.

So the project needs its own SMTP before the templates can go out. A free tier from Resend, Brevo or
Mailgun is enough for one owner. Put the host, port, user and password under `[auth.email.smtp]` in
the `[remotes.production]` block (the password as `env(...)`, never in the file), then uncomment the
two template blocks in `config.toml` and run `config push` again.

Until then the built-in service sends Supabase's own templates, which carry a link rather than a
code, and only to your organization's own members.

## 6. Create your account, then close the door

1. Install a release build (the first release, or a local release build) and sign in with your
   email. That creates your user and your profile.
2. In **Authentication**, turn off **Allow new users to sign up**. Your account keeps working; nobody
   else can create one, so nobody else can read the release bucket either.

## 7. Check

- **Table Editor → profiles:** one row, yours.
- **Storage:** a private bucket `releases` (created by migration 0002).
- A release build signs in and shows your email in Settings.
