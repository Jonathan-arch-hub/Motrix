package com.motrix.download.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.motrix.download.domain.model.DownloadTask
import com.motrix.download.domain.model.PeerInfo
import com.motrix.download.engine.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    private val gid: String = savedStateHandle["gid"] ?: ""

    private val _task = MutableStateFlow<DownloadTask?>(null)
    val task: StateFlow<DownloadTask?> = _task.asStateFlow()

    private val _peers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val peers: StateFlow<List<PeerInfo>> = _peers.asStateFlow()

    private var refreshJob: Job? = null

    init {
        startAutoRefresh()
    }

    fun pauseTask() {
        viewModelScope.launch { downloadManager.pauseTask(gid) }
    }

    fun resumeTask() {
        viewModelScope.launch { downloadManager.resumeTask(gid) }
    }

    fun removeTask() {
        viewModelScope.launch { downloadManager.forceRemoveTask(gid) }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                refreshTask()
                refreshPeers()
                delay(2000)
            }
        }
    }

    private suspend fun refreshTask() {
        if (gid.isEmpty()) return
        val result = downloadManager.fetchTaskDetail(gid)
        result.onSuccess { _task.value = it }
    }

    private suspend fun refreshPeers() {
        if (gid.isEmpty()) return
        val result = downloadManager.fetchTaskPeers(gid)
        result.onSuccess { _peers.value = it }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
