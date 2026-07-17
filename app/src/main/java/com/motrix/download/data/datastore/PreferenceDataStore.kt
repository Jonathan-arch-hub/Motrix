package com.motrix.download.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.motrix.download.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motrix_settings")

class PreferenceDataStore(private val context: Context) {

    companion object {
        const val LEGACY_PREFS = "motrix_runtime_settings"
        const val LEGACY_LOCALE = "locale"
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_LOCALE = stringPreferencesKey("locale")
        val KEY_DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        val KEY_MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val KEY_MAX_CONNECTION_PER_SERVER = intPreferencesKey("max_connection_per_server")
        val KEY_SPLIT = intPreferencesKey("split")
        val KEY_PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val KEY_PROXY_SERVER = stringPreferencesKey("proxy_server")
        val KEY_AUTO_SYNC_TRACKER = booleanPreferencesKey("auto_sync_tracker")
        val KEY_ENABLE_NOTIFICATIONS = booleanPreferencesKey("enable_notifications")
        val KEY_KEEP_SEEDING = booleanPreferencesKey("keep_seeding")
        val KEY_SEED_RATIO = floatPreferencesKey("seed_ratio")
        val KEY_SEED_TIME = intPreferencesKey("seed_time")
        val KEY_RPC_PORT = intPreferencesKey("rpc_port")
        val KEY_RPC_SECRET = stringPreferencesKey("rpc_secret")
        val KEY_USER_AGENT = stringPreferencesKey("user_agent")
        val KEY_BT_TRACKER = stringPreferencesKey("bt_tracker")
        val KEY_RESUME_ALL_ON_START = booleanPreferencesKey("resume_all_on_start")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "auto" }
    val locale: Flow<String> = context.dataStore.data.map { it[KEY_LOCALE] ?: "en-US" }
    val downloadDir: Flow<String> = context.dataStore.data.map { it[KEY_DOWNLOAD_DIR] ?: "" }
    val maxConcurrentDownloads: Flow<Int> = context.dataStore.data.map { it[KEY_MAX_CONCURRENT_DOWNLOADS] ?: 5 }
    val maxConnectionPerServer: Flow<Int> = context.dataStore.data.map { it[KEY_MAX_CONNECTION_PER_SERVER] ?: 64 }
    val split: Flow<Int> = context.dataStore.data.map { it[KEY_SPLIT] ?: 64 }
    val proxyEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PROXY_ENABLED] ?: false }
    val proxyServer: Flow<String> = context.dataStore.data.map { it[KEY_PROXY_SERVER] ?: "" }
    val autoSyncTracker: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SYNC_TRACKER] ?: true }
    val enableNotifications: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_NOTIFICATIONS] ?: true }
    val keepSeeding: Flow<Boolean> = context.dataStore.data.map { it[KEY_KEEP_SEEDING] ?: false }
    val seedRatio: Flow<Float> = context.dataStore.data.map { it[KEY_SEED_RATIO] ?: 1.0f }
    val seedTime: Flow<Int> = context.dataStore.data.map { it[KEY_SEED_TIME] ?: 60 }
    val rpcPort: Flow<Int> = context.dataStore.data.map { it[KEY_RPC_PORT] ?: 16800 }
    val rpcSecret: Flow<String> = context.dataStore.data.map { it[KEY_RPC_SECRET] ?: "" }
    val userAgent: Flow<String> = context.dataStore.data.map { it[KEY_USER_AGENT] ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36" }
    val btTracker: Flow<String> = context.dataStore.data.map { it[KEY_BT_TRACKER] ?: "" }
    val resumeAllOnStart: Flow<Boolean> = context.dataStore.data.map { it[KEY_RESUME_ALL_ON_START] ?: false }

    suspend fun setTheme(value: String) { context.dataStore.edit { it[KEY_THEME] = value } }
    suspend fun setLocale(value: String) {
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LEGACY_LOCALE, value)
            .apply()
        context.dataStore.edit { it[KEY_LOCALE] = value }
    }
    suspend fun setDownloadDir(value: String) { context.dataStore.edit { it[KEY_DOWNLOAD_DIR] = value } }
    suspend fun setMaxConcurrentDownloads(value: Int) { context.dataStore.edit { it[KEY_MAX_CONCURRENT_DOWNLOADS] = value } }
    suspend fun setMaxConnectionPerServer(value: Int) { context.dataStore.edit { it[KEY_MAX_CONNECTION_PER_SERVER] = value } }
    suspend fun setSplit(value: Int) { context.dataStore.edit { it[KEY_SPLIT] = value } }
    suspend fun setProxyEnabled(value: Boolean) { context.dataStore.edit { it[KEY_PROXY_ENABLED] = value } }
    suspend fun setProxyServer(value: String) { context.dataStore.edit { it[KEY_PROXY_SERVER] = value } }
    suspend fun setAutoSyncTracker(value: Boolean) { context.dataStore.edit { it[KEY_AUTO_SYNC_TRACKER] = value } }
    suspend fun setEnableNotifications(value: Boolean) { context.dataStore.edit { it[KEY_ENABLE_NOTIFICATIONS] = value } }
    suspend fun setKeepSeeding(value: Boolean) { context.dataStore.edit { it[KEY_KEEP_SEEDING] = value } }
    suspend fun setSeedRatio(value: Float) { context.dataStore.edit { it[KEY_SEED_RATIO] = value } }
    suspend fun setSeedTime(value: Int) { context.dataStore.edit { it[KEY_SEED_TIME] = value } }
    suspend fun setRpcPort(value: Int) { context.dataStore.edit { it[KEY_RPC_PORT] = value } }
    suspend fun setRpcSecret(value: String) { context.dataStore.edit { it[KEY_RPC_SECRET] = value } }
    suspend fun setUserAgent(value: String) { context.dataStore.edit { it[KEY_USER_AGENT] = value } }
    suspend fun setBtTracker(value: String) { context.dataStore.edit { it[KEY_BT_TRACKER] = value } }
    suspend fun setResumeAllOnStart(value: Boolean) { context.dataStore.edit { it[KEY_RESUME_ALL_ON_START] = value } }
}
