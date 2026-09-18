# M0-04: Android app skeleton

**Status:** done 2026-09-18 · **Milestone:** M0

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

## Result
- compileSdk 37 (required by the Expressive alpha), targetSdk 36.
- Verified on the emulator: email code sign-in against the local stack, profile row created, Settings, dark theme.
- A minified, signed release APK starts and reaches the network layer without R8 problems.

## Follow-up
- Sign in with a release build over HTTPS once the cloud project exists.
- Encrypt the stored session with the Android Keystore before v1.0.
