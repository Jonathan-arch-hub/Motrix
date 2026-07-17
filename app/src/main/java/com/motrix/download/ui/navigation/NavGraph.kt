package com.motrix.download.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.motrix.download.ui.tasklist.TaskListScreen
import com.motrix.download.ui.addtask.AddTaskScreen
import com.motrix.download.ui.taskdetail.TaskDetailScreen
import com.motrix.download.ui.settings.SettingsScreen
import com.motrix.download.ui.browser.BrowserScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.TaskList.route
    ) {
        composable(Screen.TaskList.route) {
            TaskListScreen(
                onAddTask = { navController.navigate(Screen.AddTask.route) },
                onTaskClick = { gid -> navController.navigate(Screen.TaskDetail.createRoute(gid)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.AddTask.route) {
            AddTaskScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.TaskDetail.route,
            arguments = listOf(navArgument("gid") { type = NavType.StringType })
        ) {
            TaskDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Browser.route) {
            BrowserScreen(
                onBack = { navController.popBackStack() },
                onUrlPaste = { url ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("pasted_url", url)
                    navController.popBackStack()
                }
            )
        }
    }
}
