package com.goalmaker.app.composition

import android.content.Context
import android.content.Intent
import com.goalmaker.app.BuildConfig
import com.goalmaker.app.application.about.AppInfo
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.environment.BackendEnvironment
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.update.ReleaseVerifier
import com.goalmaker.app.application.update.SignatureVerifier
import com.goalmaker.app.application.update.UpdateService
import com.goalmaker.app.data.auth.SupabaseAuthGateway
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.data.supabase.SupabaseClientFactory
import com.goalmaker.app.data.update.ApkInstallerLauncher
import com.goalmaker.app.data.update.EcdsaSignatureVerifier
import com.goalmaker.app.data.update.SupabaseReleaseChannel
import com.goalmaker.app.domain.update.ReleasePlatform
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The one composition root: every adapter is created here and handed to the code that needs it
 * through constructors (CodePrint architecture rule). Lives as long as the process.
 */
class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore = SharedPreferencesSettingsStore(
        appContext.getSharedPreferences("goalmaker_settings", Context.MODE_PRIVATE),
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

    /** Relaunches the app so a changed backend takes effect (dev builds only). */
    val restartApp: () -> Unit = {
        appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.let { launch ->
            appContext.startActivity(Intent.makeRestartActivityTask(launch.component))
        }
        exitProcess(0)
    }
}
