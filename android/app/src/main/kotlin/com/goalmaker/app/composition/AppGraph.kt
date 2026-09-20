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
import com.goalmaker.app.application.activity.ActivityLog
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.connector.ConnectorLinks
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.ReminderService
import com.goalmaker.app.application.planning.RitualRunList
import com.goalmaker.app.application.planning.StepList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.ProfileSettings
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import com.goalmaker.app.application.sync.SyncEngine
import com.goalmaker.app.application.sync.SyncState
import com.goalmaker.app.application.update.ReleaseVerifier
import com.goalmaker.app.application.update.SignatureVerifier
import com.goalmaker.app.application.update.UpdateService
import com.goalmaker.app.data.activity.PostgrestActivityLog
import com.goalmaker.app.data.auth.SupabaseAuthGateway
import com.goalmaker.app.data.connector.PostgrestConnectorLinks
import com.goalmaker.app.data.planning.AlarmReminderScheduler
import com.goalmaker.app.data.planning.ReminderNotifications
import com.goalmaker.app.data.replica.ReplicaFileName
import com.goalmaker.app.data.replica.ReplicaMigrator
import com.goalmaker.app.data.replica.SqliteReplica
import com.goalmaker.app.data.settings.PostgrestProfileSettings
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.data.supabase.PostgrestHttp
import com.goalmaker.app.data.supabase.SupabaseClientFactory
import com.goalmaker.app.data.sync.PostgrestRemoteTables
import com.goalmaker.app.data.sync.SupabaseChangeFeed
import com.goalmaker.app.data.sync.WorkManagerSyncScheduler
import com.goalmaker.app.data.update.ApkInstallerLauncher
import com.goalmaker.app.data.update.EcdsaSignatureVerifier
import com.goalmaker.app.data.update.SupabaseReleaseChannel
import com.goalmaker.app.domain.design.DesignTokens
import com.goalmaker.app.ui.widget.Widgets
import com.goalmaker.app.domain.design.LogoMark
import com.goalmaker.app.domain.planning.PromptLibrary
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.planning.ReviewReminder
import com.goalmaker.app.domain.sync.SyncedTable
import com.goalmaker.app.domain.sync.SyncedTableCatalog
import com.goalmaker.app.domain.update.ReleasePlatform
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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

    /** The mark's shape, drawn in each theme's logo colors. */
    val logo: LogoMark = LogoMark.parse(appContext.assets.open("logo.json").use { it.readBytes().toString(Charsets.UTF_8) })

    /** The review prompts the app ships (docs/reviews.md). */
    val prompts: PromptLibrary = PromptLibrary.load(appContext.assets.open("prompts.json"))

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
    private val postgrest = PostgrestHttp(http, backend.url, backend.publishableKey) {
        supabase.auth.currentAccessTokenOrNull()
    }
    private val remote = PostgrestRemoteTables(postgrest)

    /** The Claude connector's links (docs/connector.md), read and changed online. */
    val connectorLinks: ConnectorLinks = PostgrestConnectorLinks(postgrest)

    /** The server's activity log with undo (docs/activity.md), read online. */
    val activity: ActivityLog = PostgrestActivityLog(postgrest)

    // The profile keeps the device's time zone and day start, so the connector's "today" agrees.
    private val profile: ProfileSettings = PostgrestProfileSettings(postgrest) {
        (auth.session.value as? AuthSession.SignedIn)?.userId?.takeIf(String::isNotBlank)
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
    val steps = StepList(replica, newRows, sync::request)
    val goals = GoalList(replica, newRows, sync::request)
    val habits = HabitList(replica, newRows, sync::request)
    val reviews = ReviewList(replica, newRows, sync::request)
    val projects = ProjectList(replica, newRows, sync::request)
    val tasks = TaskList(replica, newRows, areas, tags, sync::request, ::today)

    /** The planning day it is now, by the owner's day start (docs/lists.md). */
    fun today(): LocalDate = PlanningDay.of(LocalDateTime.now(), settings.dayStartHour.value)

    // Reminders (docs/reminders.md): the replica decides, AlarmManager carries the one armed alarm.
    val reminderNotifications = ReminderNotifications(appContext)
    /** The reminder rows themselves; the calendar reads them, the service arms them. */
    val reminderList = ReminderList(replica, newRows, sync::request)
    /** Which rituals ran on which planning day (docs/reminders.md, docs/reviews.md). */
    val rituals = RitualRunList(replica, newRows, sync::request)
    val reminders = ReminderService(
        reminders = reminderList,
        tasks = tasks,
        scheduler = AlarmReminderScheduler(appContext),
        quietHours = { settings.quietHours.value },
        dayStartHour = { settings.dayStartHour.value },
        now = LocalDateTime::now,
        remindedUntil = { settings.remindedUntil()?.atZone(ZoneId.systemDefault())?.toLocalDateTime() },
        setRemindedUntil = { settings.setRemindedUntil(it.atZone(ZoneId.systemDefault()).toInstant()) },
        rituals = rituals,
        planTomorrowAt = { settings.planTomorrowReminder.value },
        weeklyReviewAt = { settings.weeklyReviewReminder.value },
        weeklyReviewWeekday = { settings.weeklyReviewWeekday.value },
        monthlyReviewAt = { settings.monthlyReviewReminder.value },
    )

    private val planRequest = MutableStateFlow(false)

    /** True while the Plan tomorrow reminder asked for the ritual and it isn't on screen yet. */
    val planRequested: StateFlow<Boolean> = planRequest.asStateFlow()

    private val reviewRequest = MutableStateFlow<Pair<String, LocalDate>?>(null)

    /** The review a reminder asked for (its kind and period), until it is on screen (docs/reviews.md). */
    val reviewRequested: StateFlow<Pair<String, LocalDate>?> = reviewRequest.asStateFlow()

    private val changeFeed = SupabaseChangeFeed(supabase, catalog, scope, sync::request)
    private val backgroundSync = WorkManagerSyncScheduler(appContext)

    @Volatile private var signedIn = false

    @Volatile private var visible = false

    init {
        reminderNotifications.createChannels()
        // A sync can leave a series with two open occurrences; every device settles it the same way.
        // A sync can also change when the next reminder is due, and settle reminders on screen here
        // that were handled on the other device, so the alarm is redone and stale ones come down.
        sync.afterRun = { report ->
            if (report.pulled > 0) tasks.repairSeries()
            if (report.pulled > 0 || report.pushed > 0) {
                reminders.rearm()
                reminders.stale(reminderNotifications.shown()).forEach(reminderNotifications::clear)
                reminderNotifications.shownPlanTomorrow()
                    .filter(reminders::planTomorrowStale)
                    .forEach(reminderNotifications::clearPlanTomorrow)
                reminderNotifications.shownReviews()
                    .filter { (ritual, day) -> reminders.reviewStale(ritual, day) }
                    .forEach { (ritual, day) -> reminderNotifications.clearReview(ritual, day) }
            }
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
            settings.dayStartHour.drop(1).collect { if (signedIn) updateProfile() }
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
        val reached = !sync.syncNow().offline
        // What came in may change what the home screen shows (docs/widgets.md).
        Widgets.refresh(appContext)
        return reached
    }

    /** Relaunches the app so a changed backend takes effect (dev builds only). */
    val restartApp: () -> Unit = {
        appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.let { launch ->
            appContext.startActivity(Intent.makeRestartActivityTask(launch.component))
        }
        exitProcess(0)
    }

    /** The owner opened the app from a reminder's notification, which settles it as dismissed. */
    fun openedFromReminder(reminderId: String) {
        scope.launch(io) { reminders.dismiss(reminderId) }
    }

    /**
     * The owner opened the app from the Plan tomorrow reminder, so the ritual opens and the reminder
     * goes (Android only takes a notification down by itself when its body is tapped, not a button).
     */
    fun openedForPlan() {
        planRequest.value = true
        reminderNotifications.shownPlanTomorrow().forEach(reminderNotifications::clearPlanTomorrow)
    }

    /** The ritual is on screen, so the request is settled. */
    fun planOpened() {
        planRequest.value = false
    }

    /** The owner opened the app from a review reminder: the review opens and the reminders come down. */
    fun openedForReview(kind: String, periodStart: LocalDate) {
        reviewRequest.value = kind to periodStart
        reminderNotifications.shownReviews().forEach { (ritual, day) -> reminderNotifications.clearReview(ritual, day) }
    }

    /** The review is on screen, so the request is settled. */
    fun reviewOpened() {
        reviewRequest.value = null
    }

    /** A review was written on planning [day]: its reminder stays quiet that day on every device. */
    fun reviewFinished(kind: String, day: LocalDate) {
        val ritual = if (kind == "monthly") RitualRunList.MONTHLY_REVIEW else RitualRunList.WEEKLY_REVIEW
        scope.launch(io) {
            reminders.finishReview(ritual, day)
            reminderNotifications.clearReview(ritual, day)
        }
    }

    /** The ritual ran to the end on planning [day]: its evening reminder stays quiet that day everywhere. */
    fun planTomorrowFinished(day: LocalDate) {
        scope.launch(io) {
            reminders.finishPlanTomorrow(day)
            reminderNotifications.clearPlanTomorrow(day)
        }
    }

    private suspend fun onSignedIn(session: AuthSession.SignedIn) {
        signedIn = true
        withContext(io) { forgetOtherAccounts(session.userId) }
        sync.request()
        backgroundSync.keepSyncing()
        // Anything that was due while the app was away, and the alarm for what comes next.
        withContext(io) {
            val look = reminders.catchUp()
            look.reminders.forEach(reminderNotifications::show)
            look.planTomorrow?.let(reminderNotifications::showPlanTomorrow)
            look.weeklyReview?.let { day ->
                reminderNotifications.showReview(RitualRunList.WEEKLY_REVIEW, day, "weekly", ReviewReminder.periodStart("weekly", day))
            }
            look.monthlyReview?.let { day ->
                reminderNotifications.showReview(RitualRunList.MONTHLY_REVIEW, day, "monthly", ReviewReminder.periodStart("monthly", day))
            }
        }
        if (visible) changeFeed.start()
        updateProfile()
    }

    // Best effort: offline, the next sign-in or day-start change writes it.
    private suspend fun updateProfile() {
        try {
            withContext(io) { profile.update(ZoneId.systemDefault().id, settings.dayStartHour.value) }
        } catch (_: RemoteUnavailableException) {
        } catch (_: RemoteRejectedException) {
        }
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
