# M0-01: Planning docs and repository layout

**Status:** in progress · **Milestone:** M0

## Scope
- `docs/spec.md`, `docs/roadmap.md`, `CONTEXT.md`, ADRs 0001 to 0006.
- README, ARCHITECTURE, AGENTS, CONTRIBUTING and SECURITY describe the real repo.
- Top-level folders: `android/`, `windows/`, `supabase/`, `contracts/`.
- A single version source (`version.properties`) read by both apps and the release workflow.

## Acceptance criteria
- `python tools/validate_repository.py --root .` passes.
- No template placeholder text remains in the root docs.
