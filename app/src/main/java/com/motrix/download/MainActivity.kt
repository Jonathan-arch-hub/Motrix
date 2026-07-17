package com.motrix.download

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.motrix.download.data.datastore.PreferenceDataStore
import com.motrix.download.domain.model.AppTheme
import com.motrix.download.ui.navigation.NavGraph
import com.motrix.download.ui.navigation.Screen
import com.motrix.download.ui.theme.MotrixTheme
import com.motrix.download.ui.viewmodel.TaskListViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        var pendingLocale: String = "en"
    }

    private val taskListViewModel: TaskListViewModel by viewModels()

    @Inject
    lateinit var prefs: PreferenceDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    .launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        taskListViewModel.autoStartEngine()

        setContent {
            val theme by prefs.theme.collectAsState(initial = "auto")
            val appTheme = when (theme) {
                "light" -> AppTheme.LIGHT
                "dark" -> AppTheme.DARK
                else -> AppTheme.AUTO
            }

            MotrixTheme(theme = appTheme) {
                MainAppContent()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = pendingLocale
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.TaskList.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_downloads)) },
                    selected = currentRoute == Screen.TaskList.route,
                    onClick = {
                        if (currentRoute != Screen.TaskList.route) {
                            navController.navigate(Screen.TaskList.route) {
                                popUpTo(Screen.TaskList.route) { inclusive = true }
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_new_task)) },
                    selected = currentRoute == Screen.AddTask.route,
                    onClick = {
                        if (currentRoute != Screen.AddTask.route) {
                            navController.navigate(Screen.AddTask.route)
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_browser)) },
                    selected = currentRoute == Screen.Browser.route,
                    onClick = {
                        if (currentRoute != Screen.Browser.route) {
                            navController.navigate(Screen.Browser.route)
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = currentRoute == Screen.Settings.route,
                    onClick = {
                        if (currentRoute != Screen.Settings.route) {
                            navController.navigate(Screen.Settings.route)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavGraph(navController = navController)
        }
    }
}
