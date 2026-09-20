# AGENTS

## Required context

1. Read `ARCHITECTURE.md` before changing module seams or dependency direction.
2. Read `CONTEXT.md` before introducing domain terms; use its words in code and copy.
3. Planning source of truth: `docs/spec.md`, `docs/roadmap.md`, ADRs in `docs/adr/`, and the
   milestone issues in `.scratch/<milestone>/`. The grilling record is `docs/grilling/`.
4. For setup and delivery work, read the matching guide in `docs/setup/`.
5. `docs/pitfalls.md` lists the mistakes this project has already made; add one when a bug took
   longer to find than to fix.

## Baseline (CodePrint)

- One top-level type per matching file; folders by capability, then role.
- Constructor injection and one explicit composition root per app (`AppGraph`).
- Domain/application code does not depend on UI, storage, network, or platform APIs.
- Behavior shared by Kotlin and C# gets a vector file in `contracts/`, and both apps' tests read it.
- Supabase migrations are immutable `0001_description.sql` files, locked with
  `python tools/supabase_migrations.py lock`, each with `supabase/migration-tests/NNNN_before.sql`
  and `NNNN_after.sql` fixtures and pgTAP row-security tests.
- Add or update deterministic tests with every behavior change.
- Conventional Commits, several coherent commits for independent slices. No Co-Authored-By trailer.
- Keep README, ARCHITECTURE, AGENTS, data-safety and delivery docs current.
- Plain English in all text, no em-dashes.

## Project specifics

- Android: Kotlin + Compose (Material 3 Expressive pinned alpha, Navigation 3), supabase-kt, min SDK
  26, compile 37, target 36, package root `com.goalmaker.app`. Device scripts in `android/tools/`.
- Windows: .NET 10 WPF with WPF UI, CommunityToolkit.Mvvm, H.NotifyIcon, Supabase C# client.
  All copy in `windows/src/GoalMaker.App/Resources/Strings.xaml`; view models get text via `IStrings`.
- Local Supabase stack: ports 553xx (`supabase/config.toml`); sign-in codes appear in the mail viewer
  at http://127.0.0.1:55324. Debug builds point at it by default.
- Launch dev builds of the Windows app with `--no-activate` so they don't take the keyboard focus;
  the window remembers its position.
- Never commit secrets, keystores, `local.properties`, `keystore.properties`,
  `windows/GoalMaker.local.props`, or private signing keys. The manifest public key in
  `contracts/keys/` is the only key material in the repository.

## Verification

```powershell
python tools/validate_repository.py --root .
powershell -ExecutionPolicy Bypass -File tools/test-powershell-syntax.ps1
python tools/supabase_migrations.py test          # needs: npx supabase start
android\gradlew.bat -p android testDebugUnitTest assembleDebug lintDebug
dotnet format windows\GoalMaker.slnx --verify-no-changes
dotnet test --solution windows\GoalMaker.slnx
```
