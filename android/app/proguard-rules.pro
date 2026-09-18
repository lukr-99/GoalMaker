-keep class com.goalmaker.app.ui.nav.** { *; }
# kotlinx.serialization and Ktor ship their own consumer rules; add project rules here when R8 needs them.
# WorkManager stores worker class names; a renamed SyncWorker would orphan scheduled syncs after an update.
-keepnames class com.goalmaker.app.data.sync.SyncWorker
