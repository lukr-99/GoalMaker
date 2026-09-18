package com.goalmaker.app.application.update

/** Hands a verified artifact to the platform installer, which asks the user to confirm. */
fun interface UpdateInstaller {
    fun launch(localPath: String)
}
