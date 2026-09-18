package com.goalmaker.app.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.goalmaker.app.application.update.UpdateInstaller
import java.io.File

/**
 * Opens the system package installer for a verified APK through the `updates` FileProvider. The
 * installer shows its own confirmation and only accepts an APK signed with the installed app's key.
 */
class ApkInstallerLauncher(private val context: Context) : UpdateInstaller {
    override fun launch(localPath: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", File(localPath))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
