package com.motrix.download.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.motrix.download.MainActivity
import com.motrix.download.MotrixApp
import com.motrix.download.R
import com.motrix.download.domain.model.DownloadTask
import com.motrix.download.engine.DownloadManager
import com.motrix.download.util.FormatUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(MotrixApp.NOTIFICATION_ID_SERVICE, createNotification("Starting engine...", 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ENGINE -> {
                val options = intent.getStringExtra(EXTRA_OPTIONS) ?: ""
                serviceScope.launch { startEngine(parseOptions(options)) }
            }
            ACTION_STOP_ENGINE -> {
                stopEngine()
                stopSelf()
            }
            ACTION_ADD_URI -> {
                val uri = intent.getStringExtra(EXTRA_URI) ?: ""
                val optionsStr = intent.getStringExtra(EXTRA_OPTIONS) ?: ""
                serviceScope.launch {
                    ensureEngineRunning()
                    addUri(uri, parseOptions(optionsStr))
                }
            }
            ACTION_ADD_TORRENT -> {
                val torrentPath = intent.getStringExtra(EXTRA_TORRENT_PATH) ?: ""
                val optionsStr = intent.getStringExtra(EXTRA_OPTIONS) ?: ""
                serviceScope.launch {
                    ensureEngineRunning()
                    addTorrent(torrentPath, parseOptions(optionsStr))
                }
            }
            ACTION_PAUSE -> {
                val gid = intent.getStringExtra(EXTRA_GID) ?: ""
                serviceScope.launch { downloadManager.pauseTask(gid) }
            }
            ACTION_PAUSE_ALL -> {
                serviceScope.launch { downloadManager.pauseAllTask() }
            }
            ACTION_RESUME -> {
                val gid = intent.getStringExtra(EXTRA_GID) ?: ""
                serviceScope.launch { downloadManager.resumeTask(gid) }
            }
            ACTION_RESUME_ALL -> {
                serviceScope.launch { downloadManager.resumeAllTask() }
            }
            ACTION_REMOVE -> {
                val gid = intent.getStringExtra(EXTRA_GID) ?: ""
                serviceScope.launch { downloadManager.forceRemoveTask(gid) }
            }
            ACTION_PURGE_COMPLETED -> {
                serviceScope.launch { downloadManager.purgeCompleted() }
            }
            ACTION_SYNC_TRACKERS -> {
                serviceScope.launch { syncTrackers() }
            }
        }
        return START_STICKY
    }

    private suspend fun ensureEngineRunning() {
        Log.i(TAG, "ensureEngineRunning: isEngineRunning=${downloadManager.isEngineRunning.value}")
        if (!downloadManager.isEngineRunning.value) {
            Log.i(TAG, "Engine not running, starting...")
            startEngine()
        }
    }

    private suspend fun startEngine(options: Map<String, String> = emptyMap()) {
        val started = downloadManager.startEngine(options)
        if (started) {
            updateNotification("Engine running", 0, 0)
            startStatPolling()
            try { downloadManager.resumeAllTask() } catch (_: Exception) { }
        }
    }

    private fun stopEngine() {
        statJob?.cancel()
        downloadManager.stopEngine()
    }

    private fun startStatPolling() {
        statJob?.cancel()
        statJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1500)
                try {
                    val stat = downloadManager.globalStat.value
                    val tasksResult = downloadManager.fetchDownloadingTasks()
                    val tasks = tasksResult.getOrNull() ?: emptyList()

                    var totalLength = 0L
                    var completedLength = 0L
                    var totalSpeed = 0L
                    var activeCount = 0

                    for (t in tasks) {
                        totalLength += t.totalLength
                        completedLength += t.completedLength
                        totalSpeed += t.downloadSpeed
                        if (t.status == com.motrix.download.domain.model.TaskStatus.ACTIVE) activeCount++
                    }

                    val progress = if (totalLength > 0) (completedLength * 100 / totalLength).toInt() else 0
                    val speedText = "${FormatUtils.bytesToSize(stat.downloadSpeed)}/s ↓"
                    val statusText = if (activeCount > 0) {
                        "$speedText | $activeCount active"
                    } else {
                        "Waiting…"
                    }
                    updateNotification(statusText, progress, 100, tasks)
                } catch (_: Exception) { }
            }
        }
    }

    private fun updateNotification(text: String, progress: Int, max: Int, tasks: List<DownloadTask> = emptyList()) {
        val notification = createNotification(text, progress, max, tasks)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(MotrixApp.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun createNotification(text: String, progress: Int, max: Int, tasks: List<DownloadTask> = emptyList()): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadForegroundService::class.java).apply { action = ACTION_PAUSE_ALL },
            PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = PendingIntent.getService(
            this, 2,
            Intent(this, DownloadForegroundService::class.java).apply { action = ACTION_RESUME_ALL },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 3,
            Intent(this, DownloadForegroundService::class.java).apply { action = ACTION_STOP_ENGINE },
            PendingIntent.FLAG_IMMUTABLE
        )

        // Build expanded inbox style with per-task details
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Motrix — $text")

        // Show individual download tasks in expanded view
        for (t in tasks) {
            val pct = if (t.totalLength > 0) (t.completedLength * 100 / t.totalLength).toInt() else 0
            val speed = if (t.downloadSpeed > 0) "${FormatUtils.bytesToSize(t.downloadSpeed)}/s" else ""
            val line = if (speed.isNotEmpty()) "${t.displayName} — $pct% ($speed)"
                        else "${t.displayName} — $pct%"
            inboxStyle.addLine(line)
        }

        if (tasks.isEmpty()) {
            inboxStyle.addLine("No active downloads")
        }

        return NotificationCompat.Builder(this, MotrixApp.CHANNEL_DOWNLOAD)
            .setContentTitle("Motrix")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(max, progress, max == 0)
            .setStyle(inboxStyle)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
            .addAction(android.R.drawable.ic_media_ff, "Stop", stopIntent)
            .build()
    }

    private suspend fun addUri(uri: String, options: Map<String, String>) {
        val uris = uri.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (!it.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) "http://$it" else it }
        val result = downloadManager.addUri(uris, options)
        result.onSuccess { gid ->
            Log.i(TAG, "addUri SUCCESS: gid=$gid")
        }.onFailure { e ->
            Log.e(TAG, "addUri FAILED: ${e.message}")
        }
    }

    private suspend fun addTorrent(torrentPath: String, options: Map<String, String>) {
        try {
            val file = java.io.File(torrentPath)
            val bytes = file.readBytes()
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            downloadManager.addTorrent(base64, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add torrent", e)
        }
    }

    private suspend fun syncTrackers() {
        val result = downloadManager.fetchBtTrackers()
        result.onSuccess { trackerStr ->
            downloadManager.changeGlobalOption(mapOf("bt-tracker" to trackerStr))
        }
    }

    private fun parseOptions(optionsStr: String): Map<String, String> {
        if (optionsStr.isEmpty()) return emptyMap()
        return try {
            optionsStr.split(";").associate {
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
            }.filter { it.key.isNotEmpty() }
        } catch (_: Exception) { emptyMap() }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        downloadManager.stopEngine()
        super.onDestroy()
    }

    companion object {
        const val TAG = "DownloadService"
        const val ACTION_START_ENGINE = "com.motrix.START_ENGINE"
        const val ACTION_STOP_ENGINE = "com.motrix.STOP_ENGINE"
        const val ACTION_ADD_URI = "com.motrix.ADD_URI"
        const val ACTION_ADD_TORRENT = "com.motrix.ADD_TORRENT"
        const val ACTION_PAUSE = "com.motrix.PAUSE"
        const val ACTION_PAUSE_ALL = "com.motrix.PAUSE_ALL"
        const val ACTION_RESUME = "com.motrix.RESUME"
        const val ACTION_RESUME_ALL = "com.motrix.RESUME_ALL"
        const val ACTION_REMOVE = "com.motrix.REMOVE"
        const val ACTION_PURGE_COMPLETED = "com.motrix.PURGE_COMPLETED"
        const val ACTION_SYNC_TRACKERS = "com.motrix.SYNC_TRACKERS"

        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TORRENT_PATH = "extra_torrent_path"
        const val EXTRA_GID = "extra_gid"
        const val EXTRA_OPTIONS = "extra_options"

        fun startEngine(context: Context, options: Map<String, String> = emptyMap()) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_ENGINE
                putExtra(EXTRA_OPTIONS, options.entries.joinToString(";") { "${it.key}=${it.value}" })
            }
            context.startForegroundService(intent)
        }

        fun stopEngine(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP_ENGINE
            }
            context.startService(intent)
        }

        fun addUri(context: Context, uri: String, options: Map<String, Any> = emptyMap()) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_ADD_URI
                putExtra(EXTRA_URI, uri)
                putExtra(EXTRA_OPTIONS, options.entries.joinToString(";") { "${it.key}=${it.value}" })
            }
            context.startForegroundService(intent)
        }

        fun addTorrent(context: Context, torrentPath: String, options: Map<String, Any> = emptyMap()) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_ADD_TORRENT
                putExtra(EXTRA_TORRENT_PATH, torrentPath)
                putExtra(EXTRA_OPTIONS, options.entries.joinToString(";") { "${it.key}=${it.value}" })
            }
            context.startForegroundService(intent)
        }

        fun pauseTask(context: Context, gid: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_GID, gid)
            }
            context.startService(intent)
        }

        fun pauseAllTask(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE_ALL
            }
            context.startService(intent)
        }

        fun resumeTask(context: Context, gid: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_GID, gid)
            }
            context.startService(intent)
        }

        fun resumeAllTask(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME_ALL
            }
            context.startService(intent)
        }

        fun removeTask(context: Context, gid: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_REMOVE
                putExtra(EXTRA_GID, gid)
            }
            context.startService(intent)
        }

        fun purgeCompleted(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PURGE_COMPLETED
            }
            context.startService(intent)
        }

        fun syncTrackers(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_SYNC_TRACKERS
            }
            context.startService(intent)
        }
    }
}
