package com.motrix.download.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motrix.download.domain.model.DownloadTask
import com.motrix.download.domain.model.GlobalStat
import com.motrix.download.domain.model.TaskListTab
import com.motrix.download.engine.DownloadManager
import com.motrix.download.service.DownloadForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    application: Application,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    private val _selectedTab = MutableStateFlow(TaskListTab.ACTIVE)
    val selectedTab: StateFlow<TaskListTab> = _selectedTab.asStateFlow()

    private val _activeTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeTasks: StateFlow<List<DownloadTask>> = _activeTasks.asStateFlow()

    private val _waitingTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val waitingTasks: StateFlow<List<DownloadTask>> = _waitingTasks.asStateFlow()

    private val _stoppedTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val stoppedTasks: StateFlow<List<DownloadTask>> = _stoppedTasks.asStateFlow()

    private val _globalStat = MutableStateFlow(GlobalStat())
    val globalStat: StateFlow<GlobalStat> = _globalStat.asStateFlow()

    private val _isEngineRunning = MutableStateFlow(false)
    val isEngineRunning: StateFlow<Boolean> = _isEngineRunning.asStateFlow()

    private val _engineError = MutableStateFlow<String?>(null)
    val engineError: StateFlow<String?> = _engineError.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            downloadManager.globalStat.collect { _globalStat.value = it }
        }
        viewModelScope.launch {
            downloadManager.isEngineRunning.collect { _isEngineRunning.value = it }
        }
        viewModelScope.launch {
            downloadManager.engineError.collect { _engineError.value = it }
        }
        startAutoRefresh()
    }

    fun selectTab(tab: TaskListTab) {
        _selectedTab.value = tab
        refresh()
    }

    fun startEngine() {
        viewModelScope.launch {
            downloadManager.startEngine()
        }
    }

    fun autoStartEngine() {
        if (!_isEngineRunning.value) {
            DownloadForegroundService.startEngine(getApplication())
        }
    }

    fun pauseTask(gid: String) {
        viewModelScope.launch {
            downloadManager.pauseTask(gid)
            refreshAll()
        }
    }

    fun resumeTask(gid: String) {
        viewModelScope.launch {
            downloadManager.resumeTask(gid)
            refreshAll()
        }
    }

    fun removeTask(gid: String) {
        viewModelScope.launch {
            downloadManager.deleteTask(gid)
            refreshAll()
        }
    }

    fun removeTaskWithFile(task: DownloadTask) {
        viewModelScope.launch {
            downloadManager.deleteTask(task.gid)
            // Delete files from storage
            for (file in task.files) {
                try {
                    val f = java.io.File(file.path)
                    if (f.exists()) {
                        f.delete()
                    }
                } catch (_: Exception) { }
            }
            // Also try dir-based deletion for parent folder if configured
            if (task.dir.isNotEmpty()) {
                try {
                    val dir = java.io.File(task.dir)
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { f ->
                            if (f.name.startsWith(task.gid) || f.name.contains(task.displayName)) {
                                f.delete()
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
            refreshAll()
        }
    }

    fun pauseAll() {
        viewModelScope.launch {
            downloadManager.pauseAllTask()
            refreshAll()
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            downloadManager.resumeAllTask()
            refreshAll()
        }
    }

    fun purgeCompleted() {
        viewModelScope.launch {
            downloadManager.purgeCompleted()
            refreshAll()
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                refresh()
                delay(2000)
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            refreshAll()
        }
    }

    private suspend fun refreshAll() {
        downloadManager.fetchActiveTasks().onSuccess { _activeTasks.value = it }
        downloadManager.fetchWaitingTasks().onSuccess { _waitingTasks.value = it }
        downloadManager.fetchStoppedTasks().onSuccess { _stoppedTasks.value = it }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
