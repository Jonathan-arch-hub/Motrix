package com.motrix.download.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.motrix.download.MainActivity
import com.motrix.download.MotrixApp
import com.motrix.download.domain.model.DownloadTask
import com.motrix.download.domain.model.TaskStatus
import com.motrix.download.engine.DownloadManager
import com.motrix.download.util.FormatUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        Log.i(TAG, "╔══════════════════════════════════╗")
        Log.i(TAG, "║  SERVICE onCreate()              ║")
        Log.i(TAG, "╚══════════════════════════════════╝")
        super.onCreate()
        val notif = createNotification(NotificationState(
            title = "Motrix",
            text = "Starting engine...",
            progress = 0,
            indeterminate = false,
            activeCount = 0,
            expandedLines = listOf("Starting download engine..."),
            subText = ""
        ))
        Log.i(TAG, "[onCreate] Calling startForeground(${MotrixApp.NOTIFICATION_ID_SERVICE})")
        startForeground(MotrixApp.NOTIFICATION_ID_SERVICE, notif)
        Log.i(TAG, "[onCreate] startForeground OK")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "╔══════════════════════════════════╗")
        Log.i(TAG, "║  SERVICE onStartCommand()        ║")
        Log.i(TAG, "╚══════════════════════════════════╝")
        Log.i(TAG, "  intent?.action = ${intent?.action}")
        Log.i(TAG, "  flags = $flags")
        Log.i(TAG, "  startId = $startId")

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
        if (!downloadManager.isEngineRunning.value) {
            Log.i(TAG, "[ensureEngineRunning] Engine not running, starting...")
            startEngine()
        }
    }

    private fun ensurePollingRunning() {
        if (statJob?.isActive != true) {
            Log.i(TAG, "[ensurePollingRunning] statJob not active, starting polling")
            startStatPolling()
        }
    }

    private suspend fun startEngine(options: Map<String, String> = emptyMap()) {
        Log.i(TAG, "╔══════════════════════════════════╗")
        Log.i(TAG, "║  SERVICE startEngine()           ║")
        Log.i(TAG, "╚══════════════════════════════════╝")
        Log.i(TAG, "  options = $options")
        try {
            Log.i(TAG, "  calling downloadManager.startEngine()...")
            val started = downloadManager.startEngine(options)
            Log.i(TAG, "  downloadManager.startEngine() returned $started")
            if (started) {
                Log.i(TAG, "  Engine started OK, updating notification")
                updateNotificationNow(text = "Engine running", expandedLines = listOf("Engine running"))
                Log.i(TAG, "  Starting stat polling...")
                startStatPolling()
                Log.i(TAG, "  Resuming all tasks...")
                try { downloadManager.resumeAllTask() } catch (e: Exception) {
                    Log.w(TAG, "  resumeAllTask() failed: ${e.message}")
                }
                Log.i(TAG, "  startEngine() complete")
            } else {
                val err = downloadManager.engineError.value
                Log.e(TAG, "  Engine FAILED to start: $err")
                updateNotificationNow(text = "Engine failed", expandedLines = listOf("Failed to start engine: $err"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "╔═══ CRASH in startEngine() ═══╗")
            Log.e(TAG, "  ${e.javaClass.name}: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "╚══════════════════════════════╝")
            updateNotificationNow(text = "Engine error: ${e.message}", expandedLines = listOf("Engine error: ${e.message}"))
        }
    }

    private fun stopEngine() {
        Log.i(TAG, "[stopEngine] cancelling polling + stopping engine")
        statJob?.cancel()
        downloadManager.stopEngine()
    }

    private fun startStatPolling() {
        statJob?.cancel()
        Log.i(TAG, "╔══════════════════════════════════╗")
        Log.i(TAG, "║  SERVICE startStatPolling()      ║")
        Log.i(TAG, "╚══════════════════════════════════╝")
        statJob = serviceScope.launch(Dispatchers.Default) {
            Log.i(TAG, "[polling] Loop started on ${Thread.currentThread().name}")
            var iteration = 0
            var lastLogTime = System.currentTimeMillis()
            while (isActive) {
                iteration++
                val now = System.currentTimeMillis()
                try {
                    Log.d(TAG, "[polling#$iteration] fetchDownloadingTasks()...")
                    val tasksResult = downloadManager.fetchDownloadingTasks()
                    Log.d(TAG, "[polling#$iteration] result.isSuccess = ${tasksResult.isSuccess}")
                    val tasks = tasksResult.getOrNull()
                    if (tasks != null) {
                        Log.d(TAG, "[polling#$iteration] tasks count = ${tasks.size}")
                        if (tasks.isNotEmpty()) {
                            tasks.forEachIndexed { i, t ->
                                val pct = if (t.totalLength > 0) (t.completedLength * 100 / t.totalLength) else 0
                                Log.d(TAG, "[polling#$iteration]   task[$i]: gid=${t.gid} status=${t.status} " +
                                    "name=${t.displayName} progress=${pct}% " +
                                    "dl=${FormatUtils.bytesToSize(t.downloadSpeed)}/s " +
                                    "${FormatUtils.bytesToSize(t.completedLength)}/${FormatUtils.bytesToSize(t.totalLength)}")
                            }
                        } else {
                            Log.d(TAG, "[polling#$iteration]   (empty list)")
                        }
                    } else {
                        val error = tasksResult.exceptionOrNull()
                        Log.w(TAG, "[polling#$iteration] fetchDownloadingTasks() FAILED: ${error?.message}")
                    }
                    val state = buildNotificationState(tasks ?: emptyList())
                    Log.d(TAG, "[polling#$iteration] state: title='${state.title}' text='${state.text}' " +
                        "progress=${state.progress} indeterminate=${state.indeterminate} " +
                        "expandedLines=${state.expandedLines.size} subText='${state.subText}'")
                    updateNotificationNow(state) { s ->
                        Log.d(TAG, "[polling#$iteration] posting: title='Motrix' text='${s.text}' " +
                            "progress=${s.progress}/100 indeterminate=${s.indeterminate} " +
                            "expanded=${s.expandedLines.size} lines")
                    }
                    if (now - lastLogTime > 5000) {
                        Log.i(TAG, "[polling#$iteration] OK — ${tasks?.size ?: 0} tasks, progress=${state.progress}%")
                        lastLogTime = now
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[polling#$iteration] UNCAUGHT EXCEPTION: ${e.javaClass.name}: ${e.message}")
                    Log.e(TAG, "[polling#$iteration] stacktrace:", e)
                }
                delay(1000)
            }
            Log.i(TAG, "[polling] Loop ended (isActive=false)")
        }
    }

    private fun buildNotificationState(tasks: List<DownloadTask>): NotificationState {
        if (tasks.isEmpty()) {
            val engineRunning = downloadManager.isEngineRunning.value
            val engineErr = downloadManager.engineError.value
            Log.d(TAG, "[state] No tasks, engineRunning=$engineRunning error=$engineErr")
            return NotificationState(
                title = "Motrix",
                text = if (engineRunning) "Waiting for downloads..." else "Engine not running",
                progress = 0,
                indeterminate = false,
                activeCount = 0,
                expandedLines = listOf(
                    if (engineRunning) "Engine running, no active downloads"
                    else if (engineErr != null) "Engine error: $engineErr"
                    else "Engine is not running"
                ),
                subText = ""
            )
        }

        val totalLength = tasks.sumOf { it.totalLength.coerceAtLeast(0) }
        val completedLength = tasks.sumOf { it.completedLength.coerceAtLeast(0) }
        val totalSpeed = tasks.sumOf { it.downloadSpeed.coerceAtLeast(0) }
        val totalUpload = tasks.sumOf { it.uploadSpeed.coerceAtLeast(0) }
        val activeCount = tasks.count { it.status == TaskStatus.ACTIVE || it.status == TaskStatus.SEEDING }

        val hasSize = totalLength > 0
        val progress = if (hasSize) (completedLength * 100 / totalLength).toInt().coerceIn(0, 100) else 0

        // Compact text
        val compact = buildString {
            if (hasSize) append("$progress%")
            if (totalSpeed > 0) {
                if (isNotEmpty()) append(" • ")
                append("${FormatUtils.bytesToSize(totalSpeed)}/s ↓")
            }
            if (hasSize && totalSpeed > 0) {
                val eta = (totalLength - completedLength).coerceAtLeast(0) / totalSpeed.coerceAtLeast(1)
                if (eta > 0) {
                    append(" • ")
                    append(FormatUtils.formatEta(eta))
                }
            }
            if (isEmpty()) {
                append("${FormatUtils.bytesToSize(completedLength)} / ${FormatUtils.bytesToSize(totalLength)}")
            }
        }

        // Size sub-text
        val sizeText = if (hasSize) {
            "${FormatUtils.bytesToSize(completedLength)} / ${FormatUtils.bytesToSize(totalLength)}"
        } else {
            "${FormatUtils.bytesToSize(completedLength)} downloaded"
        }

        // Expanded lines (up to 5 tasks) — no duplicate of compact text
        val expandedLines = mutableListOf<String>()
        expandedLines.add("$sizeText • $activeCount active" +
            if (totalUpload > 0) " • ↑ ${FormatUtils.bytesToSize(totalUpload)}/s" else "")
        for (task in tasks.take(5)) {
            val pct = if (task.totalLength > 0)
                (task.completedLength * 100 / task.totalLength).toInt().coerceIn(0, 100) else 0
            val ds = FormatUtils.bytesToSize(task.downloadSpeed)
            val us = FormatUtils.bytesToSize(task.uploadSpeed)
            val sz = if (task.totalLength > 0)
                "${FormatUtils.bytesToSize(task.completedLength)}/${FormatUtils.bytesToSize(task.totalLength)}"
                else FormatUtils.bytesToSize(task.completedLength)
            val eta = if (task.totalLength > 0 && task.downloadSpeed > 0)
                FormatUtils.formatEta((task.totalLength - task.completedLength).coerceAtLeast(0) / task.downloadSpeed)
                else null
            expandedLines.add("── ${task.displayName} — $pct% • $ds/s ↓" +
                if (task.uploadSpeed > 0) " ↑ $us/s" else "" +
                " • $sz" +
                if (eta != null) " • $eta" else "")
        }
        val titleText = tasks.firstOrNull { it.status == TaskStatus.ACTIVE }
            ?.displayName?.ifBlank { null }
            ?: tasks.first().displayName.ifBlank { "Download" }

        val showBar = activeCount > 0
        val isIndeterminate = showBar && totalLength == 0L
        val displayProgress = if (!showBar) 0 else progress

        return NotificationState(
            title = titleText,
            text = "$titleText — $compact",
            progress = displayProgress,
            indeterminate = isIndeterminate,
            activeCount = activeCount,
            expandedLines = expandedLines,
            subText = sizeText,
            showProgress = showBar
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun updateNotificationNow(state: NotificationState, log: ((NotificationState) -> Unit)? = null) {
        val notification = createNotification(state)
        log?.invoke(state)
        mainHandler.post {
            try {
                startForeground(MotrixApp.NOTIFICATION_ID_SERVICE, notification)
                Log.d(TAG, "[notify] startForeground() OK")
            } catch (e: Exception) {
                Log.w(TAG, "[notify] startForeground() threw: ${e.message}")
                try {
                    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    mgr.notify(MotrixApp.NOTIFICATION_ID_SERVICE, notification)
                    Log.d(TAG, "[notify] notify() fallback OK")
                } catch (e2: Exception) {
                    Log.e(TAG, "[notify] notify() ALSO failed: ${e2.message}")
                }
            }
        }
    }

    private fun updateNotificationNow(text: String, expandedLines: List<String>) {
        updateNotificationNow(NotificationState(
            title = "Motrix",
            text = text,
            progress = 0,
            indeterminate = false,
            activeCount = 0,
            expandedLines = expandedLines,
            subText = ""
        ))
    }

    private fun createNotification(state: NotificationState): Notification {
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

        val expandedText = state.expandedLines.joinToString("\n")

        val builder = NotificationCompat.Builder(this, MotrixApp.CHANNEL_DOWNLOAD)
            .setContentTitle(state.title)
            .setContentText(state.text)
            .setSubText(state.subText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(state.activeCount)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
            .addAction(android.R.drawable.ic_media_ff, "Stop", stopIntent)

        if (state.showProgress) {
            builder.setProgress(100, state.progress, state.indeterminate)
        }

        return builder.build()
    }

    private data class NotificationState(
        val title: String,
        val text: String,
        val progress: Int,
        val indeterminate: Boolean,
        val activeCount: Int,
        val expandedLines: List<String>,
        val subText: String,
        val showProgress: Boolean = false
    )

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
        Log.i(TAG, "[onDestroy] cancelling scope + stopping engine")
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
