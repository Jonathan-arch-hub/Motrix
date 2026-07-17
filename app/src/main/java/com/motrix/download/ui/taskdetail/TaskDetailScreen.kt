package com.motrix.download.ui.taskdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motrix.download.domain.model.DownloadTask
import com.motrix.download.domain.model.PeerInfo
import com.motrix.download.domain.model.TaskStatus
import com.motrix.download.ui.viewmodel.TaskDetailViewModel
import com.motrix.download.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val task by viewModel.task.collectAsState()
    val peers by viewModel.peers.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.displayName ?: "Task Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    task?.let { t ->
                        when (t.status) {
                            TaskStatus.ACTIVE, TaskStatus.WAITING -> {
                                IconButton(onClick = { viewModel.pauseTask() }) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                                }
                            }
                            TaskStatus.PAUSED -> {
                                IconButton(onClick = { viewModel.resumeTask() }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                                }
                            }
                            else -> {}
                        }
                        IconButton(onClick = { viewModel.removeTask() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("General") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Files") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Peers") })
            }

            when (selectedTab) {
                0 -> GeneralTab(task)
                1 -> FilesTab(task)
                2 -> PeersTab(peers)
            }
        }
    }
}

@Composable
fun GeneralTab(task: DownloadTask?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        task?.let { t ->
            InfoRow("Status", t.status.name)
            InfoRow("GID", t.gid)
            InfoRow("Name", t.displayName)
            InfoRow("Directory", t.dir)
            InfoRow("Total Size", FormatUtils.bytesToSize(t.totalLength))
            InfoRow("Completed", FormatUtils.bytesToSize(t.completedLength))
            InfoRow("Download Speed", "${FormatUtils.bytesToSize(t.downloadSpeed)}/s")
            InfoRow("Upload Speed", "${FormatUtils.bytesToSize(t.uploadSpeed)}/s")
            InfoRow("Connections", t.connections.toString())

            if (t.totalLength > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = t.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (t.errorCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Error", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            t.errorMessage.ifEmpty { "Error code: ${t.errorCode}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilesTab(task: DownloadTask?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        task?.files?.forEach { file ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = file.path.substringAfterLast('/').ifEmpty { file.path },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${FormatUtils.bytesToSize(file.completedLength)} / ${FormatUtils.bytesToSize(file.length)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun PeersTab(peers: List<PeerInfo>) {
    if (peers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("No peers connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            peers.forEach { peer ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = peer.clientName.ifEmpty { "Unknown Client" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${peer.ip}:${peer.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
