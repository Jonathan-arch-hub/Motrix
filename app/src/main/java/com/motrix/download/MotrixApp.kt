package com.motrix.download

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.motrix.download.engine.DownloadManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MotrixApp : Application() {
    @Inject lateinit var downloadManager: DownloadManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        instance = this
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher?.let { it(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }

    private var requestPermissionLauncher: ((String) -> Unit)? = null

    fun setPermissionLauncher(launcher: (String) -> Unit) {
        requestPermissionLauncher = launcher
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Download Progress",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows download progress notifications"
                setShowBadge(true)
                enableVibration(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE,
                "Download Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a download is complete"
            }

            val errorChannel = NotificationChannel(
                CHANNEL_ERROR,
                "Download Errors",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a download fails"
            }

            manager.createNotificationChannel(downloadChannel)
            manager.createNotificationChannel(completeChannel)
            manager.createNotificationChannel(errorChannel)
        }
    }

    companion object {
        const val CHANNEL_DOWNLOAD = "download_progress"
        const val CHANNEL_COMPLETE = "download_complete"
        const val CHANNEL_ERROR = "download_error"
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_COMPLETE = 2001
        const val NOTIFICATION_ID_ERROR = 3001

        lateinit var instance: MotrixApp
            private set
    }
}
