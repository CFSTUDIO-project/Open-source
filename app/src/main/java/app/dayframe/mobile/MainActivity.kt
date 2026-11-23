package app.dayframe.mobile

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.dayframe.mobile.ui.DayframeApp

data class DownloadUiState(
    val downloading: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null
)

class MainActivity : ComponentActivity() {
    private lateinit var downloadManager: DownloadManager
    private var downloadId: Long = -1L
    private var receiverRegistered = false

    private var downloadState by mutableStateOf(DownloadUiState())

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val completedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (completedId != downloadId || completedId == -1L) return
            readDownloadState(completedId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        restoreDownload()

        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true

        setContent {
            DayframeApp(
                downloadState = downloadState,
                onDownloadApk = { url, version -> enqueueApk(url, version) },
                onInstallApk = { installDownloadedApk() }
            )
        }
    }

    private fun enqueueApk(address: String, version: String) {
        val uri = runCatching { Uri.parse(address) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host != "github.com") {
            downloadState = DownloadUiState(error = "The APK link is not a trusted GitHub release URL.")
            return
        }

        runCatching {
            val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val request = DownloadManager.Request(uri)
                .setTitle("DAYFRAME $safeVersion")
                .setDescription("Downloading the published APK from GitHub Releases")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "DAYFRAME-$safeVersion.apk"
                )

            downloadId = downloadManager.enqueue(request)
            getSharedPreferences("dayframe.downloads", MODE_PRIVATE)
                .edit()
                .putLong("download_id", downloadId)
                .apply()
            downloadState = DownloadUiState(downloading = true)
            Toast.makeText(this, "DAYFRAME download started.", Toast.LENGTH_SHORT).show()
        }.onFailure {
            downloadState = DownloadUiState(error = it.message ?: "Could not start the download.")
        }
    }

    private fun restoreDownload() {
        downloadId = getSharedPreferences("dayframe.downloads", MODE_PRIVATE)
            .getLong("download_id", -1L)
        if (downloadId != -1L) readDownloadState(downloadId)
    }

    private fun readDownloadState(id: Long) {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) {
                downloadState = DownloadUiState()
                return
            }
            when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> downloadState = DownloadUiState(completed = true)
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    downloadState = DownloadUiState(error = "Download failed (code $reason). Try again.")
                }
                else -> downloadState = DownloadUiState(downloading = true)
            }
        }
    }

    private fun installDownloadedApk() {
        if (downloadId == -1L) {
            Toast.makeText(this, "Download an update first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(settingsIntent)
            Toast.makeText(this, "Allow installs for DAYFRAME, then tap Install again.", Toast.LENGTH_LONG).show()
            return
        }

        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
        if (apkUri == null) {
            Toast.makeText(this, "The downloaded APK could not be found.", Toast.LENGTH_LONG).show()
            return
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(installIntent) }
            .onFailure {
                Toast.makeText(this, "Android could not open the package installer.", Toast.LENGTH_LONG).show()
            }
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(downloadReceiver)
        super.onDestroy()
    }
}
