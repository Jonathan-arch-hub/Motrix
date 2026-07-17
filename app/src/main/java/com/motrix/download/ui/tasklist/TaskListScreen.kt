package com.motrix.download.ui.tasklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motrix.download.R
import com.motrix.download.domain.model.DownloadTask
import com.motrix.download.domain.model.TaskListTab
import com.motrix.download.domain.model.TaskStatus
import com.motrix.download.ui.theme.*
import com.motrix.download.util.FormatUtils
import com.motrix.download.ui.viewmodel.TaskListViewModel

@Composable
fun TaskListScreen(
    onAddTask: () -> Unit,
    onTaskClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val activeTasks by viewModel.activeTasks.collectAsState()
    val waitingTasks by viewModel.waitingTasks.collectAsState()
    val stoppedTasks by viewModel.stoppedTasks.collectAsState()
    val globalStat by viewModel.globalStat.collectAsState()
    val isEngineRunning by viewModel.isEngineRunning.collectAsState()
    val colors = MaterialTheme.colorScheme

    val currentTasks = when (selectedTab) {
        TaskListTab.ACTIVE -> activeTasks
        TaskListTab.WAITING -> waitingTasks
        TaskListTab.STOPPED -> stoppedTasks
    }

    val activeCount = activeTasks.size
    val waitingCount = waitingTasks.size
    val stoppedCount = stoppedTasks.size

    var deleteTask by remember { mutableStateOf<DownloadTask?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubnavBar(
                selectedTab = selectedTab,
                activeCount = activeCount,
                waitingCount = waitingCount,
                stoppedCount = stoppedCount,
                onTabSelected = { viewModel.selectTab(it) }
            )

            PanelHeader(
                selectedTab = selectedTab,
                stoppedCount = stoppedCount,
                onAddTask = onAddTask,
                onPurge = { viewModel.purgeCompleted() },
                onResumeAll = { viewModel.resumeAll() },
                onPauseAll = { viewModel.pauseAll() }
            )

            if (currentTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (selectedTab) {
                                TaskListTab.ACTIVE -> Icons.Default.CloudDownload
                                TaskListTab.WAITING -> Icons.Default.HourglassEmpty
                                TaskListTab.STOPPED -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = colors.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (selectedTab) {
                                TaskListTab.ACTIVE -> stringResource(R.string.no_active_downloads)
                                TaskListTab.WAITING -> stringResource(R.string.no_waiting_downloads)
                                TaskListTab.STOPPED -> stringResource(R.string.no_completed_downloads)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentTasks, key = { it.gid }) { task ->
                        MotrixTaskCard(
                            task = task,
                            onClick = { onTaskClick(task.gid) },
                            onPause = { viewModel.pauseTask(task.gid) },
                            onResume = { viewModel.resumeTask(task.gid) },
                            onDelete = { deleteTask = task }
                        )
                    }
                }
            }
        }

        Speedometer(
            downloadSpeed = globalStat.downloadSpeed,
            uploadSpeed = globalStat.uploadSpeed,
            isEngineRunning = isEngineRunning,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
        )
    }

    deleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTask = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_message, task.displayName))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.delete_choose),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        viewModel.removeTask(task.gid)
                        deleteTask = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_only_task))
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.removeTaskWithFile(task)
                        deleteTask = null
                    }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete_with_file))
                    }
                    TextButton(onClick = { deleteTask = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun SubnavBar(
    selectedTab: TaskListTab,
    activeCount: Int,
    waitingCount: Int,
    stoppedCount: Int,
    onTabSelected: (TaskListTab) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(tonalElevation = 0.dp, shadowElevation = 0.dp) {
        val activeName = stringResource(R.string.tab_active)
        val waitingName = stringResource(R.string.tab_waiting)
        val stoppedName = stringResource(R.string.tab_stopped)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple(TaskListTab.ACTIVE, Icons.Default.PlayArrow, activeCount),
                Triple(TaskListTab.WAITING, Icons.Default.Pause, waitingCount),
                Triple(TaskListTab.STOPPED, Icons.Default.Stop, stoppedCount)
            ).forEach { (tab, icon, count) ->
                val isSelected = selectedTab == tab
                val tabName = when (tab) {
                    TaskListTab.ACTIVE -> activeName
                    TaskListTab.WAITING -> waitingName
                    TaskListTab.STOPPED -> stoppedName
                }
                val bgColor by animateColorAsState(
                    if (isSelected) colors.primaryContainer else Color.Transparent,
                    label = "bg"
                )
                val textColor by animateColorAsState(
                    if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    label = "text"
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$tabName ($count)",
                        fontSize = 13.sp,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    selectedTab: TaskListTab,
    stoppedCount: Int,
    onAddTask: () -> Unit,
    onPurge: () -> Unit,
    onResumeAll: () -> Unit,
    onPauseAll: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val title = when (selectedTab) {
        TaskListTab.ACTIVE -> stringResource(R.string.tab_active)
        TaskListTab.WAITING -> stringResource(R.string.tab_waiting)
        TaskListTab.STOPPED -> stringResource(R.string.tab_stopped)
    }

    Surface(tonalElevation = 0.dp, shadowElevation = 0.dp, color = colors.surface) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (selectedTab == TaskListTab.STOPPED && stoppedCount > 0) {
                        ActionCircleButton(icon = Icons.Default.DeleteSweep, onClick = onPurge)
                    }
                    if (selectedTab != TaskListTab.STOPPED) {
                        ActionCircleButton(icon = Icons.Default.PlayArrow, onClick = onResumeAll)
                        ActionCircleButton(icon = Icons.Default.Pause, onClick = onPauseAll)
                    }
                    ActionCircleButton(icon = Icons.Default.Add, onClick = onAddTask)
                }
            }
            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = colors.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ActionCircleButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MotrixTaskCard(
    task: DownloadTask,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val statusColor = when (task.status) {
        TaskStatus.ACTIVE -> StatusActive
        TaskStatus.WAITING -> StatusWaiting
        TaskStatus.PAUSED -> StatusPaused
        TaskStatus.ERROR -> StatusError
        TaskStatus.COMPLETE -> StatusComplete
        TaskStatus.SEEDING -> StatusSeeding
        TaskStatus.REMOVED -> StatusRemoved
    }

    val animatedProgress by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = tween(durationMillis = 500),
        label = "progress"
    )

    val percentage = (task.progress * 100).toInt()
    val isCompleted = task.status == TaskStatus.COMPLETE || task.progress >= 1f
    val isActive = task.status == TaskStatus.ACTIVE
    val etaText = if (isActive && task.downloadSpeed > 0) {
        val remaining = task.totalLength - task.completedLength
        val etaSecs = if (remaining > 0 && task.downloadSpeed > 0) remaining / task.downloadSpeed else 0
        FormatUtils.formatEta(etaSecs)
    } else ""

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (isActive) 1.dp else 0.5.dp,
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isActive) statusColor.copy(alpha = 0.3f) else colors.outlineVariant
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
        ) {
            // Row 1: Status icon + Task name + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status dot/icon
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = task.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (task.status) {
                        TaskStatus.ACTIVE -> {
                            ActionPill(icon = Icons.Default.Pause, tint = colors.onSurfaceVariant, onClick = onPause)
                            ActionPill(icon = Icons.Default.Close, tint = colors.onSurfaceVariant, onClick = onDelete)
                        }
                        TaskStatus.WAITING, TaskStatus.PAUSED -> {
                            ActionPill(icon = Icons.Default.PlayArrow, tint = colors.onSurfaceVariant, onClick = onResume)
                            ActionPill(icon = Icons.Default.Close, tint = colors.onSurfaceVariant, onClick = onDelete)
                        }
                        TaskStatus.ERROR, TaskStatus.COMPLETE, TaskStatus.REMOVED -> {
                            ActionPill(icon = Icons.Default.Refresh, tint = colors.onSurfaceVariant, onClick = onResume)
                            ActionPill(icon = Icons.Default.Delete, tint = colors.onSurfaceVariant, onClick = onDelete)
                        }
                        TaskStatus.SEEDING -> {
                            ActionPill(icon = Icons.Default.Stop, tint = colors.onSurfaceVariant, onClick = onPause)
                            ActionPill(icon = Icons.Default.Close, tint = colors.onSurfaceVariant, onClick = onDelete)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = animatedProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = statusColor,
                        trackColor = colors.surfaceVariant,
                    )
                }
                // Percentage text
                Text(
                    text = "$percentage%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    minLines = 1,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Info row: size | speed ↑↓ | ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size info (left)
                Text(
                    text = "${FormatUtils.bytesToSize(task.completedLength)} / ${FormatUtils.bytesToSize(task.totalLength)}",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // Right side info
                if (isCompleted) {
                    Text(
                        text = "Completed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusComplete
                    )
                } else if (isActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Download speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("↓", fontSize = 11.sp, color = colors.primary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = FormatUtils.bytesToSize(task.downloadSpeed) + "/s",
                                fontSize = 11.sp,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // Upload speed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("↑", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = FormatUtils.bytesToSize(task.uploadSpeed) + "/s",
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                        // ETA
                        if (etaText.isNotEmpty()) {
                            Text(
                                text = etaText,
                                fontSize = 11.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = task.status.displayName,
                        fontSize = 12.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun Speedometer(
    downloadSpeed: Long,
    uploadSpeed: Long,
    isEngineRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasActivity = isEngineRunning && downloadSpeed > 0

    Box(
        modifier = modifier
            .width(if (hasActivity) 150.dp else 44.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(22.dp))
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        if (hasActivity) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text("↓ ${FormatUtils.bytesToSize(downloadSpeed)}/s", fontSize = 11.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                    Text("↑ ${FormatUtils.bytesToSize(uploadSpeed)}/s", fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
            }
        } else {
            Icon(Icons.Default.Speed, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
