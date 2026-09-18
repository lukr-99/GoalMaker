package com.goalmaker.app.composition

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.goalmaker.app.BuildConfig
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.ReminderService
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.application.sync.SyncEngine
import com.goalmaker.app.application.sync.SyncState
import com.goalmaker.app.application.update.ReleaseVerifier
import com.goalmaker.app.application.update.SignatureVerifier
import com.goalmaker.app.application.update.UpdateService
import com.goalmaker.app.data.auth.SupabaseAuthGateway
import com.goalmaker.app.data.planning.AlarmReminderScheduler
import com.goalmaker.app.data.planning.ReminderNotifications
import com.goalmaker.app.data.replica.ReplicaFileName
import com.goalmaker.app.data.replica.ReplicaMigrator
import com.goalmaker.app.data.replica.SqliteReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.data.supabase.SupabaseClientFactory
import com.goalmaker.app.data.sync.PostgrestRemoteTables
import com.goalmaker.app.data.sync.SupabaseChangeFeed
import com.goalmaker.app.data.sync.WorkManagerSyncScheduler
import com.goalmaker.app.data.update.ApkInstallerLauncher
import com.goalmaker.app.data.update.EcdsaSignatureVerifier
import com.goalmaker.app.data.update.SupabaseReleaseChannel
import com.goalmaker.app.domain.design.DesignTokens
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import com.goalmaker.app.domain.update.ReleasePlatform
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The one composition root: every adapter is created here and handed to the code that needs it
 * through constructors (CodePrint architecture rule). Lives as long as the process.
 */
