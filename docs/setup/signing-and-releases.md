# Signing and releases

Two keys protect updates, and both are set up once:

| Key | Protects | Lives |
| --- | --- | --- |
| Android release key (`keystore.jks`) | only you can replace the installed APK | offline drive + GitHub secret; password in the password manager |
| Manifest signing key (ECDSA P-256) | apps trust only your release manifests (ADR 0004) | offline drive + GitHub secret; public half committed in `contracts/keys/` |

Plug in the offline drive you keep the keys on first. The examples use `E:`.

## 1. Android release key

```powershell
powershell -ExecutionPolicy Bypass -File android\tools\setup-signing.ps1 -BackupTo 'E:\android-keystores\goalmaker' -UploadSecrets
```

It asks for a password (hidden), creates `android/keystore.jks` and `android/keystore.properties`
(both git-ignored), copies the key to the drive with a README, verifies the copy, and uploads the
`GOALMAKER_KEYSTORE_BASE64`, `GOALMAKER_KEYSTORE_PASSWORD`, `GOALMAKER_KEY_ALIAS` and
`GOALMAKER_KEY_PASSWORD` secrets. Put the password in the password manager right away.

## 2. Manifest signing key

```powershell
powershell -ExecutionPolicy Bypass -File tools\setup-update-signing.ps1 -KeyFolder 'E:\goalmaker-signing' -UploadSecret
git add contracts/keys/release-manifest-public.b64
git commit -m "chore(release): add the update channel's public key"
```

The script refuses to replace an existing key. From the next build on, both apps verify the channel.

## 3. The other secrets and branch protection

