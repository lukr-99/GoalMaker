# Security

## Supported versions

Only the latest release receives fixes. GoalMaker has one user and no public distribution.

## Reporting

This is a private, single-owner project. Report problems to the owner directly, never in a public
issue.

## Trust boundaries

- **Supabase Auth:** sign-in with an emailed 6-digit code (10-minute expiry). After the owner's
  account exists, new sign-ups are switched off in the cloud project
  ([docs/setup/cloud-supabase.md](docs/setup/cloud-supabase.md)).
- **Row security:** every table allows only its owner (`auth.uid() = owner`). Anonymous callers get
  nothing. pgTAP tests prove this for every table.
- **Keys:** the publishable key is built into the apps and is public by design. The secret key is
  used only by the release workflow (GitHub secret) and never ships in an app.
- **Sessions:** Windows keeps the session in a DPAPI-encrypted file readable only by the same Windows
  user. Android keeps it in private app storage, excluded from Android backups; encrypting it with the
  Android Keystore is planned before v1.0.
- **Update channel:** the latest public GitHub Release (ADR 0010). Artifacts are trusted only through
  the release manifest's ECDSA P-256 signature
  (key built into the apps) and each artifact's size and SHA-256. Development builds never update
  themselves. The manifest signing key lives offline and in one GitHub secret.
- **Android release key:** offline backup on the owner's drive, password only in the password
  manager; an APK signed by any other key can't replace the installed app.
- **Windows:** the installer is per-user and never elevates; the single-instance pipe accepts only
  the current user.
- **Connector (M3):** a secret link stored as a hash, rotatable and revocable, acting under row
  security (ADR 0003).

## Recovery

- **Leaked session or lost device:** sign out everywhere from the Supabase dashboard (Authentication →
  Users → the owner → sign out), then sign in again.
- **Leaked secret key:** roll it in the Supabase dashboard (Settings → API Keys) and update the
  `GOALMAKER_SUPABASE_SECRET_KEY` secret.
- **Leaked or lost manifest signing key:** create a new one with `tools/setup-update-signing.ps1`,
  commit the new public key, and install the next release of each app by hand once.
- **Lost Android release key:** the app can no longer update in place; uninstall and install a build
  signed with a new key (data lives in Supabase, so nothing is lost).
- **Bad release:** delete that GitHub Release (and `releases/latest/` in the old Storage bucket while
  it is still fed) to stop the offer, publish a fixed version, and install it by hand if needed.
