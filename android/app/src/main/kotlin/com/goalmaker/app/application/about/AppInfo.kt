package com.goalmaker.app.application.about

import com.goalmaker.app.application.environment.BackendEnvironment

/**
 * Facts about this build, shown in Settings → About. [localOnly] is a dev build that neither signs in
 * nor syncs and keeps everything on the device (docs/sign-in.md).
 */
data class AppInfo(
    val versionName: String,
    val isDevBuild: Boolean,
    val backend: BackendEnvironment,
    val defaultBackend: BackendEnvironment,
    val localOnly: Boolean = false,
)