- The three Supabase secrets: [cloud-supabase.md](cloud-supabase.md), step 4.
- `DOTNETLIB_PACKAGES_TOKEN`: a classic PAT with only `read:packages`, so CI and the release
  workflow can restore `DotNetLib.Tray` from the private `dotnetlib` feed
  ([local development](local-development.md#windows)). Set it with `gh secret set DOTNETLIB_PACKAGES_TOKEN`.
- The cloud backend step (section 4) needs two more repository secrets: `SUPABASE_ACCESS_TOKEN` (a
  personal access token from the Supabase dashboard, Account → Access Tokens) and
  `SUPABASE_DB_PASSWORD` (the database password from [cloud-supabase.md](cloud-supabase.md), step 1).
  Set them with `gh secret set SUPABASE_ACCESS_TOKEN` and `gh secret set SUPABASE_DB_PASSWORD`, or in
  GitHub → Settings → Secrets and variables → Actions → New repository secret.
- A GitHub **environment** named `production` with yourself as a required reviewer: GitHub →
  Settings → Environments → New environment → `production` → tick **Required reviewers**, add
  yourself, save. The release's backend job waits there until you approve it. (On a private
  repository, environments with reviewers need a paid plan.)
- `gh secret list` should show eight `GOALMAKER_*` secrets, `DOTNETLIB_PACKAGES_TOKEN`,
  `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD`.
- In the GitHub repository settings, protect `main` and require the CI checks: "Supabase migrations,
  row security and the connector", "Android build, unit tests and lint", "Windows format, build and
  tests" and "validate". Only these four: the helper jobs are explained in [ci.md](ci.md). Branch
  protection on a private repository needs a paid plan; a public one has it for free.

## 4. Cut a release

1. Raise `versionName` (X.Y.Z) and `versionCode` (+1) in `version.properties`; commit to `main`.
2. Check what the cloud is missing. A release builds the apps but the cloud backend (database
   migrations and Edge Functions) is its own step, and a release that skips it ships apps that talk to
   an older backend (GM-110: 1.17.0 went out without migrations 0029 and 0030):

   ```powershell
   powershell -ExecutionPolicy Bypass -File toolselease-backend.ps1 -Check
   ```

   It is read only. It compares the cloud's migrations with `supabase/migrations`, finds Edge
   Functions that were deployed before their code last changed, and exits 1 when anything is behind.
   It needs the Supabase CLI logged in (`npx supabase login`). It works on the production project
   `wkjnauxqwlqhsqrqnkhg` only and refuses any other link. The release workflow does this step too
   (next point), so doing it by hand first is for a look, or when the workflow cannot do it.
3. Start the release one of two ways:
   - Tag and push: `git tag vX.Y.Z` then `git push origin main vX.Y.Z`.
   - Or, with no git at hand (from the phone, or from a Claude session that can only push its own
     branch): GitHub → Actions → **Release** → Run workflow, on `main`. The run refuses any other
     branch and a version that already has a tag, and makes the `vX.Y.Z` tag itself on the commit it
     built when it publishes. A failed run leaves no tag, so fix `main` and run it again.
4. The **Release** workflow checks the tag and the secrets (including `SUPABASE_ACCESS_TOKEN` and
   `SUPABASE_DB_PASSWORD`), then the **backend** job waits for your approval in the `production`
   environment (GitHub → the run → **Review deployments** → tick `production` → Approve and deploy).
   After that it runs `tools/release-backend.ps1 -Apply -Yes`: `supabase db push` for the pending
   migrations, a deploy of each Edge Function that is behind, and the check again. Only when that
   passes does it build and test the signed APK and the installer and publish them with the signed
   manifest and `SHA256SUMS` as the GitHub Release (ADR 0010). A last job, **Check the cloud backend
   after the release**, runs `-Check` again, so a release whose cloud is behind ends red.

   **Why the backend goes first.** Migrations only add things (new tables, columns, functions), so an
   older app keeps working against a newer database, but a new app against an older database breaks.
   So the cloud is brought up first and the apps follow. A migration that would break the apps
   already out must not be written this way (write it in two releases).

   **When the backend cannot go out.** A run started by hand has a **skip_backend** box. It releases
   the apps without the backend job, writes "Backend not deployed" at the top of the release notes,
   and the last job fails, so the run cannot look complete. Run `-Apply` by hand afterwards:

   ```powershell
   powershell -ExecutionPolicy Bypass -File toolselease-backend.ps1 -Apply
   ```

   It lists the pending migrations and asks you to type `yes` before it touches the cloud (`-Yes`
   skips the question). It is safe to run again: with nothing behind it does nothing. A tag push has no
   such box, so a missing secret stops the run in its first job.
5. The apps find the update on their own within a day, or at once with Settings → Check for updates.
   The quiet check runs a little after the app starts (half a minute on the PC, a few seconds after the
   phone app comes to the front) and then about once a day: the tray app looks every hour and checks
   when the last check that got through is a day old. It only reads and verifies the signed manifest;
   nothing is downloaded or installed until the owner presses Install. A failed check says nothing and
   is tried again at the next look. Dev builds and builds without a channel never check. Settings →
   Updates says when a check last got through.
   Once a check has found the update, the Settings item in the Windows sidebar and the gear in the
   phone's top bar wear an accent mark with a download arrow ("Update available") until it is
   installed or a later check that gets an answer finds none. A check that fails (offline, GitHub
   down), quiet or asked for, leaves the mark as it is. After a restart, a found update is checked again at once, so
   the mark comes back without waiting a day.
   On the phone, a check that finds a newer trusted release also posts one quiet notification for
   that version, "GoalMaker X.Y.Z is ready", on its own channel (App updates: low importance, no
   sound). It only shows when the owner has allowed notifications; the app asks once, for
   reminders, and never again for this. Tapping it opens Settings at the update (on the phone, Settings > Your data and updates). Its Install opens
   the installer from there, and Later puts the version off. The same check asks WorkManager to
   fetch the APK in the background, on an unmetered network with the battery not low, into the
   app's private cache. The file is kept only when its size and SHA-256 match the signed manifest,
   and Install opens it at once (it is measured again first). Without it, Install downloads and
   verifies as before. The notification goes once the new version starts or a check finds none,
   and older APKs are deleted then too.
   **Later** (on the notification, or next to Install in Settings) hides the mark and the
   notification for that version for three days, or until a newer version appears. Settings still
   shows the update with Install, and says until when it is put off. When the three days are up,
   the next check shows the mark and the notification again.
   Nothing installs without the owner tapping Install, and Android asks to confirm on its own screen.

A failed workflow publishes no release unless it reached the publish job. If the backend job passed
and a later job failed, the cloud is ahead of the apps, which is safe (see above); fix the failure
and run the release again, and the backend job finds nothing left to do. If a bad release went out,
see [SECURITY.md](../../SECURITY.md) → Recovery.

## 5. First install and the manual path

- **Phone:** download `GoalMaker-X.Y.Z.apk` from the latest GitHub Release, allow installing from
  that source, install. Later updates come from inside the app.
- **PC:** run `GoalMaker-X.Y.Z-setup.exe`. It needs the .NET 10 Desktop Runtime and links to its
  download page when it's missing. Keep "Start GoalMaker in the tray when I sign in" on for reminders.
- The same downloads always work by hand if the in-app updater ever fails: Settings → Open the
  latest release.
