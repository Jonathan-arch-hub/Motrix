package com.motrix.download.ui.addtask

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.motrix.download.R
import com.motrix.download.ui.viewmodel.AddTaskViewModel
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onBack: () -> Unit,
    viewModel: AddTaskViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isAdding by viewModel.isAdding.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var urls by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var authorization by remember { mutableStateOf("") }
    var maxConnections by remember { mutableStateOf("64") }
    var split by remember { mutableStateOf("64") }
    var torrentPath by remember { mutableStateOf("") }

    val torrentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val file = java.io.File(context.cacheDir, "selected.torrent")
            inputStream?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            torrentPath = file.absolutePath
        }
    }

    val addStartedText = stringResource(R.string.add_task_started)

    LaunchedEffect(Unit) {
        viewModel.taskAdded.collect {
            Toast.makeText(context, addStartedText, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    fun extractFileName(url: String): String {
        return try {
            val cleanUrl = url.trim()
            val decoded = URLDecoder.decode(cleanUrl, "UTF-8")
            val path = Uri.parse(decoded).path ?: return ""
            val name = path.substringAfterLast('/')
            if (name.isNotEmpty() && !name.contains("?") && !name.contains("=") && name.length > 3) name else ""
        } catch (_: Exception) { "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_task_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (selectedTab == 0) {
                                val options = mutableMapOf<String, Any>()
                                if (userAgent.isNotEmpty()) options["user-agent"] = userAgent
                                if (referer.isNotEmpty()) options["referer"] = referer
                                if (cookie.isNotEmpty()) options["header"] = "Cookie: $cookie"
                                if (authorization.isNotEmpty()) options["header"] = "Authorization: $authorization"
                                if (maxConnections.isNotEmpty()) options["max-connection-per-server"] = maxConnections.toIntOrNull()?.coerceIn(1, 16)?.toString() ?: "16"
                                if (split.isNotEmpty()) options["split"] = split.toIntOrNull()?.coerceIn(1, 16)?.toString() ?: "16"
                                var finalOut = out
                                if (finalOut.isEmpty()) {
                                    val firstUrl = urls.lines().firstOrNull { it.isNotBlank() } ?: ""
                                    finalOut = extractFileName(firstUrl)
                                }
                                viewModel.addUri(urls, finalOut, options)
                            } else {
                                val options = mutableMapOf<String, Any>()
                                if (maxConnections.isNotEmpty()) options["max-connection-per-server"] = maxConnections.toIntOrNull()?.coerceIn(1, 16)?.toString() ?: "16"
                                if (split.isNotEmpty()) options["split"] = split.toIntOrNull()?.coerceIn(1, 16)?.toString() ?: "16"
                                viewModel.addTorrent(torrentPath, options)
                            }
                        },
                        enabled = !isAdding
                    ) {
                        if (isAdding) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.start), fontWeight = FontWeight.Bold)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.add_task_uri_tab)) }, icon = { Icon(Icons.Default.Link, contentDescription = null) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.add_task_torrent_tab)) }, icon = { Icon(Icons.Default.FilePresent, contentDescription = null) })
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMessage ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = urls,
                    onValueChange = {
                        urls = it
                        if (out.isEmpty()) {
                            val firstUrl = it.lines().firstOrNull { l -> l.isNotBlank() } ?: ""
                            val extracted = extractFileName(firstUrl)
                            if (extracted.isNotEmpty()) out = extracted
                        }
                    },
                    label = { Text(stringResource(R.string.add_task_urls_hint)) },
                    placeholder = { Text(stringResource(R.string.add_task_urls_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 10
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = out,
                    onValueChange = { out = it },
                    label = { Text(stringResource(R.string.add_task_file_name_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (torrentPath.isEmpty()) {
                            Text(stringResource(R.string.add_task_no_torrent))
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { torrentPicker.launch(arrayOf("application/x-bittorrent", "application/octet-stream")) }) {
                                Icon(Icons.Default.FilePresent, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.add_task_select_torrent))
                            }
                        } else {
                            Text("Selected: ${java.io.File(torrentPath).name}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { torrentPath = "" }) { Text(stringResource(R.string.back)) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.add_task_advanced), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = userAgent, onValueChange = { userAgent = it }, label = { Text(stringResource(R.string.add_task_user_agent)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = referer, onValueChange = { referer = it }, label = { Text(stringResource(R.string.add_task_referer)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = cookie, onValueChange = { cookie = it }, label = { Text(stringResource(R.string.add_task_cookie)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = authorization, onValueChange = { authorization = it }, label = { Text(stringResource(R.string.add_task_authorization)) }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = maxConnections, onValueChange = { maxConnections = it }, label = { Text(stringResource(R.string.add_task_max_connections)) }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = split, onValueChange = { split = it }, label = { Text(stringResource(R.string.add_task_split)) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
