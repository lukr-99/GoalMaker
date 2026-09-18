# The cloud Supabase project

One free project holds the real data (ADR 0001). Do these steps once. The dashboard moves things
around now and then; the setting names below are what to look for.

## 1. Create the project

1. In the Supabase dashboard, in your organization (it has a free slot: SubTrackr uses the other),
   create a project named **GoalMaker**. Pick a region close to you (for example Central EU). Save the
   database password in the password manager.
2. Note the **project ref** (the part before `.supabase.co` in the project URL).

## 2. Push the schema

```powershell
npx supabase login                          # opens the browser once
npx supabase link --project-ref <project ref>
npx supabase db push                        # applies supabase/migrations in order
```

Every later migration goes out the same way, after it passed `python tools/supabase_migrations.py test`
locally and in CI.

## 3. Sign-in by emailed code

In **Authentication**:

- **Email provider:** enabled. Set the **email OTP length** to 6 and the **OTP expiry** to 600
  seconds (10 minutes).
- **Email templates:** replace both **Magic link** and **Confirm signup** with the content of
  [supabase/templates/sign-in-code.html](../../supabase/templates/sign-in-code.html), subject
  "Your GoalMaker sign-in code". The template shows `{{ .Token }}` (the code), not a link.
- The built-in email service only sends to your organization's own members and allows a few emails an
  hour. That fits one owner; add your own SMTP only if it ever gets in the way.

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

## 5. Create your account, then close the door

1. Install a release build (the first release, or a local release build) and sign in with your
   email. That creates your user and your profile.
2. In **Authentication**, turn off **Allow new users to sign up**. Your account keeps working; nobody
   else can create one, so nobody else can read the release bucket either.

## 6. Check

- **Table Editor → profiles:** one row, yours.
- **Storage:** a private bucket `releases` (created by migration 0002).
- A release build signs in and shows your email in Settings.
