package com.goalmaker.app.application.about

import com.goalmaker.app.application.environment.BackendEnvironment

/** Facts about this build, shown in Settings → About. */
data class AppInfo(
    val versionName: String,
    val isDevBuild: Boolean,
    val backend: BackendEnvironment,
    val defaultBackend: BackendEnvironment,
)
