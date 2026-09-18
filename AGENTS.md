# AGENTS

## Required context

1. Read `ARCHITECTURE.md` before changing module seams or dependency direction.
2. Read `CONTEXT.md` before introducing domain terms.
3. Read the relevant guide linked from this repository's README.

## Baseline

- One top-level type per matching file.
- Constructor injection and one explicit composition root.
- Domain/application code does not depend on UI, storage, network, or platform APIs.
- Add or update deterministic tests with behavior changes.
- Database migrations are immutable `0001_description.sql` files and receive full-chain plus
  isolated predecessor tests.
- Use Conventional Commits and several coherent commits for independent slices.
- Keep README, ARCHITECTURE, AGENTS, data-safety, and delivery docs current.

## Verification

```powershell
python tools/validate_repository.py --root .
# Add the repository's format, build, unit, integration, and package commands.
```

