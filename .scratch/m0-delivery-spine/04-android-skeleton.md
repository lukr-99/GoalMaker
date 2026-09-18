# M0-04: Android app skeleton

**Status:** todo · **Milestone:** M0

## Scope
- Gradle project in `android/` (Kotlin, Compose, Material 3 Expressive pinned, Navigation 3).
- Composition root, domain/application/data/ui packages, semantic theme tokens with light, dark and
  system modes.
- Debug identity `com.goalmaker.app.debug`, `-dev` version; release identity `com.goalmaker.app`.
- Backend environment: debug defaults to the local stack with a debug-only switch; release reads
  the cloud URL and publishable key from untracked properties or CI secrets.
- Sign-in with an emailed 6-digit code (supabase-kt); signed-in home placeholder; sign out.
- Release signing from `keystore.properties` or CI secrets; `tools/setup-signing.ps1`.
- Device tooling from the CodePrint Android profile.

## Acceptance criteria
- `gradlew testDebugUnitTest assembleDebug lintDebug` passes.
- On the emulator, a code sent by the local stack signs in.
