package com.motrix.download.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motrix.download.engine.DownloadManager
import com.motrix.download.service.DownloadForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTaskViewModel @Inject constructor(
    application: Application,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    private val _isAdding = MutableStateFlow(false)
    val isAdding: StateFlow<Boolean> = _isAdding.asStateFlow()

    private val _taskAdded = MutableSharedFlow<Boolean>()
    val taskAdded: SharedFlow<Boolean> = _taskAdded.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun addUri(urls: String, out: String = "", options: Map<String, Any> = emptyMap()) {
        val links = urls.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (!it.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) "http://$it" else it }
        if (links.isEmpty()) return

        viewModelScope.launch {
            _isAdding.value = true
            _errorMessage.value = null

            // Check if engine is running first
            if (!downloadManager.isEngineRunning.value) {
                Log.i(TAG, "Engine not running, attempting to start...")
                val started = downloadManager.startEngine()
                if (!started) {
                    val error = downloadManager.engineError.value
                        ?: "Failed to start engine. Check if aria2c binary is compatible with your device."
                    Log.e(TAG, "Engine start failed: $error")
                    _errorMessage.value = error
                    _isAdding.value = false
                    return@launch
                }
                Log.i(TAG, "Engine started successfully")
            }

            val taskOptions = options.toMutableMap()
            if (out.isNotEmpty()) taskOptions["out"] = out

            DownloadForegroundService.addUri(
                context,
                links.joinToString("\n"),
                taskOptions
            )
            _isAdding.value = false
            _taskAdded.emit(true)
        }
    }

    fun addTorrent(torrentPath: String, options: Map<String, Any> = emptyMap()) {
        if (torrentPath.isEmpty()) return

        viewModelScope.launch {
            _isAdding.value = true
            _errorMessage.value = null

            if (!downloadManager.isEngineRunning.value) {
                val started = downloadManager.startEngine()
                if (!started) {
                    val error = downloadManager.engineError.value
                        ?: "Failed to start engine."
                    _errorMessage.value = error
                    _isAdding.value = false
                    return@launch
                }
            }

            DownloadForegroundService.addTorrent(context, torrentPath, options)
            _isAdding.value = false
            _taskAdded.emit(true)
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "AddTaskViewModel"
    }
}
