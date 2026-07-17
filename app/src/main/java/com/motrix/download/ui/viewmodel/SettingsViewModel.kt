package com.motrix.download.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motrix.download.data.datastore.PreferenceDataStore
import com.motrix.download.engine.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val prefs: PreferenceDataStore,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    val theme = prefs.theme
    val locale = prefs.locale
    val maxConcurrentDownloads = prefs.maxConcurrentDownloads
    val maxConnectionPerServer = prefs.maxConnectionPerServer
    val split = prefs.split
    val seedRatio = prefs.seedRatio
    val seedTime = prefs.seedTime
    val proxyEnabled = prefs.proxyEnabled
    val proxyServer = prefs.proxyServer
    val autoSyncTracker = prefs.autoSyncTracker
    val enableNotifications = prefs.enableNotifications
    val keepSeeding = prefs.keepSeeding
    val resumeAllOnStart = prefs.resumeAllOnStart
    val userAgent = prefs.userAgent

    fun setTheme(value: String) { viewModelScope.launch { prefs.setTheme(value) } }
    fun setLocale(value: String) { viewModelScope.launch { prefs.setLocale(value) } }
    fun setMaxConcurrentDownloads(value: Int) { viewModelScope.launch { prefs.setMaxConcurrentDownloads(value) } }
    fun setMaxConnectionPerServer(value: Int) { viewModelScope.launch { prefs.setMaxConnectionPerServer(value) } }
    fun setSplit(value: Int) { viewModelScope.launch { prefs.setSplit(value) } }
    fun setSeedRatio(value: Float) { viewModelScope.launch { prefs.setSeedRatio(value) } }
    fun setSeedTime(value: Int) { viewModelScope.launch { prefs.setSeedTime(value) } }
    fun setProxyEnabled(value: Boolean) { viewModelScope.launch { prefs.setProxyEnabled(value) } }
    fun setProxyServer(value: String) { viewModelScope.launch { prefs.setProxyServer(value) } }
    fun setAutoSyncTracker(value: Boolean) { viewModelScope.launch { prefs.setAutoSyncTracker(value) } }
    fun setEnableNotifications(value: Boolean) { viewModelScope.launch { prefs.setEnableNotifications(value) } }
    fun setKeepSeeding(value: Boolean) { viewModelScope.launch { prefs.setKeepSeeding(value) } }
    fun setResumeAllOnStart(value: Boolean) { viewModelScope.launch { prefs.setResumeAllOnStart(value) } }
    fun setUserAgent(value: String) { viewModelScope.launch { prefs.setUserAgent(value) } }
}
