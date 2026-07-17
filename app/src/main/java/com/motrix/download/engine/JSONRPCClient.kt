package com.motrix.download.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

data class JSONRPCMessage(
    val method: String,
    @com.google.gson.annotations.SerializedName("json-rpc") val jsonRpc: String = "2.0",
    val id: Int,
    val params: Any? = null
)

class JSONRPCError(
    message: String,
    val rpcCode: Int = 0,
    val rpcData: Any? = null
) : Exception(message)

class JSONRPCClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 16800,
    private val secret: String = "",
    private val path: String = "/jsonrpc"
) {
    private val gson = Gson()
    private val lastId = AtomicInteger(0)
    private val listeners = CopyOnWriteArrayList<(String, Any?) -> Unit>()
    @Volatile
    private var connected = false
    private val JSON_MEDIA = "application/json".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun nextId(): Int = lastId.getAndIncrement()
    private fun httpUrl(): String = "http://$host:$port$path"

    fun onNotification(listener: (String, Any?) -> Unit) {
        listeners.add(listener)
    }

    fun connect(): Boolean {
        Log.i(TAG, "Testing HTTP RPC at ${httpUrl()}")
        try {
            // Verify RPC is reachable with a simple getVersion call
            val message = JSONRPCMessage(method = "aria2.getVersion", id = nextId(), params = addSecret(emptyList()))
            val json = gson.toJson(message)
            val body = json.toRequestBody(JSON_MEDIA)
            val request = Request.Builder().url(httpUrl()).post(body).build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Log.i(TAG, "HTTP RPC test: code=${response.code} body=${responseBody.take(200)}")

            if (response.isSuccessful) {
                connected = true
                Log.i(TAG, "HTTP RPC connected successfully")
                return true
            } else {
                Log.e(TAG, "HTTP RPC test failed: ${response.code}")
                connected = false
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP RPC connect failed: ${e.message}")
            connected = false
            return false
        }
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting")
        connected = false
    }

    fun isConnected(): Boolean = connected

    private fun httpCall(json: String): String {
        val body = json.toRequestBody(JSON_MEDIA)
        val request = Request.Builder().url(httpUrl()).post(body).build()
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw JSONRPCError("HTTP ${response.code}: $responseBody", rpcCode = response.code)
        }
        return responseBody
    }

    fun call(method: String, vararg params: Any): CompletableDeferred<Any?> {
        val deferred = CompletableDeferred<Any?>()
        val id = nextId()
        val fullParams = addSecret(params.toList())
        val message = JSONRPCMessage(method = prefix(method), id = id, params = fullParams)
        val json = gson.toJson(message)
        Log.d(TAG, "RPC call: ${prefix(method)} id=$id")

        Thread {
            try {
                val responseText = httpCall(json)
                Log.d(TAG, "RPC response id=$id: ${responseText.take(300)}")
                val element = JsonParser.parseString(responseText)
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    if (obj.has("error") && !obj.get("error").isJsonNull) {
                        val errObj = obj.getAsJsonObject("error")
                        val errMsg = errObj.get("message")?.asString ?: "Unknown error"
                        val errCode = errObj.get("code")?.asInt ?: 0
                        Log.e(TAG, "RPC error id=$id code=$errCode msg=$errMsg")
                        deferred.completeExceptionally(
                            JSONRPCError(message = errMsg, rpcCode = errCode)
                        )
                    } else {
                        val result = obj.get("result")
                        deferred.complete(result)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RPC call failed: ${e.message}")
                if (e !is JSONRPCError) connected = false
                deferred.completeExceptionally(e)
            }
        }.start()

        return deferred
    }

    fun multicall(calls: List<List<Any>>): CompletableDeferred<Any?> {
        val deferred = CompletableDeferred<Any?>()
        val id = nextId()
        val multiParams = calls.map { call ->
            val method = call[0] as String
            val params = if (call.size > 1) call.subList(1, call.size) else emptyList()
            mapOf("methodName" to prefix(method), "params" to addSecret(params))
        }
        val message = JSONRPCMessage(method = "system.multicall", id = id, params = listOf(multiParams))
        val json = gson.toJson(message)
        Log.d(TAG, "RPC multicall id=$id (${calls.size} calls)")

        Thread {
            try {
                val responseText = httpCall(json)
                val element = JsonParser.parseString(responseText)
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    if (obj.has("error") && !obj.get("error").isJsonNull) {
                        val errObj = obj.getAsJsonObject("error")
                        val errMsg = errObj.get("message")?.asString ?: "Unknown error"
                        val errCode = errObj.get("code")?.asInt ?: 0
                        deferred.completeExceptionally(
                            JSONRPCError(message = errMsg, rpcCode = errCode)
                        )
                    } else {
                        deferred.complete(obj.get("result"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RPC multicall failed: ${e.message}")
                deferred.completeExceptionally(e)
            }
        }.start()

        return deferred
    }

    private fun prefix(str: String): String {
        if (!str.startsWith("system.") && !str.startsWith("aria2.")) return "aria2.$str"
        return str
    }

    private fun addSecret(params: List<Any>): List<Any> {
        val result = mutableListOf<Any>()
        if (secret.isNotEmpty()) result.add("token:$secret")
        result.addAll(params)
        return result
    }

    companion object {
        private const val TAG = "JSONRPCClient"
    }
}