class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Where replica and other disk work runs. */
    val io: CoroutineDispatcher = Dispatchers.IO

    val settings: SettingsStore = SharedPreferencesSettingsStore(
        appContext.getSharedPreferences("goalmaker_settings", Context.MODE_PRIVATE),
    )

    /** The themes, area colors, spacing and motion (ADR 0008), read once from the packaged tokens. */
    val design: DesignTokens = DesignTokens.parse(
        appContext.assets.open("themes.json").use { it.readBytes().toString(Charsets.UTF_8) },
    )

    private val defaultBackend = BackendEnvironment(BuildConfig.DEFAULT_SUPABASE_URL, BuildConfig.DEFAULT_SUPABASE_KEY)
    private val backend = (if (BuildConfig.IS_DEV_BUILD) settings.backendOverride() else null) ?: defaultBackend
    private val supabase = SupabaseClientFactory.create(backend)

    val appInfo = AppInfo(
        versionName = BuildConfig.VERSION_NAME,
        isDevBuild = BuildConfig.IS_DEV_BUILD,
        backend = backend,
        defaultBackend = defaultBackend,
    )

    val auth: AuthGateway = SupabaseAuthGateway(supabase, scope)

    private val manifestKey = BuildConfig.RELEASE_MANIFEST_PUBLIC_KEY
    private val signatureVerifier: SignatureVerifier =
        if (manifestKey.isBlank()) SignatureVerifier { _, _ -> false } else EcdsaSignatureVerifier(manifestKey)

    val updates = UpdateService(
        installedVersion = BuildConfig.VERSION_NAME,
        platform = ReleasePlatform.ANDROID,
        channelConfigured = manifestKey.isNotBlank(),
        channel = SupabaseReleaseChannel(supabase, File(appContext.cacheDir, "updates")),
        verifier = ReleaseVerifier(signatureVerifier),
        installer = ApkInstallerLauncher(appContext),
    )

    // Sync (docs/sync.md, ADR 0007). The replica opens on first use, off the main thread.
    private val catalog = SyncedTableCatalog.parse(
        appContext.assets.open("synced-tables.json").use { it.readBytes().toString(Charsets.UTF_8) },
    )
    private val replica = SqliteReplica(
        driver = BundledSQLiteDriver(),
        path = appContext.getDatabasePath(ReplicaFileName.forBackend(backend.url)).path,
        catalog = catalog,
        migrations = { ReplicaMigrator.builtIn(appContext.assets) },
    )
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }
    private val remote = PostgrestRemoteTables(http, backend.url, backend.publishableKey) {
        supabase.auth.currentAccessTokenOrNull()
    }

    val sync = SyncCoordinator(
        engine = SyncEngine(catalog, replica, remote, Instant::now),
        replica = replica,
        scope = scope,
        io = io,
        now = Instant::now,
        debounce = 2.seconds,
    )

    private val newRows = NewRows(
        catalog = catalog,
        ownerId = { (auth.session.value as? AuthSession.SignedIn)?.userId?.takeIf(String::isNotBlank) },
        now = Instant::now,
    )
    val areas = AreaList(replica, newRows, design.areaColors.map { it.id }, sync::request)
    val tags = TagList(replica, newRows, sync::request)
    val tasks = TaskList(replica, newRows, areas, tags, sync::request) {
        PlanningDay.of(LocalDateTime.now(), settings.dayStartHour.value)
    }

    // Reminders (docs/reminders.md): the replica decides, AlarmManager carries the one armed alarm.
    val reminderNotifications = ReminderNotifications(appContext)
    private val reminderList = ReminderList(replica, newRows, sync::request)
    val reminders = ReminderService(
        reminders = reminderList,
        tasks = tasks,
        scheduler = AlarmReminderScheduler(appContext),
        quietHours = { settings.quietHours.value },
        dayStartHour = { settings.dayStartHour.value },
        now = LocalDateTime::now,
    )

    private val changeFeed = SupabaseChangeFeed(supabase, catalog, scope, sync::request)
    private val backgroundSync = WorkManagerSyncScheduler(appContext)

    @Volatile private var signedIn = false

    @Volatile private var visible = false

    init {
        reminderNotifications.createChannels()
        // A sync can leave a series with two open occurrences; every device settles it the same way.
        // A pull can also change when the next reminder is due, so the armed alarm is redone.
        sync.afterRun = { report ->
            if (report.pulled > 0) tasks.repairSeries()
            if (report.pulled > 0 || report.pushed > 0) reminders.rearm()
        }
        scope.launch {
            auth.session.collect { session ->
                when (session) {
                    is AuthSession.SignedIn -> onSignedIn(session)
                    AuthSession.SignedOut -> onSignedOut()
                    AuthSession.Loading -> Unit
                }
            }
        }
        scope.launch {
            sync.status.collect { status ->
                if (status.state == SyncState.OFFLINE && status.pendingChanges > 0) backgroundSync.syncWhenOnline()
            }
        }
        // Realtime only while the app is on screen; WorkManager covers the rest.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    visible = true
                    if (signedIn) {
                        changeFeed.start()
                        sync.request()
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    visible = false
                    changeFeed.stop()
                }
            },
        )
    }

    /**
     * One sync for WorkManager. True when done (or nobody is signed in), false when the server
     * couldn't be reached and the run should be retried.
     */
    suspend fun syncInBackground(): Boolean {
        if (auth.session.first { it != AuthSession.Loading } !is AuthSession.SignedIn) return true
        return !sync.syncNow().offline
    }

    /** Relaunches the app so a changed backend takes effect (dev builds only). */
    val restartApp: () -> Unit = {
        appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.let { launch ->
            appContext.startActivity(Intent.makeRestartActivityTask(launch.component))
        }
        exitProcess(0)
    }

    private suspend fun onSignedIn(session: AuthSession.SignedIn) {
        signedIn = true
        withContext(io) { forgetOtherAccounts(session.userId) }
        sync.request()
        backgroundSync.keepSyncing()
        // Anything that was due while the app was away, and the alarm for what comes next.
        withContext(io) { reminders.catchUp().forEach(reminderNotifications::show) }
        if (visible) changeFeed.start()
    }

    private fun onSignedOut() {
        signedIn = false
        sync.cancelScheduled()
        backgroundSync.stop()
        changeFeed.stop()
        reminders.rearm()
    }

    // A replica only ever holds one account's rows; signing in as someone else starts clean.
    private fun forgetOtherAccounts(userId: String) {
        if (userId.isBlank()) return
        val foreign = catalog.tables.any { table ->
            replica.all(table.name).any { row ->
                (row[SyncedTable.OWNER_ID] as? JsonPrimitive)?.contentOrNull.let { it != null && it != userId }
            }
        }
        if (foreign) replica.clearAll()
    }
}
