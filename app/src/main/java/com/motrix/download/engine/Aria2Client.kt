package com.motrix.download.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Aria2Client(
    host: String = "127.0.0.1",
    port: Int = 16800,
    secret: String = ""
) {
    private val client = JSONRPCClient(host, port, secret)
    private val gson = Gson()

    fun connect(): Boolean {
        Log.i(TAG, "Aria2Client.connect() called")
        return client.connect()
    }
    fun disconnect() {
        Log.i(TAG, "Aria2Client.disconnect() called")
        client.disconnect()
    }
    fun isConnected(): Boolean = client.isConnected()
    fun onNotification(listener: (String, Any?) -> Unit) { client.onNotification(listener) }

    private fun toJson(result: Any?): String = gson.toJson(result)
    private fun toJsonObject(result: Any?) = JsonParser.parseString(toJson(result)).asJsonObject
    private fun toJsonArray(result: Any?) = JsonParser.parseString(toJson(result)).asJsonArray

    suspend fun getVersion(): com.google.gson.JsonObject = withContext(Dispatchers.IO) {
        Log.d(TAG, "getVersion()")
        toJsonObject(client.call("getVersion").await())
    }

    suspend fun getGlobalStat(): com.google.gson.JsonObject = withContext(Dispatchers.IO) {
        toJsonObject(client.call("getGlobalStat").await())
    }

    suspend fun getGlobalOption(): com.google.gson.JsonObject = withContext(Dispatchers.IO) {
        toJsonObject(client.call("getGlobalOption").await())
    }

    suspend fun changeGlobalOption(options: Map<String, String>) = withContext(Dispatchers.IO) {
        client.call("changeGlobalOption", options).await()
    }

    suspend fun addUri(uris: List<String>, options: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "addUri: $uris options=$options")
        val result = client.call("addUri", uris, options).await()
        val gid = toJson(result).replace("\"", "")
        Log.i(TAG, "addUri result gid=$gid")
        gid
    }

    suspend fun addTorrent(torrentBase64: String, options: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "addTorrent")
        val result = client.call("addTorrent", torrentBase64, emptyList<String>(), options).await()
        val gid = toJson(result).replace("\"", "")
        Log.i(TAG, "addTorrent result gid=$gid")
        gid
    }

    suspend fun pause(gid: String): String = withContext(Dispatchers.IO) {
        toJson(client.call("pause", gid).await()).replace("\"", "")
    }

    suspend fun pauseAll(): String = withContext(Dispatchers.IO) {
        toJson(client.call("pauseAll").await()).replace("\"", "")
    }

    suspend fun forcePause(gid: String): String = withContext(Dispatchers.IO) {
        toJson(client.call("forcePause", gid).await()).replace("\"", "")
    }

    suspend fun forcePauseAll(): String = withContext(Dispatchers.IO) {
        toJson(client.call("forcePauseAll").await()).replace("\"", "")
    }

    suspend fun unpause(gid: String): String = withContext(Dispatchers.IO) {
        toJson(client.call("unpause", gid).await()).replace("\"", "")
    }

    suspend fun unpauseAll(): String = withContext(Dispatchers.IO) {
        toJson(client.call("unpauseAll").await()).replace("\"", "")
    }

    suspend fun remove(gid: String): String = withContext(Dispatchers.IO) {
        toJson(client.call("remove", gid).await()).replace("\"", "")
    }

    suspend fun forceRemove(gid: String): String = withContext(Dispatchers.IO) {
        toJson(client.call("forceRemove", gid).await()).replace("\"", "")
    }

    suspend fun tellStatus(gid: String, keys: List<String> = emptyList()): com.google.gson.JsonObject = withContext(Dispatchers.IO) {
        val params = mutableListOf<Any>(gid)
        if (keys.isNotEmpty()) params.add(keys)
        toJsonObject(client.call("tellStatus", *params.toTypedArray()).await())
    }

    suspend fun tellActive(keys: List<String> = emptyList()): List<com.google.gson.JsonObject> = withContext(Dispatchers.IO) {
        try {
            val params = mutableListOf<Any>()
            if (keys.isNotEmpty()) params.add(keys)
            val result = client.call("tellActive", *params.toTypedArray()).await()
            val parsed = parseTaskList(result)
            Log.d(TAG, "tellActive: returned ${parsed.size} tasks")
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "tellActive FAILED: ${e.message}")
            throw e
        }
    }

    suspend fun tellWaiting(offset: Int = 0, num: Int = 20, keys: List<String> = emptyList()): List<com.google.gson.JsonObject> = withContext(Dispatchers.IO) {
        try {
            val params = mutableListOf<Any>(offset, num)
            if (keys.isNotEmpty()) params.add(keys)
            val result = client.call("tellWaiting", *params.toTypedArray()).await()
            val parsed = parseTaskList(result)
            Log.d(TAG, "tellWaiting(offset=$offset,num=$num): returned ${parsed.size} tasks")
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "tellWaiting FAILED: ${e.message}")
            throw e
        }
    }

    suspend fun tellStopped(offset: Int = 0, num: Int = 20, keys: List<String> = emptyList()): List<com.google.gson.JsonObject> = withContext(Dispatchers.IO) {
        val params = mutableListOf<Any>(offset, num)
        if (keys.isNotEmpty()) params.add(keys)
        val result = client.call("tellStopped", *params.toTypedArray()).await()
        parseTaskList(result)
    }

    suspend fun getPeers(gid: String): List<com.google.gson.JsonObject> = withContext(Dispatchers.IO) {
        val result = client.call("getPeers", gid).await()
        parseTaskList(result)
    }

    suspend fun changeOption(gid: String, options: Map<String, String>) = withContext(Dispatchers.IO) {
        client.call("changeOption", gid, options).await()
    }

    suspend fun saveSession() = withContext(Dispatchers.IO) { client.call("saveSession").await() }
    suspend fun purgeDownloadResult() = withContext(Dispatchers.IO) { client.call("purgeDownloadResult").await() }
    suspend fun removeDownloadResult(gid: String) = withContext(Dispatchers.IO) { client.call("removeDownloadResult", gid).await() }

    private fun parseTaskList(result: Any?): List<com.google.gson.JsonObject> {
        val arr = toJsonArray(result)
        return arr.map { it.asJsonObject }
    }

    companion object {
        private const val TAG = "Aria2Client"
    }
}
