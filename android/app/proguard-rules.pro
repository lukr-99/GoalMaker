-keep class com.goalmaker.app.ui.nav.** { *; }
# kotlinx.serialization and Ktor ship their own consumer rules; add project rules here when R8 needs them.
# WorkManager stores worker class names; a renamed SyncWorker would orphan scheduled syncs after an update.
-keepnames class com.goalmaker.app.data.sync.SyncWorker
# Glance finds a widget's placed copies by its GlanceAppWidget class name (updateAll, getGlanceIds).
# Without this R8 folds the widget classes into one and each widget draws into the others' places,
# and a renamed class loses its places after an update (docs/pitfalls.md).
-keep,allowshrinking class * extends androidx.glance.appwidget.GlanceAppWidget
