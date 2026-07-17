package com.motrix.download.engine

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.motrix.download.domain.model.*
import com.motrix.download.util.Constants
import com.motrix.download.util.FormatUtils
import com.motrix.download.util.TrackerUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DownloadManager(private val context: Context) {
    private val engine = Aria2Engine(context)
    private var client: Aria2Client? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _globalStat = MutableStateFlow(GlobalStat())
    val globalStat: StateFlow<GlobalStat> = _globalStat.asStateFlow()

    private val _isEngineRunning = MutableStateFlow(false)
    val isEngineRunning: StateFlow<Boolean> = _isEngineRunning.asStateFlow()

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    private var pollingJob: Job? = null

    suspend fun startEngine(options: Map<String, String> = emptyMap()): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== DownloadManager.startEngine() ===")
        _engineError.value = null

        // Step 1: Start aria2c process
        Log.i(TAG, "[1/3] Starting aria2c process...")
        val started = engine.start(options)
        if (!started) {
            Log.e(TAG, "[1/3] FAILED: aria2c process did not start")
            val detail = engine.engineError
            _engineError.value = detail ?: "Failed to start aria2c binary. Check logcat for details."
            _isEngineRunning.value = false
            return@withContext false
        }
        Log.i(TAG, "[1/3] OK: aria2c process is running")

        // Step 2: Connect RPC client
        Log.i(TAG, "[2/3] Connecting RPC client...")
        client = Aria2Client(Constants.ENGINE_RPC_HOST, Constants.ENGINE_RPC_PORT)
        var retries = 10
        while (retries > 0 && client?.isConnected() != true) {
            Log.i(TAG, "[2/3] RPC connect attempt ${11 - retries}/10...")
            val connected = client?.connect() ?: false
            if (connected) {
                Log.i(TAG, "[2/3] OK: RPC connected")
                break
            }
            retries--
            if (retries > 0) {
                Log.d(TAG, "[2/3] Not connected yet, waiting 1s...")
                Thread.sleep(1000)
            }
        }

        if (client?.isConnected() != true) {
            Log.e(TAG, "[2/3] FAILED: Could not connect to RPC after 10 attempts")
            _engineError.value = "Could not connect to aria2 RPC. Engine may have crashed."
            engine.stop()
            _isEngineRunning.value = false
            return@withContext false
        }

        // Step 3: Verify with getVersion
        Log.i(TAG, "[3/3] Verifying with getVersion()...")
        try {
            val version = client!!.getVersion()
            val ver = version.get("version")?.asString ?: "unknown"
            val features = version.get("enabledFeatures")?.toString() ?: "[]"
            Log.i(TAG, "[3/3] OK: aria2 v$ver, features=$features")
        } catch (e: Exception) {
            Log.e(TAG, "[3/3] FAILED: getVersion() threw: ${e.message}")
            _engineError.value = "RPC connected but getVersion failed: ${e.message}"
            _isEngineRunning.value = false
            return@withContext false
        }

        _isEngineRunning.value = true
        _engineError.value = null
        Log.i(TAG, "=== DownloadManager.startEngine() SUCCESS ===")
        startPolling()
        return@withContext true
    }

    fun stopEngine() {
        Log.i(TAG, "stopEngine()")
        stopPolling()
        client?.disconnect()
        engine.stop()
        _isEngineRunning.value = false
        client = null
    }

    private fun startPolling() {
        stopPolling()
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Polling started")
            while (isActive) {
                try { updateGlobalStat() } catch (e: Exception) {
                    Log.w(TAG, "Polling error: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun updateGlobalStat() {
        val c = client ?: return
        if (!c.isConnected()) return
        try {
            val stat = c.getGlobalStat()
            _globalStat.value = GlobalStat(
                downloadSpeed = stat.get("downloadSpeed")?.asString?.toLongOrNull() ?: 0,
                uploadSpeed = stat.get("uploadSpeed")?.asString?.toLongOrNull() ?: 0,
                numActive = stat.get("numActive")?.asString?.toIntOrNull() ?: 0,
                numWaiting = stat.get("numWaiting")?.asString?.toIntOrNull() ?: 0,
                numStopped = stat.get("numStopped")?.asString?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "updateGlobalStat failed: ${e.message}")
        }
    }

    suspend fun addUri(uris: List<String>, options: Map<String, Any> = emptyMap()): Result<String> = withContext(Dispatchers.IO) {
        Log.i(TAG, "── addUri() DIAGNOSTIC ──")

        // Check engine running state FIRST
        if (!running()) {
            val msg = "Engine is not running. Start the engine before adding downloads."
            Log.e(TAG, "REJECTED: $msg")
            Log.e(TAG, "  isEngineRunning.value = ${_isEngineRunning.value}")
            Log.e(TAG, "  engineError.value     = ${_engineError.value}")
            return@withContext Result.failure(Exception(msg))
        }

        try {
            val c = client
            if (c == null) {
                val msg = "RPC client is null (engine may have crashed)"
                Log.e(TAG, "REJECTED: $msg")
                _engineError.value = msg
                return@withContext Result.failure(Exception(msg))
            }
            Log.i(TAG, "  client.isConnected() = ${c.isConnected()}")
            if (!c.isConnected()) {
                val msg = "RPC client is not connected. Engine may have crashed."
                Log.e(TAG, "REJECTED: $msg")
                _engineError.value = msg
                return@withContext Result.failure(Exception(msg))
            }
            val engineOptions = FormatUtils.formatOptionsForEngine(options)
            Log.i(TAG, "  uris=$uris")
            Log.i(TAG, "  options=$engineOptions")
            val gid = c.addUri(uris, engineOptions)
            Log.i(TAG, "  SUCCESS: gid=$gid")
            Result.success(gid)
        } catch (e: Exception) {
            Log.e(TAG, "addUri FAILED: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun addTorrent(torrentBase64: String, options: Map<String, Any> = emptyMap()): Result<String> = withContext(Dispatchers.IO) {
        if (!running()) {
            val msg = "Engine is not running. Start the engine before adding downloads."
            Log.e(TAG, "addTorrent REJECTED: $msg")
            return@withContext Result.failure(Exception(msg))
        }
        try {
            val c = client
            if (c == null || !c.isConnected()) {
                return@withContext Result.failure(Exception("RPC client not connected"))
            }
            val engineOptions = FormatUtils.formatOptionsForEngine(options)
            val gid = c.addTorrent(torrentBase64, engineOptions)
            Log.i(TAG, "addTorrent success: gid=$gid")
            Result.success(gid)
        } catch (e: Exception) {
            Log.e(TAG, "addTorrent failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun pauseTask(gid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.pause(gid); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun pauseAllTask(): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.pauseAll(); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resumeTask(gid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.unpause(gid); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resumeAllTask(): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.unpauseAll(); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun removeTask(gid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.remove(gid); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun forceRemoveTask(gid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.forceRemove(gid); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteTask(gid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext Result.failure(Exception("RPC client not connected"))
        var removed = false
        var lastError: Exception? = null

        try {
            c.forceRemove(gid)
            removed = true
        } catch (e: Exception) {
            lastError = e
            try {
                c.remove(gid)
                removed = true
            } catch (removeError: Exception) {
                lastError = removeError
            }
        }

        try {
            c.removeDownloadResult(gid)
            removed = true
        } catch (e: Exception) {
            if (!removed) lastError = e
        }

        if (removed) Result.success(Unit) else Result.failure(lastError ?: Exception("Failed to delete task"))
    }

    suspend fun fetchActiveTasks(): Result<List<DownloadTask>> = withContext(Dispatchers.IO) {
        try { Result.success((client?.tellActive() ?: emptyList()).map { parseTask(it) }) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchWaitingTasks(): Result<List<DownloadTask>> = withContext(Dispatchers.IO) {
        try { Result.success((client?.tellWaiting() ?: emptyList()).map { parseTask(it) }) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchStoppedTasks(): Result<List<DownloadTask>> = withContext(Dispatchers.IO) {
        try { Result.success((client?.tellStopped(num = 100) ?: emptyList()).map { parseTask(it) }) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchDownloadingTasks(): Result<List<DownloadTask>> = withContext(Dispatchers.IO) {
        try {
            val c = client
            if (c == null) {
                Log.w(TAG, "fetchDownloadingTasks: client is null")
                return@withContext Result.failure(Exception("RPC client is null"))
            }
            if (!c.isConnected()) {
                Log.w(TAG, "fetchDownloadingTasks: client not connected, attempting reconnect...")
                val reconnected = c.connect()
                Log.i(TAG, "fetchDownloadingTasks: reconnect result = $reconnected")
                if (!reconnected) {
                    return@withContext Result.failure(Exception("RPC not connected, reconnect failed"))
                }
            }
            val active = c.tellActive() ?: emptyList()
            val waiting = c.tellWaiting() ?: emptyList()
            val all = active + waiting
            Log.d(TAG, "fetchDownloadingTasks: active=${active.size} waiting=${waiting.size} total=${all.size}")
            Result.success(all.map { parseTask(it) })
        } catch (e: Exception) {
            Log.w(TAG, "fetchDownloadingTasks FAILED: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchTaskDetail(gid: String): Result<DownloadTask> = withContext(Dispatchers.IO) {
        try { Result.success(parseTask(client?.tellStatus(gid) ?: throw Exception("Not connected"))) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchTaskPeers(gid: String): Result<List<PeerInfo>> = withContext(Dispatchers.IO) {
        try { Result.success((client?.getPeers(gid) ?: emptyList()).map { parsePeer(it) }) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun changeGlobalOption(options: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.changeGlobalOption(options); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun saveSession(): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.saveSession(); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun purgeCompleted(): Result<Unit> = withContext(Dispatchers.IO) {
        try { client?.purgeDownloadResult(); Result.success(Unit) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchBtTrackers(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trackers = TrackerUtils.fetchBtTrackers(
                listOf(TrackerUtils.NGOSANG_TRACKERS_BEST_URL, TrackerUtils.XIU2_TRACKERS_BEST_URL),
                okHttpClient
            )
            Result.success(TrackerUtils.reduceTrackerString(TrackerUtils.convertTrackerDataToComma(trackers)))
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun parseTask(json: JsonObject): DownloadTask {
        val status = json.get("status")?.asString ?: "waiting"
        val bt = json.getAsJsonObject("bittorrent")
        val btInfo = if (bt != null) {
            BitTorrentInfo(
                info = bt.getAsJsonObject("info")?.let { BTInfo(name = it.get("name")?.asString ?: "") },
                announceList = bt.getAsJsonArray("announceList")?.map { it.asJsonArray.map { a -> a.asString } }?.flatten() ?: emptyList(),
                mode = bt.get("mode")?.asString ?: ""
            )
        } else null

        val filesArray = json.getAsJsonArray("files")
        val filesList = (0 until (filesArray?.size() ?: 0)).map { i ->
            val f = filesArray!!.get(i).asJsonObject
            val urisArray = f.getAsJsonArray("uris")
            val uris = (0 until (urisArray?.size() ?: 0)).map { ui ->
                val u = urisArray!!.get(ui).asJsonObject
                TaskUri(
                    uri = u.get("uri")?.asString ?: "",
                    status = u.get("status")?.asString ?: ""
                )
            }
            TaskFile(
                index = i,
                path = f.get("path")?.asString ?: "",
                length = f.get("length")?.asString?.toLongOrNull() ?: 0,
                completedLength = f.get("completedLength")?.asString?.toLongOrNull() ?: 0,
                selected = f.get("selected")?.asString == "true",
                uris = uris
            )
        }

        val name = extractName(json, btInfo)

        return DownloadTask(
            gid = json.get("gid")?.asString ?: "",
            status = TaskStatus.fromString(status),
            totalLength = json.get("totalLength")?.asString?.toLongOrNull() ?: 0,
            completedLength = json.get("completedLength")?.asString?.toLongOrNull() ?: 0,
            uploadSpeed = json.get("uploadSpeed")?.asString?.toLongOrNull() ?: 0,
            downloadSpeed = json.get("downloadSpeed")?.asString?.toLongOrNull() ?: 0,
            uploadLength = json.get("uploadLength")?.asString?.toLongOrNull() ?: 0,
            connections = json.get("connections")?.asString?.toIntOrNull() ?: 0,
            errorCode = json.get("errorCode")?.asString ?: "",
            errorMessage = json.get("errorMessage")?.asString ?: "",
            dir = json.get("dir")?.asString ?: "",
            files = filesList,
            name = name,
            bittorrent = btInfo,
            infoHash = json.get("infoHash")?.asString ?: "",
            seeder = json.get("seeder")?.asString ?: "false"
        )
    }

    private fun extractName(json: JsonObject, btInfo: BitTorrentInfo?): String {
        btInfo?.info?.name?.let { if (it.isNotEmpty()) return it }
        val files = json.getAsJsonArray("files")
        if (files != null && files.size() == 1) {
            val path = files[0].asJsonObject.get("path")?.asString ?: ""
            val idx = path.lastIndexOf('/')
            return if (idx >= 0) path.substring(idx + 1) else path
        }
        return ""
    }

    private fun parsePeer(json: JsonObject): PeerInfo = PeerInfo(
        peerId = json.get("peerId")?.asString ?: "",
        ip = json.get("ip")?.asString ?: "",
        port = json.get("port")?.asString ?: "",
        clientName = json.get("clientName")?.asString ?: ""
    )

    companion object { private const val TAG = "DownloadManager" }

    private fun running(): Boolean = _isEngineRunning.value && engine.isRunning()
}
