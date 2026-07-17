package com.motrix.download.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object TaskList : Screen("task_list", "Downloads", Icons.Default.Download)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object Browser : Screen("browser", "Browser", Icons.Default.Language)
    data object About : Screen("about", "About", Icons.Default.Info)
    data object AddTask : Screen("add_task", "New Task", Icons.Default.Add)
    data object TaskDetail : Screen("task_detail/{gid}", "Task Detail", Icons.Default.Info) {
        fun createRoute(gid: String) = "task_detail/$gid"
    }
}
