package com.engvocab.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.engvocab.app.BuildConfig
import com.engvocab.app.data.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A newer build found on the GitHub "latest" release than the one currently installed. */
data class UpdateInfo(val versionCode: Int, val apkUrl: String)

sealed interface InstallOutcome {
    /** The system's "Install app?" dialog was launched - the user still has to confirm it there. */
    data object Launched : InstallOutcome

    /** EngVocab isn't allowed to install unknown apps yet; call [UpdateService.openInstallPermissionSettings]. */
    data object NeedsInstallPermission : InstallOutcome
}

/**
 * Checks the app's own GitHub Actions release ("latest" tag) for a newer build than the one
 * installed, and can download + prompt to install it. There's no Play Store distribution, so this
 * is the update channel: CI stamps every build's versionCode with the GitHub Actions run number
 * and publishes it alongside the APK as a tiny text file, which this class compares against
 * [BuildConfig.VERSION_CODE].
 *
 * Android never allows a sideloaded app to install a new APK without the user confirming the
 * system's install prompt - that final tap can't be automated away, even with the
 * REQUEST_INSTALL_PACKAGES permission.
 */
class UpdateService(
    private val context: Context,
    private val client: OkHttpClient = HttpClientProvider.client,
) {
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(VERSION_URL).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val remoteVersionCode = response.body?.string()?.trim()?.toIntOrNull() ?: return@withContext null
                if (remoteVersionCode > BuildConfig.VERSION_CODE) UpdateInfo(remoteVersionCode, APK_URL) else null
            }
        } catch (e: IOException) {
            null
        }
    }

    suspend fun downloadAndInstall(apkUrl: String): Result<InstallOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadClient = client.newBuilder().readTimeout(2, TimeUnit.MINUTES).build()
            val request = Request.Builder().url(apkUrl).build()
            val dir = File(context.cacheDir, "apk-updates").apply { mkdirs() }
            val apkFile = File(dir, "engvocab-update.apk")

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed (HTTP ${response.code})")
                val body = response.body ?: error("Empty download response")
                apkFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }

            if (!context.packageManager.canRequestPackageInstalls()) {
                return@runCatching InstallOutcome.NeedsInstallPermission
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            InstallOutcome.Launched
        }
    }

    /** Sends the user to the system settings screen where they can allow EngVocab to install unknown apps. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        private const val REPO = "alessiomartini/flash-cards-app"
        private const val VERSION_URL = "https://github.com/$REPO/releases/download/latest/engvocab-version.txt"
        const val APK_URL = "https://github.com/$REPO/releases/download/latest/engvocab-debug.apk"
    }
}
