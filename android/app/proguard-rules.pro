-keep class com.goalmaker.app.ui.nav.** { *; }
# kotlinx.serialization and Ktor ship their own consumer rules; add project rules here when R8 needs them.
# WorkManager stores worker class names; a renamed SyncWorker would orphan scheduled syncs after an update.
-keepnames class com.goalmaker.app.data.sync.SyncWorker
# Glance finds a widget's placed copies by its GlanceAppWidget class name (updateAll, getGlanceIds).
# Without this R8 folds the widget classes into one and each widget draws into the others' places,
# and a renamed class loses its places after an update (docs/pitfalls.md).
-keep,allowshrinking class * extends androidx.glance.appwidget.GlanceAppWidget
# Glance makes a widget's tap callback from its class name with the no-argument constructor
# (actionRunCallback). Nothing else calls that constructor, so R8 removed it and every tap on a
# Today or Habits row failed with NoSuchMethodException (docs/pitfalls.md).
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# The PC sign-in scanner (Play services code scanner, ML Kit) builds its parts through reflection-like
# component registries. Without these rules R8 crashed the app on "Scan the code" in release builds
# only (docs/pitfalls.md).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
