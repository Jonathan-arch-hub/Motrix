package com.motrix.download.ui.settings

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motrix.download.MainActivity
import com.motrix.download.R
import com.motrix.download.ui.viewmodel.SettingsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val theme by viewModel.theme.collectAsState(initial = "auto")
    val locale by viewModel.locale.collectAsState(initial = "en-US")
    val maxConcurrent by viewModel.maxConcurrentDownloads.collectAsState(initial = 5)
    val maxConnections by viewModel.maxConnectionPerServer.collectAsState(initial = 64)
    val split by viewModel.split.collectAsState(initial = 64)
    val seedRatio by viewModel.seedRatio.collectAsState(initial = 1.0f)
    val seedTime by viewModel.seedTime.collectAsState(initial = 60)
    val proxyEnabled by viewModel.proxyEnabled.collectAsState(initial = false)
    val proxyServer by viewModel.proxyServer.collectAsState(initial = "")
    val autoSyncTracker by viewModel.autoSyncTracker.collectAsState(initial = true)
    val enableNotifications by viewModel.enableNotifications.collectAsState(initial = true)
    val keepSeeding by viewModel.keepSeeding.collectAsState(initial = false)
    val resumeOnStart by viewModel.resumeAllOnStart.collectAsState(initial = false)
    val userAgent by viewModel.userAgent.collectAsState(initial = "")

    fun applyLocale(localeStr: String) {
        val lang = when (localeStr) {
            "ar" -> "ar"
            else -> "en"
        }
        MainActivity.pendingLocale = lang
        val loc = Locale(lang)
        Locale.setDefault(loc)
        val config = Configuration(context.resources.configuration)
        config.setLocale(loc)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        (context as? ComponentActivity)?.recreate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_appearance)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = theme == "auto", onClick = { viewModel.setTheme("auto") }, label = { Text(stringResource(R.string.settings_theme_auto)) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = theme == "light", onClick = { viewModel.setTheme("light") }, label = { Text(stringResource(R.string.settings_theme_light)) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = theme == "dark", onClick = { viewModel.setTheme("dark") }, label = { Text(stringResource(R.string.settings_theme_dark)) }, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = locale.startsWith("en"), onClick = {
                        viewModel.setLocale("en-US")
                        applyLocale("en")
                    }, label = { Text(stringResource(R.string.settings_lang_en)) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = locale.startsWith("ar"), onClick = {
                        viewModel.setLocale("ar")
                        applyLocale("ar")
                    }, label = { Text(stringResource(R.string.settings_lang_ar)) }, modifier = Modifier.weight(1f))
                }
            }

            SettingsSection(stringResource(R.string.settings_downloads)) {
                SettingsSlider(label = stringResource(R.string.settings_max_concurrent), value = maxConcurrent.toFloat(), onValueChange = { viewModel.setMaxConcurrentDownloads(it.toInt()) }, valueRange = 1f..20f, steps = 18)
                SettingsSlider(label = stringResource(R.string.settings_max_connections), value = maxConnections.toFloat(), onValueChange = { viewModel.setMaxConnectionPerServer(it.toInt()) }, valueRange = 1f..64f, steps = 62)
                SettingsSlider(label = stringResource(R.string.settings_split), value = split.toFloat(), onValueChange = { viewModel.setSplit(it.toInt()) }, valueRange = 1f..64f, steps = 62)
            }

            SettingsSection(stringResource(R.string.settings_bittorrent)) {
                SettingsSlider(label = stringResource(R.string.settings_seed_ratio), value = seedRatio, onValueChange = { viewModel.setSeedRatio(it) }, valueRange = 0f..10f, steps = 19)
                SettingsSlider(label = stringResource(R.string.settings_seed_time), value = seedTime.toFloat(), onValueChange = { viewModel.setSeedTime(it.toInt()) }, valueRange = 0f..120f, steps = 23)
                SettingsSwitch(label = stringResource(R.string.settings_keep_seeding), checked = keepSeeding, onCheckedChange = { viewModel.setKeepSeeding(it) })
                SettingsSwitch(label = stringResource(R.string.settings_auto_sync_trackers), checked = autoSyncTracker, onCheckedChange = { viewModel.setAutoSyncTracker(it) })
            }

            SettingsSection(stringResource(R.string.settings_network)) {
                SettingsSwitch(label = stringResource(R.string.settings_enable_proxy), checked = proxyEnabled, onCheckedChange = { viewModel.setProxyEnabled(it) })
                if (proxyEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = proxyServer, onValueChange = { viewModel.setProxyServer(it) }, label = { Text(stringResource(R.string.settings_proxy_server)) }, placeholder = { Text("http://127.0.0.1:8080") }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = userAgent, onValueChange = { viewModel.setUserAgent(it) }, label = { Text(stringResource(R.string.settings_user_agent)) }, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(stringResource(R.string.settings_behavior)) {
                SettingsSwitch(label = stringResource(R.string.settings_enable_notifications), checked = enableNotifications, onCheckedChange = { viewModel.setEnableNotifications(it) })
                SettingsSwitch(label = stringResource(R.string.settings_resume_on_start), checked = resumeOnStart, onCheckedChange = { viewModel.setResumeAllOnStart(it) })
            }

            SettingsSection(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.settings_engine_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
fun SettingsSlider(label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int = 0) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()}", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
