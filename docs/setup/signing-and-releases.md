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
- `gh secret list` should show eight `GOALMAKER_*` secrets.
- In the GitHub repository settings, protect `main` and require the CI checks: "Supabase migrations,
  row security and the connector", "Android build, unit tests and lint", "Windows format, build and
  tests" and "validate". Branch protection on a private repository needs a paid plan; a public one
  has it for free.

## 4. Cut a release

1. Raise `versionName` (X.Y.Z) and `versionCode` (+1) in `version.properties`; commit.
2. Tag and push: `git tag vX.Y.Z` then `git push origin main vX.Y.Z`.
3. The **Release** workflow checks the tag and the secrets, builds and tests the signed APK and the
   installer, and publishes them with the signed manifest and `SHA256SUMS` as the GitHub Release
   (ADR 0010).
4. The apps find the update on their own within a day, or at once with Settings → Check for updates.
   The quiet check runs a little after the app starts (half a minute on the PC, a few seconds after the
   phone app comes to the front) and then about once a day: the tray app looks every hour and checks
   when the last check that got through is a day old. It only reads and verifies the signed manifest;
   nothing is downloaded or installed until the owner presses Install. A failed check says nothing and
   is tried again at the next look. Dev builds and builds without a channel never check. Settings →
   Updates says when a check last got through.
   Once a check has found the update, the Settings item in the Windows sidebar and the gear in the
   phone's top bar wear an accent mark with a download arrow ("Update available") until it is
   installed or a later check finds none. After a restart, a found update is checked again at once, so
   the mark comes back without waiting a day.

A failed workflow publishes no release unless it reached the last job. If a bad release went out,
see [SECURITY.md](../../SECURITY.md) → Recovery.

## 5. First install and the manual path

- **Phone:** download `GoalMaker-X.Y.Z.apk` from the latest GitHub Release, allow installing from
  that source, install. Later updates come from inside the app.
- **PC:** run `GoalMaker-X.Y.Z-setup.exe`. It needs the .NET 10 Desktop Runtime and links to its
  download page when it's missing. Keep "Start GoalMaker in the tray when I sign in" on for reminders.
- The same downloads always work by hand if the in-app updater ever fails: Settings → Open the
  latest release.
