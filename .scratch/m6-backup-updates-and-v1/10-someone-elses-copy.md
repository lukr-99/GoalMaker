# M6-10: Someone else's copy

**Status:** todo · **Milestone:** M6 (before the repository is public)

## Scope

GoalMaker is one person's planner, and the way it is built says so: one cloud project, one sender,
one signing key, one owner. That is the right shape, but it means a stranger who likes the app cannot
use it without standing up their own of each. Right now the repository half-assumes it is ours.

- **What is ours and must become theirs**, all of it named in one place rather than found by grep:
  - `supabase/config.toml`: `project_id` and `[remotes.production].auth.site_url` are this project's
    ref. A fork must change both, and nothing should push to ours by accident.
  - `contracts/keys/release-manifest-public.b64`: our update signing key. A fork keeping it would
    trust our releases and not their own, which is the one mistake here with teeth.
  - The `gh secret set --repo lukr-99/GoalMaker` lines in `docs/setup/`, and the eight
    `GOALMAKER_*` secrets they set.
  - The Android release keystore and `applicationId`, if they ever publish rather than sideload.
- **One guide, `docs/setup/your-own-copy.md`**, that runs start to finish for someone who has just
  cloned it: a Supabase project, the schema, the settings, a sender, the two signing keys, the
  secrets, the first release. It should read as a list of things to do, each pointing at the setup
  doc that already covers it, rather than repeating them.
- **A check that a fork is not half-configured**: `tools/validate_repository.py` (or a small script
  beside it) says plainly when `config.toml` still names another project than the one the signing key
  and the secrets belong to. Failing loudly beats a release nobody can install.
- **The licence stays what it is** (PolyForm Noncommercial): this is about someone running their own
  copy, not about selling it. The guide should say so in a line.
- **Nothing personal in the repository.** There is none today; keep it that way, and say where owner
  details do live (the offline drive, the password manager, GitHub secrets).

## Acceptance criteria

- A clean clone on a machine with no GoalMaker state reaches a signed-in release build on a phone and
  a PC, following only `docs/setup/your-own-copy.md`.
- The repository names our project ref in exactly the places the guide says to change, and the
  validator refuses a half-changed one.
- `contracts/vectors/dev-sign-in.json` and the other examples use a placeholder project, not ours.
